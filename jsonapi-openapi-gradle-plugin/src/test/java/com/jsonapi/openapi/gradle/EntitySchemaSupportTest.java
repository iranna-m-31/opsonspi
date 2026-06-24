package com.jsonapi.openapi.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Operation;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntitySchemaSupportTest {

  private static final File FIXTURE_SPEC =
      new File("src/test/resources/fixtures/openapi.yaml").getAbsoluteFile();

  @Test
  void loadOperationServicesReadsXService() {
    Map<String, Map<String, String>> result = EntitySchemaSupport.loadOperationServices(FIXTURE_SPEC);
    assertFalse(result.isEmpty());
    assertTrue(result.containsKey("schemas/items.yaml"));
    assertEquals("itemService.create", result.get("schemas/items.yaml").get("add-request"));
    assertEquals("itemService.findAll", result.get("schemas/items.yaml").get("find-request"));
  }

  @Test
  void validateEntitySchemaFileDetectsMissingXEntity() throws Exception {
    File bad = File.createTempFile("bad-entity", ".yaml");
    bad.deleteOnExit();
    java.nio.file.Files.writeString(
        bad.toPath(),
        """
        type: object
        properties:
          name: { type: string, x-field: /X/name }
        """);
    List<String> errors = new ArrayList<>();
    EntitySchemaSupport.validateEntitySchemaFile(bad, errors);
    assertTrue(errors.stream().anyMatch(e -> e.contains("x-entity")));
  }

  @Test
  void validateEntityOperationsRequiresBothExtensions() {
    OpenAPI openAPI = new OpenAPI();
    Paths paths = new Paths();
    PathItem item = new PathItem();
    Operation op = new Operation();
    op.addExtension("x-entity-schema", "schemas/items.yaml");
    item.setGet(op);
    paths.addPathItem("/api/items", item);
    openAPI.setPaths(paths);

    List<String> errors = EntitySchemaSupport.validateEntityOperations(openAPI, FIXTURE_SPEC);
    assertTrue(
        errors.stream().anyMatch(e -> e.contains("x-operation") && e.contains("must both be set")));
  }

  @Test
  void normalizeSchemaPathStripsClasspath() {
    assertEquals(
        "schemas/items.yaml",
        EntitySchemaSupport.normalizeSchemaPath("classpath:openapi/schemas/items.yaml"));
  }
}
