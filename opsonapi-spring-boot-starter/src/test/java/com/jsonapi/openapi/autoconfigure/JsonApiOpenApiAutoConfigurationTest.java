package com.jsonapi.openapi.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.web.JsonApiDispatcherController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = com.jsonapi.openapi.support.StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "jsonapi.openapi.location=classpath:openapi/openapi.json",
      "jsonapi.openapi.entity-schemas-location=classpath:openapi/schemas/"
    })
class JsonApiOpenApiAutoConfigurationTest {

  @Autowired(required = false)
  OpenApiSpecRegistry registry;

  @Autowired(required = false)
  JsonApiDispatcherController dispatcher;

  @Test
  void autoconfigRegistersBeansWhenEnabled() {
    assertNotNull(registry);
    assertNotNull(dispatcher);
  }
}

@SpringBootTest(
    classes = com.jsonapi.openapi.support.StarterTestApplication.class,
    properties = "jsonapi.openapi.enabled=false")
class JsonApiOpenApiAutoConfigurationDisabledTest {

  @Autowired(required = false)
  OpenApiSpecRegistry registry;

  @Autowired(required = false)
  JsonApiDispatcherController dispatcher;

  @org.junit.jupiter.api.Test
  void autoconfigSkipsBeansWhenDisabled() {
    org.junit.jupiter.api.Assertions.assertNull(registry);
    org.junit.jupiter.api.Assertions.assertNull(dispatcher);
  }
}
