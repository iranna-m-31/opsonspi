package com.opsonapi.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.web.OpsonApiDispatcherController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = com.opsonapi.support.StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "opsonapi.location=classpath:openapi/openapi.json",
      "opsonapi.entity-schemas-location=classpath:openapi/schemas/"
    })
class OpsonApiAutoConfigurationTest {

  @Autowired(required = false)
  OpsonApiSpecRegistry registry;

  @Autowired(required = false)
  OpsonApiDispatcherController dispatcher;

  @Test
  void autoconfigRegistersBeansWhenEnabled() {
    assertNotNull(registry);
    assertNotNull(dispatcher);
  }
}

@SpringBootTest(
    classes = com.opsonapi.support.StarterTestApplication.class,
    properties = "opsonapi.enabled=false")
class OpsonApiAutoConfigurationDisabledTest {

  @Autowired(required = false)
  OpsonApiSpecRegistry registry;

  @Autowired(required = false)
  OpsonApiDispatcherController dispatcher;

  @org.junit.jupiter.api.Test
  void autoconfigSkipsBeansWhenDisabled() {
    org.junit.jupiter.api.Assertions.assertNull(registry);
    org.junit.jupiter.api.Assertions.assertNull(dispatcher);
  }
}
