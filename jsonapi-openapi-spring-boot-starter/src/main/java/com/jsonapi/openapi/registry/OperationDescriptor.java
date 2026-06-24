package com.jsonapi.openapi.registry;

import java.util.List;

public record OperationDescriptor(
    String operationId,
    String method,
    String pathTemplate,
    String service,
    List<String> allowedAtomicOperations,
    boolean atomic,
    String resourceType,
    String entitySchema,
    String operationKey) {

  public OperationDescriptor(
      String operationId,
      String method,
      String pathTemplate,
      String service,
      List<String> allowedAtomicOperations,
      boolean atomic,
      String resourceType) {
    this(
        operationId,
        method,
        pathTemplate,
        service,
        allowedAtomicOperations,
        atomic,
        resourceType,
        null,
        null);
  }
}
