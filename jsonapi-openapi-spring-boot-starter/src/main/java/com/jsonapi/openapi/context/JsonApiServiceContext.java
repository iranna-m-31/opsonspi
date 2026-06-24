package com.jsonapi.openapi.context;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonApiServiceContext implements ServiceContext {

  private static final Pattern FIELDS_PATTERN = Pattern.compile("^fields\\[(.+)]$");

  private final HttpServletRequest request;
  private final Map<String, String> pathVariables;
  private final String portalId;
  private final String filter;
  private final LinkedHashMap<String, String> sortFieldMap;
  private final Pagination pagination;
  private final Map<String, Set<String>> sparseFields;
  private final Set<String> includedFields;
  private final List<String> configuredFields;
  private final LinkedHashMap<String, String[]> extraParams;
  private ServiceContext parentContext;
  private final Map<String, Object> atomicParams = new HashMap<>();

  public JsonApiServiceContext(
      HttpServletRequest request,
      Map<String, String> pathVariables,
      JsonNode operationBody) {
    this.request = request;
    this.pathVariables = pathVariables != null ? pathVariables : Map.of();
    this.portalId = request.getParameter("portal_id");
    this.filter = firstParam(request, "filter");
    this.sortFieldMap = parseSort(request.getParameter("sort"));
    this.pagination = parsePagination(request);
    this.sparseFields = parseSparseFields(request);
    this.includedFields = parseInclude(request.getParameter("include"));
    this.configuredFields = parseConfiguredFields(operationBody);
    this.extraParams = copyExtraParams(request);
  }

  @Override
  public String getPortalId() {
    return portalId;
  }

  @Override
  public HttpServletRequest getRequest() {
    return request;
  }

  @Override
  public Map<String, String> getPathVariables() {
    return pathVariables;
  }

  @Override
  public String getPathVariable(String name) {
    return pathVariables.get(name);
  }

  @Override
  public LinkedHashMap<String, String[]> getParams() {
    LinkedHashMap<String, String[]> params = new LinkedHashMap<>(extraParams);
    if (!atomicParams.isEmpty()) {
      atomicParams.forEach((k, v) -> params.put(k, new String[] {String.valueOf(v)}));
    }
    return params;
  }

  @Override
  public Map<String, Set<String>> getSparseFields() {
    return sparseFields;
  }

  @Override
  public Set<String> getIncludedFields() {
    return includedFields;
  }

  @Override
  public String getFilter() {
    return filter;
  }

  @Override
  public LinkedHashMap<String, String> getSortFieldMap() {
    return sortFieldMap;
  }

  @Override
  public Pagination getPagination() {
    return pagination;
  }

  @Override
  public List<String> getConfiguredFields() {
    return configuredFields;
  }

  @Override
  public ServiceContext getParentContext() {
    return parentContext;
  }

  @Override
  public void setParentContext(ServiceContext parent) {
    this.parentContext = parent;
  }

  @Override
  public Map<String, Object> getAtomicParams() {
    return atomicParams;
  }

  @Override
  public void setAtomicParam(String key, Object value) {
    atomicParams.put(key, value);
    if (parentContext instanceof JsonApiServiceContext parent) {
      parent.setAtomicParam(key, value);
    }
  }

  private static String firstParam(HttpServletRequest request, String name) {
    String[] values = request.getParameterValues(name);
    return values != null && values.length > 0 ? values[0] : null;
  }

  private static LinkedHashMap<String, String> parseSort(String sort) {
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    if (sort == null || sort.isBlank()) {
      return map;
    }
    for (String field : sort.split(",")) {
      String trimmed = field.trim();
      if (trimmed.startsWith("-")) {
        map.put(trimmed.substring(1), "DESC");
      } else {
        map.put(trimmed, "ASC");
      }
    }
    return map;
  }

  private static Pagination parsePagination(HttpServletRequest request) {
    String offset = request.getParameter("page[offset]");
    String limit = request.getParameter("page[limit]");
    String size = request.getParameter("page[size]");
    Integer o = offset != null ? Integer.parseInt(offset) : 0;
    Integer l = limit != null ? Integer.parseInt(limit) : (size != null ? Integer.parseInt(size) : 10);
    return new Pagination(o, l, size != null ? Integer.parseInt(size) : null);
  }

  private static Map<String, Set<String>> parseSparseFields(HttpServletRequest request) {
    Map<String, Set<String>> result = new HashMap<>();
    request
        .getParameterMap()
        .forEach(
            (key, values) -> {
              Matcher m = FIELDS_PATTERN.matcher(key);
              if (m.matches() && values.length > 0) {
                Set<String> fields = new TreeSet<>(Arrays.asList(values[0].split(",")));
                result.put(m.group(1), fields);
              }
            });
    return result;
  }

  private static Set<String> parseInclude(String include) {
    if (include == null || include.isBlank()) {
      return Set.of();
    }
    return Set.of(include.split(","));
  }

  private static List<String> parseConfiguredFields(JsonNode operationBody) {
    if (operationBody == null || !operationBody.has("data")) {
      return null;
    }
    JsonNode data = operationBody.get("data");
    List<String> fields = new ArrayList<>();
    if (data.has("attributes") && data.get("attributes").isObject()) {
      data.get("attributes").propertyNames().forEach(fields::add);
    }
    return fields.isEmpty() ? null : fields;
  }

  private static LinkedHashMap<String, String[]> copyExtraParams(HttpServletRequest request) {
    LinkedHashMap<String, String[]> params = new LinkedHashMap<>();
    Set<String> reserved =
        Set.of("sort", "filter", "include", "portal_id", "page[offset]", "page[limit]", "page[size]");
    request
        .getParameterMap()
        .forEach(
            (key, values) -> {
              if (reserved.contains(key) || key.startsWith("fields[")) {
                return;
              }
              params.put(key, values);
            });
    return params;
  }
}
