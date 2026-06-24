package com.jsonapi.openapi.support;

import tools.jackson.databind.JsonNode;
import com.jsonapi.openapi.atomic.AtomicOperationId;
import com.jsonapi.openapi.context.ServiceContext;
import com.jsonapi.openapi.exception.JsonApiValidationException;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.registry.OperationDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public class JsonApiRequestValidator {

  private final OpenApiSpecRegistry registry;

  public JsonApiRequestValidator(OpenApiSpecRegistry registry) {
    this.registry = registry;
  }

  public void validateHeaders(HttpServletRequest request, boolean hasBody) {
    if (hasBody) {
      String contentType = request.getContentType();
      if (contentType == null || !contentType.startsWith(OpenApiSpecRegistry.JSON_API_MEDIA)) {
        throw new JsonApiValidationException(
            415, "Unsupported Media Type", "Content-Type must be application/vnd.api+json");
      }
    }
    String accept = request.getHeader("Accept");
    if (accept != null
        && !accept.contains("*/*")
        && !accept.contains(OpenApiSpecRegistry.JSON_API_MEDIA)) {
      throw new JsonApiValidationException(
          406, "Not Acceptable", "Accept must include application/vnd.api+json");
    }
  }

  public void validateRequestBody(JsonNode body, OperationDescriptor operation) {
    if (body == null || body.isNull() || !body.has("data")) {
      return;
    }
    OpenApiSpecRegistry.OperationConstraints constraints =
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
        throw new JsonApiValidationException(
            400, "Bad Request", "Missing required attribute: " + required);
      }
    }
  }

  public void validatePortalId(ServiceContext context) {
    if (!StringUtils.hasText(context.getPortalId())) {
      throw new JsonApiValidationException(
          400, "Bad Request", "portal_id query parameter is required");
    }
  }

  public void validateAtomicOperations(JsonNode body, OperationDescriptor operation) {
    if (!body.has("atomic:operations") || !body.get("atomic:operations").isArray()) {
      throw new JsonApiValidationException(
          400, "Bad Request", "atomic:operations array is required");
    }
    if (body.get("atomic:operations").isEmpty()) {
      throw new JsonApiValidationException(400, "Bad Request", "atomic:operations must not be empty");
    }
    for (JsonNode op : body.get("atomic:operations")) {
      String opId = AtomicOperationId.create(op);
      if (!operation.allowedAtomicOperations().contains(opId)) {
        throw new JsonApiValidationException(
            400,
            "Bad Request",
            "Operation " + opId + " is not allowed on " + operation.pathTemplate());
      }
    }
  }
}
