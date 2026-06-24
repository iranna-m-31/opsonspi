package com.jsonapi.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonApiDocumentMeta {

  private Long totalCount;
  private Integer pageSize;
  private Integer currentPage;
  private Integer totalPages;
  private Map<String, Object> additional = new HashMap<>();

  public Long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(Long totalCount) {
    this.totalCount = totalCount;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public void setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
  }

  public Integer getCurrentPage() {
    return currentPage;
  }

  public void setCurrentPage(Integer currentPage) {
    this.currentPage = currentPage;
  }

  public Integer getTotalPages() {
    return totalPages;
  }

  public void setTotalPages(Integer totalPages) {
    this.totalPages = totalPages;
  }

  public Map<String, Object> getAdditional() {
    return additional;
  }

  public void setAdditional(Map<String, Object> additional) {
    this.additional = additional;
  }
}
