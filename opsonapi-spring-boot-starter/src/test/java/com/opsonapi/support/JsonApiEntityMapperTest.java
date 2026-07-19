package com.opsonapi.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.opsonapi.context.OpsonApiServiceContext;
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
      "opsonapi.location=classpath:openapi/openapi.json",
      "opsonapi.entity-schemas-location=classpath:openapi/schemas/",
      "opsonapi.entity-package=com.opsonapi.testmodel"
    })
class OpsonApiEntityMapperTest {

  @Autowired OpsonApiEntityMapper mapper;
  @Autowired OpsonApiSpecRegistry registry;
  @Autowired ObjectMapper objectMapper;

  @Test
  void mapsRequestBodyToDomainEntity() throws Exception {
    OpsonApiOperationDescriptor op = registry.matchOperation("POST", "/api/items");
    ObjectNode body = objectMapper.createObjectNode();
    ObjectNode data = objectMapper.createObjectNode();
    data.put("type", "items");
    ObjectNode attributes = objectMapper.createObjectNode();
    attributes.put("name", "Gadget");
    data.set("attributes", attributes);
    body.set("data", data);

    MockHttpServletRequest request = new MockHttpServletRequest();
    OpsonApiServiceContext context = new OpsonApiServiceContext(request, Map.of(), body);
    Object entity = mapper.mapRequestBody(body, op, "com.opsonapi.testmodel", context);
    assertNotNull(entity);
    assertEquals(Item.class, entity.getClass());
    assertEquals("Gadget", ((Item) entity).getName());
  }
}
