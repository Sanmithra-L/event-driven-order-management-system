# Event-Driven Order Management System - API Documentation

## Overview

This document describes the REST APIs exposed by the Order Management System microservices.

---

## Order Service API

**Base URL**: `http://localhost:8081`

### 1. Create Order

Creates a new order and initiates the Saga workflow.

**Endpoint**: `POST /orders`

**Request Body**:
```json
{
  "productName": "string",
  "quantity": "integer",
  "price": "double"
}
```

**Example Request**:
```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Laptop",
    "quantity": 2,
    "price": 2400.00
  }'
```

**Response** (201 Created):
```json
{
  "id": 1,
  "productName": "Laptop",
  "quantity": 2,
  "price": 2400.00,
  "status": "CREATED"
}
```

**Status Codes**:
- `201 Created` - Order created successfully
- `400 Bad Request` - Invalid input
- `500 Internal Server Error` - Server error

---

### 2. Get Order by ID

Retrieves an order by its ID.

**Endpoint**: `GET /orders/{id}`

**Path Parameters**:
- `id` (Long) - Order ID

**Example Request**:
```bash
curl http://localhost:8081/orders/1
```

**Response** (200 OK):
```json
{
  "id": 1,
  "productName": "Laptop",
  "quantity": 2,
  "price": 2400.00,
  "status": "COMPLETED"
}
```

**Status Codes**:
- `200 OK` - Order found
- `404 Not Found` - Order not found
- `500 Internal Server Error` - Server error

---

## Order Status Values

| Status | Description |
|--------|-------------|
| `CREATED` | Order has been created, awaiting payment and inventory confirmation |
| `PROCESSING` | Payment successful, waiting for inventory confirmation |
| `COMPLETED` | Both payment and inventory confirmed, order fulfilled |
| `FAILED` | Either payment or inventory failed |

---

## Actuator Endpoints

All services expose Spring Boot Actuator endpoints for monitoring.

### Health Check

**Endpoint**: `GET /actuator/health`

**Example**:
```bash
curl http://localhost:8081/actuator/health
```

**Response**:
```json
{
  "status": "UP"
}
```

### Metrics

**Endpoint**: `GET /actuator/metrics`

Lists all available metrics.

**Endpoint**: `GET /actuator/metrics/{metricName}`

Gets specific metric details.

**Example**:
```bash
curl http://localhost:8081/actuator/metrics/jvm.memory.used
```

---

## Event Schema

### OrderCreatedEvent

Published by: **order-service**  
Consumed by: **payment-service**, **inventory-service**  
Topic: **order-events**

```json
{
  "orderId": 1,
  "productName": "Laptop",
  "quantity": 2,
  "price": 2400.00,
  "status": "CREATED",
  "eventType": "ORDER_CREATED"
}
```

### PaymentProcessedEvent

Published by: **payment-service**  
Consumed by: **order-service**  
Topic: **payment-events**

```json
{
  "orderId": 1,
  "paymentId": 1,
  "paymentSuccessful": true,
  "paymentStatus": "SUCCESS",
  "amount": 2400.00,
  "eventType": "PAYMENT_PROCESSED"
}
```

### InventoryUpdatedEvent

Published by: **inventory-service**  
Consumed by: **order-service**  
Topic: **inventory-events**

```json
{
  "orderId": 1,
  "productName": "Laptop",
  "quantity": 2,
  "inventoryAvailable": true,
  "inventoryStatus": "RESERVED",
  "eventType": "INVENTORY_UPDATED"
}
```

---

## Error Responses

All services use standard HTTP error responses:

### 400 Bad Request
```json
{
  "timestamp": "2025-01-13T00:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid input parameters",
  "path": "/orders"
}
```

### 404 Not Found
```json
{
  "timestamp": "2025-01-13T00:00:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Order not found with ID: 999",
  "path": "/orders/999"
}
```

### 500 Internal Server Error
```json
{
  "timestamp": "2025-01-13T00:00:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "path": "/orders"
}
```

---

## Rate Limiting (Future Enhancement)

Currently not implemented. Recommended for production:

- **Rate Limit**: 100 requests per minute per IP
- **Response Header**: `X-RateLimit-Remaining`
- **Response Code**: `429 Too Many Requests`

---

## Authentication (Future Enhancement)

Currently not implemented. Recommended for production:

- Use JWT tokens
- Bearer authentication in Authorization header
- Example: `Authorization: Bearer <token>`

---

## Postman Collection

Import the following JSON to test APIs:

```json
{
  "info": {
    "name": "Order Management System",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Create Order - Success",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"productName\": \"Laptop\",\n  \"quantity\": 2,\n  \"price\": 2400.00\n}"
        },
        "url": {
          "raw": "http://localhost:8081/orders",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8081",
          "path": ["orders"]
        }
      }
    },
    {
      "name": "Get Order by ID",
      "request": {
        "method": "GET",
        "url": {
          "raw": "http://localhost:8081/orders/1",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8081",
          "path": ["orders", "1"]
        }
      }
    },
    {
      "name": "Health Check",
      "request": {
        "method": "GET",
        "url": {
          "raw": "http://localhost:8081/actuator/health",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8081",
          "path": ["actuator", "health"]
        }
      }
    }
  ]
}
```

---

## WebSocket Support (Future Enhancement)

For real-time order status updates, consider adding WebSocket support:

**Endpoint**: `ws://localhost:8081/ws/orders/{orderId}`

Clients can subscribe to receive real-time status updates.
