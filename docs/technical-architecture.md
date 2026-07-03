# Technical Architecture: Product Catalog Search

This document details the technical architecture, design patterns, data modeling, and search integration for the Product Catalog Search system built with **Spring Boot** and **Elasticsearch**.

---

## 1. System Topology & Component Architecture

The system is designed as a lightweight, containerized three-tier architecture. It comprises a client-facing REST API, a Spring Boot application server, a highly performant search engine, and an exploration GUI.

```
                  +-----------------------+
                  |  Client (cURL / Web)  |
                  +-----------+-----------+
                              |
                              | HTTP (Port 8080)
                              v
                  +---------------------------+
                  |   Spring Boot Backend     | <-----+ (Reads JSON Seeds)
                  | (Service & Data Layers)   |       |
                  +-----------+---------------+       |
                              |                       | Classpath Resources
                              | Spring Data ES        | (products-seed.json)
                              | (Port 9200)           |
                              v                       |
+--------+        +---------------------------+       |
| Kibana | ---->  |    Elasticsearch Engine   | ------+
| (5601) |        |      (Single-Node)        |
+--------+        +---------------------------+
```

### 1.1 Components & Responsibility Grid

| Component | Technology | Primary Responsibilities |
| :--- | :--- | :--- |
| **Edge API / Entry** | Spring Boot MVC | Exposes HTTP `/search` REST endpoint, validates query parameters, maps DTOs, and provides unified error handling. |
| **Search Client** | Spring Data Elasticsearch | Serializes Spring entities to JSON documents, builds fluent queries, handles pagination/aggregations, and coordinates connection pooling. |
| **Search Engine** | Elasticsearch 8.x | Indexes structured data, executes relevance scoring, manages inverted indexes, handles fuzziness/typo-tolerance, and computes facets. |
| **Visualization** | Kibana 8.x | Simplifies administrative indexing monitoring, mapping validation, and manual document query testing. |

---

## 2. Infrastructure & Environment Design

Docker and Docker Compose govern the environment, establishing a predictable lifecycle and networking boundaries.

```
  +-------------------------------------------------------------+
  |                   Docker Compose Network                    |
  |                                                             |
  |   +-----------------------+     +-----------------------+   |
  |   | Elasticsearch (9200)  | <---|     Kibana (5601)     |   |
  |   +-----------+-----------+     +-----------------------+   |
  |               |                                             |
  |               v                                             |
  |       [ esdata Volume ]                                     |
  |                                                             |
  +-------------------------------------------------------------+
```

### 2.1 Service Configurations

*   **Elasticsearch (Single-Node Mode)**:
    *   To keep local testing lightweight and free of cluster-coordination overhead, `discovery.type` is set to `single-node`.
    *   **Security Context**: `xpack.security.enabled` is explicitly set to `false`. *Note: This simplifies laboratory development but is strictly prohibited in production.*
    *   **Resource Constraints**: Memory is constrained to `-Xms512m -Xmx512m` via JVM options to prevent container runtime throttling on local workstations.
    *   **Data Persistence**: A named volume `esdata` is mapped to `/usr/share/elasticsearch/data` to persist indexes across container recycles.
*   **Kibana**:
    *   Configured to poll and hook into the Elasticsearch node via `ELASTICSEARCH_HOSTS=http://elasticsearch:9200`.
    *   Leverages Docker Compose `depends_on` with `service_healthy` conditions on Elasticsearch's `_cluster/health` to guarantee startup order.

---

## 3. Data Model & Index Schema Design

To ensure predictable search behavior and optimized index sizing, **dynamic mapping is disabled** on the `products` index. The schema is strongly-typed and statically configured via Spring Data annotations mapping to the underlying Elasticsearch structure.

### 3.1 Entity Representation: `Product`

The `Product` Java object maps to the `products` index with the following field configurations:

