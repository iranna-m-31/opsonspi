package com.jsonapi.openapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads mitsogo-style entity schemas and validates path operation bindings. */
final class EntitySchemaSupport {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private EntitySchemaSupport() {}

  static Map<String, Map<String, String>> loadOperationServices(File specFile) {
    Map<String, Map<String, String>> byPath = new LinkedHashMap<>();
    File specDir = specFile.getParentFile();
    if (specDir == null) {
      return byPath;
    }
    File schemasDir = new File(specDir, "schemas");
    if (!schemasDir.isDirectory()) {
      return byPath;
    }
    File[] files =
        schemasDir.listFiles(
            (dir, name) ->
                name.endsWith(".yaml")
                    && !name.equals("common.yaml")
                    && !name.equals("atomic-operations.yaml"));
    if (files == null) {
      return byPath;
    }
    for (File entityFile : files) {
      try {
        JsonNode root = YAML.readTree(entityFile);
        String schemaPath = "schemas/" + entityFile.getName();
        Map<String, String> ops = parseOperationServices(root);
        if (!ops.isEmpty()) {
          byPath.put(schemaPath, ops);
        }
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to load entity schema " + entityFile.getAbsolutePath() + ": " + e.getMessage(),
            e);
      }
    }
    return byPath;
  }

  static List<String> validateEntityOperations(OpenAPI openAPI, File specFile) {
    List<String> errors = new ArrayList<>();
    Map<String, Map<String, String>> entityOps = loadOperationServices(specFile);
    File schemasDir = new File(specFile.getParentFile(), "schemas");
    if (schemasDir.isDirectory()) {
      File[] files =
          schemasDir.listFiles((dir, name) -> name.endsWith(".yaml") && !name.equals("common.yaml"));
      if (files != null) {
        for (File entityFile : files) {
          if (!nameLooksLikeEntityFile(entityFile.getName())) {
            continue;
          }
          validateEntitySchemaFile(entityFile, errors);
        }
      }
    }
    if (openAPI.getPaths() == null) {
      return errors;
    }
    openAPI
        .getPaths()
        .forEach(
            (path, item) -> {
              validateOperation(errors, path, "GET", item.getGet(), entityOps);
              validateOperation(errors, path, "POST", item.getPost(), entityOps);
              validateOperation(errors, path, "PATCH", item.getPatch(), entityOps);
              validateOperation(errors, path, "PUT", item.getPut(), entityOps);
              validateOperation(errors, path, "DELETE", item.getDelete(), entityOps);
            });
    return errors;
  }

  static List<String> validateWireSchemas(File specFile, File wireSchemasDir, OpenAPI openAPI) {
    List<String> errors = new ArrayList<>();
    if (openAPI.getPaths() == null) {
      return errors;
    }
    openAPI
        .getPaths()
        .forEach(
            (path, item) -> {
              validateWireRequestOperation(errors, path, "POST", item.getPost(), wireSchemasDir);
              validateWireRequestOperation(errors, path, "PATCH", item.getPatch(), wireSchemasDir);
              validateWireRequestOperation(errors, path, "PUT", item.getPut(), wireSchemasDir);
              validateWireResponseOperation(errors, path, "GET", item.getGet(), wireSchemasDir);
              validateWireResponseOperation(errors, path, "POST", item.getPost(), wireSchemasDir);
              validateWireResponseOperation(errors, path, "PATCH", item.getPatch(), wireSchemasDir);
              validateWireResponseOperation(errors, path, "PUT", item.getPut(), wireSchemasDir);
            });
    return errors;
  }

  private static void validateWireResponseOperation(
      List<String> errors, String path, String method, Operation operation, File wireSchemasDir) {
    if (operation == null || operation.getExtensions() == null || operation.getResponses() == null) {
      return;
    }
    Object entitySchemaObj = operation.getExtensions().get("x-entity-schema");
    Object operationKeyObj = operation.getExtensions().get("x-operation");
    if (entitySchemaObj == null || operationKeyObj == null) {
      return;
    }
    String requestOp = operationKeyObj.toString();
    if (!requestOp.endsWith("-request")) {
      return;
    }
    boolean hasSuccessBody =
        operation.getResponses().containsKey("200") || operation.getResponses().containsKey("201");
    if (!hasSuccessBody) {
      return;
    }
    String responseOp = WireSchemaGenerator.toResponseOperationKey(requestOp);
    if (responseOp == null) {
      return;
    }
    String entitySchema = normalizeSchemaPath(entitySchemaObj.toString());
    String wireDefName = "jsonapi-" + responseOp;
    File entityFile = new File(wireSchemasDir, "schemas/" + new File(entitySchema).getName());
    if (!entityFile.isFile()) {
      errors.add(path + " " + method + ": generated entity schema missing: " + entityFile.getName());
      return;
    }
    try {
      JsonNode root = YAML.readTree(entityFile);
      JsonNode wireDef = root.path("$defs").path(wireDefName);
      if (wireDef.isMissingNode() || !wireDef.isObject()) {
        errors.add(
            path
                + " "
                + method
                + ": missing generated wire response schema $defs/"
                + wireDefName
                + " in "
                + entityFile.getName());
      }
    } catch (Exception e) {
      errors.add(path + " " + method + ": failed to read wire response schema: " + e.getMessage());
    }
  }

  private static void validateWireRequestOperation(
      List<String> errors, String path, String method, Operation operation, File wireSchemasDir) {
    if (operation == null || operation.getExtensions() == null) {
      return;
    }
    Object entitySchemaObj = operation.getExtensions().get("x-entity-schema");
    Object operationKeyObj = operation.getExtensions().get("x-operation");
    if (entitySchemaObj == null || operationKeyObj == null) {
      return;
    }
    String operationKey = operationKeyObj.toString();
    if (!operationKey.endsWith("-request") || operation.getRequestBody() == null) {
      return;
    }
    String entitySchema = normalizeSchemaPath(entitySchemaObj.toString());
    String wireDefName = "jsonapi-" + operationKey;
    File entityFile = new File(wireSchemasDir, "schemas/" + new File(entitySchema).getName());
    if (!entityFile.isFile()) {
      errors.add(path + " " + method + ": generated entity schema missing: " + entityFile.getName());
      return;
    }
    try {
      JsonNode root = YAML.readTree(entityFile);
      JsonNode wireDef = root.path("$defs").path(wireDefName);
      if (wireDef.isMissingNode() || !wireDef.isObject()) {
        errors.add(
            path
                + " "
                + method
                + ": missing generated wire schema $defs/"
                + wireDefName
                + " in "
                + entityFile.getName());
        return;
      }
      validateOperationRequiredFields(errors, path, method, entityFile, operationKey, root);
    } catch (Exception e) {
      errors.add(path + " " + method + ": failed to read wire schema: " + e.getMessage());
    }
  }

  private static void validateOperationRequiredFields(
      List<String> errors,
      String path,
      String method,
      File entityFile,
      String operationKey,
      JsonNode mergedRoot) {
    JsonNode opDef = mergedRoot.path("$defs").path(operationKey);
    if (!opDef.isObject()) {
      return;
    }
    JsonNode required = opDef.get("required");
    if (required == null || !required.isArray()) {
      return;
    }
    JsonNode rootProps = mergedRoot.path("properties");
    for (JsonNode req : required) {
      String field = req.asText();
      if (field.startsWith("$")) {
        continue;
      }
      if (!rootProps.has(field)) {
        errors.add(
            entityFile.getName()
                + " "
                + operationKey
                + ": required field "
                + field
                + " not found on entity root");
      }
    }
  }

  static String resolveService(
      Operation operation, Map<String, Map<String, String>> entityOps) {
    if (operation == null || operation.getExtensions() == null) {
      return null;
    }
    Object entitySchemaObj = operation.getExtensions().get("x-entity-schema");
    Object operationKeyObj = operation.getExtensions().get("x-operation");
    if (entitySchemaObj == null || operationKeyObj == null) {
      Object pathService = operation.getExtensions().get("x-service");
      return pathService != null ? pathService.toString() : null;
    }
    String entitySchema = entitySchemaObj.toString();
    String operationKey = operationKeyObj.toString();
    Map<String, String> ops = entityOps.get(normalizeSchemaPath(entitySchema));
    if (ops != null) {
      String service = ops.get(operationKey);
      if (service != null) {
        return service;
      }
    }
    Object pathService = operation.getExtensions().get("x-service");
    return pathService != null ? pathService.toString() : null;
  }

  static boolean isEntityFile(String name) {
    return name.endsWith(".yaml")
        && !name.equals("common.yaml")
        && !name.equals("atomic-operations.yaml");
  }

  private static boolean nameLooksLikeEntityFile(String name) {
    return isEntityFile(name);
  }

  private static void validateOperation(
      List<String> errors,
      String path,
      String method,
      Operation operation,
      Map<String, Map<String, String>> entityOps) {
    if (operation == null || operation.getExtensions() == null) {
      return;
    }
    Object entitySchemaObj = operation.getExtensions().get("x-entity-schema");
    Object operationKeyObj = operation.getExtensions().get("x-operation");
    if (entitySchemaObj == null && operationKeyObj == null) {
      return;
    }
    if (entitySchemaObj == null || operationKeyObj == null) {
      errors.add(
          path
              + " "
              + method
              + ": x-entity-schema and x-operation must both be set when using entity dispatch");
      return;
    }
    String entitySchema = entitySchemaObj.toString();
    String operationKey = operationKeyObj.toString();
    Map<String, String> ops = entityOps.get(normalizeSchemaPath(entitySchema));
    if (ops == null) {
      errors.add(path + " " + method + ": entity schema not found: " + entitySchema);
      return;
    }
    String service = ops.get(operationKey);
    if (service == null) {
      errors.add(
          path
              + " "
              + method
              + ": no x-service branch for x-operation "
              + operationKey
              + " in "
              + entitySchema);
    }
  }

  private static Map<String, String> parseOperationServices(JsonNode root) {
    Map<String, String> services = new HashMap<>();
    JsonNode anyOf = root.path("dependentSchemas").path("$").path("anyOf");
    if (!anyOf.isArray()) {
      return services;
    }
    for (JsonNode branch : anyOf) {
      String ref = text(branch, "$ref");
      String service = text(branch, "x-service");
      if (ref == null || service == null) {
        continue;
      }
      String opKey = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
      services.put(opKey, service);
    }
    return services;
  }

  static void validateEntitySchemaFile(File entityFile, List<String> errors) {
    if (!nameLooksLikeEntityFile(entityFile.getName())) {
      return;
    }
    try {
      JsonNode root = YAML.readTree(entityFile);
      if (text(root, "x-entity") == null) {
        errors.add(entityFile.getName() + ": missing x-entity on root");
      }
      if (text(root, "x-resource-type") == null) {
        errors.add(entityFile.getName() + ": missing x-resource-type on root");
      }
      JsonNode properties = root.get("properties");
      if (properties == null || !properties.isObject()) {
        errors.add(entityFile.getName() + ": missing properties");
        return;
      }
      boolean hasId = false;
      Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        JsonNode prop = entry.getValue();
        if ("@id".equals(entry.getKey())) {
          hasId = true;
        }
        if (prop != null
            && prop.isObject()
            && text(prop, "x-field") == null
            && !entry.getKey().startsWith("$")) {
          errors.add(entityFile.getName() + ": property " + entry.getKey() + " missing x-field");
        }
      }
      if (!hasId) {
        errors.add(entityFile.getName() + ": missing @id property");
      }
    } catch (Exception e) {
      errors.add(entityFile.getName() + ": " + e.getMessage());
    }
  }

  static String normalizeSchemaPath(String entitySchema) {
    if (entitySchema == null) {
      return null;
    }
    String path = entitySchema;
    if (path.startsWith("classpath:")) {
      path = path.substring("classpath:".length());
    }
    int idx = path.indexOf("schemas/");
    if (idx >= 0) {
      return path.substring(idx);
    }
    return path;
  }

  private static String text(JsonNode node, String field) {
    if (node == null || !node.has(field) || node.get(field).isNull()) {
      return null;
    }
    return node.get(field).asText();
  }
}
