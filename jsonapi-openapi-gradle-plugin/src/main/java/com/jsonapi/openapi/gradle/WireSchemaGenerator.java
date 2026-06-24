package com.jsonapi.openapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives explicit JSON:API wire request/response schemas from domain entity YAML. */
public final class WireSchemaGenerator {

  public static final String ERROR_WIRE_REF = "schemas/common.yaml#/$defs/jsonapi-wire-error-response";

  private static final ObjectMapper YAML =
      new ObjectMapper(
          YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build());

  private WireSchemaGenerator() {}

  public record WireSchemaResult(
      String entitySchemaPath,
      Map<String, JsonNode> wireDefs,
      List<String> requestOperations,
      List<String> responseOperations) {}

  public static List<WireSchemaResult> generateAll(File specFile) throws IOException {
    List<WireSchemaResult> results = new ArrayList<>();
    File schemasDir = new File(specFile.getParentFile(), "schemas");
    if (!schemasDir.isDirectory()) {
      return results;
    }
    File[] files =
        schemasDir.listFiles(
            (dir, name) ->
                name.endsWith(".yaml")
                    && !name.equals("common.yaml")
                    && !name.equals("atomic-operations.yaml"));
    if (files == null) {
      return results;
    }
    for (File entityFile : files) {
      if (!EntitySchemaSupport.isEntityFile(entityFile.getName())) {
        continue;
      }
      JsonNode root = YAML.readTree(entityFile);
      if (text(root, "x-entity") == null) {
        continue;
      }
      String schemaPath = "schemas/" + entityFile.getName();
      results.add(generateForEntity(schemaPath, root, schemasDir));
    }
    return results;
  }

  /** Wire defs merged into {@code schemas/common.yaml} during generation. */
  public static Map<String, JsonNode> buildCommonWireDefs() {
    Map<String, JsonNode> defs = new LinkedHashMap<>();
    defs.put("jsonapi-wire-error-response", buildErrorWireSchema());
    return defs;
  }

  public static WireSchemaResult generateForEntity(String entitySchemaPath, JsonNode root) {
    File schemasDir = null;
    int slash = entitySchemaPath.lastIndexOf('/');
    if (slash >= 0) {
      schemasDir = new File(entitySchemaPath.substring(0, slash));
    }
    return generateForEntity(entitySchemaPath, root, schemasDir);
  }