```
+-----------------------------------------------------------------------------------+
|                                 Product Entity                                    |
+-----------------------------------------------------------------------------------+
|  [String] id (keyword, Doc ID)                                                    |
|                                                                                   |
|  [String] name (text, analyzer="standard")                                        |
|     +--> .keyword (keyword, ignore_above=256)                                     |
|                                                                                   |
|  [String] description (text, analyzer="standard")                                 |
|                                                                                   |
|  [String] category (keyword)                                                      |
|                                                                                   |
|  [String] brand (keyword)                                                         |
|                                                                                   |
|  [Double] price (double)                                                          |
|                                                                                   |
|  [List<String>] tags (keyword)                                                    |
|                                                                                   |
|  [Boolean] inStock (boolean)                                                      |
|                                                                                   |
|  [Instant] createdAt (date, format=strict_date_time)                             |
+-----------------------------------------------------------------------------------+
```

### 3.2 Index Settings & Explicit Mapping Strategy

Using Spring Data Elasticsearch annotations (`@Document`, `@Field`, `@MultiField`), the mapping schema will translate to the following underlying Elasticsearch settings:

```json
{
  "settings": {
    "index": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  },
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "id": { "type": "keyword" },
      "name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "description": {
        "type": "text",
        "analyzer": "standard"
      },
      "category": { "type": "keyword" },
      "brand": { "type": "keyword" },
      "price": { "type": "double" },
      "tags": { "type": "keyword" },
      "inStock": { "type": "boolean" },
      "createdAt": {
        "type": "date",
        "format": "strict_date_time||epoch_millis"
      }
    }
  }
}
```

*   **Strict Mode**: `dynamic` mapping is set to `strict` (or fallback equivalent) so that any untyped fields sent during manual interventions are rejected, safeguarding index performance.
*   **Shard Strategy**: 1 primary shard is deployed. Since this is a standalone node, 0 replicas are configured, preventing cluster health from showing as "Yellow" (due to unassigned replicas).

---

## 4. Bootstrapping & Data Seeding Lifecycle

On system startup, the application verifies the schema and populates mock data in an idempotent manner.

```
       [ Application Startup ]
                  |
                  v
       Does 'products' index exist?
                 / \
         No     /   \     Yes
       +-------<     >-------+
       |        \   /        |
       v         \ /         v
 [Create Index + Mapping]    |
       |                     |
       +--------->-----------+
                 |
                 v
       Is 'products' index empty?
                 / \
         Yes    /   \     No
       +-------<     >-------+
       |        \   /        |
       v         \ /         v
[Read products-seed.json]  [Skip Seeding]
       |                     |
[Bulk Index Documents]       |
       |                     |
       +--------->-----------+
                 |
                 v
      [Application Ready]
```

1.  **Index Verification**: At startup, `IndexOperations indexOps = elasticsearchOperations.indexOps(Product.class)` checks for the existence of the index. If missing, it creates the index and applies the mapping generated from the annotations.
2.  **Idempotent Seeding**: A `CommandLineRunner` polls the document count. If the count is 0, it streams `src/main/resources/products-seed.json`, parses the items with Jackson, and executes a bulk save:
    ```java
    elasticsearchOperations.save(productList);
    ```

---

## 5. Search Execution & Query DSL Architecture

The core of the system is the `/search` endpoint, which translates client parameters into optimized, structured Elasticsearch Query DSL.

### 5.1 Query Parameter Composition

```
GET /search?q=phone&category=electronics&minPrice=100&maxPrice=500&page=0&size=10
```

### 5.2 Query Construction Strategy

The endpoint compiles a single `bool` query composed of two execution contexts:

```
                        +----------------------------------------+
                        |               bool query               |
                        +----------------------------------------+
                                             |
                      +----------------------+----------------------+
                      |                                             |
                      v (Scoring Context)                           v (Filter Context)
        +---------------------------+                 +---------------------------+
        |           must            |                 |          filter           |
        +---------------------------+                 +---------------------------+
        | multi_match:              |                 | term:                     |
        |  - fields: name^2, desc   |                 |  - category               |
        |  - type: best_fields      |                 | range:                    |
        |  - fuzziness: AUTO        |                 |  - price [min TO max]     |
        +---------------------------+                 +---------------------------+
```

