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
