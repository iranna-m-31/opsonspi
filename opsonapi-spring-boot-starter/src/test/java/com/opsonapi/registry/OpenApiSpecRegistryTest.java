package com.opsonapi.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = com.opsonapi.support.StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "opsonapi.location=classpath:openapi/openapi.json",
      "opsonapi.entity-schemas-location=classpath:openapi/schemas/",
      "opsonapi.entity-package=com.opsonapi.testmodel"
    })
class OpsonApiSpecRegistryTest {

  @Autowired OpsonApiSpecRegistry registry;

  @Test
  void loadsOperationsFromSpec() {
    OpsonApiOperationDescriptor op = registry.matchOperation("GET", "/api/items");
    assertNotNull(op);
    assertEquals("GET", op.method());
    assertEquals("/api/items", op.pathTemplate());
    assertEquals("find-request", op.operationKey());
  }

  @Test
  void resolvesServiceFromEntitySchema() {
    OpsonApiOperationDescriptor op = registry.matchOperation("GET", "/api/items");
    assertNotNull(op);
    assertEquals("itemService.findAll", registry.resolveService(op));
  }

  @Test
  void loadsEntitySchemaMetadata() {
    OpsonApiSpecRegistry.SchemaMetadata meta = registry.getEntitySchema("schemas/items.yaml");
    assertNotNull(meta);
    assertEquals("Item", meta.getEntityName());
    assertEquals("items", meta.getResourceType());
    assertEquals("name", meta.getAttributeFields().get("name"));
  }

  @Test
  void extractsPathVariables() {
    var vars = registry.extractPathVariables("/api/items/{id}", "/api/items/abc-1");
    assertEquals("abc-1", vars.get("id"));
  }
}
