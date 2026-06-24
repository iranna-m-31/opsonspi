package com.jsonapi.openapi.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Standard return type for all JSON:API service methods invoked via {@code x-service}.
 */
public record JsonApiResponseEntity<T>(
    T entity,
    JsonApiDocumentMeta meta,
    List<Object> included,
    Map<String, String> links,
    int httpStatus) {

  public static <T> JsonApiResponseEntity<T> of(T entity) {
    return new JsonApiResponseEntity<>(
        entity, null, Collections.emptyList(), Collections.emptyMap(), 200);
  }

  public static <T> JsonApiResponseEntity<T> of(T entity, JsonApiDocumentMeta meta) {
    return new JsonApiResponseEntity<>(
        entity, meta, Collections.emptyList(), Collections.emptyMap(), 200);
  }

  public static JsonApiResponseEntity<Void> noContent() {
    return new JsonApiResponseEntity<>(
        null, null, Collections.emptyList(), Collections.emptyMap(), 204);
  }

  public static <T> JsonApiResponseEntity<T> created(T entity) {
    return new JsonApiResponseEntity<>(
        entity, null, Collections.emptyList(), Collections.emptyMap(), 201);
  }

  @SuppressWarnings("unchecked")
  public static <T> JsonApiResponseEntity<T> ofMany(List<?> entities) {
    return ofMany(entities, null);
  }

  @SuppressWarnings("unchecked")
  public static <T> JsonApiResponseEntity<T> ofMany(List<?> entities, JsonApiDocumentMeta meta) {
    return (JsonApiResponseEntity<T>)
        new JsonApiResponseEntity<>(
            entities, meta, Collections.emptyList(), Collections.emptyMap(), 200);
  }

  @SuppressWarnings("unchecked")
  public static <T> JsonApiResponseEntity<T> noContentTyped() {
    return (JsonApiResponseEntity<T>)
        new JsonApiResponseEntity<>(
            null, null, Collections.emptyList(), Collections.emptyMap(), 204);
  }
}
