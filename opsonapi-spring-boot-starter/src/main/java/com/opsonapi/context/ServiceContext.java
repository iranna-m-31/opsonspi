package com.opsonapi.context;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Request-scoped context passed to every service method alongside an optional entity.
 * Modeled after zeq {@code ServiceContext} / {@code JsonAPIServiceContext}.
 */
public interface ServiceContext {

  String getPortalId();

  HttpServletRequest getRequest();

  Map<String, String> getPathVariables();

  String getPathVariable(String name);

  LinkedHashMap<String, String[]> getParams();

  Map<String, Set<String>> getSparseFields();

  Set<String> getIncludedFields();

  /** Raw URL-encoded JSON filter expression, if present. */
  String getFilter();

  LinkedHashMap<String, String> getSortFieldMap();

  Pagination getPagination();

  List<String> getConfiguredFields();

  ServiceContext getParentContext();

  void setParentContext(ServiceContext parent);

  Map<String, Object> getAtomicParams();

  void setAtomicParam(String key, Object value);
}