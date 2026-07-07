package com.jsonapi.openapi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = com.jsonapi.openapi.support.StarterTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "jsonapi.openapi.location=classpath:openapi/openapi.json",
      "jsonapi.openapi.entity-schemas-location=classpath:openapi/schemas/",
      "jsonapi.openapi.entity-package=com.jsonapi.openapi.testmodel",
      "jsonapi.openapi.dispatcher-paths[0]=/api/**"
    })
class JsonApiDispatcherIntegrationTest {

  @Autowired MockMvc mockMvc;

  @Test
  void getListReturnsJsonApiDocument() throws Exception {
    mockMvc
        .perform(
            get("/api/items")
                .param("portal_id", "p1")
                .accept(MediaType.parseMediaType("application/vnd.api+json")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].type").value("items"));
  }

  @Test
  void postCreateReturnsCreatedEntity() throws Exception {
    String body =
        """
        {"data":{"type":"items","attributes":{"name":"New Gadget"}}}
        """;
    mockMvc
        .perform(
            post("/api/items")
                .param("portal_id", "p1")
                .contentType(MediaType.parseMediaType("application/vnd.api+json"))
                .accept(MediaType.parseMediaType("application/vnd.api+json"))
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.type").value("items"))
        .andExpect(jsonPath("$.data.attributes.name").value("New Gadget"));
  }

  @Test
  void missingPortalIdReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/items").accept(MediaType.parseMediaType("application/vnd.api+json")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].title").value("Bad Request"));
  }

  @Test
  void unknownPathReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/unknown")
                .param("portal_id", "p1")
                .accept(MediaType.parseMediaType("application/vnd.api+json")))
        .andExpect(status().isNotFound());
  }
}
