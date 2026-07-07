package com.jsonapi.openapi.atomic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.jsonapi.openapi.exception.JsonApiValidationException;
import org.junit.jupiter.api.Test;

class AtomicOperationIdTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void createsOperationIdFromDataAndOp() {
    ObjectNode op = objectMapper.createObjectNode();
    ObjectNode data = objectMapper.createObjectNode();
    data.put("type", "items");
    op.set("data", data);
    op.put("op", "add");
    assertEquals("items.add", AtomicOperationId.create(op));
  }

  @Test
  void createsOperationIdFromRefWithRelationship() {
    ObjectNode op = objectMapper.createObjectNode();
    ObjectNode ref = objectMapper.createObjectNode();
    ref.put("type", "categories");
    ref.put("relationship", "members");
    op.set("ref", ref);
    op.put("op", "add");
    assertEquals("categories.addMembers", AtomicOperationId.create(op));
  }

  @Test
  void rejectsMissingOp() {
    ObjectNode op = objectMapper.createObjectNode();
    ObjectNode data = objectMapper.createObjectNode();
    data.put("type", "items");
    op.set("data", data);
    assertThrows(JsonApiValidationException.class, () -> AtomicOperationId.create(op));
  }

  @Test
  void parsesResourceTypeAndSuffix() {
    assertEquals("items", AtomicOperationId.resourceType("items.add"));
    assertEquals("add", AtomicOperationId.operationSuffix("items.add"));
  }
}
