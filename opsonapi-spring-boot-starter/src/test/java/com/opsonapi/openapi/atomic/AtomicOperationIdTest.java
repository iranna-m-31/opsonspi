package com.opsonapi.atomic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.opsonapi.exception.OpsonApiValidationException;
import org.junit.jupiter.api.Test;

class OpsonApiAtomicOperationIdTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void createsOperationIdFromDataAndOp() {
    ObjectNode op = objectMapper.createObjectNode();
    ObjectNode data = objectMapper.createObjectNode();
    data.put("type", "items");
    op.set("data", data);
    op.put("op", "add");
    assertEquals("items.add", OpsonApiAtomicOperationId.create(op));
  }

  @Test
  void createsOperationIdFromRefWithRelationship() {
    ObjectNode op = objectMapper.createObjectNode();
    ObjectNode ref = objectMapper.createObjectNode();
    ref.put("type", "categories");
    ref.put("relationship", "members");
    op.set("ref", ref);
    op.put("op", "add");
    assertEquals("categories.addMembers", OpsonApiAtomicOperationId.create(op));
  }

  @Test
  void rejectsMissingOp() {
    ObjectNode op = objectMapper.createObjectNode();
    ObjectNode data = objectMapper.createObjectNode();
    data.put("type", "items");
    op.set("data", data);
    assertThrows(OpsonApiValidationException.class, () -> OpsonApiAtomicOperationId.create(op));
  }

  @Test
  void parsesResourceTypeAndSuffix() {
    assertEquals("items", OpsonApiAtomicOperationId.resourceType("items.add"));
    assertEquals("add", OpsonApiAtomicOperationId.operationSuffix("items.add"));
  }
}
