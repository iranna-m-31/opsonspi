package com.opsonapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Generates JSON:API wire schemas from entity YAML files in an OpenAPI spec. */
@CacheableTask
public abstract class GenerateWireSchemasTask extends DefaultTask {

  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract RegularFileProperty getSpecFile();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @TaskAction
  public void generate() throws Exception {
    File specFile = getSpecFile().getAsFile().get();
    File outputDir = getOutputDirectory().getAsFile().get();

    // Validate inputs
    if (!specFile.exists()) {
      throw new IllegalArgumentException("Spec file not found: " + specFile.getAbsolutePath());
    }

    // Generate wire schemas
    List<OpsonApiWireSchemaGenerator.WireSchemaResult> results =
        OpsonApiWireSchemaGenerator.generateAll(specFile);

    // Process each entity schema
    for (OpsonApiWireSchemaGenerator.WireSchemaResult result : results) {
      String entitySchemaPath = result.entitySchemaPath();
      Map<String, JsonNode> wireDefs = result.wireDefs();

      // Resolve the entity file path relative to the spec file's directory
      File specDir = specFile.getParentFile();
      File entityFile = new File(specDir, entitySchemaPath);

      if (entityFile.exists()) {
        // Write the enhanced entity file with wire schemas in $defs
        File outputFile = new File(outputDir, entitySchemaPath);
        OpsonApiWireSchemaGenerator.writeMergedEntityFile(
            entityFile, wireDefs, outputFile);
      }
    }
  }
}