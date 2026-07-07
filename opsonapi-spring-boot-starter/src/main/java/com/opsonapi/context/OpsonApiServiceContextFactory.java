package com.opsonapi.context;

import tools.jackson.databind.JsonNode;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.registry.OpsonApiOperationDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public class OpsonApiServiceContextFactory {

  private final OpsonApiSpecRegistry registry;

  public OpsonApiServiceContextFactory(OpsonApiSpecRegistry registry) {
    this.registry = registry;
  }

  public OpsonApiServiceContext create(HttpServletRequest request) {
    return create(request, null, null, null);
  }

  public OpsonApiServiceContext create(
      HttpServletRequest request, JsonNode operationBody) {
    return create(request, operationBody, null, null);
  }

  public OpsonApiServiceContext create(
      HttpServletRequest request,
      JsonNode operationBody,
      OpsonApiOperationDescriptor operation,
      String requestPath) {
    Map<String, String> pathVars = Map.of();
    if (operation != null && requestPath != null) {
      pathVars = registry.extractPathVariables(operation.pathTemplate(), requestPath);
    }
    return new OpsonApiServiceContext(request, pathVars, operationBody);
  }
}
