package com.jsonapi.openapi.gradle;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Map;

/** Patches OpenAPI path request bodies to use generated wire schemas. */
final class WireSchemaPatcher {

  private WireSchemaPatcher() {}

  static void patchRequestBodies(OpenAPI openAPI, Map<String, String> wireRefs) {
    if (openAPI.getPaths() == null || wireRefs.isEmpty()) {
      return;
    }
    openAPI
        .getPaths()
        .forEach(
            (path, item) -> {
              patchOperation(item.getGet(), wireRefs);
              patchOperation(item.getPost(), wireRefs);
              patchOperation(item.getPatch(), wireRefs);
              patchOperation(item.getPut(), wireRefs);
              patchOperation(item.getDelete(), wireRefs);
            });
  }

  private static void patchOperation(Operation operation, Map<String, String> wireRefs) {
    if (operation == null || operation.getRequestBody() == null) {
      return;
    }
    Map<String, Object> extensions = operation.getExtensions();
    if (extensions == null) {
      return;
    }
    Object entitySchemaObj = extensions.get("x-entity-schema");
    Object operationKeyObj = extensions.get("x-operation");
    if (entitySchemaObj == null || operationKeyObj == null) {
      return;
    }
    String operationKey = operationKeyObj.toString();
    if (!operationKey.endsWith("-request")) {
      return;
    }
    String entitySchema = EntitySchemaSupport.normalizeSchemaPath(entitySchemaObj.toString());
    String wireRef = wireRefs.get(entitySchema + "::" + operationKey);
    if (wireRef == null) {
      wireRef = WireSchemaGenerator.wireRef(entitySchema, operationKey);
    }
    operation.setRequestBody(copyRequestBodyWithRef(wireRef));
  }

  private static RequestBody copyRequestBodyWithRef(String ref) {
    RequestBody requestBody = new RequestBody();
    requestBody.setRequired(true);
    Content content = new Content();
    MediaType mediaType = new MediaType();
    Schema<?> schema = new Schema<>();
    schema.set$ref(ref);
    mediaType.setSchema(schema);
    content.addMediaType("application/vnd.api+json", mediaType);
    requestBody.setContent(content);
    return requestBody;
  }
}
