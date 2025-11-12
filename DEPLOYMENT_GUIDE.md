# Event-Driven Order Management System - Deployment Guide

## Prerequisites

- **Java 21** or higher
- **Maven 3.6+**
- **Docker** and **Docker Compose**
- **Git**

## Quick Start (Local Development)

### Step 1: Start Infrastructure Services

Start MySQL, Kafka, and Zookeeper using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- MySQL (Order DB) on port **3307**
- MySQL (Payment DB) on port **3308**
- MySQL (Inventory DB) on port **3309**
- Kafka on port **9092**
- Zookeeper on port **2181**
- Kafka UI on port **8090** (http://localhost:8090)

Wait for all services to be healthy:

```bash
docker-compose ps
```

### Step 2: Build All Services

```bash
# Build order-service
cd d:\event-driven-order-management-system
mvnw clean install

# Build payment-service
cd payment-service
mvnw clean install

# Build inventory-service
cd ..\inventory-service
mvnw clean install
```

### Step 3: Run Microservices

Open **3 separate terminals** and run each service:

#### Terminal 1 - Order Service
```bash
cd d:\event-driven-order-management-system
mvnw spring-boot:run
```
- **Port**: 8081
- **Health**: http://localhost:8081/actuator/health

#### Terminal 2 - Payment Service
```bash
cd d:\event-driven-order-management-system\payment-service
mvnw spring-boot:run
```
- **Port**: 8082
- **Health**: http://localhost:8082/actuator/health

#### Terminal 3 - Inventory Service
```bash
cd d:\event-driven-order-management-system\inventory-service
mvnw spring-boot:run
```
- **Port**: 8083
- **Health**: http://localhost:8083/actuator/health

### Step 4: Verify System is Running

Check all services are healthy:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

Monitor Kafka topics via Kafka UI:
- Open http://localhost:8090
- View topics: order-events, payment-events, inventory-events

---

## Testing the System

### Create an Order (Success Scenario)

```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Laptop",
    "quantity": 2,
    "price": 2400.00
  }'
```

**Expected Flow:**
1. Order created with status `CREATED`
2. Order event published to Kafka
3. Payment service processes payment → `SUCCESS`
4. Inventory service reserves stock → `RESERVED`
5. Order status updated to `COMPLETED`

### Create an Order (Payment Failure Scenario)

```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Laptop",
    "quantity": 1,
    "price": 15000.00
  }'
```

**Expected Flow:**
1. Order created with status `CREATED`
2. Payment fails (amount > 10000)
3. Order status updated to `FAILED`

### Create an Order (Inventory Insufficient Scenario)

```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Laptop",
    "quantity": 100,
    "price": 5000.00
  }'
```

**Expected Flow:**
1. Order created with status `CREATED`
2. Inventory insufficient (only 50 available)
3. Order status updated to `FAILED`

### Retrieve an Order

```bash
curl http://localhost:8081/orders/1
```

---

## Database Access

### MySQL Access

Connect to MySQL databases:

```bash
# Order Database
mysql -h localhost -P 3307 -u root -pmypassword orderdb

# Payment Database
mysql -h localhost -P 3308 -u root -pmypassword paymentdb

# Inventory Database
mysql -h localhost -P 3309 -u root -pmypassword inventorydb
```

### Sample Queries

```sql
-- View orders
USE orderdb;
SELECT * FROM orders;

-- View payments
USE paymentdb;
SELECT * FROM payments;

-- View inventory
USE inventorydb;
SELECT * FROM inventory;
```

---

## Monitoring

### Actuator Endpoints

Each service exposes actuator endpoints:

- **Health**: `/actuator/health`
- **Info**: `/actuator/info`
- **Metrics**: `/actuator/metrics`

### Kafka Monitoring

Access Kafka UI at http://localhost:8090 to:
- View topics and messages
- Monitor consumer lag
- Inspect message contents

---

## Troubleshooting

### Service Won't Start

**Issue**: Port already in use

```bash
# Windows: Find and kill process
netstat -ano | findstr :8081
taskkill /PID <process_id> /F
```

**Issue**: Database connection failed

```bash
# Check MySQL is running
docker-compose ps mysql-order

# Restart MySQL
docker-compose restart mysql-order
```

### Kafka Issues

**Issue**: Kafka not ready

```bash
# Check Kafka health
docker-compose logs kafka

# Restart Kafka and Zookeeper
docker-compose restart zookeeper kafka
```

### Messages Not Being Consumed

1. Check Kafka UI for messages in topics
2. Verify consumer groups are active
3. Check service logs for errors
4. Ensure all services are running

---

## Stopping the System

### Stop Microservices
Press `Ctrl+C` in each terminal running the services.

### Stop Infrastructure
```bash
docker-compose down
```

### Clean Up (Remove Data)
```bash
docker-compose down -v
```

---

## Production Deployment Considerations

### Environment Variables

Create `.env` file for production:

```env
DB_USERNAME=prod_user
DB_PASSWORD=<secure_password>
KAFKA_BOOTSTRAP_SERVERS=kafka.production.com:9092
```

### Security

- Use **secrets management** (AWS Secrets Manager, HashiCorp Vault)
- Enable **SSL/TLS** for Kafka
- Configure **database encryption**
- Implement **API authentication** (JWT, OAuth2)

### Scaling

- Run multiple instances of each service
- Configure Kafka **consumer groups** properly
- Use **load balancers** for API endpoints
- Implement **database connection pooling**

### Observability

- Integrate with **Prometheus** for metrics
- Use **Grafana** for dashboards
- Implement **distributed tracing** (Zipkin, Jaeger)
- Centralized logging (**ELK Stack**, **Splunk**)

---

## Next Steps

- Implement API Gateway (Spring Cloud Gateway)
- Add service discovery (Consul, Eureka)
- Implement authentication and authorization
- Add comprehensive integration tests
- Set up CI/CD pipeline