1.  **Scoring Context (`must` / `should`)**:
    *   Applies only when the text search string `q` is provided. If `q` is absent, a `match_all` query is substituted.
    *   Uses a `multi_match` query on `name` and `description`.
    *   **Field Boosting**: Since matches in titles are structurally more indicative of user intent than descriptions, `name` is boosted (e.g., `name^2`).
    *   **Match Type**: Set to `best_fields` because we want to score the document based on the single most relevant field rather than summing scores across distinct fields (as in `most_fields`).
    *   **Typo Tolerance**: `fuzziness` is set to `AUTO`, matching terms within a Damerau-Levenshtein distance of 1 (for 3-5 character words) or 2 (for >5 character words).
2.  **Filter Context (`filter`)**:
    *   Applied strictly via the `filter` clause of the `bool` query. 
    *   Filters are scored-independent; they do not impact the `_score` of documents, which ensures that category-filtering a product does not skew its text relevance score.
    *   By executing inside the filter context, Elasticsearch skips score calculation, enabling it to utilize internal bitsets and filter caches for extremely fast subsequent execution.

### 5.3 Aggregations (Faceting)

To dynamically calculate category metadata alongside the search results, the query appends a **Terms Aggregation** on the `category` keyword field:

```json
{
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

Because aggregations are scoped to the query, the returned category counts dynamically adjust to respect any active search queries (`q`) and filters (such as price ranges).

---

## 6. Error Handling & Resilience Boundaries

To safeguard user experience, the system implements a strict global error handling topology via a `@RestControllerAdvice`.

```
         Client HTTP Request
                 |
                 v
     +-----------------------+
     |  Controller Execution  |
     +-----------+-----------+
                 |
                 +---- (Illegal Argument / Invalid Price Range) ----> [400 Bad Request]
                 |
                 +---- (Elasticsearch connection timed out) -------> [503 Service Unavailable]
                 |
                 +---- (Unexpected Runtime Exception) --------------> [500 Internal Server Error]
```

### 6.1 Status Mapping Matrix

| Source Exception | Target HTTP Status | Response Payload Shape | Rationale |
| :--- | :--- | :--- | :--- |
| `IllegalArgumentException` | **400 Bad Request** | `{"error": "Bad Request", "message": "minPrice cannot be greater than maxPrice"}` | Client-side input validation failure. |
| `DataAccessResourceFailureException` (Spring wrapper for ES timeouts/refusals) | **503 Service Unavailable** | `{"error": "Service Unavailable", "message": "Search service is temporarily unavailable"}` | Elasticsearch is offline or overloaded. Internal trace is masked. |
| Generic `Exception.class` | **500 Internal Error** | `{"error": "Internal Error", "message": "An unexpected error occurred"}` | Safeguards codebase information from leaking in stack traces. |

---

## 7. Package & Code Structure

The Spring Boot application is structured around idiomatic domain-driven/layered packages:

```
com.example.catalog
├── CatalogApplication.java           # Entry point
├── config                            # Infrastructure configuration
│   ├── ElasticsearchConfig.java      # Client beans, socket timeouts
│   └── SeedDataLoader.java           # CommandLineRunner for json seeding
├── controller                        # REST controllers
│   ├── SearchController.java         # /search GET endpoint handler
│   └── GlobalExceptionHandler.java   # Exception mapping and unified response formats
├── dto                               # Data Transfer Objects
│   ├── SearchResponse.java           # Structured API responses (results, facets, pagination)
│   └── ErrorResponse.java            # Standardized error shapes
├── model                             # Domain Entities
│   └── Product.java                  # Product document mapping model
└── service                           # Business / Query Logic
    └── SearchService.java            # Spring Data query assembly and execution
```

---

## 8. Development Lifecycle Workflow

### 8.1 Setup & Bootstrapping

```bash
# 1. Spin up Elasticsearch and Kibana containers
docker-compose up -d

# 2. Monitor Elasticsearch readiness
curl -I http://localhost:9200/_cluster/health

# 3. Boot the Spring Boot application (Maven example)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 8.2 Verifying Mapping & Index State via Kibana Console

Administrators can navigate to the Kibana Dev Tools Console (`http://localhost:5601/app/dev_tools#/console`) to inspect index creation and mappings:

```http
GET /products/_mapping

GET /products/_search
{
  "query": {
    "match_all": {}
  }
}
```
