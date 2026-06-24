package com.jsonapi.openapi.web;

import tools.jackson.databind.JsonNode;
import com.jsonapi.openapi.atomic.JsonApiAtomicProcessor;
import com.jsonapi.openapi.autoconfigure.JsonApiOpenApiProperties;
import com.jsonapi.openapi.context.JsonApiServiceContext;
import com.jsonapi.openapi.context.JsonApiServiceContextFactory;
import com.jsonapi.openapi.model.JsonApiResponseEntity;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.registry.OperationDescriptor;
import com.jsonapi.openapi.support.JsonApiEntityMapper;
import com.jsonapi.openapi.support.JsonApiRequestValidator;
import com.jsonapi.openapi.support.JsonApiResponseAssembler;
import com.jsonapi.openapi.support.ServiceInvoker;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class JsonApiDispatcherController {

  private final JsonApiOpenApiProperties properties;
  private final OpenApiSpecRegistry registry;
  private final JsonApiRequestValidator validator;
  private final JsonApiServiceContextFactory contextFactory;
  private final JsonApiEntityMapper entityMapper;
  private final ServiceInvoker serviceInvoker;
  private final JsonApiResponseAssembler assembler;
  private final JsonApiAtomicProcessor atomicProcessor;

  public JsonApiDispatcherController(
      JsonApiOpenApiProperties properties,
      OpenApiSpecRegistry registry,
      JsonApiRequestValidator validator,
      JsonApiServiceContextFactory contextFactory,
      JsonApiEntityMapper entityMapper,
      ServiceInvoker serviceInvoker,
      JsonApiResponseAssembler assembler,
      JsonApiAtomicProcessor atomicProcessor) {
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
      path = "${jsonapi.openapi.dispatcher-paths[0]:/api/**}",
      produces = OpenApiSpecRegistry.JSON_API_MEDIA)
  @Transactional
  public ResponseEntity<JsonNode> dispatch(
      HttpServletRequest request, @RequestBody(required = false) JsonNode body) throws Exception {
    String path = request.getRequestURI();
    String method = request.getMethod();
    boolean hasBody = body != null && !body.isNull();

    validator.validateHeaders(request, hasBody);

    OperationDescriptor operation = registry.matchOperation(method, path);
    if (operation == null) {
      return ResponseEntity.notFound().build();
    }

    JsonApiServiceContext context =
        contextFactory.create(request, body, operation, path);
    validator.validatePortalId(context);

    if (hasBody) {
      validator.validateRequestBody(body, operation);
    }

    if (operation.atomic()) {
      JsonApiResponseEntity<Void> result =
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

    JsonApiResponseEntity<?> serviceResult =
        serviceInvoker.invoke(registry.resolveService(operation), context, entity, operation);

    if (serviceResult.httpStatus() == 204) {
      return ResponseEntity.noContent().build();
    }
    JsonNode response = assembler.assemble(serviceResult, operation);
    return ResponseEntity.status(serviceResult.httpStatus())
        .contentType(MediaType.parseMediaType(OpenApiSpecRegistry.JSON_API_MEDIA))
        .body(response);
  }
}
