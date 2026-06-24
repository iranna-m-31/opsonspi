package com.jsonapi.openapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Generates JSON:API wire request schemas from domain entity YAML files. */
@CacheableTask
public abstract class GenerateWireSchemasTask extends DefaultTask {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract RegularFileProperty getSpecFile();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @TaskAction
  public void generate() throws IOException {
    File spec = getSpecFile().getAsFile().get();
    File outDir = getOutputDirectory().getAsFile().get();
    File schemasOut = new File(outDir, "schemas");
    schemasOut.mkdirs();

    File sourceSchemasDir = new File(spec.getParentFile(), "schemas");
    if (sourceSchemasDir.isDirectory()) {
      File[] allSchemas = sourceSchemasDir.listFiles((dir, name) -> name.endsWith(".yaml"));
      if (allSchemas != null) {
        for (File file : allSchemas) {
          Files.copy(
              file.toPath(),
              new File(schemasOut, file.getName()).toPath(),
              StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }

    List<WireSchemaGenerator.WireSchemaResult> results = WireSchemaGenerator.generateAll(spec);
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("wireRefs", new HashMap<String, String>());

    @SuppressWarnings("unchecked")
    Map<String, String> wireRefs = (Map<String, String>) manifest.get("wireRefs");

    for (WireSchemaGenerator.WireSchemaResult result : results) {
      File sourceEntity = new File(sourceSchemasDir, new File(result.entitySchemaPath()).getName());
      if (!sourceEntity.isFile()) {
        continue;
      }
      File outputEntity = new File(schemasOut, sourceEntity.getName());
      WireSchemaGenerator.writeMergedEntityFile(sourceEntity, result.wireDefs(), outputEntity);
      for (String op : result.requestOperations()) {
        wireRefs.put(
            result.entitySchemaPath() + "::" + op,
            WireSchemaGenerator.wireRef(result.entitySchemaPath(), op));
      }
      for (String op : result.responseOperations()) {
        wireRefs.put(
            result.entitySchemaPath() + "::" + op,
            WireSchemaGenerator.wireRef(result.entitySchemaPath(), op));
      }
      getLogger()
          .lifecycle(
              "Generated {} wire schema(s) for {}",
              result.wireDefs().size(),
              result.entitySchemaPath());
    }

    File manifestFile = new File(outDir, "wire-manifest.yaml");
    YAML.writerWithDefaultPrettyPrinter().writeValue(manifestFile, manifest);
    getLogger().lifecycle("Wire schemas written to {}", schemasOut);

    File commonFile = new File(schemasOut, "common.yaml");
    if (commonFile.isFile()) {
      JsonNode commonRoot = YAML.readTree(commonFile);
      ObjectNode mergedCommon = commonRoot.deepCopy();
      ObjectNode defs =
          mergedCommon.has("$defs") && mergedCommon.get("$defs").isObject()
              ? (ObjectNode) mergedCommon.get("$defs")
              : mergedCommon.putObject("$defs");
      WireSchemaGenerator.buildCommonWireDefs().forEach(defs::set);
      YAML.writerWithDefaultPrettyPrinter().writeValue(commonFile, mergedCommon);
    }
  }
}
