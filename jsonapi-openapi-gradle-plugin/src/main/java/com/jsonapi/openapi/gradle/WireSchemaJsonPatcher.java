package com.jsonapi.openapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Patches OpenAPI path request/response bodies on a JsonNode tree to use generated wire schemas. */
final class WireSchemaJsonPatcher {

  private static final String JSON_API_MEDIA = "application/vnd.api+json";
  private static final Set<String> SUCCESS_RESPONSE_CODES = Set.of("200", "201");
  private static final Set<String> ERROR_RESPONSE_CODES = Set.of("400", "404");

  private WireSchemaJsonPatcher() {}

  static void patchRequestBodies(JsonNode root, Map<String, String> wireRefs) {
    walkEntityOperations(
        root, (operation, entitySchema, requestOp) -> patchRequestBody(operation, entitySchema, requestOp, wireRefs));
  }

  static void patchResponses(JsonNode root, Map<String, String> wireRefs) {
    walkEntityOperations(
        root,
        (operation, entitySchema, requestOp) ->
            patchSuccessResponses(operation, entitySchema, requestOp, wireRefs));
    patchErrorResponses(root);
  }

  private interface EntityOperationVisitor {
    void accept(JsonNode operation, String entitySchema, String requestOperationKey);
  }

  private static void walkEntityOperations(JsonNode root, EntityOperationVisitor visitor) {
    JsonNode paths = root.get("paths");
    if (paths == null || !paths.isObject()) {
      return;
    }
    paths
        .properties()
        .forEach(
            pathEntry ->
                pathEntry
                    .getValue()
                    .properties()
                    .forEach(
                        opEntry -> {
                          if (!isHttpMethod(opEntry.getKey())) {
                            return;
                          }
                          JsonNode operation = opEntry.getValue();
                          if (operation == null || !operation.isObject()) {
                            return;
                          }
                          JsonNode entitySchemaNode = operation.get("x-entity-schema");
                          JsonNode operationKeyNode = operation.get("x-operation");
                          if (entitySchemaNode == null || operationKeyNode == null) {
                            return;
                          }
                          String requestOp = operationKeyNode.asText();
                          if (!requestOp.endsWith("-request")) {
                            return;
                          }
                          visitor.accept(
                              operation,
                              EntitySchemaSupport.normalizeSchemaPath(entitySchemaNode.asText()),
                              requestOp);
                        }));
  }

  private static void walkAllOperations(JsonNode root, java.util.function.Consumer<JsonNode> visitor) {
    JsonNode paths = root.get("paths");
    if (paths == null || !paths.isObject()) {
      return;
    }
    paths
        .properties()
        .forEach(
            pathEntry ->
                pathEntry
                    .getValue()
                    .properties()
                    .forEach(
                        opEntry -> {
                          if (!isHttpMethod(opEntry.getKey())) {
                            return;
                          }
                          JsonNode operation = opEntry.getValue();
                          if (operation != null && operation.isObject()) {
                            visitor.accept(operation);
                          }
                        }));
  }

  private static void patchRequestBody(
      JsonNode operation, String entitySchema, String requestOp, Map<String, String> wireRefs) {
    JsonNode requestBody = operation.get("requestBody");
    if (requestBody == null || !requestBody.isObject()) {
      return;
    }
    String wireRef = wireRef(wireRefs, entitySchema, requestOp);
    ObjectNode requestBodyObject = (ObjectNode) requestBody;
    requestBodyObject.put("required", true);
    setMediaTypeSchema(requestBodyObject, wireRef);
  }

  private static void patchSuccessResponses(
      JsonNode operation, String entitySchema, String requestOp, Map<String, String> wireRefs) {
    String responseOp = WireSchemaGenerator.toResponseOperationKey(requestOp);
    if (responseOp == null) {
      return;
    }
    String wireRef = wireRef(wireRefs, entitySchema, responseOp);
    if (wireRef == null) {
      return;
    }
    JsonNode responses = operation.get("responses");
    if (responses == null || !responses.isObject()) {
      return;
    }
    for (String code : SUCCESS_RESPONSE_CODES) {
      JsonNode response = responses.get(code);
      if (response == null || !isJsonApiComponentResponse(response)) {
        continue;
      }
      inlineResponseSchema((ObjectNode) response, wireRef, defaultDescription(code));
    }
  }