  public static WireSchemaResult generateForEntity(
      String entitySchemaPath, JsonNode root, File schemasDir) {
    Map<String, JsonNode> wireDefs = new LinkedHashMap<>();
    List<String> requestOps = new ArrayList<>();
    List<String> responseOps = new ArrayList<>();
    String resourceType = text(root, "x-resource-type");
    JsonNode rootProperties = root.path("properties");
    JsonNode defs = root.path("$defs");

    for (String opKey : listRequestOperations(root)) {
      JsonNode opDef = defs.path(opKey);
      if (opDef.isMissingNode() || !opDef.isObject()) {
        continue;
      }
      String wireName = "jsonapi-" + opKey;
      JsonNode wireSchema = buildRequestWireSchema(opKey, opDef, resourceType, rootProperties);
      if (wireSchema != null) {
        wireDefs.put(wireName, wireSchema);
        requestOps.add(opKey);
      }
    }

    for (String opKey : listResponseOperations(defs)) {
      try {
        JsonNode wireSchema =
            buildResponseWireSchema(
                opKey, resourceType, rootProperties, root, entitySchemaPath, schemasDir);
        if (wireSchema != null) {
          wireDefs.put("jsonapi-" + opKey, wireSchema);
          responseOps.add(opKey);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(
            "Failed to generate wire response schema for " + opKey + " in " + entitySchemaPath, e);
      }
    }

    return new WireSchemaResult(entitySchemaPath, wireDefs, requestOps, responseOps);
  }

  private static List<String> listRequestOperations(JsonNode root) {
    Set<String> ops = new LinkedHashSet<>();
    JsonNode anyOf = root.path("dependentSchemas").path("$").path("anyOf");
    if (anyOf.isArray()) {
      for (JsonNode branch : anyOf) {
        String ref = text(branch, "$ref");
        if (ref == null) {
          continue;
        }
        String opKey = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
        if (opKey.endsWith("-request")) {
          ops.add(opKey);
        }
      }
    }
    return new ArrayList<>(ops);
  }

  private static List<String> listResponseOperations(JsonNode defs) {
    List<String> ops = new ArrayList<>();
    if (!defs.isObject()) {
      return ops;
    }
    defs
        .properties()
        .forEach(
            entry -> {
              if (entry.getKey().endsWith("-response")) {
                ops.add(entry.getKey());
              }
            });
    return ops;
  }

  private static JsonNode buildRequestWireSchema(
      String opKey, JsonNode opDef, String resourceType, JsonNode rootProperties) {
    if (isRelationshipArrayOperation(opKey, opDef)) {
      return buildRelationshipArrayBody();
    }
    if (!isResourceBodyOperation(opKey)) {
      return null;
    }
    Set<String> excluded = excludedProperties(opDef);
    return buildDocumentSchema(
        "common.yaml#/$defs/jsonapi-request-document",
        resourceType,
        rootProperties,
        excluded,
        requiredList(opDef),
        requiresId(opDef),
        false,
        false);
  }

  private static JsonNode buildResponseWireSchema(
      String opKey,
      String resourceType,
      JsonNode rootProperties,
      JsonNode root,
      String entitySchemaPath,
      File schemasDir)
      throws IOException {
    if (!generatesResponseBody(opKey)) {
      return null;
    }
    if ("find-members-response".equals(opKey)) {
      return buildFindMembersResponse(root, schemasDir);
    }
    String itemResourceType = resourceType;
    JsonNode itemProperties = rootProperties;
    boolean collection = isCollectionResponse(opKey);
    return buildSuccessResponseSchema(
        itemResourceType, itemProperties, Set.of(), List.of(), true, true, collection);
  }

  private static JsonNode buildFindMembersResponse(JsonNode root, File schemasDir)
      throws IOException {
    String memberSchemaPath = resolveMemberEntitySchema(root);
    if (memberSchemaPath == null || schemasDir == null || !schemasDir.isDirectory()) {
      return null;
    }
    File memberFile = new File(schemasDir, new File(memberSchemaPath).getName());
    if (!memberFile.isFile()) {
      throw new IOException(
          "x-member-entity-schema references missing file: "
              + memberSchemaPath
              + " (expected under "
              + schemasDir.getAbsolutePath()
              + ")");
    }
    JsonNode memberRoot = YAML.readTree(memberFile);
    return buildSuccessResponseSchema(
        text(memberRoot, "x-resource-type"),
        memberRoot.path("properties"),
        Set.of(),
        List.of(),
        true,
        true,
        true);
  }

  /** Resolves member entity schema from x-member-entity-schema on find-members $defs or @-relationships. */
  static String resolveMemberEntitySchema(JsonNode root) {
    JsonNode defs = root.path("$defs");
    String fromRequest = text(defs.path("find-members-request"), "x-member-entity-schema");
    if (fromRequest != null) {
      return fromRequest;
    }
    String fromResponse = text(defs.path("find-members-response"), "x-member-entity-schema");
    if (fromResponse != null) {
      return fromResponse;
    }
    JsonNode props = root.path("properties");
    if (props.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String key = entry.getKey();
        if (key.startsWith("@") && !"@id".equals(key)) {
          String schema = text(entry.getValue(), "x-member-entity-schema");
          if (schema != null) {
            return schema;
          }
        }
      }
    }
    return null;
  }

  private static boolean generatesResponseBody(String opKey) {
    if (!opKey.endsWith("-response")) {
      return false;
    }
    return !"remove-response".equals(opKey);
  }

  private static boolean isCollectionResponse(String opKey) {
    return "find-response".equals(opKey) || "find-members-response".equals(opKey);
  }

  private static boolean isRelationshipArrayOperation(String opKey, JsonNode opDef) {
    if (opKey.contains("relationships-request")) {
      return true;
    }
    return opDef.has("$relationship");
  }

  private static boolean isResourceBodyOperation(String opKey) {
    if (!opKey.endsWith("-request")) {
      return false;
    }
    return switch (opKey) {
      case "find-request", "find-by-id-request", "remove-request" -> false;
      default -> !opKey.contains("relationships-request");
    };
  }

  private static JsonNode buildRelationshipArrayBody() {
    ObjectMapper mapper = YAML;
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    ArrayNode required = schema.putArray("required");
    required.add("data");
    ObjectNode properties = schema.putObject("properties");
    ObjectNode data = properties.putObject("data");
    data.put("type", "array");
    data.putObject("items").put("$ref", "common.yaml#/$defs/resource-linkage");
    return schema;
  }

  /** Success JSON:API document: typed {@code data} plus optional envelope fields (no {@code errors}). */
  private static JsonNode buildSuccessResponseSchema(
      String resourceType,
      JsonNode rootProperties,
      Set<String> excludedAttributes,
      List<String> attributeRequired,
      boolean includeId,
      boolean includeRelationships,
      boolean collection) {
    ObjectMapper mapper = YAML;
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.putArray("required").add("data");

    ObjectNode props = schema.putObject("properties");
    ObjectNode dataSchema =
        buildResourceDataSchema(
            resourceType,
            rootProperties,
            excludedAttributes,
            attributeRequired,
            includeId,
            includeRelationships);
    props.set("data", collection ? wrapArray(dataSchema) : dataSchema);
    props.set("included", buildIncludedSchema());
    props.set("meta", buildMetaSchema());
    props.putObject("links").put("type", "object");
    return schema;
  }

