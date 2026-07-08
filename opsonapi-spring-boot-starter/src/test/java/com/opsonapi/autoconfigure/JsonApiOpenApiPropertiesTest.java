package com.opsonapi.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = com.opsonapi.support.StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "opsonapi.location=classpath:openapi/openapi.json",
      "opsonapi.entity-package=com.opsonapi.testmodel",
      "opsonapi.dispatcher-paths=/api/**,/v1/**"
    })
class OpsonApiPropertiesTest {

  @Autowired OpsonApiProperties properties;

  @Test
  void bindsConfigurationProperties() {
    assertTrue(properties.isEnabled());
    assertEquals("classpath:openapi/openapi.json", properties.getLocation());
    assertEquals("com.opsonapi.testmodel", properties.getEntityPackage());
    assertEquals(2, properties.getDispatcherPaths().size());
    assertEquals("/api/**", properties.getDispatcherPathsArray()[0]);
  }
}
