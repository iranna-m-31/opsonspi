package com.opsonapi.atomic;

import com.fasterxml.jackson.databind.JsonNode;
import com.opsonapi.exception.OpsonApiValidationException;

/** Derives operation IDs like zeq {@code AtomicService.createOperationID}. */
public final class OpsonApiAtomicOperationId {

  private static final String REF = "ref";
  private static final String DATA = "data";
  private static final String TYPE = "type";

  private OpsonApiAtomicOperationId() {}

  public static String create(JsonNode operationNode) {
    if (operationNode == null
        || (operationNode.get(REF) == null && operationNode.get(DATA) == null)) {
      throw new OpsonApiValidationException(400, "Bad Request", "Invalid atomic operation format");
    }
    if (!operationNode.has("op")) {
      throw new OpsonApiValidationException(400, "Bad Request", "atomic operation requires op");
    }
    StringBuilder operationId = new StringBuilder();
    if (operationNode.has(REF) && operationNode.get(REF).isObject()) {
      JsonNode ref = operationNode.get(REF);
      operationId.append(ref.get(TYPE).asText());
    } else {
      operationId.append(operationNode.get(DATA).get(TYPE).asText());
    }
    operationId.append(".");
    operationId.append(operationNode.get("op").asText());
    if (operationNode.has(REF)
        && operationNode.get(REF).has("relationship")
        && !operationNode.get(REF).get("relationship").asText().isEmpty()) {
      String rel = operationNode.get(REF).get("relationship").asText();
      operationId.append(capitalize(rel));
    }
    return operationId.toString();
  }

  public static String resourceType(String operationId) {
    int dot = operationId.indexOf('.');
    return dot > 0 ? operationId.substring(0, dot) : operationId;
  }

  public static String operationSuffix(String operationId) {
    int dot = operationId.indexOf('.');
    return dot > 0 ? operationId.substring(dot + 1) : operationId;
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}