package com.opsonapi.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.opsonapi.atomic.OpsonApiAtomicOperationId;
import com.opsonapi.context.OpsonApiServiceContext;
import com.opsonapi.exception.OpsonApiValidationException;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.registry.OpsonApiOperationDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public class OpsonApiRequestValidator {

  private final OpsonApiSpecRegistry registry;

  public OpsonApiRequestValidator(OpsonApiSpecRegistry registry) {
    this.registry = registry;
  }

  public void validateHeaders(HttpServletRequest request, boolean hasBody) {
    if (hasBody) {
      String contentType = request.getContentType();
      if (contentType == null || !contentType.startsWith(OpsonApiSpecRegistry.JSON_API_MEDIA)) {
        throw new OpsonApiValidationException(
            415, "Unsupported Media Type", "Content-Type must be application/vnd.api+json");
      }
    }
    String accept = request.getHeader("Accept");
    if (accept != null
        && !accept.contains("*/*")
        && !accept.contains(OpsonApiSpecRegistry.JSON_API_MEDIA)) {
      throw new OpsonApiValidationException(
          406, "Not Acceptable", "Accept must include application/vnd.api+json");
    }
  }

  public void validateRequestBody(JsonNode body, OpsonApiOperationDescriptor operation) {
    if (body == null || body.isNull() || !body.has("data")) {
      return;
    }
    OpsonApiSpecRegistry.OperationConstraints constraints =
        registry.getOperationConstraints(operation);
    if (constraints == null || constraints.getRequired().isEmpty()) {
      return;
    }
    JsonNode data = body.get("data");
    if (data.isArray()) {
      return;
    }
    JsonNode attributes = data.get("attributes");
    for (String required : constraints.getRequired()) {
      if (required.startsWith("@") || required.startsWith("$")) {
        continue;
      }
      if (attributes == null
          || !attributes.isObject()
          || !attributes.has(required)
          || attributes.get(required).isNull()) {
        throw new OpsonApiValidationException(
            400, "Bad Request", "Missing required attribute: " + required);
      }
    }
  }

  public void validatePortalId(OpsonApiServiceContext context) {
    if (!StringUtils.hasText(context.getPortalId())) {
      throw new OpsonApiValidationException(
          400, "Bad Request", "portal_id query parameter is required");
    }
  }

  public void validateAtomicOperations(JsonNode body, OpsonApiOperationDescriptor operation) {
    if (!body.has("atomic:operations") || !body.get("atomic:operations").isArray()) {
      throw new OpsonApiValidationException(
          400, "Bad Request", "atomic:operations array is required");
    }
    if (body.get("atomic:operations").isEmpty()) {
      throw new OpsonApiValidationException(400, "Bad Request", "atomic:operations must not be empty");
    }
    for (JsonNode op : body.get("atomic:operations")) {
      String opId = OpsonApiAtomicOperationId.create(op);
      if (!operation.allowedAtomicOperations().contains(opId)) {
        throw new OpsonApiValidationException(
            400,
            "Bad Request",
            "Operation " + opId + " is not allowed on " + operation.pathTemplate());
      }
    }
  }
}