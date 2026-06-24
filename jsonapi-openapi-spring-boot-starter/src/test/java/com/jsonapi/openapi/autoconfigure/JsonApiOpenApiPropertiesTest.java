package com.jsonapi.openapi.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = com.jsonapi.openapi.support.StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "jsonapi.openapi.location=classpath:openapi/openapi.json",
      "jsonapi.openapi.entity-package=com.jsonapi.openapi.testmodel",
      "jsonapi.openapi.dispatcher-paths=/api/**,/v1/**"
    })
class JsonApiOpenApiPropertiesTest {

  @Autowired JsonApiOpenApiProperties properties;

  @Test
  void bindsConfigurationProperties() {
    assertTrue(properties.isEnabled());
    assertEquals("classpath:openapi/openapi.json", properties.getLocation());
    assertEquals("com.jsonapi.openapi.testmodel", properties.getEntityPackage());
    assertEquals(2, properties.getDispatcherPaths().size());
    assertEquals("/api/**", properties.getDispatcherPathsArray()[0]);
  }
}
