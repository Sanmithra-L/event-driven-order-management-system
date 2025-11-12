# Event-Driven Order Management System

A microservice-based order management system demonstrating event-driven architecture, Saga pattern, and distributed transactions using Spring Boot, MySQL, and Apache Kafka.

## 🎯 Project Overview

This system implements an **event-driven microservices architecture** where three independent services collaborate to process orders asynchronously through Kafka messaging. The system showcases:

- **Saga Pattern** for distributed transaction management
- **Event Sourcing** for order state management
- **Eventual Consistency** across microservices
- **Resilience Patterns** (Circuit Breaker, Retry)
- **Clean Architecture** principles

## 🏗️ System Architecture

```
┌─────────────────┐         ┌──────────────┐         ┌───────────────────┐
│  Order Service  │ ◄─────► │    Kafka     │ ◄─────► │ Payment Service   │
│   Port: 8081    │         │              │         │   Port: 8082      │
└─────────────────┘         └──────────────┘         └───────────────────┘
        │                           ▲                         
        │                           │                         
        ▼                           ▼                         
┌─────────────────┐         ┌───────────────────┐
│  MySQL (Order)  │         │ Inventory Service │
│   Port: 3307    │         │   Port: 8083      │
└─────────────────┘         └───────────────────┘
                                    │
                                    ▼
                            ┌───────────────────┐
                            │ MySQL (Inventory) │
                            │   Port: 3309      │
                            └───────────────────┘
```

## 📦 Microservices

### 1. Order Service (Port 8081)
- **Purpose**: Orchestrates order lifecycle using Saga pattern
- **Database**: MySQL (orderdb)
- **Key Features**:
  - REST API for order creation and retrieval
  - Publishes OrderCreatedEvent to Kafka
  - Consumes payment and inventory events
  - Coordinates order status based on saga completion

### 2. Payment Service (Port 8082)
- **Purpose**: Processes payments for orders
- **Database**: MySQL (paymentdb)
- **Key Features**:
  - Consumes OrderCreatedEvent
  - Simulates payment processing
  - Publishes PaymentProcessedEvent
  - Tracks payment status (SUCCESS/FAILED)

### 3. Inventory Service (Port 8083)
- **Purpose**: Manages product inventory
- **Database**: MySQL (inventorydb)
- **Key Features**:
  - Consumes OrderCreatedEvent
  - Reserves/releases inventory
  - Publishes InventoryUpdatedEvent
  - Tracks available and reserved stock

## 🚀 Quick Start

### Prerequisites
- **Java 21**
- **Maven 3.6+**
- **Docker & Docker Compose**

### 1. Start Infrastructure
```bash
docker-compose up -d
```

### 2. Run Services
```bash
# Order Service
cd d:\event-driven-order-management-system
mvnw spring-boot:run

# Payment Service (new terminal)
cd payment-service
mvnw spring-boot:run

# Inventory Service (new terminal)
cd ..\inventory-service
mvnw spring-boot:run
```

### 3. Test the System
```bash
# Create an order
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Laptop",
    "quantity": 2,
    "price": 2400.00
  }'

# Get order status
curl http://localhost:8081/orders/1
```

## 📋 Tech Stack

| Technology | Purpose |
|------------|---------|
| **Spring Boot 3.5.7** | Microservice framework |
| **Java 21** | Programming language |
| **MySQL 8.0** | Relational database |
| **Apache Kafka** | Event streaming platform |
| **Spring Kafka** | Kafka integration |
| **Spring Data JPA** | Database persistence |
| **Lombok** | Boilerplate reduction |
| **Resilience4j** | Circuit breaker & retry |
| **Spring Boot Actuator** | Monitoring & health checks |
| **Docker Compose** | Container orchestration |

## 🎯 Key Features

### Saga Pattern Implementation
- **Choreography-based Saga**: Services coordinate through events
- **Compensation logic**: Inventory rollback on payment failure
- **State tracking**: Order status updates based on all participants

### Event-Driven Communication
- **Topics**: order-events, payment-events, inventory-events
- **Asynchronous processing**: Non-blocking service communication
- **Event sourcing**: Complete audit trail of order lifecycle

### Resilience
- **Circuit Breaker**: Fault tolerance for Kafka operations
- **Retry Logic**: Automatic retry for transient failures
- **Health Checks**: Actuator endpoints for monitoring

