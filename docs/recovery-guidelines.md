# Recovery Guidelines: Product Catalog Search

This document outlines troubleshooting patterns, recovery procedures, and diagnostics routines for the Product Catalog Search system. It focuses on resolving infrastructure failures, mapping drift, data corruption, and memory constraints.

---

## 1. Diagnostic Matrix & Failure Patterns

| Symptom / Error | Likely Root Cause | Diagnostic Commands | Primary Recovery Action |
| :--- | :--- | :--- | :--- |
| **HTTP 503 Service Unavailable** (Spring logs `DataAccessResourceFailureException`) | Elasticsearch container is offline, starting up, or network ports are blocked. | `docker ps -a`<br>`curl -I http://localhost:9200/_cluster/health` | Restart Elasticsearch service container. Verify port binding. |
| **HTTP 500 / 400 on Valid Mappings** (Spring logs indicating type mismatch or unknown fields) | Elasticsearch index mappings are out of sync with [Product.java](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/model/Product.java) entity definition (Mapping Drift). | `curl -s http://localhost:9200/products/_mapping` | Delete the stale index and restart the Spring Boot application to trigger auto-recreation. |
| **Elasticsearch exits with code 137** | Elasticsearch container terminated by Docker Daemon due to Out-Of-Memory (OOM). | `docker inspect elasticsearch -f '{{.State.OOMKilled}}'` | Increase JVM heap memory parameters in `docker-compose.yml`. |
| **No search results returned for valid keywords** | Seed data loading failed on startup, or database was wiped. | `curl -s http://localhost:9200/products/_count` | Verify [products-seed.json](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/resources/products-seed.json) location and trigger seeder by restarting Spring Boot. |

---

## 2. Infrastructure Recovery Procedures

### 2.1 Elasticsearch Service Recovery
If the Elasticsearch container stops unexpectedly:

1. **Check container state**:
   ```bash
   docker ps -a -f name=elasticsearch
   ```
2. **Review execution logs**:
   ```bash
   docker logs elasticsearch
   ```
3. **Restart service**:
   ```bash
   docker-compose restart elasticsearch
   ```
4. **Monitor health status**:
   ```bash
   curl http://localhost:9200/_cluster/health
   ```
   *Note: Wait until the status property is either `green` or `yellow` before starting the Spring Boot application.*

### 2.2 Handling Out-of-Memory (OOM) Exits
If the Elasticsearch container is killed due to memory pressure:

1. Open [docker-compose.yml](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/docker-compose.yml).
2. Adjust environment variable `ES_JAVA_OPTS` to increase memory limits (e.g. to `-Xms1g -Xmx1g` if local resources allow):
   ```yaml
   environment:
     - ES_JAVA_OPTS=-Xms1g -Xmx1g
   ```
3. Re-launch the containers:
   ```bash
   docker-compose up -d
   ```

---

## 3. Index & Data Recovery Procedures

### 3.1 Resolving Mapping Drift
During development, fields inside [Product.java](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/model/Product.java) may change. Since Spring Boot will not alter existing mappings in a live Elasticsearch index, the index must be dropped to apply the changes.

1. **Stop the Spring Boot Application** to prevent concurrent writes.
2. **Delete the products index**:
   ```bash
   curl -X DELETE http://localhost:9200/products
   ```
   *Expected Response:* `{"acknowledged":true}`
3. **Restart the Spring Boot Application**. On boot, [SeedDataLoader](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/config/SeedDataLoader.java) will:
   * Recreate the `products` index.
   * Apply the updated mappings.
   * Re-seed the product catalog.

### 3.2 Re-indexing Seed Data Manually
If data needs to be re-seeded without restarting the Spring Boot application, run these commands:

1. **Delete index**:
   ```bash
   curl -X DELETE http://localhost:9200/products
   ```
2. **Re-create mapping and index by hitting actuator endpoint** (if configured) or restart the Spring Boot app. Alternatively, write mappings manually:
   ```bash
   curl -X PUT "http://localhost:9200/products" -H 'Content-Type: application/json' -d'
   {
     "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
     "mappings": {
       "dynamic": "strict",
       "properties": {
         "id": { "type": "keyword" },
         "name": { "type": "text", "analyzer": "standard", "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } } },
         "description": { "type": "text", "analyzer": "standard" },
         "category": { "type": "keyword" },
         "brand": { "type": "keyword" },
         "price": { "type": "double" },
         "tags": { "type": "keyword" },
         "inStock": { "type": "boolean" },
         "createdAt": { "type": "date", "format": "strict_date_time||epoch_millis" }
       }
     }
   }'
   ```

---

## 4. Query & Relevance Diagnostics

If search results do not match expectations or typo tolerance fails:

1. Use Kibana Dev Tools Console (`http://localhost:5601/app/dev_tools#/console`) to test raw Elasticsearch queries.
2. Run a raw multi-match fuzzy query:
   ```http
   GET /products/_search
   {
     "query": {
       "multi_match": {
         "query": "fone",
         "fields": ["name^2", "description"],
         "type": "best_fields",
         "fuzziness": "AUTO"
       }
     }
   }
   ```
3. Check the `_score` of returned documents. If description matches are scoring too high relative to name matches, increase the boosting factor in [SearchService.java](file:///C:/Users/ALBIN/Desktop/main/DEV/sample-springboot-elasticsearch/src/main/java/com/example/catalog/service/SearchService.java) (e.g., `name^3`).
