package com.example.catalog.config;

import com.example.catalog.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SeedDataLoaderTest {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Test
    void testSeedDataLoaded() {
        long count = elasticsearchOperations.count(Query.findAll(), Product.class);
        assertThat(count).isEqualTo(26);
    }
}