## 📚 Documentation

- **[Deployment Guide](DEPLOYMENT_GUIDE.md)** - Complete setup and deployment instructions
- **[API Documentation](API_DOCUMENTATION.md)** - REST API reference
- **[Project Structure](PROJECT_STRUCTURE.md)** - Architecture and component details

## 🧪 Testing Scenarios

### Scenario 1: Successful Order
- Product: Laptop, Quantity: 2, Price: $2400
- ✅ Payment succeeds
- ✅ Inventory available
- **Result**: Order status = `COMPLETED`

### Scenario 2: Payment Failure
- Product: Laptop, Quantity: 1, Price: $15000 (exceeds limit)
- ❌ Payment fails
- **Result**: Order status = `FAILED`

### Scenario 3: Insufficient Inventory
- Product: Laptop, Quantity: 100 (only 50 available)
- ❌ Inventory insufficient
- **Result**: Order status = `FAILED`

## 📊 Monitoring

### Kafka UI
Access http://localhost:8090 to monitor:
- Kafka topics and messages
- Consumer groups and lag
- Message content inspection

### Actuator Endpoints
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Info: `/actuator/info`

## 🎓 Learning Outcomes

This project demonstrates:
1. **Microservices Architecture** with proper service boundaries
2. **Event-Driven Design** for loose coupling
3. **Saga Pattern** for distributed transactions
4. **Resilience Patterns** for fault tolerance
5. **Clean Code** principles (SOLID, DRY)
6. **Production-Ready** practices (monitoring, health checks)

## 📝 Project Information

### Order Service
- **GroupId**: com.edoms.order
- **ArtifactId**: order-service
- **Name**: Order Service
- **Package**: com.edoms.order
- **Java Version**: 21
- **Spring Boot Version**: 3.5.7
- **Packaging**: JAR

## 🔧 Dependencies

### Core Dependencies
- **Spring Web**: RESTful web services and MVC support
- **Spring Data JPA**: Database persistence layer
- **MySQL Driver**: MySQL database connectivity
- **Spring Kafka**: Apache Kafka integration for event-driven messaging
- **Spring Boot Actuator**: Production-ready monitoring and management
- **Lombok**: Reduces boilerplate code with annotations
- **Resilience4j**: Circuit breaker and retry patterns

### Test Dependencies
- Spring Boot Starter Test
- Spring Kafka Test

## Project Structure

```
order-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── edoms/
│   │   │           └── order/
│   │   │               └── OrderServiceApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/
│   │       └── templates/
│   └── test/
│       └── java/
│           └── com/
│               └── edoms/
│                   └── order/
│                       └── OrderServiceApplicationTests.java
├── .mvn/
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

## Prerequisites

- Java 21 or higher
- Maven 3.6+ (or use included Maven wrapper)
- MySQL 8.0+
- Apache Kafka (for event streaming)

## Configuration

Update `src/main/resources/application.properties` with your database and Kafka configurations:

```properties
# Application Name
spring.application.name=order-service

# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/order_db
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=order-service-group
spring.kafka.consumer.auto-offset-reset=earliest

# Actuator Configuration
management.endpoints.web.exposure.include=health,info,metrics
```

## Building the Project

### Using Maven Wrapper (Recommended)

```bash
# Windows
.\mvnw clean install

# Linux/Mac
./mvnw clean install
```

### Using Local Maven

```bash
mvn clean install
```

## Running the Application

### Using Maven Wrapper

```bash
# Windows
.\mvnw spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```

### Using JAR

```bash
java -jar target/order-service-0.0.1-SNAPSHOT.jar
```

## Testing

Run all tests:

```bash
# Windows
.\mvnw test

# Linux/Mac
./mvnw test
```

## Actuator Endpoints

Once running, access actuator endpoints at:

- Health: http://localhost:8080/actuator/health
- Info: http://localhost:8080/actuator/info
- Metrics: http://localhost:8080/actuator/metrics

## Development Guidelines

This project follows Clean Architecture and SOLID principles:

- Write code in English with meaningful comments
- Maintain high test coverage (≥85%)
- Use Lombok annotations to reduce boilerplate
- Follow conventional commit messages (feat:, fix:, refactor:, etc.)
- Document public APIs with proper JavaDoc

## License

Apache-2.0
