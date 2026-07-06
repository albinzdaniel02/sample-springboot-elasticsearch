# Product Catalog Search Implementation Todo

Goal: Build a Spring Boot backend that indexes a product catalog dataset into Elasticsearch, exposing a `/search` REST endpoint with multi-field text search, price and category filters, typo-tolerance, and category facet counts.

---

## Coding Agent Workflow Instructions

Each developer subagent dispatched to work on a task must follow these workflow steps exactly:
1. **Branching**: Create a local git branch named after the task ID (e.g., `git checkout -b P1-1`).
2. **Implementation & Test**: Implement the changes specified in the task description. Write tests and run them locally to verify.
3. **Pull Request**: Push the branch and create a Pull Request on GitHub using the CLI:
   ```bash
   gh pr create --title "task: implement <task-id>" --body "Implements <task-id>"
   ```
4. **Code Review**: Run a reviewer subagent to check the changes. The reviewer subagent must only write review comments on the GitHub PR and must not modify any code files directly. Fix any requested changes (up to a limit of 3 reviews).
5. **Merge & Cleanup**: Merge the PR using the GitHub CLI:
   ```bash
   gh pr merge --squash --delete-branch
   ```
   Pull the changes back to the local main branch, and prune local git tracking branches.
6. **Result Output**: Return the final JSON result payload to the coordinator:
   ```json
   {
     "implementation": "done",
     "pr": "done",
     "reason": ""
   }
   ```

---

## Phase 1: Infrastructure & Base Build Setup

- [x] P1-1: Create `docker-compose.yml` in the project root containing Elasticsearch (single-node, security disabled, memory limits at `-Xms512m -Xmx512m`) and Kibana (configured to point to Elasticsearch, and running health checks).
- [x] P1-2: Create `pom.xml` in the project root containing dependencies for Spring Boot 3.3+, Spring Data Elasticsearch, Lombok, Jackson, and JUnit/Testcontainers for testing.
- [x] P1-EC: Start infrastructure via `docker-compose up -d`. Verify Elasticsearch cluster health is green or yellow at `http://localhost:9200/_cluster/health` and Kibana is accessible on `http://localhost:5601`. Verify Maven dependencies download and project compiles cleanly with `mvn clean compile`.

---

## Phase 2: Configuration & Domain Mapping

- [x] P2-1: Create `src/main/resources/application.yml` defining connection URIs (`http://localhost:9200`), explicit timeouts (connection timeout 5s, socket timeout 3s), and query wire logs at `DEBUG` level.
- [x] P2-2: Create `com/example/catalog/CatalogApplication.java` main application runner class.
- [x] P2-3: Create domain class `com/example/catalog/model/Product.java` with strict settings (no dynamic mapping, shards = 1, replicas = 0), annotations for keyword types, and text type fields (analyzed with standard analyzer and keyword subfield).
- [x] P2-EC: Start the Spring Boot skeleton application and verify it starts without throwing connection exceptions or database mapping validation errors.

---

## Phase 3: Idempotent Seeding Implementation

- [x] P3-1: Add mock data json file `src/main/resources/products-seed.json` containing 25+ product items with varied prices, categories, stocks, and timestamps.
- [x] P3-2: Create `com/example/catalog/config/SeedDataLoader.java` implementing `CommandLineRunner`. The loader must idempotently verify if the `products` index exists, create it with mappings if missing, count documents, and seed products if count is 0.
- [x] P3-EC: Restart application. Verify log outputs indicate successful data seeding. Verify document counts using Kibana Dev Tools (`GET /products/_count`). Restart application again and verify no duplicate indexing occurs.

---

## Phase 4: Business Logic & Query DSL Development

- [x] P4-1: Create DTO class `com/example/catalog/dto/SearchResponse.java` and error response DTO `com/example/catalog/dto/ErrorResponse.java` to map results, page numbers, sizes, total hits, and category facets.
- [x] P4-2: Create `com/example/catalog/service/SearchService.java` building a boolean query context combining a fuzzy `multi_match` text search on `name^2` and `description` with filters for category (term match) and prices (range match). Add category terms aggregation.
- [x] P4-EC: Write and execute unit/integration tests to assert correct Query DSL building, field boosting, fuzzy parameters, and aggregations inside `SearchService`.

---

## Phase 5: REST Endpoint & Exception Boundary Setup

- [x] P5-1: Create `com/example/catalog/controller/SearchController.java` exposing `/search` mapping page, size, min/max price, category, and text query parameters. Validate parameters (e.g. `minPrice <= maxPrice`, `page >= 0`, `size > 0`).
- [x] P5-2: Create `com/example/catalog/controller/GlobalExceptionHandler.java` translating validation failures to `400 Bad Request`, client connection/timeout errors to `503 Service Unavailable`, and all other errors to `500 Internal Error`.
- [ ] P5-EC: Run all tests using `./mvnw clean test` verifying minimum 80% coverage. Execute test curls:
  1. Standard query: `GET /search?q=phone` (matches phone results)
  2. Typo query: `GET /search?q=fone` (matches phone results fuzzy)
  3. Filtered search: `GET /search?category=electronics&minPrice=100&maxPrice=500` (matches range and category, returns facets)
  4. Parameter validation: `GET /search?minPrice=500&maxPrice=10` (returns 400 Bad Request)
  5. Connection failure check (stop ES container and run curl) -> returns 503 Service Unavailable.


