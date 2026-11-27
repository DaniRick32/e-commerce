# E-commerce

This is a demo app, written in Java Spring Boot.

Development in progress.

## Running

The app uses PostgreSQL. Product search is cached in Redis. `compose.yaml`
starts all three containers, including the Spring Boot app built from the
Dockerfile:

```bash
docker compose up --build
```

The API is then available at `http://localhost:8080`. Add `-d` to run in the
background, and use `docker compose logs -f app` to follow the application
logs. Compose waits for PostgreSQL and Redis to become healthy before starting
the app, and connects to them by their Compose service names (`postgres` and
`redis`). Stop the stack with `docker compose down`.

The defaults in `application.properties` target local host development. For
that workflow, start only the backing services and run the app from the host:

```bash
docker compose up -d postgres redis
./mvnw spring-boot:run
```

By default the host-run app connects to
`jdbc:postgresql://localhost:5432/ecommerce` with the username and password
`postgres`. Override the connection with the `DB_URL`, `DB_USERNAME` and
`DB_PASSWORD` environment variables.

Hibernate creates and updates the schema on startup
(`spring.jpa.hibernate.ddl-auto=update`).

Redis runs in the `redis` Docker container defined in `compose.yaml`; no local
Redis installation is needed. The container publishes its port on loopback only
(`127.0.0.1:6380`) because it has no password; port 6380 is used instead of
6379 so a locally installed Redis can keep running on the default port. This
published port is for host development, while the containerized app uses the
internal Compose network (`redis:6379`). To use the container's Redis from a
host-run app, set `REDIS_HOST=127.0.0.1` and `REDIS_PORT=6380`. Check the
container with:

```bash
docker compose exec redis redis-cli ping
```

If Redis is unreachable, search falls back to the database and the app keeps
working (cache errors are logged and retried every 30 seconds).

Cache settings live in `src/main/resources/application.properties`:

| Property | Default | Description |
| --- | --- | --- |
| `app.cache.product-search.enabled` | `true` | Turn the search cache on or off |
| `app.cache.product-search.ttl` | `60s` | How long cached search results live |
| `spring.data.redis.host` | `127.0.0.1` | Redis host; Compose uses `redis` (override with `REDIS_HOST`) |
| `spring.data.redis.port` | `6379` | Redis port (`6380` when using the container from the host; override with `REDIS_PORT`) |

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
