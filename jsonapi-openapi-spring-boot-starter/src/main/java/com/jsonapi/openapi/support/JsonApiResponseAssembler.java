package com.jsonapi.openapi.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.jsonapi.openapi.model.JsonApiResponseEntity;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry.SchemaMetadata;
import com.jsonapi.openapi.registry.OperationDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
public class JsonApiResponseAssembler {

  private final ObjectMapper objectMapper;
  private final OpenApiSpecRegistry registry;

  public JsonApiResponseAssembler(ObjectMapper objectMapper, OpenApiSpecRegistry registry) {
    this.objectMapper = objectMapper;
    this.registry = registry;
  }

  public JsonNode assemble(JsonApiResponseEntity<?> result, OperationDescriptor operation) {
    if (result.httpStatus() == 204) {
      return null;
    }
    ObjectNode root = objectMapper.createObjectNode();
    Object entity = result.entity();
    if (entity == null) {
      root.set("data", objectMapper.nullNode());
    } else if (entity instanceof Collection<?> collection) {
      ArrayNode array = objectMapper.createArrayNode();
      for (Object item : collection) {
        array.add(toResourceObject(item, operation));
      }
      root.set("data", array);
    } else if (entity instanceof JsonNode node) {
      root.set("data", node);
    } else {
      root.set("data", toResourceObject(entity, operation));
    }
    if (result.meta() != null) {
      root.set("meta", objectMapper.valueToTree(result.meta()));
    }
    if (result.included() != null && !result.included().isEmpty()) {
      ArrayNode included = objectMapper.createArrayNode();
      for (Object item : result.included()) {
        if (item instanceof JsonNode n) {
          included.add(n);
        } else {
          included.add(objectMapper.valueToTree(item));
        }
      }
      root.set("included", included);
    }
    if (result.links() != null && !result.links().isEmpty()) {
      root.set("links", objectMapper.valueToTree(result.links()));
    }
    return root;
  }

  private ObjectNode toResourceObject(Object entity, OperationDescriptor operation) {
    ObjectNode resource = objectMapper.createObjectNode();
    SchemaMetadata meta = resolveMetadata(entity, operation);
    String type =
        meta != null && meta.getResourceType() != null
            ? meta.getResourceType()
            : inferType(entity);
    resource.put("type", type);

    String id = readId(entity);
    if (id != null) {
      resource.put("id", id);
    }

    ObjectNode attributes = buildAttributes(entity, meta);
    if (!attributes.isEmpty()) {
      resource.set("attributes", attributes);
    }

    JsonNode relationships = readRelationships(entity);
    if (relationships != null && !relationships.isNull()) {
      resource.set("relationships", relationships);
    }
    return resource;
  }

  private SchemaMetadata resolveMetadata(Object entity, OperationDescriptor operation) {
    if (operation.entitySchema() != null) {
      SchemaMetadata fromPath = registry.getEntitySchema(operation.entitySchema());
      if (fromPath != null) {
        return fromPath;
      }
    }
    if (operation.resourceType() != null) {
      return registry.getSchemaForResourceType(operation.resourceType());
    }
    return registry.getSchemaForResourceType(inferType(entity));
  }

  private ObjectNode buildAttributes(Object entity, SchemaMetadata meta) {
    ObjectNode attributes = objectMapper.createObjectNode();
    if (meta != null && meta.isDomainSchema()) {
      for (Map.Entry<String, String> entry : meta.getAttributeFields().entrySet()) {
        Object value = readField(entity, entry.getValue());
        if (value != null) {
          attributes.set(entry.getKey(), objectMapper.valueToTree(value));
        }
      }
      return attributes;
    }
    ObjectNode all = objectMapper.valueToTree(entity);
    if (all.isObject()) {
      all.remove("id");
      all.remove("relationships");
      return all;
    }
    return attributes;
  }

  private String readId(Object entity) {
    try {
      Method getId = entity.getClass().getMethod("getId");
      Object id = getId.invoke(entity);
      return id != null ? id.toString() : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private JsonNode readRelationships(Object entity) {
    try {
      Method getRelationships = entity.getClass().getMethod("getRelationships");
      Object rel = getRelationships.invoke(entity);
      if (rel instanceof JsonNode relNode) {
        return relNode;
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private Object readField(Object entity, String fieldName) {
    try {
      Field field = findField(entity.getClass(), fieldName);
      if (field == null) {
        return null;
      }
      field.setAccessible(true);
      return field.get(entity);
    } catch (Exception ignored) {
      return null;
    }
  }

  private Field findField(Class<?> clazz, String name) {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  private String inferType(Object entity) {
    String name = entity.getClass().getSimpleName();
    if (name.isEmpty()) {
      return name;
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1) + "s";
  }
}
