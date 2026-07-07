package com.opsonapi.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Standard return type for all OpsonAPI service methods invoked via {@code x-service}.
 */
public record OpsonApiResponseEntity<T>(
    T entity,
    OpsonApiDocumentMeta meta,
    List<Object> included,
    Map<String, String> links,
    int httpStatus) {

  public static <T> OpsonApiResponseEntity<T> of(T entity) {
    return new OpsonApiResponseEntity<>(
        entity, null, Collections.emptyList(), Collections.emptyMap(), 200);
  }

  public static <T> OpsonApiResponseEntity<T> of(T entity, OpsonApiDocumentMeta meta) {
    return new OpsonApiResponseEntity<>(
        entity, meta, Collections.emptyList(), Collections.emptyMap(), 200);
  }

  public static OpsonApiResponseEntity<Void> noContent() {
    return new OpsonApiResponseEntity<>(
        null, null, Collections.emptyList(), Collections.emptyMap(), 204);
  }

  public static <T> OpsonApiResponseEntity<T> created(T entity) {
    return new OpsonApiResponseEntity<>(
        entity, null, Collections.emptyList(), Collections.emptyMap(), 201);
  }

  @SuppressWarnings("unchecked")
  public static <T> OpsonApiResponseEntity<T> ofMany(List<?> entities) {
    return ofMany(entities, null);
  }

  @SuppressWarnings("unchecked")
  public static <T> OpsonApiResponseEntity<T> ofMany(List<?> entities, OpsonApiDocumentMeta meta) {
    return (OpsonApiResponseEntity<T>)
        new OpsonApiResponseEntity<>(
            entities, meta, Collections.emptyList(), Collections.emptyMap(), 200);
  }

  @SuppressWarnings("unchecked")
  public static <T> OpsonApiResponseEntity<T> noContentTyped() {
    return (OpsonApiResponseEntity<T>)
        new OpsonApiResponseEntity<>(
            null, null, Collections.emptyList(), Collections.emptyMap(), 204);
  }
}