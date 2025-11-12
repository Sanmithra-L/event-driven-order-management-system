# Event-Driven Order Management System - Project Structure

## Overview
A microservice-based order management system using Spring Boot, MySQL, Kafka, and Saga pattern for distributed transactions.

## Current Progress

### ✅ Order-Service (COMPLETED)
**Location**: `d:\event-driven-order-management-system`

#### Components Created:
1. **Entity Layer**
   - `Order.java` - Entity with id, productName, quantity, price, status
   - `OrderStatus.java` - Enum (CREATED, PROCESSING, COMPLETED, FAILED)

2. **Repository Layer**
   - `OrderRepository.java` - JPA repository with custom queries

3. **DTO Layer**
   - `OrderCreatedEvent.java` - Published when order is created
   - `PaymentProcessedEvent.java` - Consumed from payment-service
   - `InventoryUpdatedEvent.java` - Consumed from inventory-service

4. **Configuration Layer**
   - `KafkaProducerConfig.java` - Kafka producer setup with JSON serialization
   - `KafkaConsumerConfig.java` - Kafka consumer setup for event consumption

5. **Service Layer**
   - `OrderService.java` - Business logic with Saga coordination
   - `KafkaProducerService.java` - Publishes events to Kafka
   - `KafkaConsumerService.java` - Consumes payment/inventory events

6. **Controller Layer**
   - `OrderController.java` - REST endpoints (POST /orders, GET /orders/{id})

7. **Configuration Files**
   - `pom.xml` - Maven dependencies (Spring Web, JPA, MySQL, Kafka, Actuator, Lombok)
   - `application.properties` - MySQL, Kafka, server configuration

#### Kafka Topics:
- **order-events** - Publishes OrderCreatedEvent
- **payment-events** - Consumes PaymentProcessedEvent
- **inventory-events** - Consumes InventoryUpdatedEvent

#### Saga Pattern Implementation:
- Tracks payment and inventory status for each order
- Updates order status based on both confirmations
- Handles failure scenarios with status rollback

---

## Next Steps

### 🔄 Payment-Service (IN PROGRESS)
**Location**: `d:\event-driven-order-management-system\payment-service` (to be created)

#### Components Needed:
1. **Entity**: Payment (id, orderId, amount, paymentStatus)
2. **Repository**: PaymentRepository
3. **Service**: PaymentService (process payments), KafkaConsumer (listen to order-events), KafkaProducer (publish payment-events)
4. **Configuration**: Kafka setup
5. **Database**: MySQL (separate schema)

---

### ⏳ Inventory-Service (PENDING)
**Location**: `d:\event-driven-order-management-system\inventory-service` (to be created)

#### Components Needed:
1. **Entity**: Inventory (id, productName, availableStock)
2. **Repository**: InventoryRepository
3. **Service**: InventoryService (manage stock), KafkaConsumer (listen to order-events), KafkaProducer (publish inventory-events)
4. **Configuration**: Kafka setup
5. **Database**: MySQL (separate schema)

---

### ⏳ Additional Components (PENDING)

#### Resilience4j
- Circuit breaker for Kafka operations
- Retry mechanisms for failed operations
- Update pom.xml with Resilience4j dependencies

#### Docker Infrastructure
- `docker-compose.yml` for:
  - MySQL containers (orderdb, paymentdb, inventorydb)
  - Kafka
  - Zookeeper
  - Optional: Kafka UI

#### Testing
- Unit tests for services
- Integration tests for Kafka flows
- End-to-end Saga pattern testing

---

## Architecture Flow

```
User → POST /orders
  ↓
Order-Service
  ├─ Save order (status: CREATED)
  ├─ Publish OrderCreatedEvent → order-events topic
  ↓
Payment-Service (consumes order-events)
  ├─ Process payment
  ├─ Publish PaymentProcessedEvent → payment-events
  ↓
Inventory-Service (consumes order-events)
  ├─ Check/reserve inventory
  ├─ Publish InventoryUpdatedEvent → inventory-events
  ↓
Order-Service (consumes payment-events & inventory-events)
  ├─ If both success → status: COMPLETED
  ├─ If any fails → status: FAILED (compensation)
```

---

## Technology Stack
- **Backend**: Spring Boot 3.5.7, Java 21
- **Database**: MySQL 8.0+
- **Messaging**: Apache Kafka
- **Resilience**: Resilience4j (planned)
- **Build Tool**: Maven
- **Containerization**: Docker, Docker Compose (planned)

---

## Database Schemas

### orderdb
- **orders** table (id, product_name, quantity, price, status)

### paymentdb (to be created)
- **payments** table (id, order_id, amount, payment_status)

### inventorydb (to be created)
- **inventory** table (id, product_name, available_stock)

---

## Kafka Topics Summary

| Topic | Producer | Consumer | Event Type |
|-------|----------|----------|------------|
| order-events | order-service | payment-service, inventory-service | OrderCreatedEvent |
| payment-events | payment-service | order-service | PaymentProcessedEvent |
| inventory-events | inventory-service | order-service | InventoryUpdatedEvent |
