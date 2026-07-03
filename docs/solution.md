# Solution Design: Product Catalog Search with Spring Boot + Elasticsearch

This document outlines the design, configuration, and implementation plan for building the Product Catalog Search system. It translates the raw requirements and technical architecture specifications into actionable codebase designs and files.

---

## 1. Architectural Overview & Component Topology

The system comprises three primary tiers running locally via Docker Compose:

1. **Client Tier**: REST clients (cURL, browser, frontend) communicating over HTTP (Port 8080).
2. **Application Tier**: A Spring Boot 3.3+ backend running Java 21. It manages API parameters, constructs structured search queries, executes aggregations, and handles exception safety boundaries.
3. **Data Tier**: Elasticsearch 8.x running as a single-node cluster (Port 9200) for inverted index storage and execution, paired with Kibana 8.x (Port 5601) for indexing inspection and debugging.

---

## 2. Infrastructure Setup (`docker-compose.yml`)

We will place a `docker-compose.yml` in the project root directory. To ensure Kibana and the application start only after Elasticsearch is ready, a service healthcheck is configured on the Elasticsearch cluster.

```yaml
version: '3.8'

services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.14.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - esdata:/usr/share/elasticsearch/data
    networks:
      - catalog-net
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -q '\"status\":\"green\"\\|\"status\":\"yellow\"'"]
      interval: 10s
      timeout: 5s
      retries: 5

  kibana:
    image: docker.elastic.co/kibana/kibana:8.14.0
    container_name: kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    networks:
      - catalog-net
    depends_on:
      elasticsearch:
        condition: service_healthy

volumes:
  esdata:
    driver: local

networks:
  catalog-net:
    driver: bridge
```

---

## 3. Spring Boot Configuration (`application.yml`)

The connection parameters, index settings, and query logging are externalized under `src/main/resources/application.yml`. Timeout configurations prevent the application thread pool from depleting due to slow search queries.

```yaml
spring:
  application:
    name: catalog-search-service
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 5s
    socket-timeout: 3s

logging:
  level:
    root: INFO
    org.springframework.data.elasticsearch.client. WIRE: DEBUG # Logs underlying client requests/responses
    com.example.catalog: DEBUG # Custom package query execution logs
```

---

## 4. Domain Model Mapping (`Product.java`)

To disable dynamic mappings, we explicitly annotate the `Product` entity with strict settings. This ensures Elasticsearch does not generate unwanted type inferencing on startup.

```java
package com.example.catalog.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.util.List;

@Data
@Document(indexName = "products")
@Setting(shards = 1, replicas = 0)
public class Product {

    @Id
    private String id;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "standard"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256)
        }
    )
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Boolean)
    private Boolean inStock;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSX||epoch_millis")
    private Instant createdAt;
}
```

---

## 5. Bootstrapping & Seeding Process (`SeedDataLoader.java`)

At application startup, `SeedDataLoader` performs idempotent setup:
1. Validates the existence of the `products` index.
2. If missing, creates the index and applies the mapping dynamically generated from `Product.java` annotations.
3. Queries the count of documents. If the count is `0`, it parses `src/main/resources/products-seed.json` and bulk-indexes them.

```java
package com.example.catalog.config;

import com.example.catalog.model.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeedDataLoader implements CommandLineRunner {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        IndexOperations indexOps = elasticsearchOperations.indexOps(Product.class);
        
        // 1. Create index if it does not exist
        if (!indexOps.exists()) {
            log.info("Index 'products' does not exist. Creating index...");
            indexOps.create();
            indexOps.putMapping(indexOps.createMapping(Product.class));
            log.info("Index 'products' and mappings created successfully.");
        }

        // 2. Load seed data if index is empty
        long count = elasticsearchOperations.count(Query.findAll(), Product.class);
        if (count == 0) {
            log.info("Index is empty. Initializing product seed data...");
            Resource resource = resourceLoader.getResource("classpath:products-seed.json");
            
            try (InputStream inputStream = resource.getInputStream()) {
                List<Product> products = objectMapper.readValue(inputStream, new TypeReference<List<Product>>() {});
                elasticsearchOperations.save(products);
                log.info("Successfully seeded {} products into Elasticsearch.", products.size());
            } catch (Exception e) {
                log.error("Failed to seed product data from JSON file.", e);
                throw e;
            }
        } else {
            log.info("Index 'products' already contains {} records. Skipping seeding.", count);
        }
    }
}
```

---

## 6. Query Construction & Aggregations (`SearchService.java`)

The `SearchService` translates incoming search parameters into a native Elasticsearch Query.

### 6.1 Native Elasticsearch Query DSL Structure
The service constructs the equivalent of the following query:
```json
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "phone",
            "fields": ["name^2", "description"],
            "type": "best_fields",
            "fuzziness": "AUTO"
          }
        }
      ],
      "filter": [
        { "term": { "category": "electronics" } },
        { "range": { "price": { "gte": 100, "lte": 500 } } }
      ]
    }
  },
  "aggs": {
    "category_facets": {
      "terms": {
        "field": "category",
        "size": 10
      }
    }
  }
}
```

