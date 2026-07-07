package com.opsonapi.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Loads domain entity schemas from YAML files on the classpath. */
final class OpsonApiEntitySchemaLoader {

  private static final Logger log = LoggerFactory.getLogger(OpsonApiEntitySchemaLoader.class);
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private OpsonApiEntitySchemaLoader() {}

  static Map<String, OpsonApiSpecRegistry.SchemaMetadata> loadEntitySchemas(
      ResourceLoader resourceLoader, String entitySchemasLocation) {
    Map<String, OpsonApiSpecRegistry.SchemaMetadata> byPath = new LinkedHashMap<>();
    Map<String, OpsonApiSpecRegistry.SchemaMetadata> byResourceType = new LinkedHashMap<>();

    String pattern = toClasspathPattern(entitySchemasLocation);
    PathMatchingResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver(resourceLoader);

    try {
      Resource[] resources = resolver.getResources(pattern);
      for (Resource resource : resources) {
        if (!resource.exists() || !resource.isReadable()) {
          continue;
        }
        String fileName = resource.getFilename();
        if (fileName == null
            || !fileName.endsWith(".yaml")
            || "common.yaml".equals(fileName)
            || "atomic-operations.yaml".equals(fileName)) {
          continue;
        }
        loadEntityResource(resource, fileName, byPath, byResourceType);
      }
    } catch (Exception e) {
      log.warn("Failed to scan entity schemas at {}: {}", pattern, e.getMessage());
    }

    byPath.putAll(
        byResourceType.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    e -> "resourceType:" + e.getKey(), Map.Entry::getValue)));
    return byPath;
  }

  private static String toClasspathPattern(String location) {
    String base = location == null ? "classpath:opsonapi/schemas/" : location.trim();
    if (!base.startsWith("classpath:") && !base.startsWith("file:")) {
      base = "classpath:" + base;
    }
    if (!base.endsWith("/")) {
      base = base + "/";
    }
    return base + "*.yaml";
  }

  private static void loadEntityResource(
      Resource resource,
      String fileName,
      Map<String, OpsonApiSpecRegistry.SchemaMetadata> byPath,
      Map<String, OpsonApiSpecRegistry.SchemaMetadata> byResourceType) {
    String path = "schemas/" + fileName;
    try (InputStream in = resource.getInputStream()) {
      JsonNode root = YAML.readTree(in);
      OpsonApiSpecRegistry.SchemaMetadata meta = parseEntitySchema(root, path);
      if (meta != null) {
        byPath.put(normalizeSchemaPath(path), meta);
        if (meta.getResourceType() != null) {
          byResourceType.put(meta.getResourceType(), meta);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to load entity schema {}: {}", fileName, e.getMessage());
    }
  }

  static String normalizeSchemaPath(String path) {
    if (path == null) {
      return null;
    }
    String p = path;
    if (p.startsWith("classpath:")) {
      p = p.substring("classpath:".length());
    }
    int idx = p.indexOf("schemas/");
    if (idx >= 0) {
      return p.substring(idx);
    }
    return p;
  }

  private static OpsonApiSpecRegistry.SchemaMetadata parseEntitySchema(JsonNode root, String path) {
    if (root == null || !root.isObject()) {
      return null;
    }
    if (text(root, "x-entity") == null) {
      return null;
    }
    OpsonApiSpecRegistry.SchemaMetadata meta = new OpsonApiSpecRegistry.SchemaMetadata();
    meta.setSchemaPath(normalizeSchemaPath(path));
    meta.setEntityName(text(root, "x-entity"));
    meta.setResourceType(text(root, "x-resource-type"));
    meta.setFilterable(readStringList(root.get("x-filterable")));
    meta.setSortable(readStringList(root.get("x-sortable")));

    JsonNode properties = root.get("properties");
    if (properties != null && properties.isObject()) {
      for (Map.Entry<String, JsonNode> entry : properties.properties()) {
        String propName = entry.getKey();
        JsonNode prop = entry.getValue();
        String xField = text(prop, "x-field");
        if (xField != null) {
          meta.getXFieldMappings().put(propName, xField);
          String javaField = javaFieldFromXField(xField, propName);
          if (propName.startsWith("@")) {
            String wireName = propName.substring(1);
            meta.getRelationshipFields().put(propName, wireName);
            meta.getFieldMappings().put(wireName, javaField);
          } else {
            meta.getAttributeFields().put(propName, javaField);
            meta.getFieldMappings().put(propName, xField);
          }
        }
        if (Boolean.TRUE.equals(prop.get("x-filterable"))) {
          meta.getFilterable().add(stripAt(propName));
        }
        if (Boolean.TRUE.equals(prop.get("x-sortable"))) {
          meta.getSortable().add(stripAt(propName));
        }
      }
    }

    parseOperationServices(root, meta);
    parseOperationConstraints(root, meta);
    return meta;
  }

  private static void parseOperationConstraints(
      JsonNode root, OpsonApiSpecRegistry.SchemaMetadata meta) {
    JsonNode defs = root.get("$defs");
    if (defs == null || !defs.isObject()) {
      return;
    }
    defs.properties()
        .forEach(
            entry -> {
              String opKey = entry.getKey();
              if (!opKey.endsWith("-request")) {
                return;
              }
              JsonNode opDef = entry.getValue();
              if (!opDef.isObject()) {
                return;
              }
              OpsonApiSpecRegistry.OperationConstraints constraints =
                  new OpsonApiSpecRegistry.OperationConstraints();
              JsonNode required = opDef.get("required");
              if (required != null && required.isArray()) {
                required.forEach(n -> constraints.getRequired().add(n.asText()));
              }
              JsonNode props = opDef.get("properties");
              if (props != null && props.isObject()) {
                props
                    .properties()
                    .forEach(
                        prop -> {
                          if (prop.getValue().isBoolean() && !prop.getValue().booleanValue()) {
                            constraints.getExcluded().add(prop.getKey());
                          }
                        });
              }
              meta.getOperationConstraints().put(opKey, constraints);
            });
  }

  private static void parseOperationServices(JsonNode root, OpsonApiSpecRegistry.SchemaMetadata meta) {
    JsonNode anyOf = root.path("dependentSchemas").path("$").path("anyOf");
    if (!anyOf.isArray()) {
      return;
    }
    for (JsonNode branch : anyOf) {
      String ref = text(branch, "$ref");
      String service = text(branch, "x-service");
      if (ref == null || service == null) {
        continue;
      }
      String opKey = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
      meta.getOperationServices().put(opKey, service);
    }
  }

  private static String javaFieldFromXField(String xField, String propName) {
    if (xField != null && xField.contains("/")) {
      return xField.substring(xField.lastIndexOf('/') + 1);
    }
    return stripAt(propName);
  }

  private static String stripAt(String name) {
    return name != null && name.startsWith("@") ? name.substring(1) : name;
  }

  private static String text(JsonNode node, String field) {
    if (node == null || !node.has(field) || node.get(field).isNull()) {
      return null;
    }
    return node.get(field).asText();
  }

  private static List<String> readStringList(JsonNode node) {
    if (node == null || !node.isArray()) {
      return new ArrayList<>();
    }
    List<String> out = new ArrayList<>();
    node.forEach(n -> out.add(n.asText()));
    return out;
  }
}