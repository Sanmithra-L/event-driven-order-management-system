# Event-Driven Order Management System

A Java 21 / Spring Boot demo with independent order, payment, and inventory services, MySQL databases, and Kafka.

## Run locally

Install Java 21 and Docker Compose. Start infrastructure from this repository:

```bash
docker compose up -d
```

Run these commands from the repository root in three terminals:

```bash
./mvnw spring-boot:run
./mvnw -f payment-service/pom.xml spring-boot:run
./mvnw -f inventory-service/pom.xml spring-boot:run
```

On Windows use ` .\mvnw.cmd ` instead of `./mvnw`. Only the root contains the Maven wrapper. Wait for the infrastructure health checks before starting services.

| Service | HTTP port | Local database |
| --- | --- | --- |
| Order | 8081 | localhost:3307/orderdb |
| Payment | 8082 | localhost:3308/paymentdb |
| Inventory | 8083 | localhost:3309/inventorydb |

Kafka uses localhost:9092; Kafka UI uses http://localhost:8090. Development database credentials are root / mypassword. Override configuration using the environment variables in [Deployment Guide](DEPLOYMENT_GUIDE.md).

```bash
curl -X POST http://localhost:8081/orders -H "Content-Type: application/json" \
  -d '{"productName":"Laptop","quantity":2,"price":2400.00}'
curl http://localhost:8081/orders/1
```

`price` is the **total order amount**, with at most two decimal places. It is not multiplied by quantity. Payment is simulated: positive totals up to 10000.00 succeed; larger totals fail. Initial stock is Laptop 50, Mouse 100, Keyboard 75.

## Processing and recovery

1. Order creation commits the order and an `ORDER_CREATED` outbox row in one database transaction.
2. A publisher sends committed outbox rows to Kafka and deletes them only after broker acknowledgement.
3. Payment and inventory consumers store one result per order. Product rows are locked while reserving stock.
4. Order service persists each participant result. Both successes complete the order; either failure makes it terminally failed and queues `ORDER_CANCELLED`.
5. Cancellation uses the same `order-events` topic and order ID key. Inventory releases only that order's actual reservation; payment changes a successful simulated payment to `REFUNDED`. Repeated cancellations have no additional effect.

This is an at-least-once, choreography-based saga with durable state. A crash after publishing but before deleting an outbox row can deliver an event again; consumers handle replays idempotently. A failed order may appear before compensation has finished.

Transient listener failures are retried without acknowledging the record. Malformed/invalid messages go to `<source-topic>.DLT` as an envelope with `payload` and `error`; failed dead-letter publication is retried. Inspect and correct dead letters before manually republishing the original payload.

The project does not implement event sourcing, a real payment gateway, authentication, or production database migrations. Completed orders retain their stock reservation; shipment/fulfilment is outside this demo.

## Verify

```bash
./mvnw verify
./mvnw -f payment-service/pom.xml verify
./mvnw -f inventory-service/pom.xml verify
```

Service tests use H2 and embedded Kafka; no external database or Docker is needed. GitHub Actions runs all three builds, then checks the complete Compose stack with `scripts/smoke-test.sh`.

- [API](API_DOCUMENTATION.md)
- [Deployment and existing-data upgrades](DEPLOYMENT_GUIDE.md)
- [Testing](TESTING_GUIDE.md)
- [Project structure](PROJECT_STRUCTURE.md)
- [Repair summary](BUILD_SUMMARY.md)
