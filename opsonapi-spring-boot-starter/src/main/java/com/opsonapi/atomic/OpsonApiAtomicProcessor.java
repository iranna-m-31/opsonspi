package com.opsonapi.atomic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.opsonapi.autoconfigure.OpsonApiProperties;
import com.opsonapi.context.OpsonApiServiceContext;
import com.opsonapi.context.OpsonApiServiceContextFactory;
import com.opsonapi.exception.OpsonApiValidationException;
import com.opsonapi.model.OpsonApiResponseEntity;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.registry.OpsonApiOperationDescriptor;
import com.opsonapi.support.OpsonApiEntityMapper;
import com.opsonapi.support.OpsonApiRequestValidator;
import com.opsonapi.support.OpsonApiServiceInvoker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes JSON:API atomic operations (see <a href="https://jsonapi.org/ext/atomic/">spec</a>).
 */
public class OpsonApiAtomicProcessor {

  private final OpsonApiProperties properties;
  private final OpsonApiServiceContextFactory contextFactory;
  private final OpsonApiRequestValidator validator;
  private final OpsonApiEntityMapper entityMapper;
  private final OpsonApiServiceInvoker serviceInvoker;
  private final OpsonApiSpecRegistry registry;
  private final ObjectMapper objectMapper;

  public OpsonApiAtomicProcessor(
      OpsonApiProperties properties,
      OpsonApiServiceContextFactory contextFactory,
      OpsonApiRequestValidator validator,
      OpsonApiEntityMapper entityMapper,
      OpsonApiServiceInvoker serviceInvoker,
      OpsonApiSpecRegistry registry,
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
  public OpsonApiResponseEntity<Void> process(
      JsonNode body, HttpServletRequest request, OpsonApiOperationDescriptor operation, String requestPath)
      throws Exception {
    validator.validateAtomicOperations(body, operation);
    OpsonApiServiceContext parentContext = contextFactory.create(request);

    Map<String, String> lidMapper = new HashMap<>();

    JsonNode operationsNode = body.get("atomic:operations");
    if (operationsNode != null && operationsNode.isObject()) {
      for (JsonNode operationNode : operationsNode) {
        resolveLidsInOperation(operationNode, lidMapper);

        String operationId = OpsonApiAtomicOperationId.create(operationNode);
        OpsonApiServiceContext opContext =
            contextFactory.create(request, operationNode, operation, requestPath);
        opContext.setParentContext(parentContext);

        Object entity = mapAtomicEntity(operationNode, operationId, operation, opContext);
        String serviceRef = registry.resolveAtomicOperationService(operation, operationId);
        if (serviceRef == null) {
          throw new OpsonApiValidationException(
              500, "Configuration Error", "No x-service for atomic operation " + operationId);
        }

        OpsonApiResponseEntity<?> result =
            serviceInvoker.invoke(serviceRef, opContext, entity, operation);
        storeLidFromResult(operationNode, result, lidMapper);
      }
    }
    return OpsonApiResponseEntity.noContent();
  }

  private void storeLidFromResult(
      JsonNode operationNode, OpsonApiResponseEntity<?> result, Map<String, String> lidMapper) {
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
      OpsonApiOperationDescriptor operation,
      OpsonApiServiceContext opContext)
      throws Exception {
    String resourceType = OpsonApiAtomicOperationId.resourceType(operationId);
    OpsonApiOperationDescriptor synthetic =
        new OpsonApiOperationDescriptor(
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