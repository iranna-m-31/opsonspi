package com.jsonapi.openapi.context;

public record Pagination(Integer offset, Integer limit, Integer size) {

  public static Pagination defaults() {
    return new Pagination(0, 10, null);
  }
}
