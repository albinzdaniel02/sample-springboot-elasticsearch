package com.example.catalog.controller;

import com.example.catalog.dto.SearchResponse;
import com.example.catalog.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    void testSearchSuccess() throws Exception {
        SearchResponse mockResponse = SearchResponse.builder()
                .results(Collections.emptyList())
                .page(0)
                .size(10)
                .totalHits(0L)
                .facets(Collections.emptyMap())
                .build();

        when(searchService.search(eq("phone"), eq("electronics"), eq(100.0), eq(500.0), eq(0), eq(10)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/search")
                        .param("q", "phone")
                        .param("category", "electronics")
                        .param("minPrice", "100.0")
                        .param("maxPrice", "500.0")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalHits").value(0));
    }

    @Test
    void testSearchInvalidPage() throws Exception {
        mockMvc.perform(get("/search")
                        .param("page", "-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Page index must not be less than zero"));
    }

    @Test
    void testSearchInvalidSize() throws Exception {
        mockMvc.perform(get("/search")
                        .param("size", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Page size must be greater than zero"));
    }

    @Test
    void testSearchInvalidPriceRange() throws Exception {
        mockMvc.perform(get("/search")
                        .param("minPrice", "500.0")
                        .param("maxPrice", "100.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("minPrice cannot be greater than maxPrice"));
    }
}
