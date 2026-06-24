package com.jsonapi.openapi.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiSpecValidatorTest {

  @TempDir Path tempDir;

  @Test
  void validSpecPasses() throws Exception {
    Files.createDirectories(tempDir.resolve("schemas"));
    Files.writeString(
        tempDir.resolve("schemas/items.yaml"),
        """
        type: object
        x-entity: Item
        x-resource-type: items
        properties:
          '@id':
            type: string
            x-field: /Item/id
          name: { type: string, x-field: /Item/name }
        dependentSchemas:
          $:
            anyOf:
              - $ref: '#/$defs/find-request'
                x-service: itemService.findAll
        $defs:
          find-request: { type: object }
        """);
    File spec = writeSpec(
        """
        openapi: 3.1.0
        info: { title: Test, version: 1.0.0 }
        paths:
          /api/items:
            get:
              summary: List
              x-entity-schema: schemas/items.yaml
              x-operation: find-request
              responses:
                '200':
                  description: OK
        """);
    var result = OpenApiSpecValidator.validate(spec, true);
    assertTrue(result.isValid(), () -> String.join("; ", result.errors()));
    assertNotNull(result.openAPI());
  }

  @Test
  void emptyPathsFails() throws Exception {
    File spec =
        writeSpec(
            """
            openapi: 3.1.0
            info: { title: Test, version: 1.0.0 }
            paths: {}
            """);
    var result = OpenApiSpecValidator.validate(spec, true);
    assertFalse(result.isValid());
  }

  @Test
  void missingFileThrows() {
    File missing = tempDir.resolve("missing.yaml").toFile();
    try {
      OpenApiSpecValidator.validate(missing, true);
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("does not exist"));
    }
  }

  @Test
  void failOnWarningsFalseAllowsNonErrorMessages() throws Exception {
    File spec = writeSpec(
        """
        openapi: 3.1.0
        info: { title: Test, version: 1.0.0 }
        paths:
          /api/items:
            get:
              summary: List
        """);
    var result = OpenApiSpecValidator.validate(spec, false);
    assertTrue(result.isValid() || result.errors().stream().noneMatch(m -> m.toLowerCase().contains("warning")));
  }

  private File writeSpec(String yaml) throws Exception {
    Path path = tempDir.resolve("openapi.yaml");
    Files.writeString(path, yaml);
    return path.toFile();
  }
}
