# Order API

Base URL: `http://localhost:8081`.

## Create an order

`POST /orders`, Content-Type `application/json`:

```json
{"productName":"Laptop","quantity":2,"price":2400.00}
```

- `productName`: nonblank string, maximum 255 characters; surrounding whitespace is removed.
- `quantity`: positive integer.
- `price`: positive total order amount, minimum 0.01, maximum 12 integer digits and two fractional digits. Stored as a decimal amount.
- Only these fields are accepted. Clients cannot supply an ID, status, or saga confirmations.

Returns `201 Created` and a `Location: /orders/{id}` header:

```json
{"id":1,"productName":"Laptop","quantity":2,"price":2400.00,"status":"CREATED"}
```

Invalid input returns `400`. Successful creation means the order and event are durably stored; processing is asynchronous. Repeating the HTTP request creates another order. API request idempotency keys are not implemented.

## Retrieve an order

`GET /orders/{id}` returns `200` with the order, or `404` if absent.

| Status | Meaning |
| --- | --- |
| CREATED | Waiting for participant results |
| PROCESSING | One participant has succeeded |
| COMPLETED | Payment and inventory both succeeded |
| FAILED | A participant failed; compensation is queued or has run |

Terminal statuses do not regress on duplicate or late results. Internal confirmation fields are omitted from JSON.

## Events

All event values are JSON objects without Java type headers; keys are the decimal order ID.

| Topic | Event types | Consumers |
| --- | --- | --- |
| order-events | ORDER_CREATED, ORDER_CANCELLED | Payment and inventory |
| payment-events | PAYMENT_PROCESSED | Order |
| inventory-events | INVENTORY_UPDATED | Order |

Cancellation has the same fields as creation, with `eventType=ORDER_CANCELLED` and `status=FAILED`. Payment results use `SUCCESS/FAILED`; inventory results use `RESERVED/INSUFFICIENT`. The boolean success field must agree with the status.

Compensation updates participant databases without publishing new success/failure results. A successful simulated payment becomes `REFUNDED`; a cancellation received before creation creates a `CANCELLED` payment record. Inventory retains a `RELEASED` reservation as a replay guard.

Each topic may have a matching `.DLT` topic containing malformed input and an error description. This envelope is for inspection and is not a business event.
