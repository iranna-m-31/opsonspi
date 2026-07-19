package com.opsonapi.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.opsonapi.context.OpsonApiServiceContext;
import com.opsonapi.model.OpsonApiResponseEntity;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.registry.OpsonApiOperationDescriptor;
import com.opsonapi.testmodel.Item;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "jsonapi.openapi.location=classpath:openapi/openapi.json",
      "jsonapi.openapi.entity-schemas-location=classpath:openapi/schemas/",
      "jsonapi.openapi.entity-package=com.opsonapi.testmodel"
    })
class OpsonApiServiceInvokerTest {

  @Autowired OpsonApiServiceInvoker serviceInvoker;
  @Autowired OpsonApiSpecRegistry registry;

  @Test
  void invokesRegisteredServiceBean() throws Exception {
    OpsonApiOperationDescriptor op = registry.matchOperation("GET", "/api/items");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("portal_id", "p1");
    OpsonApiServiceContext context = new OpsonApiServiceContext(request, Map.of(), null);

    OpsonApiResponseEntity<?> result =
        serviceInvoker.invoke("itemService.findAll", context, null, op);

    assertNotNull(result);
    assertEquals(200, result.httpStatus());
  }

  @Test
  void passesEntityTypeToServiceMethod() throws Exception {
    OpsonApiOperationDescriptor op = registry.matchOperation("POST", "/api/items");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("portal_id", "p1");
    OpsonApiServiceContext context = new OpsonApiServiceContext(request, Map.of(), null);
    Item item = new Item();
    item.setName("Gadget");

    OpsonApiResponseEntity<?> result =
        serviceInvoker.invoke("itemService.create", context, item, op);

    assertEquals(201, result.httpStatus());
    assertEquals("Gadget", ((Item) result.entity()).getName());
  }
}
