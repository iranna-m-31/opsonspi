package com.opsonapi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opsonapi.exception.OpsonApiValidationException;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles exceptions and formats them as JSON:API errors.
 */
@RestControllerAdvice
public class OpsonApiExceptionHandler {

  private final ObjectMapper objectMapper;

  public OpsonApiExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @ExceptionHandler(OpsonApiValidationException.class)
  public ResponseEntity<ObjectNode> handleValidation(OpsonApiValidationException ex) {
    return errorResponse(ex.getStatus(), ex.getTitle(), ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ObjectNode> handleNotFound(IllegalArgumentException ex) {
    return errorResponse(404, "Not Found", ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ObjectNode> handleGeneric(Exception ex) {
    return errorResponse(500, "Internal Server Error", ex.getMessage());
  }

  private ResponseEntity<ObjectNode> errorResponse(int status, String title, String detail) {
    ObjectNode root = objectMapper.createObjectNode();
    ArrayNode errors = objectMapper.createArrayNode();
    ObjectNode error = objectMapper.createObjectNode();
    error.put("status", String.valueOf(status));
    error.put("title", title);
    error.put("detail", detail != null ? detail : title);
    errors.add(error);
    root.set("errors", errors);
    return ResponseEntity.status(HttpStatus.valueOf(status))
        .contentType(MediaType.parseMediaType(OpsonApiSpecRegistry.JSON_API_MEDIA))
        .body(root);
  }
}