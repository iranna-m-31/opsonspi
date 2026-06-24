package com.jsonapi.openapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.util.List;
import java.util.Map;

/** Bundles a patched OpenAPI document into a single JSON tree with external schema refs inlined. */
final class OpenApiSpecBundler {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private OpenApiSpecBundler() {}

  static JsonNode bundle(File specFile, File wireSchemasDir, Map<String, String> wireRefs)
      throws Exception {
    JsonNode root = YAML.readTree(specFile);
    WireSchemaJsonPatcher.patchRequestBodies(root, wireRefs);
    WireSchemaJsonPatcher.patchResponses(root, wireRefs);
    JsonNode bundled = OpenApiJsonRefInliner.inlineExternalRefs(root, wireSchemasDir);
    List<String> unresolved = OpenApiJsonRefInliner.findExternalRefs(bundled);
    if (!unresolved.isEmpty()) {
      throw new IllegalStateException(
          "Bundled OpenAPI still contains external file $refs (Swagger Editor cannot load these): "
              + String.join(", ", unresolved));
    }
    return bundled;
  }
}
