package com.opsonapi.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class OpsonApiEntitySchemaLoaderTest {

  @Test
  void scansClasspathSchemasDirectory() {
    Map<String, OpsonApiSpecRegistry.SchemaMetadata> schemas =
        OpsonApiEntitySchemaLoader.loadEntitySchemas(
            new DefaultResourceLoader(), "classpath:openapi/schemas/");
    assertTrue(schemas.containsKey("schemas/items.yaml"));
    OpsonApiSpecRegistry.SchemaMetadata meta = schemas.get("schemas/items.yaml");
    assertEquals("Item", meta.getEntityName());
    assertEquals("items", meta.getResourceType());
  }

  @Test
  void parsesIdAndAttributeFieldMappings() {
    Map<String, OpsonApiSpecRegistry.SchemaMetadata> schemas =
        OpsonApiEntitySchemaLoader.loadEntitySchemas(
            new DefaultResourceLoader(), "classpath:openapi/schemas/");
    OpsonApiSpecRegistry.SchemaMetadata meta = schemas.get("schemas/items.yaml");
    assertNotNull(meta);
    assertEquals("id", meta.getFieldMappings().get("id"));
    assertEquals("name", meta.getAttributeFields().get("name"));
  }

  @Test
  void normalizesSchemaPaths() {
    assertEquals("schemas/items.yaml", OpsonApiEntitySchemaLoader.normalizeSchemaPath("classpath:openapi/schemas/items.yaml"));
    assertEquals("schemas/items.yaml", OpsonApiEntitySchemaLoader.normalizeSchemaPath("openapi/schemas/items.yaml"));
  }

  @Test
  void loadsOperationServicesFromDependentSchemas() {
    Map<String, OpsonApiSpecRegistry.SchemaMetadata> schemas =
        OpsonApiEntitySchemaLoader.loadEntitySchemas(
            new DefaultResourceLoader(), "classpath:openapi/schemas/");
    OpsonApiSpecRegistry.SchemaMetadata meta = schemas.get("schemas/items.yaml");
    assertEquals("itemService.findAll", meta.getOperationServices().get("find-request"));
    assertEquals("itemService.create", meta.getOperationServices().get("add-request"));
  }
}
