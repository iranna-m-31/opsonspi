package com.jsonapi.openapi.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.jsonapi.openapi.context.JsonApiServiceContext;
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
class JsonApiEntityMapperTest {

  @Autowired JsonApiEntityMapper mapper;
  @Autowired OpenApiSpecRegistry registry;
  @Autowired ObjectMapper objectMapper;

  @Test
  void mapsRequestBodyToDomainEntity() throws Exception {
    OperationDescriptor op = registry.matchOperation("POST", "/api/items");
    ObjectNode body = objectMapper.createObjectNode();
    ObjectNode data = objectMapper.createObjectNode();
    data.put("type", "items");
    ObjectNode attributes = objectMapper.createObjectNode();
    attributes.put("name", "Gadget");
    data.set("attributes", attributes);
    body.set("data", data);

    MockHttpServletRequest request = new MockHttpServletRequest();
    JsonApiServiceContext context = new JsonApiServiceContext(request, Map.of(), body);
    Object entity = mapper.mapRequestBody(body, op, "com.jsonapi.openapi.testmodel", context);
    assertNotNull(entity);
    assertEquals(Item.class, entity.getClass());
    assertEquals("Gadget", ((Item) entity).getName());
  }
}
