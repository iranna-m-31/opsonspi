package com.jsonapi.openapi.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiJsonRefInlinerTest {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final File SCHEMAS_DIR =
      new File(
              OpenApiJsonRefInlinerTest.class
                  .getClassLoader()
                  .getResource("schemas/items.yaml")
                  .getFile())
          .getParentFile();

  @Test
  void inlinesExternalSchemaRef() throws Exception {
    String yaml =
        """
        type: object
        properties:
          data:
            $ref: 'items.yaml#/$defs/add-request'
        """;
    JsonNode root = YAML.readTree(yaml);
    JsonNode inlined = OpenApiJsonRefInliner.inlineExternalRefs(root, SCHEMAS_DIR);
    assertTrue(inlined.at("/properties/data/type").asText().contains("object")
        || inlined.at("/properties/data/allOf").isArray()
        || !inlined.at("/properties/data/$ref").isMissingNode() == false);
  }

  @Test
  void findExternalRefsDetectsUnresolvedRefs() throws Exception {
    JsonNode root =
        YAML.readTree(
            """
            { "$ref": "missing.yaml#/defs/x" }
            """);
    List<String> refs = OpenApiJsonRefInliner.findExternalRefs(root);
    assertEquals(1, refs.size());
    assertTrue(refs.get(0).contains("missing.yaml"));
  }

  @Test
  void unresolvedRefThrows() {
    assertThrows(
        Exception.class,
        () ->
            OpenApiJsonRefInliner.inlineExternalRefs(
                YAML.readTree("{\"$ref\": \"missing.yaml#/$defs/x\"}"),
                SCHEMAS_DIR));
  }
}
