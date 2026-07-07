package com.opsonapi.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.opsonapi.atomic.OpsonApiAtomicProcessor;
import com.opsonapi.autoconfigure.OpsonApiProperties;
import com.opsonapi.context.OpsonApiServiceContext;
import com.opsonapi.context.OpsonApiServiceContextFactory;
import com.opsonapi.model.OpsonApiResponseEntity;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.registry.OpsonApiOperationDescriptor;
import com.opsonapi.support.OpsonApiEntityMapper;
import com.opsonapi.support.OpsonApiRequestValidator;
import com.opsonapi.support.OpsonApiResponseAssembler;
import com.opsonapi.support.OpsonApiServiceInvoker;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dispatches JSON:API requests to service methods based on OpenAPI paths and x-service extensions.
 */
@RestController
@RequestMapping
public class OpsonApiDispatcherController {

  private final OpsonApiProperties properties;
  private final OpsonApiSpecRegistry registry;
  private final OpsonApiRequestValidator validator;
  private final OpsonApiServiceContextFactory contextFactory;
  private final OpsonApiEntityMapper entityMapper;
  private final OpsonApiServiceInvoker serviceInvoker;
  private final OpsonApiResponseAssembler assembler;
  private final OpsonApiAtomicProcessor atomicProcessor;

  public OpsonApiDispatcherController(
      OpsonApiProperties properties,
      OpsonApiSpecRegistry registry,
      OpsonApiRequestValidator validator,
      OpsonApiServiceContextFactory contextFactory,
      OpsonApiEntityMapper entityMapper,
      OpsonApiServiceInvoker serviceInvoker,
      OpsonApiResponseAssembler assembler,
      OpsonApiAtomicProcessor atomicProcessor) {
    this.properties = properties;
    this.registry = registry;
    this.validator = validator;
    this.contextFactory = contextFactory;
    this.entityMapper = entityMapper;
    this.serviceInvoker = serviceInvoker;
    this.assembler = assembler;
    this.atomicProcessor = atomicProcessor;
  }

  @RequestMapping(
      path = "${opsonapi.dispatcher-paths[0]:/api/**}",
      produces = OpsonApiSpecRegistry.JSON_API_MEDIA)
  @Transactional
  public ResponseEntity<JsonNode> dispatch(
      HttpServletRequest request, @RequestBody(required = false) JsonNode body) throws Exception {
    String path = request.getRequestURI();
    String method = request.getMethod();
    boolean hasBody = body != null && !body.isNull();

    validator.validateHeaders(request, hasBody);

    OpsonApiOperationDescriptor operation = registry.matchOperation(method, path);
    if (operation == null) {
      return ResponseEntity.notFound().build();
    }

    OpsonApiServiceContext context =
        contextFactory.create(request, body, operation, path);
    validator.validatePortalId(context);

    if (hasBody) {
      validator.validateRequestBody(body, operation);
    }

    if (operation.atomic()) {
      OpsonApiResponseEntity<Void> result =
          atomicProcessor.process(body, request, operation, path);
      if (result.httpStatus() == 204) {
        return ResponseEntity.noContent().build();
      }
    }

    String entityPackage = properties.getEntityPackage();
    Object entity = null;
    if (hasBody) {
      entity = entityMapper.mapRequestBody(body, operation, entityPackage, context);
    } else if (context.getPathVariable("id") != null) {
      entity = entityMapper.createProbeEntity(operation, entityPackage, context);
    }

    OpsonApiResponseEntity<?> serviceResult =
        serviceInvoker.invoke(registry.resolveService(operation), context, entity, operation);

    if (serviceResult.httpStatus() == 204) {
      return ResponseEntity.noContent().build();
    }
    JsonNode response = assembler.assemble(serviceResult, operation);
    return ResponseEntity.status(serviceResult.httpStatus())
        .contentType(MediaType.parseMediaType(OpsonApiSpecRegistry.JSON_API_MEDIA))
        .body(response);
  }
}