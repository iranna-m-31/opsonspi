package com.opsonapi.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.opsonapi.context.OpsonApiServiceContext;
import com.opsonapi.exception.OpsonApiValidationException;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.registry.OpsonApiOperationDescriptor;
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
      "opsonapi.location=classpath:openapi/openapi.json",
      "opsonapi.entity-schemas-location=classpath:openapi/schemas/"
    })
class OpsonApiRequestValidatorTest {

  @Autowired OpsonApiRequestValidator validator;
  @Autowired OpsonApiSpecRegistry registry;
  @Autowired ObjectMapper objectMapper;

  private OpsonApiOperationDescriptor createOp;

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
    assertThrows(OpsonApiValidationException.class, () -> validator.validateHeaders(request, true));
  }

  @Test
  void rejectsAcceptHeaderWithoutJsonApi() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept", "application/json");
    assertThrows(OpsonApiValidationException.class, () -> validator.validateHeaders(request, false));
  }

  @Test
  void validatesRequiredAttributes() {
    ObjectNode body = objectMapper.createObjectNode();
    ObjectNode data = objectMapper.createObjectNode();
    data.put("type", "items");
    data.set("attributes", objectMapper.createObjectNode());
    body.set("data", data);
    assertThrows(OpsonApiValidationException.class, () -> validator.validateRequestBody(body, createOp));
  }

  @Test
  void requiresPortalId() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    OpsonApiServiceContext context = new OpsonApiServiceContext(request, Map.of(), null);
    assertThrows(OpsonApiValidationException.class, () -> validator.validatePortalId(context));
  }

  @Test
  void acceptsPortalIdWhenPresent() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("portal_id", "portal-1");
    OpsonApiServiceContext context = new OpsonApiServiceContext(request, Map.of(), null);
    assertDoesNotThrow(() -> validator.validatePortalId(context));
  }
}