### 6.2 Java Implementation Using Spring Data Elasticsearch Client
```java
package com.example.catalog.service;

import com.example.catalog.dto.SearchResponse;
import com.example.catalog.model.Product;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.clients.elasticsearch.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.query.Query as SpringDataQuery;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public SearchResponse searchProducts(String q, String category, Double minPrice, Double maxPrice, int page, int size) {
        log.debug("Executing product search. q='{}', category='{}', minPrice='{}', maxPrice='{}', page={}, size={}",
                q, category, minPrice, maxPrice, page, size);

        NativeQueryBuilder queryBuilder = new NativeQueryBuilder();

        // 1. Text Search query (Scoring Context)
        Query textQuery;
        if (q != null && !q.trim().isEmpty()) {
            textQuery = QueryBuilders.multiMatch(m -> m
                    .query(q)
                    .fields(List.of("name^2", "description"))
                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                    .fuzziness("AUTO")
            );
        } else {
            textQuery = QueryBuilders.matchAll(m -> m);
        }

        // 2. Build Filters (Filter Context)
        List<Query> filters = new ArrayList<>();
        if (category != null && !category.trim().isEmpty()) {
            filters.add(QueryBuilders.term(t -> t.field("category").value(category)));
        }
        if (minPrice != null || maxPrice != null) {
            filters.add(QueryBuilders.range(r -> {
                r.field("price");
                if (minPrice != null) r.gte(co.elastic.clients.json.JsonData.of(minPrice));
                if (maxPrice != null) r.lte(co.elastic.clients.json.JsonData.of(maxPrice));
                return r;
            }));
        }

        // Combine into Bool Query
        Query finalQuery = QueryBuilders.bool(b -> b
                .must(textQuery)
                .filter(filters)
        );
        queryBuilder.withQuery(finalQuery);

        // 3. Add Terms Aggregations for Category Facets
        queryBuilder.withAggregation("category_facets", AggregationBuilders.terms(t -> t.field("category").size(10)));

        // 4. Pagination
        queryBuilder.withPageable(PageRequest.of(page, size));

        // 5. Execute Search
        SearchHits<Product> searchHits = elasticsearchOperations.search(queryBuilder.build(), Product.class);

        // 6. Map results
        List<Product> products = searchHits.stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        // Extract Aggregations
        Map<String, Long> facets = new HashMap<>();
        if (searchHits.getAggregations() instanceof ElasticsearchAggregations aggregations) {
            var termsAgg = aggregations.aggregationsAsMap().get("category_facets");
            if (termsAgg != null && termsAgg.aggregation().isSterms()) {
                termsAgg.aggregation().sterms().buckets().array().forEach(bucket -> {
                    facets.put(bucket.key().stringValue(), bucket.docCount());
                });
            }
        }

        // 7. Assemble Response DTO
        SearchResponse response = new SearchResponse();
        response.setResults(products);
        response.setPage(page);
        response.setSize(size);
        response.setTotalHits(searchHits.getTotalHits());
        response.setFacets(Map.of("category", facets));

        return response;
    }
}
```

---

## 7. Endpoint Controller & Parameters Validation (`SearchController.java`)

The controller maps requests, ensures parameter validation invariants, and invokes business logic.

```java
package com.example.catalog.controller;

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
    public ResponseEntity<SearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Business validation invariant: minPrice <= maxPrice
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("minPrice cannot be greater than maxPrice");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page index must be greater than or equal to 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("page size must be greater than 0");
        }

        SearchResponse response = searchService.searchProducts(q, category, minPrice, maxPrice, page, size);
        return ResponseEntity.ok(response);
    }
}
```

---

## 8. Global Error Handling (`GlobalExceptionHandler.java`)

To prevent raw connection stack traces or invalid parameter dumps from leaking details to clients, exceptions are caught at the controller boundary and returned as standardized JSON bodies.

```java
package com.example.catalog.controller;

import com.example.catalog.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Invalid client parameters supplied: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("Bad Request", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ErrorResponse> handleElasticsearchOffline(DataAccessResourceFailureException ex) {
        log.error("Elasticsearch database connection failed or timed out", ex);
        ErrorResponse error = new ErrorResponse("Service Unavailable", "Search service is temporarily unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleFallback(Exception ex) {
        log.error("Unexpected runtime exception captured by handler", ex);
        ErrorResponse error = new ErrorResponse("Internal Error", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

## 9. Implementation Checklist

- [ ] Save the Docker Compose configuration file to [docker-compose.yml](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/docker-compose.yml)
- [ ] Initialize the Maven project structural layout and define dependencies.
- [ ] Set up connection values and port properties in [application.yml](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/resources/application.yml)
- [ ] Create domain class [Product.java](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/model/Product.java) with explicit annotations.
- [ ] Place seed JSON dataset containing 20+ records into the resources directory.
- [ ] Implement [SeedDataLoader.java](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/config/SeedDataLoader.java) to load resources on boot.
- [ ] Build [SearchService.java](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/service/SearchService.java) to compile boolean and terms queries.
- [ ] Expose [SearchController.java](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/controller/SearchController.java) mapping variables.
- [ ] Bind safety routes in [GlobalExceptionHandler.java](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/controller/GlobalExceptionHandler.java).
- [ ] Verify using the verification plan below.

---

## 10. Verification Plan

### 10.1 Environment Startup
```bash
docker-compose up -d
```
Check health via Kibana status or directly query the cluster API:
```bash
curl -I http://localhost:9200/_cluster/health
```

### 10.2 API Search Validations
* **Standard Match Query**: Find name matches.
  ```bash
  curl "http://localhost:8080/search?q=phone"
  ```
* **Fuzzy Typo Tolerance Query**: Verify term distance matching (e.g., `fone` matches `phone`).
  ```bash
  curl "http://localhost:8080/search?q=fone"
  ```
* **Faceted Category and Price Boundaries Query**: Verify scoring exclusion and facets.
  ```bash
  curl "http://localhost:8080/search?category=electronics&minPrice=100&maxPrice=500"
  ```
* **Constraint Validation Handling**:
  ```bash
  curl "http://localhost:8080/search?minPrice=500&maxPrice=10"
  ```
  *Expected Output*: HTTP 400 Bad Request.