  static JsonNode buildErrorWireSchema() {
    ObjectMapper mapper = YAML;
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.putArray("required").add("errors");
    ObjectNode errors = schema.putObject("properties").putObject("errors");
    errors.put("type", "array");
    ObjectNode item = errors.putObject("items");
    item.put("type", "object");
    ObjectNode itemProps = item.putObject("properties");
    itemProps.putObject("id").put("type", "string");
    itemProps.putObject("status").put("type", "string");
    itemProps.putObject("code").put("type", "string");
    itemProps.putObject("title").put("type", "string");
    itemProps.putObject("detail").put("type", "string");
    ObjectNode source = itemProps.putObject("source");
    source.put("type", "object");
    ObjectNode sourceProps = source.putObject("properties");
    sourceProps.putObject("pointer").put("type", "string");
    sourceProps.putObject("parameter").put("type", "string");
    return schema;
  }

  private static ObjectNode buildMetaSchema() {
    ObjectMapper mapper = YAML;
    ObjectNode meta = mapper.createObjectNode();
    meta.put("type", "object");
    meta.put("additionalProperties", true);
    ObjectNode metaProps = meta.putObject("properties");
    metaProps.putObject("totalCount").put("type", "integer");
    metaProps.putObject("pageSize").put("type", "integer");
    metaProps.putObject("currentPage").put("type", "integer");
    metaProps.putObject("totalPages").put("type", "integer");
    return meta;
  }

  private static ObjectNode buildIncludedSchema() {
    ObjectMapper mapper = YAML;
    ObjectNode included = mapper.createObjectNode();
    included.put("type", "array");
    ObjectNode item = included.putObject("items");
    item.put("type", "object");
    item.putArray("required").add("type").add("id");
    ObjectNode itemProps = item.putObject("properties");
    itemProps.putObject("type").put("type", "string");
    itemProps.putObject("id").put("type", "string");
    itemProps.putObject("attributes").put("type", "object");
    return included;
  }

  private static JsonNode buildDocumentSchema(
      String envelopeRef,
      String resourceType,
      JsonNode rootProperties,
      Set<String> excludedAttributes,
      List<String> attributeRequired,
      boolean includeId,
      boolean includeRelationships,
      boolean collection) {
    ObjectMapper mapper = YAML;
    ObjectNode schema = mapper.createObjectNode();
    ArrayNode allOf = schema.putArray("allOf");
    allOf.addObject().put("$ref", envelopeRef);

    ObjectNode envelope = allOf.addObject();
    envelope.put("type", "object");
    envelope.putArray("required").add("data");

    ObjectNode dataSchema =
        buildResourceDataSchema(
            resourceType,
            rootProperties,
            excludedAttributes,
            attributeRequired,
            includeId,
            includeRelationships);
    envelope.putObject("properties").set("data", collection ? wrapArray(dataSchema) : dataSchema);
    return schema;
  }

  private static ObjectNode wrapArray(ObjectNode itemSchema) {
    ObjectNode array = YAML.createObjectNode();
    array.put("type", "array");
    array.set("items", itemSchema);
    return array;
  }

  private static ObjectNode buildResourceDataSchema(
      String resourceType,
      JsonNode rootProperties,
      Set<String> excludedAttributes,
      List<String> attributeRequired,
      boolean includeId,
      boolean includeRelationships) {
    ObjectMapper mapper = YAML;
    ObjectNode data = mapper.createObjectNode();
    data.put("type", "object");

    ArrayNode dataRequired = data.putArray("required");
    dataRequired.add("type");
    dataRequired.add("attributes");
    if (includeId) {
      dataRequired.add("id");
    }

    ObjectNode dataProps = data.putObject("properties");
    ObjectNode typeNode = dataProps.putObject("type");
    typeNode.put("type", "string");
    if (resourceType != null) {
      typeNode.put("const", resourceType);
    }
    if (includeId) {
      dataProps.putObject("id").put("type", "string");
    }

    ObjectNode attributes = dataProps.putObject("attributes");
    attributes.put("type", "object");
    ObjectNode attrProps = attributes.putObject("properties");
    if (rootProperties.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = rootProperties.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String propName = entry.getKey();
        if (propName.startsWith("@") || excludedAttributes.contains(propName)) {
          continue;
        }
        attrProps.set(propName, copySchemaShape(entry.getValue()));
      }
    }
    List<String> requiredAttrs = new ArrayList<>();
    for (String req : attributeRequired) {
      if (!req.startsWith("@") && !excludedAttributes.contains(req)) {
        requiredAttrs.add(req);
      }
    }
    if (!requiredAttrs.isEmpty()) {
      ArrayNode attrRequired = attributes.putArray("required");
      requiredAttrs.forEach(attrRequired::add);
    }

