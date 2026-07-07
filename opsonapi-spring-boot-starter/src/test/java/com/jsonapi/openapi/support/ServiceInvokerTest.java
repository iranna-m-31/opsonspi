package com.jsonapi.openapi.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jsonapi.openapi.context.JsonApiServiceContext;
import com.jsonapi.openapi.model.JsonApiResponseEntity;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.registry.OperationDescriptor;
import com.jsonapi.openapi.testmodel.Item;
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
      "jsonapi.openapi.entity-package=com.jsonapi.openapi.testmodel"
    })
class ServiceInvokerTest {

  @Autowired ServiceInvoker serviceInvoker;
  @Autowired OpenApiSpecRegistry registry;

  @Test
  void invokesRegisteredServiceBean() throws Exception {
    OperationDescriptor op = registry.matchOperation("GET", "/api/items");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("portal_id", "p1");
    JsonApiServiceContext context = new JsonApiServiceContext(request, Map.of(), null);

    JsonApiResponseEntity<?> result =
        serviceInvoker.invoke("itemService.findAll", context, null, op);

    assertNotNull(result);
    assertEquals(200, result.httpStatus());
  }

  @Test
  void passesEntityTypeToServiceMethod() throws Exception {
    OperationDescriptor op = registry.matchOperation("POST", "/api/items");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("portal_id", "p1");
    JsonApiServiceContext context = new JsonApiServiceContext(request, Map.of(), null);
    Item item = new Item();
    item.setName("Gadget");

    JsonApiResponseEntity<?> result =
        serviceInvoker.invoke("itemService.create", context, item, op);

    assertEquals(201, result.httpStatus());
    assertEquals("Gadget", ((Item) result.entity()).getName());
  }
}
