package com.jsonapi.openapi.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.jsonapi.openapi.model.JsonApiDocumentMeta;
import com.jsonapi.openapi.model.JsonApiResponseEntity;
import com.jsonapi.openapi.registry.OpenApiSpecRegistry;
import com.jsonapi.openapi.registry.OperationDescriptor;
import com.jsonapi.openapi.testmodel.Item;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = StarterTestApplication.class)
@TestPropertySource(
    properties = {
      "jsonapi.openapi.location=classpath:openapi/openapi.json",
      "jsonapi.openapi.entity-schemas-location=classpath:openapi/schemas/"
    })
class JsonApiResponseAssemblerTest {

  @Autowired JsonApiResponseAssembler assembler;
  @Autowired OpenApiSpecRegistry registry;
  @Autowired ObjectMapper objectMapper;

  @Test
  void assemblesSingleEntityWithResourceTypeFromSchema() {
    OperationDescriptor op = registry.matchOperation("GET", "/api/items");
    Item item = new Item();
    item.setId("1");
    item.setName("Widget");
    JsonApiDocumentMeta meta = new JsonApiDocumentMeta();
    meta.setTotalCount(1L);

    JsonNode doc = assembler.assemble(JsonApiResponseEntity.of(item, meta), op);
    assertEquals("items", doc.path("data").path("type").asText());
    assertEquals("1", doc.path("data").path("id").asText());
    assertEquals("Widget", doc.path("data").path("attributes").path("name").asText());
    assertEquals(1, doc.path("meta").path("totalCount").asInt());
  }

  @Test
  void assemblesCollectionResponse() {
    OperationDescriptor op = registry.matchOperation("GET", "/api/items");
    Item a = new Item();
    a.setId("1");
    a.setName("A");
    Item b = new Item();
    b.setId("2");
    b.setName("B");

    JsonNode doc = assembler.assemble(JsonApiResponseEntity.of(List.of(a, b)), op);
    assertTrue(doc.path("data").isArray());
    assertEquals(2, doc.path("data").size());
  }
}
