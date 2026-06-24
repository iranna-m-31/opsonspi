package com.jsonapi.openapi.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import org.junit.jupiter.api.Test;

class WireSchemaGeneratorTest {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final File SCHEMAS_DIR =
      new File("src/test/resources/schemas").getAbsoluteFile();

  @Test
  void itemAddRequestIncludesCoreAttributes() throws Exception {
    File entityFile = new File(SCHEMAS_DIR, "items.yaml");
    JsonNode root = YAML.readTree(entityFile);
    WireSchemaGenerator.WireSchemaResult result =
        WireSchemaGenerator.generateForEntity("schemas/items.yaml", root);

    JsonNode wire = result.wireDefs().get("jsonapi-add-request");
    assertNotNull(wire);

    JsonNode attributes =
        wire.at("/allOf/1/properties/data/properties/attributes/properties");
    assertTrue(attributes.has("title"));
    assertTrue(attributes.has("code"));
    assertTrue(attributes.has("quantity"));
  }

  @Test
  void categoryAddRequestIncludesAttributes() throws Exception {
    File entityFile = new File(SCHEMAS_DIR, "categories.yaml");
    JsonNode root = YAML.readTree(entityFile);
    WireSchemaGenerator.WireSchemaResult result =
        WireSchemaGenerator.generateForEntity("schemas/categories.yaml", root);

    JsonNode wire = result.wireDefs().get("jsonapi-add-request");
    assertNotNull(wire);
    JsonNode attributes =
        wire.at("/allOf/1/properties/data/properties/attributes/properties");
    assertTrue(attributes.has("name"));
    assertTrue(attributes.has("region"));
  }

  @Test
  void itemFindByIdResponseIncludesIdAndAttributes() throws Exception {
    File entityFile = new File(SCHEMAS_DIR, "items.yaml");
    JsonNode root = YAML.readTree(entityFile);
    WireSchemaGenerator.WireSchemaResult result =
        WireSchemaGenerator.generateForEntity("schemas/items.yaml", root, entityFile.getParentFile());

    JsonNode wire = result.wireDefs().get("jsonapi-find-by-id-response");
    assertNotNull(wire);
    JsonNode data = wire.at("/properties/data");
    assertEquals("object", data.path("type").asText());
    assertTrue(data.path("required").toString().contains("id"));
    assertEquals("string", data.at("/properties/id/type").asText());
    assertEquals("string", data.at("/properties/attributes/properties/title/type").asText());
  }

  @Test
  void categoryFindResponseUsesCollectionData() throws Exception {
    File entityFile = new File(SCHEMAS_DIR, "categories.yaml");
    JsonNode root = YAML.readTree(entityFile);
    WireSchemaGenerator.WireSchemaResult result =
        WireSchemaGenerator.generateForEntity(
            "schemas/categories.yaml", root, entityFile.getParentFile());

    JsonNode wire = result.wireDefs().get("jsonapi-find-response");
    assertNotNull(wire);
    assertEquals("array", wire.at("/properties/data/type").asText());
    assertEquals(
        "string",
        wire.at("/properties/data/items/properties/attributes/properties/name/type").asText());
  }

  @Test
  void errorWireSchemaIsErrorsOnly() {
    JsonNode wire = WireSchemaGenerator.buildCommonWireDefs().get("jsonapi-wire-error-response");
    assertNotNull(wire);
    assertTrue(wire.path("required").toString().contains("errors"));
    assertEquals("string", wire.at("/properties/errors/items/properties/status/type").asText());
    assertTrue(wire.path("properties").path("data").isMissingNode());
  }

  @Test
  void atomicConfirmActionUsesObjectBody() throws Exception {
    File entityFile = new File(SCHEMAS_DIR, "atomic-ops.yaml");
    JsonNode root = YAML.readTree(entityFile);
    WireSchemaGenerator.WireSchemaResult result =
        WireSchemaGenerator.generateForEntity("schemas/atomic-ops.yaml", root);

    JsonNode wire = result.wireDefs().get("jsonapi-confirm-action-request");
    assertNotNull(wire);
    assertTrue(wire.has("allOf") || !wire.path("properties").path("data").isMissingNode());
  }

  @Test
  void findMembersResponseUsesMemberEntitySchema() throws Exception {
    File entityFile = new File(SCHEMAS_DIR, "category-groups.yaml");
    JsonNode root = YAML.readTree(entityFile);
    WireSchemaGenerator.WireSchemaResult result =
        WireSchemaGenerator.generateForEntity(
            "schemas/category-groups.yaml", root, entityFile.getParentFile());

    JsonNode wire = result.wireDefs().get("jsonapi-find-members-response");
    assertNotNull(wire);
    assertEquals("array", wire.at("/properties/data/type").asText());
    assertEquals("members", wire.at("/properties/data/items/properties/type/const").asText());
    assertEquals(
        "string",
        wire.at("/properties/data/items/properties/attributes/properties/name/type").asText());
  }

  @Test
  void resolveMemberEntitySchemaReadsExtension() throws Exception {
    File entityFile = new File(SCHEMAS_DIR, "category-groups.yaml");
    JsonNode root = YAML.readTree(entityFile);
    assertEquals("schemas/members.yaml", WireSchemaGenerator.resolveMemberEntitySchema(root));
  }
}
