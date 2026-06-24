package com.jsonapi.openapi.atomic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.jsonapi.openapi.autoconfigure.JsonApiOpenApiProperties;
import com.jsonapi.openapi.context.JsonApiServiceContext;
import com.jsonapi.openapi.context.JsonApiServiceContextFactory;
import com.jsonapi.openapi.exception.JsonApiValidationException;
import com.jsonapi.openapi.model.JsonApiResponseEntity;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.registry.OperationDescriptor;
import com.jsonapi.openapi.support.JsonApiEntityMapper;
import com.jsonapi.openapi.support.JsonApiRequestValidator;
import com.jsonapi.openapi.support.ServiceInvoker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

public class JsonApiAtomicProcessor {

  private final JsonApiOpenApiProperties properties;
  private final JsonApiServiceContextFactory contextFactory;
  private final JsonApiRequestValidator validator;
  private final JsonApiEntityMapper entityMapper;
  private final ServiceInvoker serviceInvoker;
  private final OpenApiSpecRegistry registry;
  private final ObjectMapper objectMapper;

  public JsonApiAtomicProcessor(
      JsonApiOpenApiProperties properties,
      JsonApiServiceContextFactory contextFactory,
      JsonApiRequestValidator validator,
      JsonApiEntityMapper entityMapper,
      ServiceInvoker serviceInvoker,
      OpenApiSpecRegistry registry,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.contextFactory = contextFactory;
    this.validator = validator;
    this.entityMapper = entityMapper;
    this.serviceInvoker = serviceInvoker;
    this.registry = registry;
    this.objectMapper = objectMapper;
  }

  @Transactional(rollbackFor = Exception.class)
  public JsonApiResponseEntity<Void> process(
      JsonNode body, HttpServletRequest request, OperationDescriptor operation, String requestPath)
      throws Exception {
    validator.validateAtomicOperations(body, operation);
    JsonApiServiceContext parentContext = contextFactory.create(request);

    Map<String, String> lidMapper = new HashMap<>();

    for (JsonNode operationNode : body.get("atomic:operations").values()) {
      resolveLidsInOperation(operationNode, lidMapper);

      String operationId = AtomicOperationId.create(operationNode);
      JsonApiServiceContext opContext =
          contextFactory.create(request, operationNode, operation, requestPath);
      opContext.setParentContext(parentContext);

      Object entity = mapAtomicEntity(operationNode, operationId, operation, opContext);
      String serviceRef = registry.resolveAtomicOperationService(operation, operationId);
      if (serviceRef == null) {
        throw new JsonApiValidationException(
            500, "Configuration Error", "No x-service for atomic operation " + operationId);
      }

      JsonApiResponseEntity<?> result =
          serviceInvoker.invoke(serviceRef, opContext, entity, operation);
      storeLidFromResult(operationNode, result, lidMapper);
    }
    return JsonApiResponseEntity.noContent();
  }

  private void storeLidFromResult(
      JsonNode operationNode, JsonApiResponseEntity<?> result, Map<String, String> lidMapper) {
    if (result.entity() == null || !operationNode.has("data")) return;
    JsonNode data = operationNode.get("data");
    if (data.has("lid") && data instanceof ObjectNode) {
      try {
        var getId = result.entity().getClass().getMethod("getId");
        Object id = getId.invoke(result.entity());
        if (id != null) {
          lidMapper.put(data.get("lid").asText(), id.toString());
        }
      } catch (Exception ignored) {
      }
    }
  }

  private void resolveLidsInOperation(JsonNode operationNode, Map<String, String> lidMapper) {
    JsonNode dataNode = operationNode.get("data");
    if (dataNode == null) return;
    if (dataNode.isArray()) {
      for (JsonNode item : dataNode) {
        resolveLidOnResource(item, lidMapper);
      }
    } else if (dataNode.isObject()) {
      resolveLidOnResource(dataNode, lidMapper);
      JsonNode relationships = dataNode.get("relationships");
      if (relationships != null && relationships.isObject()) {
        relationships
            .properties()
            .forEach(
                entry -> {
                  JsonNode relData = entry.getValue().get("data");
                  if (relData != null && relData.isArray()) {
                    for (JsonNode item : relData) {
                      resolveLidOnResource(item, lidMapper);
                    }
                  } else if (relData != null) {
                    resolveLidOnResource(relData, lidMapper);
                  }
                });
      }
    }
  }

  private void resolveLidOnResource(JsonNode resource, Map<String, String> lidMapper) {
    if (resource.has("lid") && lidMapper.containsKey(resource.get("lid").asText())) {
      if (resource instanceof ObjectNode obj) {
        obj.put("id", lidMapper.get(resource.get("lid").asText()));
        obj.remove("lid");
      }
    }
  }

  private Object mapAtomicEntity(
      JsonNode operationNode,
      String operationId,
      OperationDescriptor operation,
      JsonApiServiceContext opContext)
      throws Exception {
    String resourceType = AtomicOperationId.resourceType(operationId);
    OperationDescriptor synthetic =
        new OperationDescriptor(
            operationId,
            "POST",
            operation.pathTemplate(),
            operation.service(),
            operation.allowedAtomicOperations(),
            true,
            resourceType);

    if (operationNode.has("data")) {
      JsonNode data = operationNode.get("data");
      if (data.isArray()) {
        return entityMapper.mapDataArray(data, resourceType, properties.getEntityPackage());
      }
      ObjectNode wrapper = objectMapper.createObjectNode();
      wrapper.set("data", data);
      return entityMapper.mapRequestBody(wrapper, synthetic, properties.getEntityPackage(), opContext);
    }
    if (operationNode.has("ref")) {
      return entityMapper.mapRelationshipRef(
          operationNode.get("ref"), resourceType, properties.getEntityPackage());
    }
    return null;
  }
}
