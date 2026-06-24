package com.jsonapi.openapi.registry;

import com.jsonapi.openapi.autoconfigure.JsonApiOpenApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.AntPathMatcher;

public class OpenApiSpecRegistry {

  private static final Logger log = LoggerFactory.getLogger(OpenApiSpecRegistry.class);
  public static final String JSON_API_MEDIA = "application/vnd.api+json";

  private final JsonApiOpenApiProperties properties;
  private final ResourceLoader resourceLoader;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  private OpenAPI openAPI;
  private final List<OperationDescriptor> operations = new ArrayList<>();
  private final Map<String, SchemaMetadata> schemaMetadata = new HashMap<>();
  private final Map<String, SchemaMetadata> entitySchemasByPath = new HashMap<>();
  private final Map<String, SchemaMetadata> entitySchemasByResourceType = new HashMap<>();

  public OpenApiSpecRegistry(
      JsonApiOpenApiProperties properties, ResourceLoader resourceLoader) {
    this.properties = properties;
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  public void load() throws Exception {
    Resource resource = resourceLoader.getResource(properties.getLocation());
    ParseOptions options = new ParseOptions();
    options.setResolve(true);
    try (InputStream in = resource.getInputStream()) {
      openAPI =
          new OpenAPIV3Parser()
              .readContents(new String(in.readAllBytes()), null, options)
              .getOpenAPI();
    }
    if (openAPI == null || openAPI.getPaths() == null) {
      throw new IllegalStateException("Failed to load OpenAPI from " + properties.getLocation());
    }
    loadEntitySchemas();
    buildOperations();
    log.info(
        "Loaded OpenAPI with {} operations and {} entity schemas",
        operations.size(),
        entitySchemasByPath.size());
  }

  private void loadEntitySchemas() {
    Map<String, SchemaMetadata> loaded =
        EntitySchemaLoader.loadEntitySchemas(resourceLoader, properties.getEntitySchemasLocation());
    loaded.forEach(
        (key, meta) -> {
          if (key.startsWith("resourceType:")) {
            entitySchemasByResourceType.put(key.substring("resourceType:".length()), meta);
          } else if (meta != null) {
            entitySchemasByPath.put(key, meta);
            schemaMetadata.put(metaKey(meta), meta);
            if (meta.getResourceType() != null) {
              entitySchemasByResourceType.put(meta.getResourceType(), meta);
            }
          }
        });
  }

  private static String metaKey(SchemaMetadata meta) {
    if (meta.getResourceType() != null) {
      return meta.getResourceType();
    }
    return meta.getSchemaPath();
  }

  private void buildOperations() {
    openAPI
        .getPaths()
        .forEach(
            (path, item) -> {
              register(path, "GET", item.getGet());
              register(path, "POST", item.getPost());
              register(path, "PATCH", item.getPatch());
              register(path, "PUT", item.getPut());
              register(path, "DELETE", item.getDelete());
            });
  }

  private void register(String path, String method, Operation op) {
    if (op == null) {
      return;
    }
    String entitySchema = extension(op, "x-entity-schema");
    String operationKey = extension(op, "x-operation");
    String service = resolveServiceFromEntity(entitySchema, operationKey);
    if (service == null) {
      service = extension(op, "x-service");
    }
    List<String> atomicOps = extensionList(op, "x-atomic-allowed-operations");
    String resourceType = inferResourceType(path, op, entitySchema);
    operations.add(
        new OperationDescriptor(
            op.getOperationId() != null ? op.getOperationId() : method + path,
            method,
            path,
            service,
            atomicOps,
            !atomicOps.isEmpty(),
            resourceType,
            entitySchema,
            operationKey));
  }

  private String inferResourceType(String path, Operation op, String entitySchema) {
    SchemaMetadata meta = getEntitySchema(entitySchema);
    if (meta != null && meta.getResourceType() != null) {
      return meta.getResourceType();
    }
    if (path.startsWith("/api/")) {
      String remainder = path.substring("/api/".length());
      int slash = remainder.indexOf('/');
      String segment = slash >= 0 ? remainder.substring(0, slash) : remainder;
      return pathSegmentToResourceType(segment);
    }
    return null;
  }

  public String resolveService(OperationDescriptor operation) {
    if (operation.entitySchema() != null && operation.operationKey() != null) {
      String fromEntity =
          resolveServiceFromEntity(operation.entitySchema(), operation.operationKey());
      if (fromEntity != null) {
        return fromEntity;
      }
    }
    return operation.service();
  }

  private String resolveServiceFromEntity(String entitySchema, String operationKey) {
    if (entitySchema == null || operationKey == null) {
      return null;
    }
    SchemaMetadata meta = getEntitySchema(entitySchema);
    if (meta == null) {
      return null;
    }
    return meta.getOperationServices().get(operationKey);
  }

  public SchemaMetadata getEntitySchema(String entitySchemaPath) {
    if (entitySchemaPath == null) {
      return null;
    }
    SchemaMetadata meta = entitySchemasByPath.get(EntitySchemaLoader.normalizeSchemaPath(entitySchemaPath));
    if (meta != null) {
      return meta;
    }
    return entitySchemasByPath.get(entitySchemaPath);
  }

  public OperationConstraints getOperationConstraints(OperationDescriptor operation) {
    if (operation.entitySchema() == null || operation.operationKey() == null) {
      return null;
    }
    SchemaMetadata meta = getEntitySchema(operation.entitySchema());
    return meta != null ? meta.constraintsFor(operation.operationKey()) : null;
  }

  public OperationDescriptor matchOperation(String method, String requestPath) {
    for (OperationDescriptor op : operations) {
      if (!op.method().equalsIgnoreCase(method)) {
        continue;
      }
      if (pathMatcher.match(op.pathTemplate(), requestPath)) {
        return op;
      }
    }
    return null;
  }

  public Map<String, String> extractPathVariables(String template, String actual) {
    Map<String, String> vars = new HashMap<>();
    if (!pathMatcher.match(template, actual)) {
      return vars;
    }
    vars.putAll(pathMatcher.extractUriTemplateVariables(template, actual));
    return vars;
  }

  public Schema getRequestBodySchema(OperationDescriptor op) {
    Operation operation = getOperation(op.pathTemplate(), op.method());
    if (operation == null || operation.getRequestBody() == null) {
      return null;
    }
    MediaType mt = operation.getRequestBody().getContent().get(JSON_API_MEDIA);
    return mt != null ? mt.getSchema() : null;
  }

  public Operation getOperation(String path, String method) {
    PathItem item = openAPI.getPaths().get(path);
    if (item == null) {
      return null;
    }
    return switch (method.toUpperCase(Locale.ROOT)) {
      case "GET" -> item.getGet();
      case "POST" -> item.getPost();
      case "PATCH" -> item.getPatch();
      case "PUT" -> item.getPut();
      case "DELETE" -> item.getDelete();
      default -> null;
    };
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getOperationExtensions(OperationDescriptor descriptor) {
    Operation operation = getOperation(descriptor.pathTemplate(), descriptor.method());
    if (operation == null || operation.getExtensions() == null) {
      return Collections.emptyMap();
    }
    return operation.getExtensions();
  }

  public String resolveAtomicOperationService(OperationDescriptor operation, String operationId) {
    Map<String, Object> extensions = getOperationExtensions(operation);
    Object mapObj = extensions.get("x-atomic-operation-services");
    if (mapObj instanceof Map<?, ?> services) {
      Object ref = services.get(operationId);
      if (ref != null) {
        return ref.toString();
      }
    }
  return resolveService(operation);
  }

  public OpenAPI getOpenAPI() {
    return openAPI;
  }

  public SchemaMetadata getSchemaMetadata(String name) {
    return schemaMetadata.get(name);
  }

  public SchemaMetadata getSchemaForResourceType(String resourceType) {
    if (resourceType == null) {
      return null;
    }
    SchemaMetadata meta = entitySchemasByResourceType.get(resourceType);
    if (meta != null) {
      return meta;
    }
    return schemaMetadata.get(resourceType);
  }

  private static String pathSegmentToResourceType(String segment) {
    if (segment == null || segment.isEmpty()) {
      return null;
    }
    int dash = segment.indexOf('-');
    if (dash < 0) {
      return segment;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < segment.length(); i++) {
      char c = segment.charAt(i);
      if (c == '-') {
        continue;
      }
      if (i > 0 && segment.charAt(i - 1) == '-') {
        sb.append(Character.toUpperCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private String extension(Object extensionsHolder, String key) {
    Map<String, Object> ext = getExtensions(extensionsHolder);
    if (ext == null) {
      return null;
    }
    Object v = ext.get(key);
    return v != null ? v.toString() : null;
  }

  @SuppressWarnings("unchecked")
  private List<String> extensionList(Object holder, String key) {
    Map<String, Object> ext = getExtensions(holder);
    if (ext == null) {
      return Collections.emptyList();
    }
    Object v = ext.get(key);
    if (v instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      list.forEach(o -> out.add(o.toString()));
      return out;
    }
    return Collections.emptyList();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getExtensions(Object holder) {
    if (holder == null) {
      return null;
    }
    try {
      var method = holder.getClass().getMethod("getExtensions");
      return (Map<String, Object>) method.invoke(holder);
    } catch (Exception e) {
      return null;
    }
  }

  public static class SchemaMetadata {
    private String schemaName;
    private String schemaPath;
    private String entityName;
    private String resourceType;
    private Map<String, String> fieldMappings = new HashMap<>();
    private Map<String, String> attributeFields = new HashMap<>();
    private Map<String, String> relationshipFields = new HashMap<>();
    private Map<String, String> xFieldMappings = new HashMap<>();
    private Map<String, String> operationServices = new HashMap<>();
    private Map<String, OperationConstraints> operationConstraints = new HashMap<>();
    private List<String> filterable = new ArrayList<>();
    private List<String> sortable = new ArrayList<>();

    public String getSchemaName() {
      return schemaName;
    }

    public void setSchemaName(String schemaName) {
      this.schemaName = schemaName;
    }

    public String getSchemaPath() {
      return schemaPath;
    }

    public void setSchemaPath(String schemaPath) {
      this.schemaPath = schemaPath;
    }

    public String getEntityName() {
      return entityName;
    }

    public void setEntityName(String entityName) {
      this.entityName = entityName;
    }

    public String getResourceType() {
      return resourceType;
    }

    public void setResourceType(String resourceType) {
      this.resourceType = resourceType;
    }

    public Map<String, String> getFieldMappings() {
      return fieldMappings;
    }

    public Map<String, String> getAttributeFields() {
      return attributeFields;
    }

    public Map<String, String> getRelationshipFields() {
      return relationshipFields;
    }

    public Map<String, String> getXFieldMappings() {
      return xFieldMappings;
    }

    public Map<String, String> getOperationServices() {
      return operationServices;
    }

    public Map<String, OperationConstraints> getOperationConstraints() {
      return operationConstraints;
    }

    public OperationConstraints constraintsFor(String operationKey) {
      return operationKey != null ? operationConstraints.get(operationKey) : null;
    }

    public List<String> getFilterable() {
      return filterable;
    }

    public void setFilterable(List<String> filterable) {
      this.filterable = filterable != null ? new ArrayList<>(filterable) : new ArrayList<>();
    }

    public List<String> getSortable() {
      return sortable;
    }

    public void setSortable(List<String> sortable) {
      this.sortable = sortable != null ? new ArrayList<>(sortable) : new ArrayList<>();
    }

    public String javaFieldForAttribute(String jsonAttribute) {
      return attributeFields.getOrDefault(jsonAttribute, jsonAttribute);
    }

    public boolean isDomainSchema() {
      return resourceType != null && !xFieldMappings.isEmpty();
    }
  }

  public static class OperationConstraints {
    private final List<String> required = new ArrayList<>();
    private final List<String> excluded = new ArrayList<>();

    public List<String> getRequired() {
      return required;
    }

    public List<String> getExcluded() {
      return excluded;
    }

    public boolean isExcluded(String property) {
      return excluded.contains(property);
    }
  }
}
