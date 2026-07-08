package com.opsonapi.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import com.opsonapi.exception.OpsonApiValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = com.opsonapi.support.StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "jsonapi.openapi.location=classpath:openapi/openapi.json",
      "jsonapi.openapi.entity-schemas-location=classpath:openapi/schemas/"
    })
class OpsonApiExceptionHandlerTest {

  @Autowired OpsonApiExceptionHandler handler;
  @Autowired ObjectMapper objectMapper;

  @Test
  void validationExceptionProducesErrorsArray() {
    ResponseEntity<?> response =
        handler.handleValidation(new OpsonApiValidationException(400, "Bad Request", "Missing field"));
    assertEquals(400, response.getStatusCode().value());
    assertTrue(response.getBody().toString().contains("errors"));
    assertTrue(response.getBody().toString().contains("Missing field"));
  }

  @Test
  void notFoundProduces404ErrorDocument() {
    ResponseEntity<?> response = handler.handleNotFound(new IllegalArgumentException("Unknown route"));
    assertEquals(404, response.getStatusCode().value());
    assertTrue(response.getBody().toString().contains("Not Found"));
  }

  @Test
  void genericExceptionProduces500ErrorDocument() {
    ResponseEntity<?> response = handler.handleGeneric(new RuntimeException("boom"));
    assertEquals(500, response.getStatusCode().value());
    assertTrue(response.getBody().toString().contains("Internal Server Error"));
  }
}
