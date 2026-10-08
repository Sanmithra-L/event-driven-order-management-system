# Testing

Run all three independent service builds from the repository root:

```bash
./mvnw verify
./mvnw -f payment-service/pom.xml verify
./mvnw -f inventory-service/pom.xml verify
```

On Windows replace `./mvnw` with `.\mvnw.cmd`. GitHub Actions runs the same commands.

After those builds pass, CI starts the actual Compose stack and runs `bash scripts/smoke-test.sh` against MySQL and Kafka. It checks completed orders, stock restoration after failed payments, refunds after insufficient inventory, and HTTP validation. Run this script only against a fresh demo database; it expects the initial sample stock and creates test orders.

## Automated coverage

The test profile uses an isolated H2 database in MySQL compatibility mode. Database tests invoke transactional Spring services and inspect committed state. Kafka tests start an embedded broker and exercise JSON serialization, listeners, outgoing event publication, cancellation routing, and invalid-message dead letters.

Covered regressions include:

- Both arrival orders of payment and inventory results.
- Concurrent results without lost updates.
- Duplicate messages and terminal order state.
- Failure-triggered cancellation.
- Broker publication failure retaining an outbox row.
- Request validation, decimal money, unknown IDs, and rejection of client-owned status/ID fields.
- Single payment per order, payment limits, repeat refunds, and cancellation before creation.
- Duplicate reservations, repeat releases, unknown products, insufficient stock, and concurrent orders competing for stock.
- Database rollback if recording an outgoing result fails.
- Malformed messages reaching a dead-letter topic instead of being silently acknowledged.

Reports are under each service's `target/surefire-reports`.

## Manual integration scenarios

Start the Compose stack and all three applications as described in [Deployment Guide](DEPLOYMENT_GUIDE.md).

1. Laptop / quantity 2 / price 2400.00: poll the returned order URL until COMPLETED.
2. Laptop / quantity 1 / price 15000.00: expect FAILED; after compensation, available stock must be restored.
3. Laptop / quantity 100 / price 500.00: expect FAILED; any successful simulated payment must become REFUNDED.
4. Stop Kafka, create an order, and confirm an outbox row remains. Restart Kafka and confirm eventual processing.
5. Restart the order service after only one result has arrived; the next result must complete the persisted saga.
6. Republish a creation/cancellation event with its original order ID key. Payment and stock must change at most once.

Compensation is asynchronous: FAILED is visible before every participant necessarily finishes cleanup. H2 tests do not prove MySQL migration compatibility.
