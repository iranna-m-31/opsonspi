package com.opsonapi.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.opsonapi.model.OpsonApiDocumentMeta;
import com.opsonapi.model.OpsonApiResponseEntity;
import com.opsonapi.registry.OpsonApiSpecRegistry;
import com.opsonapi.registry.OpsonApiOperationDescriptor;
import com.opsonapi.testmodel.Item;
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
class OpsonApiResponseAssemblerTest {

  @Autowired OpsonApiResponseAssembler assembler;
  @Autowired OpsonApiSpecRegistry registry;
  @Autowired ObjectMapper objectMapper;

  @Test
  void assemblesSingleEntityWithResourceTypeFromSchema() {
    OpsonApiOperationDescriptor op = registry.matchOperation("GET", "/api/items");
    Item item = new Item();
    item.setId("1");
    item.setName("Widget");
    OpsonApiDocumentMeta meta = new OpsonApiDocumentMeta();
    meta.setTotalCount(1L);

    JsonNode doc = assembler.assemble(OpsonApiResponseEntity.of(item, meta), op);
    assertEquals("items", doc.path("data").path("type").asText());
    assertEquals("1", doc.path("data").path("id").asText());
    assertEquals("Widget", doc.path("data").path("attributes").path("name").asText());
    assertEquals(1, doc.path("meta").path("totalCount").asInt());
  }

  @Test
  void assemblesCollectionResponse() {
    OpsonApiOperationDescriptor op = registry.matchOperation("GET", "/api/items");
    Item a = new Item();
    a.setId("1");
    a.setName("A");
    Item b = new Item();
    b.setId("2");
    b.setName("B");

    JsonNode doc = assembler.assemble(OpsonApiResponseEntity.of(List.of(a, b)), op);
    assertTrue(doc.path("data").isArray());
    assertEquals(2, doc.path("data").size());
  }
}