    if (includeRelationships) {
      ObjectNode relationships = buildRelationships(rootProperties, Set.of());
      if (relationships != null) {
        dataProps.set("relationships", relationships);
      }
    }
    return data;
  }

  private static ObjectNode buildRelationships(JsonNode rootProperties, Set<String> excluded) {
    ObjectMapper mapper = YAML;
    ObjectNode relationships = null;
    if (!rootProperties.isObject()) {
      return null;
    }
    Iterator<Map.Entry<String, JsonNode>> fields = rootProperties.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      String propName = entry.getKey();
      if (!propName.startsWith("@") || "@id".equals(propName) || excluded.contains(propName)) {
        continue;
      }
      String wireName = propName.substring(1);
      if (relationships == null) {
        relationships = mapper.createObjectNode();
        relationships.put("type", "object");
        relationships.putObject("properties");
      }
      ObjectNode rel = mapper.createObjectNode();
      rel.put("type", "object");
      ObjectNode relProps = rel.putObject("properties");
      ObjectNode relData = relProps.putObject("data");
      JsonNode rootProp = entry.getValue();
      if (rootProp.path("type").asText("").equals("array")) {
        relData.put("type", "array");
        ObjectNode item = relData.putObject("items");
        item.put("type", "object");
        item.putArray("required").add("type").add("id");
        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("type").put("type", "string");
        itemProps.putObject("id").put("type", "string");
        itemProps.putObject("lid").put("type", "string");
      } else {
        relData.put("type", "object");
        relData.putArray("required").add("type").add("id");
        ObjectNode itemProps = relData.putObject("properties");
        itemProps.putObject("type").put("type", "string");
        itemProps.putObject("id").put("type", "string");
        itemProps.putObject("lid").put("type", "string");
      }
      relationships.withObject("properties").set(wireName, rel);
    }
    return relationships;
  }

  private static Set<String> excludedProperties(JsonNode opDef) {
    Set<String> excluded = new LinkedHashSet<>();
    JsonNode props = opDef.path("properties");
    if (!props.isObject()) {
      return excluded;
    }
    Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      if (entry.getValue().isBoolean() && !entry.getValue().booleanValue()) {
        excluded.add(entry.getKey());
      }
    }
    return excluded;
  }

  private static List<String> requiredList(JsonNode opDef) {
    List<String> required = new ArrayList<>();
    JsonNode req = opDef.get("required");
    if (req != null && req.isArray()) {
      req.forEach(n -> required.add(n.asText()));
    }
    return required;
  }

  private static boolean requiresId(JsonNode opDef) {
    JsonNode req = opDef.get("required");
    if (req != null && req.isArray()) {
      for (JsonNode n : req) {
        if ("@id".equals(n.asText())) {
          return true;
        }
      }
    }
    return false;
  }

  static JsonNode copySchemaShape(JsonNode source) {
    ObjectMapper mapper = YAML;
    ObjectNode copy = mapper.createObjectNode();
    if (source.has("type")) {
      copy.set("type", source.get("type"));
    }
    if (source.has("format")) {
      copy.set("format", source.get("format"));
    }
    if (source.has("enum")) {
      copy.set("enum", source.get("enum"));
    }
    if (source.has("items")) {
      copy.set("items", source.get("items"));
    }
    if (source.has("minimum")) {
      copy.set("minimum", source.get("minimum"));
    }
    if (source.has("maximum")) {
      copy.set("maximum", source.get("maximum"));
    }
    if (copy.isEmpty()) {
      copy.put("type", "string");
    }
    return copy;
  }

  public static void writeMergedEntityFile(
      File sourceEntityFile, Map<String, JsonNode> wireDefs, File outputFile) throws IOException {
    JsonNode root = YAML.readTree(sourceEntityFile);
    ObjectNode merged = root.deepCopy();
    ObjectNode defs =
        merged.has("$defs") && merged.get("$defs").isObject()
            ? (ObjectNode) merged.get("$defs")
            : merged.putObject("$defs");
    wireDefs.forEach(defs::set);
    outputFile.getParentFile().mkdirs();
    YAML.writerWithDefaultPrettyPrinter().writeValue(outputFile, merged);
  }

  public static String wireRef(String entitySchemaPath, String operationKey) {
    return entitySchemaPath + "#/$defs/jsonapi-" + operationKey;
  }

  public static String toResponseOperationKey(String requestOperationKey) {
    if (requestOperationKey == null || !requestOperationKey.endsWith("-request")) {
      return null;
    }
    return requestOperationKey.substring(0, requestOperationKey.length() - "-request".length())
        + "-response";
  }

  private static String text(JsonNode node, String field) {
    if (node == null || !node.has(field) || node.get(field).isNull()) {
      return null;
    }
    return node.get(field).asText();
  }
}
