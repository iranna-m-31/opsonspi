package com.jsonapi.openapi.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.jsonapi.openapi.context.JsonApiServiceContext;
import com.jsonapi.openapi.exception.JsonApiValidationException;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.registry.OperationDescriptor;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "jsonapi.openapi.location=classpath:openapi/openapi.json",
      "jsonapi.openapi.entity-schemas-location=classpath:openapi/schemas/"
    })
class JsonApiRequestValidatorTest {

  @Autowired JsonApiRequestValidator validator;
  @Autowired OpenApiSpecRegistry registry;
  @Autowired ObjectMapper objectMapper;

  private OperationDescriptor createOp;

  @BeforeEach
  void setUp() {
    createOp = registry.matchOperation("POST", "/api/items");
  }

  @Test
  void acceptsJsonApiContentTypeForBodies() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setContentType("application/vnd.api+json");
    assertDoesNotThrow(() -> validator.validateHeaders(request, true));
  }

  @Test
  void rejectsUnsupportedContentType() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setContentType("application/json");
    assertThrows(JsonApiValidationException.class, () -> validator.validateHeaders(request, true));
  }

  @Test
  void rejectsAcceptHeaderWithoutJsonApi() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept", "application/json");
    assertThrows(JsonApiValidationException.class, () -> validator.validateHeaders(request, false));
  }

  @Test
  void validatesRequiredAttributes() {
    ObjectNode body = objectMapper.createObjectNode();
    ObjectNode data = objectMapper.createObjectNode();
    data.put("type", "items");
    data.set("attributes", objectMapper.createObjectNode());
    body.set("data", data);
    assertThrows(JsonApiValidationException.class, () -> validator.validateRequestBody(body, createOp));
  }

  @Test
  void requiresPortalId() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    JsonApiServiceContext context = new JsonApiServiceContext(request, Map.of(), null);
    assertThrows(JsonApiValidationException.class, () -> validator.validatePortalId(context));
  }

  @Test
  void acceptsPortalIdWhenPresent() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("portal_id", "portal-1");
    JsonApiServiceContext context = new JsonApiServiceContext(request, Map.of(), null);
    assertDoesNotThrow(() -> validator.validatePortalId(context));
  }
}