  private static void patchErrorResponses(JsonNode root) {
    walkAllOperations(
        root,
        operation -> {
          JsonNode responses = operation.get("responses");
          if (responses == null || !responses.isObject()) {
            return;
          }
          for (String code : ERROR_RESPONSE_CODES) {
            JsonNode response = responses.get(code);
            if (response == null || !isErrorComponentResponse(response)) {
              continue;
            }
            inlineResponseSchema(
                (ObjectNode) response, WireSchemaGenerator.ERROR_WIRE_REF, errorDescription(code));
          }
        });
  }

  private static void inlineResponseSchema(
      ObjectNode responseObject, String wireRef, String description) {
    responseObject.remove("$ref");
    responseObject.put("description", description);
    ObjectNode content =
        responseObject.has("content") && responseObject.get("content").isObject()
            ? (ObjectNode) responseObject.get("content")
            : responseObject.putObject("content");
    ObjectNode mediaType =
        content.has(JSON_API_MEDIA) && content.get(JSON_API_MEDIA).isObject()
            ? (ObjectNode) content.get(JSON_API_MEDIA)
            : content.putObject(JSON_API_MEDIA);
    ObjectNode schema = mediaType.putObject("schema");
    schema.put("$ref", wireRef);
  }

  private static boolean isJsonApiComponentResponse(JsonNode response) {
    if (!response.has("$ref")) {
      JsonNode content = response.path("content").path(JSON_API_MEDIA);
      return !content.isMissingNode();
    }
    String ref = response.get("$ref").asText("").toLowerCase(Locale.ROOT);
    return ref.contains("/responses/")
        && !ref.contains("badrequest")
        && !ref.contains("notfound")
        && !ref.contains("error");
  }

  private static boolean isErrorComponentResponse(JsonNode response) {
    if (!response.has("$ref")) {
      return response.has("content")
          && response.path("content").has(JSON_API_MEDIA)
          && response.path("content").path(JSON_API_MEDIA).path("schema").path("properties").has("errors");
    }
    String ref = response.get("$ref").asText("").toLowerCase(Locale.ROOT);
    return ref.contains("badrequest") || ref.contains("notfound");
  }

  private static String defaultDescription(String code) {
    return switch (code) {
      case "201" -> "Created";
      default -> "Success";
    };
  }

  private static String errorDescription(String code) {
    return switch (code) {
      case "404" -> "Not found";
      default -> "Bad request";
    };
  }

  private static void setMediaTypeSchema(ObjectNode requestBodyObject, String wireRef) {
    ObjectNode content =
        requestBodyObject.has("content") && requestBodyObject.get("content").isObject()
            ? (ObjectNode) requestBodyObject.get("content")
            : requestBodyObject.putObject("content");
    ObjectNode mediaType =
        content.has(JSON_API_MEDIA) && content.get(JSON_API_MEDIA).isObject()
            ? (ObjectNode) content.get(JSON_API_MEDIA)
            : content.putObject(JSON_API_MEDIA);
    ObjectNode schema = mediaType.putObject("schema");
    schema.put("$ref", wireRef);
  }

  private static String wireRef(Map<String, String> wireRefs, String entitySchema, String operationKey) {
    String ref = wireRefs.get(entitySchema + "::" + operationKey);
    if (ref == null) {
      ref = WireSchemaGenerator.wireRef(entitySchema, operationKey);
    }
    return ref;
  }

  private static boolean isHttpMethod(String method) {
    return switch (method) {
      case "get", "post", "put", "patch", "delete", "head", "options" -> true;
      default -> false;
    };
  }
}
