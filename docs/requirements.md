# Requirements: Product Catalog Search with Spring Boot + Elasticsearch

## 1. Overview

Build a Spring Boot backend that indexes a product/catalog dataset into
Elasticsearch (with Kibana for inspection) using Spring Data Elasticsearch,
and exposes a `/search` REST endpoint offering multi-field text search,
filtering, typo-tolerance, and category facet counts.

## 2. Tech Stack

| Layer            | Choice                                              |
|-------------------|-----------------------------------------------------|
| Language          | Java 21+                                             |
| Framework         | Spring Boot 3.3+                                     |
| Search client     | Spring Data Elasticsearch (spring-boot-starter-data-elasticsearch) |
| Search engine     | Elasticsearch 8.x                                    |
| Visualization     | Kibana 8.x (matching ES version)                     |
| Build tool        | Maven or Gradle                                      |
| Containerization  | Docker + docker-compose                              |
| Data format       | JSON seed file (or CSV) loaded on startup            |

## 3. Infrastructure Requirements

### 3.1 docker-compose.yml
- Single-node Elasticsearch service:
  - `discovery.type=single-node`
  - `xpack.security.enabled=false` (lab simplicity) — document this is not for production
  - Exposed port `9200`
  - Named volume for persistent data (`esdata`)
  - Memory limits set via `ES_JAVA_OPTS` (e.g. `-Xms512m -Xmx512m`)
- Kibana service:
  - Points to the Elasticsearch container via `ELASTICSEARCH_HOSTS`
  - Exposed port `5601`
  - `depends_on` Elasticsearch
- Both services on a shared Docker network
- Healthchecks for Elasticsearch (`_cluster/health`) so Kibana/app wait for readiness

### 3.2 Spring Boot Configuration
- `spring.elasticsearch.uris=http://localhost:9200`
- Connection/socket timeouts configured explicitly
- Externalized via `application.yml` with profile support (`local`, `docker`)

## 4. Data Model

### 4.1 Domain Entity: `Product`
Minimum fields:
| Field         | Type              | Notes                                   |
|----------------|-------------------|------------------------------------------|
| id             | String / Keyword  | Document ID                              |
| name           | Text (+ keyword subfield) | Searchable, sortable/aggregatable via subfield |
| description    | Text              | Searchable, standard analyzer            |
| category       | Keyword           | Exact-match filter + aggregation field   |
| brand          | Keyword           | Optional, exact-match filter             |
| price          | Double / ScaledFloat | Range filter                          |
| tags           | Keyword (array)   | Optional, exact-match filter             |
| inStock        | Boolean           | Optional filter                          |
| createdAt      | Date              | Optional, sort/filter                    |

### 4.2 Explicit Mapping Requirements
- **No dynamic mapping.** Index template/mapping must be defined explicitly via:
  - `@Document(indexName = "products")` on the entity, **and**
  - `@Field` annotations specifying `type`, `analyzer`, and multi-fields, **or**
  - A JSON mapping file applied at index-creation time (`@Setting`/`@Mapping` with `mappingPath`)
- `name` and `description`: `text` type, standard analyzer; `name` also has a
  `.keyword` sub-field (`ignore_above: 256`) for exact matching/sorting.
- `category` and `brand`: `keyword` type (not analyzed) — required for
  filters and aggregations.
- `price`: `double` or `scaled_float` (scaling factor 100) for range queries.
- Index settings: sensible `number_of_shards` (e.g. 1 for lab) and
  `number_of_replicas: 0` (single-node).
- Application must **create the index with this mapping on startup** if it
  does not already exist (verify via `elasticsearchOperations.indexOps(...)`).

## 5. Data Loading Requirements

- A seed dataset (JSON) of at least 20–50 sample products covering multiple
  categories and a range of prices.
- A `CommandLineRunner` (or dedicated loader component) that:
  - Runs only if the index is empty (idempotent on restart)
  - Reads the seed file from `src/main/resources`
  - Bulk-indexes documents via `ElasticsearchOperations.save(Iterable<Product>)`

## 6. REST API Requirements

### 6.1 Endpoint: `GET /search`

**Query parameters:**
| Param       | Type    | Required | Description                                  |
|-------------|---------|----------|-----------------------------------------------|
| q           | String  | No       | Free-text search term (matches name/description) |
| category    | String  | No       | Exact category filter                         |
| minPrice    | Double  | No       | Lower bound (inclusive) for price range filter |
| maxPrice    | Double  | No       | Upper bound (inclusive) for price range filter |
| page        | int     | No (default 0)  | Pagination page number                 |
| size        | int     | No (default 10) | Page size                              |

**Query behavior:**
- Text search (`q`) uses a `multi_match` query across `name` and
  `description`, with:
  - `type: best_fields` (or `most_fields`, document choice and rationale)
  - `fuzziness: AUTO` to tolerate typos
  - Field boosting optional (e.g. boost `name` over `description`)
- Category and price range are applied as **filter** clauses inside a
  `bool` query (`filter`, not `must`, so they don't affect relevance
  scoring):
  - `term` filter on `category` (keyword field)
  - `range` filter on `price` using `minPrice`/`maxPrice` when present
- If `q` is absent, endpoint still returns filtered results using
  `match_all` + the same filters.
- Response includes pagination metadata (total hits, page, size).

**Response body (example shape):**
```json
{
  "results": [
    { "id": "...", "name": "...", "description": "...", "category": "...", "price": 0.0 }
  ],
  "page": 0,
  "size": 10,
  "totalHits": 42,
  "facets": {
    "category": {
      "electronics": 12,
      "home": 9,
      "sports": 5
    }
  }
}
```

### 6.2 Aggregation: Category Facets
- Same `/search` call also returns a **terms aggregation** on the
  `category` keyword field, producing a count per category, scoped to the
  current filters/query (so facet counts reflect the active search, not the
  whole index).
- Aggregation results mapped into a simple `Map<String, Long>` in the
  response DTO.

## 7. Non-Functional Requirements
- Explicit error handling: invalid params (e.g. `minPrice > maxPrice`)
  return HTTP 400 with a clear message.
- Elasticsearch/Kibana connectivity failures surface as HTTP 503, not a
  stack trace.
- Basic logging of executed queries at DEBUG level for troubleshooting.
- README (or this document) explains how to run `docker-compose up`, start
  the Spring Boot app, and hit `/search` with example `curl` commands.

## 8. Out of Scope
- Authentication/authorization
- Production-grade ES security (TLS, RBAC)
- Multi-node cluster / sharding strategy beyond lab defaults
- Write/update/delete endpoints (read/search-focused lab)

## 9. Acceptance Criteria
- [ ] `docker-compose up` starts Elasticsearch (green/yellow health) and Kibana, reachable on 9200/5601.
- [ ] Index `products` is created with the explicit mapping defined above (verifiable via `GET /products/_mapping` in Kibana Dev Tools).
- [ ] Seed data is loaded and visible in Kibana Discover.
- [ ] `GET /search?q=phone` returns relevance-ranked results from name/description.
- [ ] `GET /search?q=fone` (typo) still returns phone results due to fuzziness.
- [ ] `GET /search?category=electronics&minPrice=100&maxPrice=500` returns only matching, filtered results without affecting score-based ranking of `q`.
- [ ] Response includes a `facets.category` breakdown matching the active filters.