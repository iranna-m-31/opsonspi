package com.opsonapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/** Resolves external $refs, applies wire schemas, and bundles OpenAPI spec into JSON. */
@CacheableTask
public abstract class ConvertOpsonApiToJsonTask extends DefaultTask {

  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract RegularFileProperty getSpecFile();

  @Input
  public abstract Property<Boolean> getFailOnWarnings();

  @InputDirectory
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract DirectoryProperty getWireSchemasDirectory();

  @OutputFile
  public abstract RegularFileProperty getOutputJson();

  @TaskAction
  public void convert() throws Exception {
    File specFile = getSpecFile().getAsFile().get();
    File wireSchemasDir = getWireSchemasDirectory().getAsFile().get();
    File outputFile = getOutputJson().getAsFile().get();
    boolean failOnWarnings = getFailOnWarnings().getOrElse(true);

    // Validate inputs
    if (!specFile.exists()) {
      throw new IllegalArgumentException("Spec file not found: " + specFile.getAbsolutePath());
    }
    if (!wireSchemasDir.exists() || !wireSchemasDir.isDirectory()) {
      throw new IllegalArgumentException("Wire schemas directory not found: " + wireSchemasDir.getAbsolutePath());
    }

    Files.createDirectories(outputFile.getParentFile().toPath());

    // For now, we're using a simplified approach - in a full implementation,
    // we would generate proper wire refs from the wire schemas
    // For the basic case, we pass an empty map and let the bundler work with what it has
    JsonNode bundled = OpsonApiSpecBundler.bundle(specFile, wireSchemasDir, Map.of());

    // Write the bundled JSON
    ObjectMapper JSON = new ObjectMapper();
    JSON.writerWithDefaultPrettyPrinter().writeValue(outputFile, bundled);
  }
}