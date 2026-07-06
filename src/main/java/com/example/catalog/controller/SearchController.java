package com.example.catalog.controller;

import com.example.catalog.dto.ErrorResponse;
import com.example.catalog.dto.SearchResponse;
import com.example.catalog.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) {
            return ResponseEntity.badRequest().body(
                    ErrorResponse.builder()
                            .error("Bad Request")
                            .message("Page index must not be less than zero")
                            .build()
            );
        }
        if (size <= 0) {
            return ResponseEntity.badRequest().body(
                    ErrorResponse.builder()
                            .error("Bad Request")
                            .message("Page size must be greater than zero")
                            .build()
            );
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            return ResponseEntity.badRequest().body(
                    ErrorResponse.builder()
                            .error("Bad Request")
                            .message("minPrice cannot be greater than maxPrice")
                            .build()
            );
        }

        SearchResponse response = searchService.search(q, category, minPrice, maxPrice, page, size);
        return ResponseEntity.ok(response);
    }
}
