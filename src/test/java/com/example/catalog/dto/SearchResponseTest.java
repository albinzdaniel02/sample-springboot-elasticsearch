package com.example.catalog.dto;

import com.example.catalog.model.Product;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResponseTest {

    @Test
    void testSearchResponseBuilderAndGetters() {
        Product product = Product.builder()
                .id("prod-101")
                .name("Test Product")
                .price(99.9)
                .category("test-category")
                .build();

        List<Product> products = List.of(product);
        Map<String, Map<String, Long>> facets = new HashMap<>();
        Map<String, Long> categoryFacet = new HashMap<>();
        categoryFacet.put("test-category", 1L);
        facets.put("category", categoryFacet);

        SearchResponse response = SearchResponse.builder()
                .results(products)
                .page(0)
                .size(10)
                .totalHits(1L)
                .facets(facets)
                .build();

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getName()).isEqualTo("Test Product");
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getFacets()).containsKey("category");
        assertThat(response.getFacets().get("category")).containsEntry("test-category", 1L);
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        SearchResponse response = new SearchResponse();
        response.setPage(2);
        response.setSize(20);
        response.setTotalHits(100L);
        response.setResults(Collections.emptyList());
        response.setFacets(Collections.emptyMap());

        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalHits()).isEqualTo(100L);
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getFacets()).isEmpty();
    }
}
