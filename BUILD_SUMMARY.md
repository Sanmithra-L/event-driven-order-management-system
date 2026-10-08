# Repair summary

The code review identified and corrected these failures:

| Failure | Correction |
| --- | --- |
| JSON deserializers conflicted with String listener inputs and absent type headers | Consume strings and parse each topic's DTO; use Boot's configured Kafka factories |
| Listener exceptions were logged and swallowed | Propagate failures; retry transient errors and retain invalid messages in dead letters |
| Asynchronous publish failures could leave committed state without an event | Transactional database outbox with broker acknowledgement before deletion |
| Saga progress lived only in process memory | Persist participant confirmations on the order |
| Concurrent or duplicate results could regress/lossily update status | Lock order rows and preserve terminal states |
| Duplicate creation could charge or reserve repeatedly | Unique payment order IDs and a per-order reservation ledger |
| Concurrent orders could oversell | Lock inventory rows during stock changes |
| Failed orders never invoked compensation | Durable cancellation commands release stock and refund simulated payments |
| Repeated release could invent stock | Release only the saved reservation once |
| Invalid requests could accept negative values or client-owned IDs | Validated request DTO and decimal monetary values |
| Database ports differed from Compose | Align defaults with 3307, 3308, and 3309 |
| Kafka internal listener was incompletely configured | Bind both listeners and select the internal inter-broker listener |
| Tests needed external MySQL and covered only context loading | H2 regression tests and embedded Kafka flows for all services |
| Setup instructions used missing child wrappers and claimed unimplemented features | Correct commands, recovery behavior, and project limits |

See [Testing Guide](TESTING_GUIDE.md) for verification and [Deployment Guide](DEPLOYMENT_GUIDE.md) before upgrading existing databases.

Kafka retry behavior follows the [Spring Kafka error-handling documentation](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html). These changes provide at-least-once delivery with idempotent consumers, not exactly-once execution across MySQL and Kafka.
