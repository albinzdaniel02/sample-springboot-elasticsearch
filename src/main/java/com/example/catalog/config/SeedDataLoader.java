package com.example.catalog.config;

import com.example.catalog.model.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeedDataLoader implements CommandLineRunner {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ObjectMapper objectMapper;

    @Value("classpath:products-seed.json")
    private Resource seedDataResource;

    @Override
    public void run(String... args) throws Exception {
        IndexOperations indexOps = elasticsearchOperations.indexOps(Product.class);

        if (!indexOps.exists()) {
            log.info("Index 'products' does not exist. Creating index...");
            indexOps.create();
            indexOps.putMapping(indexOps.createMapping(Product.class));
            log.info("Index 'products' created with mappings.");
        } else {
            log.info("Index 'products' already exists.");
        }

        long count = elasticsearchOperations.count(Query.findAll(), Product.class);
        log.info("Current document count in 'products': {}", count);

        if (count == 0) {
            log.info("Seeding product data...");
            try (InputStream inputStream = seedDataResource.getInputStream()) {
                List<Product> products = objectMapper.readValue(inputStream, new TypeReference<List<Product>>() {});
                elasticsearchOperations.save(products);
                log.info("Successfully seeded {} products.", products.size());
            }
        } else {
            log.info("Seeding skipped because index is not empty.");
        }
    }
}
