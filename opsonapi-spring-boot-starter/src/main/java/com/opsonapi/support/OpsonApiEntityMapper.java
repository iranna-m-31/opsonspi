package com.opsonapi.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.opsonapi.context.ServiceContext;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.registry.OpsonApiSpecRegistry.SchemaMetadata;
import com.opsonapi.registry.OpsonApiOperationDescriptor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpsonApiEntityMapper {

  private static final Logger log = LoggerFactory.getLogger(OpsonApiEntityMapper.class);
  private final OpsonApiSpecRegistry registry;
  private final ObjectMapper objectMapper;

  public OpsonApiEntityMapper(OpsonApiSpecRegistry registry, ObjectMapper objectMapper) {
    this.registry = registry;
    this.objectMapper = objectMapper;
  }

  public Object mapRequestBody(
      JsonNode body,
      OpsonApiOperationDescriptor operation,
      String entityPackage,
      ServiceContext context)
      throws Exception {
    if (body == null || body.isNull() || !body.has("data")) {
      return null;
    }
    JsonNode data = body.get("data");
    if (data.isArray()) {
      return mapRelationshipDataArray(body, operation, entityPackage, context);
    }
    String resourceType = data.has("type") ? data.get("type").asText() : operation.resourceType();
    SchemaMetadata meta = resolveMetadata(operation, resourceType);
    if (meta == null || meta.getEntityName() == null) {
      log.warn("No entity mapping for resource type {}", resourceType);
      return null;
    }

    String className = entityPackage + "." + capitalize(meta.getEntityName());
    Class<?> entityClass = Class.forName(className);
    Object entity = entityClass.getDeclaredConstructor().newInstance();

    if (data.has("id") && !data.get("id").isNull()) {
      setField(entity, "id", data.get("id").asText());
    }

    if (data.has("attributes") && data.get("attributes").isObject()) {
      if (meta.isDomainSchema()) {
        mapDomainAttributes(data.get("attributes"), entity, meta, operation);
      } else {
        mapLegacyAttributes(data.get("attributes"), entity, meta.getFieldMappings());
      }
    }

    if (data.has("relationships") && data.get("relationships").isObject()) {
      mapRelationships(data.get("relationships"), entity);
    }
    applyPathId(entity, context);
    return entity;
  }

  private SchemaMetadata resolveMetadata(OpsonApiOperationDescriptor operation, String resourceType) {
    if (operation.entitySchema() != null) {
      SchemaMetadata fromPath = registry.getEntitySchema(operation.entitySchema());
      if (fromPath != null) {
        return fromPath;
      }
    }
    return registry.getSchemaForResourceType(resourceType);
  }

  private Object mapRelationshipDataArray(
      JsonNode body,
      OpsonApiOperationDescriptor operation,
      String entityPackage,
      ServiceContext context)
      throws Exception {
    String resourceType = operation.resourceType();
    if (resourceType == null) {
      return null;
    }
    SchemaMetadata meta = registry.getSchemaForResourceType(resourceType);
    if (meta == null || meta.getEntityName() == null) {
      return null;
    }
    Class<?> entityClass = Class.forName(entityPackage + "." + capitalize(meta.getEntityName()));
    Object entity = entityClass.getDeclaredConstructor().newInstance();
    applyPathId(entity, context);

    String relName = inferRelationshipName(context != null ? context.getRequest().getRequestURI() : null);
    if (relName != null) {
      ObjectNode relPayload = objectMapper.createObjectNode();
      relPayload.set("data", body.get("data"));
      ObjectNode relationships = objectMapper.createObjectNode();
      relationships.set(relName, relPayload);
      setField(entity, "relationships", relationships);
    }
    return entity;
  }

  private void applyPathId(Object entity, ServiceContext context) throws Exception {
    if (entity == null || context == null) {
      return;
    }
    String id = context.getPathVariable("id");
    if (id != null) {
      setField(entity, "id", id);
    }
  }

  private String inferRelationshipName(String requestPath) {
    if (requestPath == null) {
      return null;
    }
    String marker = "/relationships/";
    int idx = requestPath.indexOf(marker);
    if (idx < 0) {
      return null;
    }
    String segment = requestPath.substring(idx + marker.length());
    int slash = segment.indexOf('/');
    if (slash >= 0) {
      segment = segment.substring(0, slash);
    }
    int query = segment.indexOf('?');
    if (query >= 0) {
      segment = segment.substring(0, query);
    }
    return kebabToCamel(segment);
  }

  private String kebabToCamel(String segment) {
    if (segment == null || segment.isEmpty()) {
      return segment;
    }
    String[] parts = segment.split("-");
    StringBuilder sb = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      if (parts[i].isEmpty()) {
        continue;
      }
      sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
    }
    return sb.toString();
  }

  public Object createProbeEntity(
      OpsonApiOperationDescriptor operation, String entityPackage, ServiceContext context)
      throws Exception {
    String resourceType = operation.resourceType();
    if (resourceType == null) return null;
    SchemaMetadata meta = resolveMetadata(operation, resourceType);
    if (meta == null || meta.getEntityName() == null) return null;
    Class<?> entityClass = Class.forName(entityPackage + "." + capitalize(meta.getEntityName()));
    Object entity = entityClass.getDeclaredConstructor().newInstance();
    String id = context.getPathVariable("id");
    if (id != null) {
      setField(entity, "id", id);
    }
    return entity;
  }

  public Object mapRelationshipRef(JsonNode ref, String resourceType, String entityPackage)
      throws Exception {
    SchemaMetadata meta = registry.getSchemaForResourceType(resourceType);
    if (meta == null || meta.getEntityName() == null) {
      return null;
    }
    String className = entityPackage + "." + capitalize(meta.getEntityName());
    Class<?> entityClass = Class.forName(className);
    Object entity = entityClass.getDeclaredConstructor().newInstance();
    if (ref.has("id")) {
      setField(entity, "id", ref.get("id").asText());
    }
    return entity;
  }

  public Object mapDataArray(JsonNode dataArray, String resourceType, String entityPackage)
      throws Exception {
    if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
      return null;
    }
    return mapRequestBody(
        objectMapper.createObjectNode().set("data", dataArray.get(0)),
        new OpsonApiOperationDescriptor("atomic", "POST", "/", null, List.of(), true, resourceType),
        entityPackage,
        null);
  }

  private void mapDomainAttributes(
      JsonNode attributes, Object entity, SchemaMetadata meta, OpsonApiOperationDescriptor operation)
      throws Exception {
    OpsonApiSpecRegistry.OperationConstraints constraints =
        registry.getOperationConstraints(operation);
    for (Map.Entry<String, String> entry : meta.getAttributeFields().entrySet()) {
      String jsonAttr = entry.getKey();
      if (constraints != null && constraints.isExcluded(jsonAttr)) {
        continue;
      }
      if (!attributes.has(jsonAttr)) {
        continue;
      }
      setFieldFromJson(entity, entry.getValue(), attributes.get(jsonAttr));
    }
  }

  private void mapLegacyAttributes(JsonNode attributes, Object entity, Map<String, String> mappings)
      throws Exception {
    Map<String, String> attrToEntity = new HashMap<>();
    for (Map.Entry<String, String> e : mappings.entrySet()) {
      if (!e.getKey().contains(".")) {
        attrToEntity.put(e.getKey(), e.getValue());
      } else if (e.getKey().startsWith("attributes.")) {
        attrToEntity.put(e.getKey().substring("attributes.".length()), e.getValue());
      }
    }
    attributes
        .properties()
        .forEach(
            entry -> {
              String jsonAttr = entry.getKey();
              String mapping = attrToEntity.get(jsonAttr);
              if (mapping == null) {
                mapping = mappings.get(jsonAttr);
              }
              if (mapping != null) {
                try {
                  applyLegacyMapping(entity, mapping, entry.getValue());
                } catch (Exception ex) {
                  log.debug("Could not map attribute {}: {}", jsonAttr, ex.getMessage());
                }
              }
            });
  }

  private void mapRelationships(JsonNode relationships, Object entity) throws Exception {
    ObjectNode relContainer = objectMapper.createObjectNode();
    relationships
        .properties()
        .forEach(rel -> relContainer.set(rel.getKey(), relationships.get(rel.getKey())));
    setField(entity, "relationships", relContainer);
  }

  private void applyLegacyMapping(Object entity, String mapping, JsonNode value) throws Exception {
    String simpleName = mapping.substring(mapping.indexOf('.') + 1);
    if (simpleName.contains(".")) {
      simpleName = simpleName.substring(simpleName.lastIndexOf('.') + 1);
    }
    setFieldFromJson(entity, simpleName, value);
  }

  private void setFieldFromJson(Object entity, String fieldName, JsonNode value) throws Exception {
    Field field = findField(entity.getClass(), fieldName);
    if (field == null) {
      field = findField(entity.getClass(), toCamelCase(fieldName));
    }
    if (field == null) return;
    field.setAccessible(true);
    Object converted = objectMapper.convertValue(value, field.getType());
    field.set(entity, converted);
  }

  private void setField(Object entity, String name, Object value) throws Exception {
    Field field = findField(entity.getClass(), name);
    if (field != null) {
      field.setAccessible(true);
      field.set(entity, value);
    }
  }

  private Field findField(Class<?> clazz, String name) {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private String toCamelCase(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }
}