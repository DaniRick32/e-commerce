# E-commerce

This is a demo app, written in Java Spring Boot.

Development in progress.

## Running

Product search is cached in Redis. Start it with Docker:

```bash
docker compose up -d redis
```

Then start the app:

```bash
./mvnw spring-boot:run
```

Redis is optional: if it is unreachable, search falls back to the database and
the app keeps working (cache errors are logged and retried every 30 seconds).

Cache settings live in `src/main/resources/application.properties`:

| Property | Default | Description |
| --- | --- | --- |
| `app.cache.product-search.enabled` | `true` | Turn the search cache on or off |
| `app.cache.product-search.ttl` | `60s` | How long cached search results live |
| `spring.data.redis.host` | `localhost` | Redis host (override with `REDIS_HOST`) |
| `spring.data.redis.port` | `6379` | Redis port (override with `REDIS_PORT`) |

Search results are invalidated automatically when a product is created,
updated, or deleted.

## Brands

Brands (manufacturers) are managed through `/api/brands`:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/brands` | List all brands |
| `GET` | `/api/brands/{id}` | Get a single brand |
| `GET` | `/api/brands/{id}/products` | List the products of a brand |
| `POST` | `/api/brands` | Create a brand |
| `PATCH` | `/api/brands/{id}` | Partially update a brand |
| `DELETE` | `/api/brands/{id}` | Delete a brand (refused while products use it) |

A brand stores its `name`, `countryOfMade`, `rating` (0–5) and `description`.
Products reference a brand through `brandId`; product responses also include
`brandName`, `brandCountryOfMade` and `brandRating`. The product search endpoint
accepts a `brandId` filter alongside the category and price filters.
