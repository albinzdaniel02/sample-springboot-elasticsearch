# Testing Plan: Product Catalog Search

This document defines the comprehensive testing plan, mocking strategies, test configurations, and coverage guidelines to ensure the Product Catalog Search system operates reliably and matches all requirements.

---

## 1. Testing Strategy & Principles

We will adopt a test-driven approach to cover three distinct testing boundaries:

```
                  +-----------------------------------+
                  |      System / E2E Integration      |
                  |     - Real ES container / service  |
                  |     - Seeding & Index validation   |
                  +-----------------+-----------------+
                                    |
                                    v
                  +-----------------+-----------------+
                  |      Mock API / Integration       |
                  |     - MockMvc controller tests    |
                  |     - Service logic tests         |
                  +-----------------+-----------------+
                                    |
                                    v
                  +-----------------+-----------------+
                  |            Unit Testing           |
                  |     - DTO, Domain constraints     |
                  |     - Parameter validations       |
                  +-----------------------------------+
```

### 1.1 Core Objectives
* **Code Coverage**: Aim for at least **80% code coverage** across the codebase using JaCoCo.
* **Test Isolation**: External calls (like actual Elasticsearch network calls) must be mocked in unit and API integration tests. Real connection tests must be isolated to System Integration tests.
* **Test Invariants**: Ensure both happy paths and edge cases (such as connection loss, empty queries, and negative pagination values) are covered.

---

## 2. Test Execution Command Matrix

| Test Suite | Command | Notes |
| :--- | :--- | :--- |
| **All Tests** | `./mvnw clean test` | Runs all unit and integration tests. |
| **Unit Tests Only** | `./mvnw test -Dtest=*UnitTest` | Runs fast unit test cases. |
| **Integration Tests** | `./mvnw test -Dtest=*IntegrationTest` | Runs API and query execution integration tests. |
| **Coverage Report** | `./mvnw test jacoco:report` | Runs tests and generates HTML coverage reports. |

---

## 3. Detailed Testing Boundaries

### 3.1 Unit Testing
Unit tests execute in memory, run in `< 50ms`, and require no Docker infrastructure.

#### Test Target: Parameter Validation & Invariants (`SearchControllerUnitTest`)
* **Test cases**:
  * Assert validation exception when `minPrice > maxPrice`.
  * Assert validation exception when `page < 0`.
  * Assert validation exception when `size <= 0`.
  * Verify correct forwarding of parameters to [SearchService](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/service/SearchService.java).

#### Test Target: DTO mapping (`SearchResponseTest`)
* **Test cases**:
  * Verify that DTO fields are mapped, getters/setters operate correctly, and Lombok builders function.

---

### 3.2 Mock API & Service Integration Testing
These tests utilize `@WebMvcTest` or mock frameworks to simulate MVC lifecycles and query compilation.

#### Test Target: Spring MVC Layer (`SearchControllerIntegrationTest`)
* **Framework**: Spring `MockMvc` + `MockBean` for [SearchService](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/service/SearchService.java).
* **Test cases**:
  * **Success envelope**: HTTP 200 containing valid DTO JSON structure matching the API contract.
  * **Input Validation failure**: HTTP 400 Bad Request when parameters are invalid.
  * **Connectivity Exception mapping**: HTTP 503 Service Unavailable when the mock service throws `DataAccessResourceFailureException`.
  * **Uncaught Exception safety**: HTTP 500 Internal Server Error when mock service throws a raw `RuntimeException`.

#### Test Target: Query Composition (`SearchServiceIntegrationTest`)
* **Framework**: JUnit 5 + Mockito. Mock `ElasticsearchOperations`.
* **Strategy**: Capture and inspect the `Query` object passed to `elasticsearchOperations.search()`.
* **Test cases**:
  * **Free-text matching**: Assert that query `q` compiles into a `multi_match` query on `name^2` and `description` with `fuzziness(AUTO)`.
  * **Empty text query**: Assert that omitting `q` results in a `match_all` query.
  * **Range filter mapping**: Verify `minPrice` and `maxPrice` compile into a `range` filter query with correct boundary expressions.
  * **Aggregation mapping**: Verify a terms aggregation named `category_facets` targeting the field `category` is present.

---

### 3.3 System Integration Testing (Testcontainers or Live Service)
These tests interact with a real instance of Elasticsearch.

#### Test Target: Seeder & Index Creation (`SeedDataLoaderSystemTest`)
* **Prerequisites**: Elasticsearch container running on port `9200`.
* **Test cases**:
  * **Schema mapping creation**: Verify index `products` is automatically created with strict mapping on startup.
  * **Startup seeder run**: Verify seed documents from `products-seed.json` are loaded when index is empty.
  * **Seeding idempotency**: Run loader again and verify that no duplicate documents are inserted.

#### Test Target: Actual Search Queries (`SearchServiceSystemTest`)
* **Test cases**:
  * **Standard Match Query**: Executing a query for `phone` returns items ranked by title score.
  * **Fuzzy Typo Tolerance Query**: Executing `fone` returns items containing `phone`.
  * **Filtered Search**: Filtering by category returns only items belonging to that category, and has no effect on relevance scores.
  * **Aggregation Scoping**: Verify the facet counts return correct numbers corresponding *only* to the current search results scope.

---

## 4. Test Verification Checklist

Use the checklist below to verify the test suite:

- [ ] Write unit test file `SearchControllerUnitTest.java` verifying boundary validation constraints.
- [ ] Implement `SearchControllerIntegrationTest.java` verifying JSON responses and HTTP status mappings.
- [ ] Implement `SearchServiceIntegrationTest.java` checking query structures and aggregation parameters.
- [ ] Add Jacoco plugin mapping to `pom.xml` with target threshold of `80%`.
- [ ] Create system test verification checking database seeding idempotency.
- [ ] Run `./mvnw clean test` and verify that all tests pass.
- [ ] Inspect generated coverage reports (`target/site/jacoco/index.html`) to ensure coverage targets are met.
