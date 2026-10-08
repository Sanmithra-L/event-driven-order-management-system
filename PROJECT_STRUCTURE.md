# Project structure

The repository contains three independently built Maven applications:

| Path | Application |
| --- | --- |
| pom.xml, src/ | Order service |
| payment-service/ | Payment service |
| inventory-service/ | Inventory service |

The Maven wrapper is at the repository root. Use `./mvnw -f <service>/pom.xml` for a child service.

Each application contains:

- `dto/`: JSON event contracts; Java package names are not sent over Kafka.
- `model/` and `repository/`: persisted domain state.
- `service/`: transactional business logic and Kafka listeners.
- `outbox/`: transaction-bound enqueueing and acknowledged publication.
- `config/`: Kafka retry/dead-letter handling and optional scheduling.
- `src/test/`: database regression tests and embedded Kafka tests.

Order service additionally owns the REST API and persisted participant confirmations. Inventory owns the per-order reservation ledger. Payment owns a unique payment row per order.

`docker-compose.yml` starts three MySQL databases, ZooKeeper, Kafka, and Kafka UI. `.github/workflows/verify.yml` verifies all services.

The root `order-service.zip` is a historical scaffold archive; builds use the checked-in source files.
