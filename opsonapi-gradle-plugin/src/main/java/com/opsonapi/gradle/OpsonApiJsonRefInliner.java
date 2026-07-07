package com.opsonapi.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Inlines external YAML/JSON {@code $ref}s on a JsonNode tree without losing JSON Schema types. */
final class OpsonApiJsonRefInliner {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final Pattern EXTERNAL_FILE_REF =
      Pattern.compile("^[^#]+\\.(ya?ml|json)(#.*)?$", Pattern.CASE_INSENSITIVE);

  private OpsonApiJsonRefInliner() {}

  static JsonNode inlineExternalRefs(JsonNode root, File specRoot) throws IOException {
    return inlineNode(root, specRoot, null);
  }

  static List<String> findExternalRefs(JsonNode root) {
    List<String> refs = new ArrayList<>();
    collectExternalRefs(root, refs);
    return refs;
  }

  private static JsonNode inlineNode(JsonNode node, File specRoot, File contextFile)
      throws IOException {
    if (node == null || node.isNull()) {
      return node;
    }
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      JsonNode refNode = object.get("$ref");
      if (refNode != null && refNode.isTextual() && object.size() == 1) {
        String ref = refNode.asText();
        if (isExternalFileRef(ref)) {
          return resolveExternalRef(ref, specRoot);
        }
        if (ref.startsWith("#/") && contextFile != null && ref.contains("/$defs/")) {
          JsonNode contextDoc = YAML.readTree(contextFile);
          JsonNode resolved = followJsonPointer(contextDoc, ref.substring(1));
          if (resolved != null && !resolved.isMissingNode()) {
            return inlineNode(resolved.deepCopy(), specRoot, contextFile);
          }
        }
      }
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        object.set(entry.getKey(), inlineNode(entry.getValue(), specRoot, contextFile));
      }
      return object;
    }
    if (node.isArray()) {
      ArrayNode array = (ArrayNode) node;
      for (int i = 0; i < array.size(); i++) {
        array.set(i, inlineNode(array.get(i), specRoot, contextFile));
      }
      return array;
    }
    return node;
  }

  private static JsonNode resolveExternalRef(String ref, File specRoot) throws IOException {
    int hash = ref.indexOf('#');
    String filePart = hash >= 0 ? ref.substring(0, hash) : ref;
    String fragment = hash >= 0 ? ref.substring(hash + 1) : "";

    File file = locateRefFile(specRoot, filePart);
    if (!file.isFile()) {
      throw new IOException(
          "Cannot resolve external ref "
              + ref
              + " (file not found under "
              + specRoot.getAbsolutePath()
              + ")");
    }

    JsonNode doc = YAML.readTree(file);
    if (fragment.isBlank()) {
      return inlineNode(doc.deepCopy(), specRoot, file);
    }
    JsonNode target = followJsonPointer(doc, fragment);
    if (target == null || target.isMissingNode()) {
      throw new IOException("Cannot resolve fragment " + fragment + " in " + file.getName());
    }
    return inlineNode(target.deepCopy(), specRoot, file);
  }

  private static String filePart(String ref) {
    int hash = ref.indexOf('#');
    return hash >= 0 ? ref.substring(0, hash) : ref;
  }

  private static File locateRefFile(File specRoot, String filePart) {
    File direct = new File(specRoot, filePart);
    if (direct.isFile()) {
      return direct;
    }
    File inSchemas = new File(specRoot, "schemas/" + new File(filePart).getName());
    if (inSchemas.isFile()) {
      return inSchemas;
    }
    return direct;
  }

  private static JsonNode followJsonPointer(JsonNode doc, String pointer) {
    if (pointer.isBlank()) {
      return doc;
    }
    if (!pointer.startsWith("/")) {
      return null;
    }
    JsonNode current = doc;
    String[] parts = pointer.substring(1).split("/");
    for (String rawPart : parts) {
      if (rawPart.isEmpty()) {
        continue;
      }
      String part = rawPart.replace("~1", "/").replace("~0", "~");
      current = current.get(part);
      if (current == null || current.isMissingNode()) {
        return null;
      }
    }
    return current;
  }

  private static void collectExternalRefs(JsonNode node, List<String> refs) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      JsonNode refNode = node.get("$ref");
      if (refNode != null && refNode.isTextual() && isExternalFileRef(refNode.asText())) {
        refs.add(refNode.asText());
      }
      node.fields().forEachRemaining(entry -> collectExternalRefs(entry.getValue(), refs));
    } else if (node.isArray()) {
      node.forEach(child -> collectExternalRefs(child, refs));
    }
  }

  private static boolean isExternalFileRef(String ref) {
    return ref != null && EXTERNAL_FILE_REF.matcher(ref).matches();
  }
}