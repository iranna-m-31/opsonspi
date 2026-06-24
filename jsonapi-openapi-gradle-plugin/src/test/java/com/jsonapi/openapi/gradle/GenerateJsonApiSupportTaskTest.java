package com.jsonapi.openapi.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateJsonApiSupportTaskTest {

  @TempDir Path tempDir;
  private Path specFile;
  private Path jsonFile;
  private Path outputDir;

  @BeforeEach
  void setUp() throws Exception {
    Path openapiDir = tempDir.resolve("openapi");
    Files.createDirectories(openapiDir.resolve("schemas"));
    specFile = openapiDir.resolve("openapi.yaml");
    Files.writeString(
        specFile,
        """
        openapi: 3.1.0
        info: { title: Test, version: 1.0.0 }
        paths:
          /api/items:
            get:
              operationId: listItems
              x-entity-schema: schemas/items.yaml
              x-operation: find-request
        """);
    Files.writeString(
        openapiDir.resolve("schemas/items.yaml"),
        """
        $schema: https://json-schema.org/draft/2020-12/schema
        type: object
        x-entity: Item
        x-resource-type: items
        properties:
          name:
            type: string
            x-field: /Item/name
        dependentSchemas:
          $:
            anyOf:
              - $ref: '#/$defs/find-request'
                x-service: itemService.findAll
        $defs:
          find-request:
            type: object
        """);
    jsonFile = tempDir.resolve("openapi.json");
    Files.writeString(
        jsonFile,
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "Test", "version": "1.0.0" },
          "paths": {
            "/api/items": {
              "get": {
                "operationId": "listItems",
                "x-entity-schema": "schemas/items.yaml",
                "x-operation": "find-request"
              }
            }
          }
        }
        """);
    outputDir = tempDir.resolve("generated");
  }

  @Test
  void generatesOperationContextClasses() throws Exception {
    var project = ProjectBuilder.builder().build();
    GenerateJsonApiSupportTask task =
        project.getTasks().create("gen", GenerateJsonApiSupportTask.class);
    task.getInputJson().set(jsonFile.toFile());
    task.getSpecFile().set(specFile.toFile());
    task.getGeneratedPackage().set("com.example.generated");
    task.getOutputDirectory().set(outputDir.toFile());
    task.generate();

    assertTrue(Files.exists(outputDir.resolve("com/example/generated/ListItemsContext.java")));
  }

  @Test
  void generatesRegistryClass() throws Exception {
    var project = ProjectBuilder.builder().build();
    GenerateJsonApiSupportTask task =
        project.getTasks().create("gen", GenerateJsonApiSupportTask.class);
    task.getInputJson().set(jsonFile.toFile());
    task.getSpecFile().set(specFile.toFile());
    task.getGeneratedPackage().set("com.example.generated");
    task.getOutputDirectory().set(outputDir.toFile());
    task.generate();

    assertTrue(Files.exists(outputDir.resolve("com/example/generated/JsonApiOperationRegistry.java")));
  }
}
