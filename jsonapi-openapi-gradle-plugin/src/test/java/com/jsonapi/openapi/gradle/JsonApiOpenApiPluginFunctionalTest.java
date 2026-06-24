package com.jsonapi.openapi.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonApiOpenApiPluginFunctionalTest {

  @TempDir Path projectDir;

  @BeforeEach
  void setUp() throws IOException {
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"test-project\"");

    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        """
        plugins {
          java
          id("com.jsonapi.openapi")
        }

        repositories {
          mavenCentral()
        }

        jsonapiOpenapi {
          generatedPackage.set("com.example.generated")
        }
        """);

    Path openapiDir = projectDir.resolve("src/main/resources/openapi");
    Files.createDirectories(openapiDir);
    Files.createDirectories(openapiDir.resolve("schemas"));

    Files.writeString(
        openapiDir.resolve("openapi.yaml"),
        """
        openapi: 3.1.0
        info:
          title: Test API
          version: 1.0.0
        paths:
          /api/items:
            get:
              summary: List items
              x-entity-schema: schemas/items.yaml
              x-operation: find-request
              responses:
                '200':
                  $ref: '#/components/responses/JsonApiSuccess'
        components:
          responses:
            JsonApiSuccess:
              description: Success
              content:
                application/vnd.api+json:
                  schema:
                    type: object
        """);

    Files.writeString(
        openapiDir.resolve("schemas/common.yaml"),
        """
        $schema: https://json-schema.org/draft/2020-12/schema
        $defs:
          jsonapi-request-document:
            type: object
            required: [data]
            properties:
              data:
                type: object
          resource-linkage:
            type: object
            required: [type, id]
            properties:
              type:
                type: string
              id:
                type: string
        """);

    Files.writeString(
        openapiDir.resolve("schemas/items.yaml"),
        """
        $schema: https://json-schema.org/draft/2020-12/schema
        type: object
        x-entity: Item
        x-resource-type: items
        properties:
          '@id':
            type: string
            x-field: /Item/id
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
          find-response: {}
        """);
  }

  @Test
  void pluginGeneratesWireSchemasAndJsonApiSupport() {
    BuildResult result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateJsonApiSupport", "--stacktrace")
            .forwardOutput()
            .build();

    File generatedJava =
        projectDir
            .resolve("build/generated/sources/jsonapi/com/example/generated")
            .toFile();
    File openapiJson =
        projectDir
            .resolve("build/generated/resources/openapi/openapi/openapi.json")
            .toFile();

    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
    assertTrue(generatedJava.isDirectory(), "Generated Java sources missing");
    assertTrue(openapiJson.isFile(), "Bundled openapi.json missing");
  }

  @Test
  void validateOpenApiFailsOnBrokenSpec() throws IOException {
    Files.writeString(
        projectDir.resolve("src/main/resources/openapi/openapi.yaml"),
        "not: valid: openapi: [[[");

    try {
      GradleRunner.create()
          .withProjectDir(projectDir.toFile())
          .withPluginClasspath()
          .withArguments("validateOpenApi")
          .forwardOutput()
          .build();
    } catch (UnexpectedBuildFailure failure) {
      assertTrue(
          failure.getMessage().contains("validateOpenApi")
              || failure.getMessage().contains("FAILED"));
      return;
    }
    throw new AssertionError("Expected validateOpenApi to fail");
  }

  @Test
  void generateOpenApiFromControllersEmitsPathsYaml() {
    Path javaRoot = projectDir.resolve("src/main/java/com/example/spec");
    try {
      Files.createDirectories(javaRoot);
      Files.writeString(
          javaRoot.resolve("ItemsSpecAnchor.java"),
          """
          package com.example.spec;
          import org.springframework.web.bind.annotation.GetMapping;
          @GetMapping("/api/items")
          public class ItemsSpecAnchor {}
          """);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    BuildResult result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateOpenApiFromControllers", "--stacktrace")
            .forwardOutput()
            .build();

    File pathsYaml = projectDir.resolve("build/generated/openapi/controller-paths.yaml").toFile();
    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
    assertTrue(pathsYaml.isFile(), "controller-paths.yaml missing");
  }

  @Test
  void failOnWarningsFalseAllowsParserWarnings() throws IOException {
    Files.writeString(
        projectDir.resolve("src/main/resources/openapi/openapi.yaml"),
        """
        openapi: 3.1.0
        info: { title: Test, version: 1.0.0 }
        paths:
          /api/items:
            get:
              summary: List
        """);

    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        """
        plugins {
          java
          id("com.jsonapi.openapi")
        }

        repositories {
          mavenCentral()
        }

        jsonapiOpenapi {
          generatedPackage.set("com.example.generated")
          failOnWarnings.set(false)
        }
        """);

    BuildResult result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("validateOpenApi")
            .forwardOutput()
            .build();

    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
  }

  @Test
  void compileJavaUsesGeneratedSources() {
    BuildResult result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("compileJava", "--stacktrace")
            .forwardOutput()
            .build();

    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
  }
}
