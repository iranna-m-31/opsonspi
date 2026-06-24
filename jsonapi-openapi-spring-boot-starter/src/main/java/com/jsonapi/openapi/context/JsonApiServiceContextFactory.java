package com.jsonapi.openapi.context;

import tools.jackson.databind.JsonNode;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.registry.OperationDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
public class JsonApiServiceContextFactory {

  private final OpenApiSpecRegistry registry;

  public JsonApiServiceContextFactory(OpenApiSpecRegistry registry) {
    this.registry = registry;
  }

  public JsonApiServiceContext create(HttpServletRequest request) {
    return create(request, null, null, null);
  }

  public JsonApiServiceContext create(
      HttpServletRequest request, JsonNode operationBody) {
    return create(request, operationBody, null, null);
  }

  public JsonApiServiceContext create(
      HttpServletRequest request,
      JsonNode operationBody,
      OperationDescriptor operation,
      String requestPath) {
    Map<String, String> pathVars = Map.of();
    if (operation != null && requestPath != null) {
      pathVars = registry.extractPathVariables(operation.pathTemplate(), requestPath);
    }
    return new JsonApiServiceContext(request, pathVars, operationBody);
  }
}
