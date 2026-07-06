package com.example.catalog.dto;

import com.example.catalog.model.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private List<Product> results;
    private int page;
    private int size;
    private long totalHits;
    private Map<String, Map<String, Long>> facets;
}
