package com.jsonapi.openapi.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WireSchemaPatcherTest {

  @Test
  void patchesRequestBodyRefFromWireRefs() {
    OpenAPI openAPI = new OpenAPI();
    Paths paths = new Paths();
    PathItem pathItem = new PathItem();
    Operation operation = new Operation();
    operation.addExtension("x-entity-schema", "schemas/items.yaml");
    operation.addExtension("x-operation", "add-request");
    RequestBody body = new RequestBody();
    Content content = new Content();
    MediaType mediaType = new MediaType();
    mediaType.setSchema(new Schema<>().$ref("#/components/schemas/placeholder"));
    content.addMediaType("application/vnd.api+json", mediaType);
    body.setContent(content);
    operation.setRequestBody(body);
    pathItem.setPost(operation);
    paths.addPathItem("/api/items", pathItem);
    openAPI.setPaths(paths);

    Map<String, String> wireRefs =
        Map.of(
            "schemas/items.yaml::add-request",
            "schemas/items.yaml#/$defs/jsonapi-add-request");

    WireSchemaPatcher.patchRequestBodies(openAPI, wireRefs);

    Schema<?> schema =
        operation
            .getRequestBody()
            .getContent()
            .get("application/vnd.api+json")
            .getSchema();
    assertNotNull(schema);
    assertEquals("schemas/items.yaml#/$defs/jsonapi-add-request", schema.get$ref());
  }
}
