package com.jsonapi.openapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class ConvertOpenApiToJsonTask extends DefaultTask {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

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
    File spec = getSpecFile().getAsFile().get();
    File out = getOutputJson().getAsFile().get();
    out.getParentFile().mkdirs();

    boolean failOnWarnings = getFailOnWarnings().getOrElse(true);
    OpenApiSpecValidator.ValidationResult result =
        OpenApiSpecValidator.validate(spec, failOnWarnings);
    if (!result.isValid()) {
      throw new IllegalStateException(
          "OpenAPI spec is invalid: " + String.join(", ", result.errors()));
    }

    OpenAPI openAPI = result.openAPI();
    Map<String, String> wireRefs = loadWireRefs(getWireSchemasDirectory().getAsFile().get());
    WireSchemaPatcher.patchRequestBodies(openAPI, wireRefs);

    File wireSchemasDir = getWireSchemasDirectory().getAsFile().get();
    OpenApiSpecValidator.ValidationResult wireValidation =
        OpenApiSpecValidator.validateWithWireSchemas(spec, wireSchemasDir, openAPI);
    if (!wireValidation.isValid()) {
      throw new IllegalStateException(
          "OpenAPI wire schema validation failed: "
              + String.join(", ", wireValidation.errors()));
    }

    JsonNode bundled = OpenApiSpecBundler.bundle(spec, wireSchemasDir, wireRefs);

    try {
      Json.mapper().writerWithDefaultPrettyPrinter().writeValue(out, bundled);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write OpenAPI JSON", e);
    }
    getLogger().lifecycle("Converted {} -> {} (bundled with wire schemas)", spec, out);
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> loadWireRefs(File wireDir) throws Exception {
    Map<String, String> refs = new HashMap<>();
    File manifest = new File(wireDir, "wire-manifest.yaml");
    if (manifest.isFile()) {
      Map<String, Object> parsed = YAML.readValue(manifest, Map.class);
      Object wireRefsObj = parsed.get("wireRefs");
      if (wireRefsObj instanceof Map<?, ?> map) {
        map.forEach((k, v) -> refs.put(String.valueOf(k), String.valueOf(v)));
      }
    }
    List<WireSchemaGenerator.WireSchemaResult> generated =
        WireSchemaGenerator.generateAll(getSpecFile().getAsFile().get());
    for (WireSchemaGenerator.WireSchemaResult result : generated) {
      for (String op : result.requestOperations()) {
        refs.put(
            result.entitySchemaPath() + "::" + op,
            WireSchemaGenerator.wireRef(result.entitySchemaPath(), op));
      }
      for (String op : result.responseOperations()) {
        refs.put(
            result.entitySchemaPath() + "::" + op,
            WireSchemaGenerator.wireRef(result.entitySchemaPath(), op));
      }
    }
    return refs;
  }
}
