# API Specifications: Product Catalog Search

This document defines the API contracts, endpoint details, query parameter validations, and standard response envelopes for the Product Catalog Search system.

---

## 1. Base URL & Protocol

All requests must communicate over HTTP/1.1 or HTTP/2.

* **Base URL**: `http://localhost:8080` (Standard local development context)
* **Format**: All payloads, including successful responses and error models, are encoded in `application/json`.

---

## 2. Search Endpoint Specification

### 2.1 HTTP Request
`GET /search`

#### Query Parameters
| Parameter | Type | Required | Default | Validation / Constraints | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `q` | String | No | *None* | Trimmed, maximum 256 characters. | Free-text search query targeting the product `name` (boosted) and `description` fields. Supports typo tolerance. |
| `category` | String | No | *None* | Exact keyword match. Case-sensitive. | Filters results to a specific product category. |
| `minPrice` | Double | No | *None* | `minPrice >= 0.0`. Must be `<= maxPrice` if both are supplied. | Inclusive lower boundary for product price filtering. |
| `maxPrice` | Double | No | *None* | `maxPrice >= 0.0`. Must be `>= minPrice` if both are supplied. | Inclusive upper boundary for product price filtering. |
| `page` | Integer | No | `0` | `page >= 0`. | Zero-indexed page number for pagination. |
| `size` | Integer | No | `10` | `size > 0`, maximum `100`. | Number of search results to return in a single page. |

---

### 2.2 HTTP Responses

#### Response Schema (200 OK)
The search returns a paginated list of products matched by query/filter criteria, alongside pagination statistics and a terms-aggregation facet mapping of active product categories.

```json
{
  "results": [
    {
      "id": "prod-101",
      "name": "SuperPhone X",
      "description": "High-performance smartphone with advanced dual-lens camera system.",
      "category": "electronics",
      "brand": "TechCorp",
      "price": 899.99,
      "tags": ["mobile", "smartphone", "5g"],
      "inStock": true,
      "createdAt": "2026-07-01T12:00:00.000Z"
    }
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

##### Field Definitions (200 OK)
* `results` (Array): Collection of matching product documents.
  * `id` (String): Unique identifier of the product.
  * `name` (String): Product name.
  * `description` (String): Product description.
  * `category` (String): Broad categorization keyword.
  * `brand` (String): Brand or manufacturer keyword (optional).
  * `price` (Double): Decimal price value.
  * `tags` (Array of Strings): Tag labels associated with the product.
  * `inStock` (Boolean): Stock availability status.
  * `createdAt` (String): ISO-8601 UTC timestamp of creation.
* `page` (Integer): The current page number (0-indexed).
* `size` (Integer): Requested page size limit.
* `totalHits` (Long): The total number of documents matching query/filters in the entire index.
* `facets` (Object): Map of aggregate category facet counts within the scope of current filters.
  * `category` (Map<String, Long>): Keys representing unique categories matched, values indicating count of occurrences.

---

### 2.3 Error Responses

When the API encounters failures, it maps them to specific status codes.

#### 400 Bad Request (Validation Failure)
Occurs when request parameters violate validation constraints (e.g. negative pages, negative prices, or `minPrice > maxPrice`).

* **Status Code**: `400 Bad Request`
* **Response Body**:
```json
{
  "error": "Bad Request",
  "message": "minPrice cannot be greater than maxPrice"
}
```

#### 503 Service Unavailable (Elasticsearch Offline)
Occurs when the application server cannot connect to the backend Elasticsearch server (e.g. cluster is starting up, offline, or network socket times out).

* **Status Code**: `503 Service Unavailable`
* **Response Body**:
```json
{
  "error": "Service Unavailable",
  "message": "Search service is temporarily unavailable"
}
```

#### 500 Internal Server Error (Generic Failure)
Fallback for unhandled runtime exceptions inside the Spring Boot container.

* **Status Code**: `500 Internal Server Error`
* **Response Body**:
```json
{
  "error": "Internal Error",
  "message": "An unexpected error occurred"
}
```

---

## 3. Example Request & Response Scenarios

### Scenario A: Basic Text Search with Fuzziness
**Request**:
```http
GET /search?q=fone HTTP/1.1
Host: localhost:8080
```

**Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "results": [
    {
      "id": "prod-101",
      "name": "SuperPhone X",
      "description": "High-performance smartphone with advanced dual-lens camera system.",
      "category": "electronics",
      "brand": "TechCorp",
      "price": 899.99,
      "tags": ["mobile", "smartphone", "5g"],
      "inStock": true,
      "createdAt": "2026-07-01T12:00:00.000Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalHits": 1,
  "facets": {
    "category": {
      "electronics": 1
    }
  }
}
```

---

### Scenario B: Filtered Search with Price Boundaries
**Request**:
```http
GET /search?category=electronics&minPrice=100&maxPrice=1000 HTTP/1.1
Host: localhost:8080
```

**Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "results": [
    {
      "id": "prod-101",
      "name": "SuperPhone X",
      "description": "High-performance smartphone with advanced dual-lens camera system.",
      "category": "electronics",
      "brand": "TechCorp",
      "price": 899.99,
      "tags": ["mobile", "smartphone", "5g"],
      "inStock": true,
      "createdAt": "2026-07-01T12:00:00.000Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalHits": 1,
  "facets": {
    "category": {
      "electronics": 1
    }
  }
}
```
