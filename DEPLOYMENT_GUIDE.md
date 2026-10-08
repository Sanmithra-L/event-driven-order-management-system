# Local deployment

Requirements: Java 21, Maven wrapper (included) or Maven, and Docker Compose.

From the repository root:

```bash
docker compose up -d
docker compose ps
./mvnw verify
./mvnw -f payment-service/pom.xml verify
./mvnw -f inventory-service/pom.xml verify
```

Start each service in its own terminal:

```bash
java -jar target/order-service-0.0.1-SNAPSHOT.jar
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
java -jar inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar
```

Windows wrapper commands use `.\mvnw.cmd` from the root with the same `-f` arguments.

## Configuration

| Environment variable | Default |
| --- | --- |
| SERVER_PORT | 8081 order / 8082 payment / 8083 inventory |
| DB_URL | jdbc:mysql://localhost:3307/orderdb, :3308/paymentdb, or :3309/inventorydb |
| DB_USERNAME | root |
| DB_PASSWORD | mypassword (also used by Compose for initial MySQL setup) |
| DDL_AUTO | update |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 |

If running applications inside containers, use the relevant MySQL service name on port 3306 and `kafka:29092`. The Compose file starts infrastructure only.

The development setup uses plaintext Kafka and sample credentials. Authentication, authorization, TLS, managed migrations, retention policies, and a real payment provider require separate deployment work.

Health endpoints are at `/actuator/health`; `info` and `metrics` are also exposed. Kafka UI runs on port 8090.

## Persistence and recovery

MySQL, Kafka, and ZooKeeper use named volumes. `docker compose down` preserves them. Removing volumes discards their data.

Each service has an `outbox_events` table. Pending rows remain on broker failure and are retried by the publisher, every 500 ms by default. `edoms.outbox.poll-interval-ms` changes that delay. The publisher locks a batch in ID order; consumers tolerate replay after publication succeeds but the database commit fails.

Kafka retries transient processing errors. An unavailable database can hold up a partition; check logs and restore the dependency. Invalid events are retained in the corresponding `.DLT` topic. Correct the cause before republishing the original payload.

Initial demo inventory is seeded before listeners start on an empty database. Disable `edoms.inventory.seed-data` when managing your own stock or running multiple inventory instances. Use a single instance for first-time demo seeding.

## Upgrading existing data

These changes add persisted order confirmations, outbox tables, inventory reservations, unique payment order IDs, unique product names, and decimal monetary columns. Back up databases and stop old application instances before upgrading.

Before applying schema changes, reconcile duplicate `payments.order_id` and `inventory.product_name` values and review conversion of existing floating-point amounts to DECIMAL(14,2). Hibernate's development `update` mode is not a substitute for a reviewed migration.

Previously pending orders have no recoverable in-memory confirmations, and old stock reservations have no per-order ledger. Reconcile those records against payment, inventory, and Kafka history before resuming or replaying events. Do not replay historical creation events against old balances without that reconciliation. Existing terminal orders are preserved; historical compensations are not run automatically.

For existing non-demo environments, create and validate a migration against a database copy before setting `DDL_AUTO=validate`.

## Validation limits

Automated tests exercise H2 transactions, concurrency, embedded Kafka, serializers, listeners, and dead-letter publication. MySQL-specific migrations and this Compose stack must also be checked in an environment with Docker before deployment.
