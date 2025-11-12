# Event-Driven Order Management System - Testing Guide

## Overview

This guide provides comprehensive testing instructions for the Event-Driven Order Management System.

---

## 🧪 Test Scenarios

### Scenario 1: Successful Order Processing

**Objective**: Verify complete order lifecycle when payment and inventory are successful.

**Steps**:

1. **Create Order**
```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Laptop",
    "quantity": 2,
    "price": 2400.00
  }'
```

2. **Expected Response** (Status: 201 Created):
```json
{
  "id": 1,
  "productName": "Laptop",
  "quantity": 2,
  "price": 2400.00,
  "status": "CREATED"
}
```

3. **Wait 2-3 seconds** for event processing

4. **Check Order Status**:
```bash
curl http://localhost:8081/orders/1
```

5. **Expected Final Status**:
```json
{
  "id": 1,
  "productName": "Laptop",
  "quantity": 2,
  "price": 2400.00,
  "status": "COMPLETED"
}
```

**Verification**:
- ✅ Order created with status `CREATED`
- ✅ Payment processed successfully
- ✅ Inventory reserved (2 laptops)
- ✅ Order status updated to `COMPLETED`

---

### Scenario 2: Payment Failure

**Objective**: Verify order failure when payment exceeds limit.

**Test Case**: Payment amount > $10,000

**Steps**:

1. **Create High-Value Order**:
```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Server",
    "quantity": 1,
    "price": 15000.00
  }'
```

2. **Wait 2-3 seconds**

3. **Check Order Status**:
```bash
curl http://localhost:8081/orders/2
```

4. **Expected Status**:
```json
{
  "id": 2,
  "productName": "Server",
  "quantity": 1,
  "price": 15000.00,
  "status": "FAILED"
}
```

**Verification**:
- ✅ Order created with status `CREATED`
- ❌ Payment failed (amount > 10000)
- ✅ Order status updated to `FAILED`

---

### Scenario 3: Insufficient Inventory

**Objective**: Verify order failure when inventory is insufficient.

**Pre-condition**: Inventory service has 50 Laptops available

**Steps**:

1. **Create Order Exceeding Stock**:
```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Laptop",
    "quantity": 100,
    "price": 5000.00
  }'
```

2. **Wait 2-3 seconds**

3. **Check Order Status**:
```bash
curl http://localhost:8081/orders/3
```

4. **Expected Status**:
```json
{
  "id": 3,
  "productName": "Laptop",
  "quantity": 100,
  "price": 5000.00,
  "status": "FAILED"
}
```

**Verification**:
- ✅ Order created with status `CREATED`
- ✅ Payment processed successfully
- ❌ Inventory insufficient (only 50 available)
- ✅ Order status updated to `FAILED`

---

### Scenario 4: Product Not in Inventory

**Objective**: Verify order failure when product doesn't exist.

**Steps**:

1. **Create Order for Non-Existent Product**:
```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "Tablet",
    "quantity": 1,
    "price": 500.00
  }'
```

2. **Wait 2-3 seconds**

3. **Check Order Status**:
```bash
curl http://localhost:8081/orders/4
```

4. **Expected Status**:
```json
{
  "id": 4,
  "productName": "Tablet",
  "quantity": 1,
  "price": 500.00,
  "status": "FAILED"
}
```

**Verification**:
- ✅ Order created
- ❌ Product not found in inventory
- ✅ Order status updated to `FAILED`

---

## 🔍 Event Flow Verification

### Monitor Kafka Events

1. **Access Kafka UI**: http://localhost:8090

2. **Check order-events Topic**:
   - Should contain `OrderCreatedEvent` messages
   - One message per order created

3. **Check payment-events Topic**:
   - Should contain `PaymentProcessedEvent` messages
   - Status: SUCCESS or FAILED

4. **Check inventory-events Topic**:
   - Should contain `InventoryUpdatedEvent` messages
   - Status: RESERVED, INSUFFICIENT, or RELEASED

---

## 📊 Database Verification

### Order Database

```bash
mysql -h localhost -P 3307 -u root -pmypassword orderdb
```

```sql
-- View all orders
SELECT * FROM orders;

-- Check order status distribution
SELECT status, COUNT(*) FROM orders GROUP BY status;

-- Find failed orders
SELECT * FROM orders WHERE status = 'FAILED';
```

### Payment Database

```bash
mysql -h localhost -P 3308 -u root -pmypassword paymentdb
```

```sql
-- View all payments
SELECT * FROM payments;

-- Check payment success rate
SELECT payment_status, COUNT(*) FROM payments GROUP BY payment_status;

-- Find payments for specific order
SELECT * FROM payments WHERE order_id = 1;
```

### Inventory Database

```bash
mysql -h localhost -P 3309 -u root -pmypassword inventorydb
```

```sql
-- View current inventory
SELECT * FROM inventory;

-- Check available vs reserved stock
SELECT 
    product_name,
    available_stock,
    reserved_stock,
    (available_stock + reserved_stock) as total_stock
FROM inventory;

-- Find low stock items
SELECT * FROM inventory WHERE available_stock < 10;
```

---

## 🏥 Health Check Tests

### Service Health

```bash
# Order Service
curl http://localhost:8081/actuator/health

# Payment Service
curl http://localhost:8082/actuator/health

# Inventory Service
curl http://localhost:8083/actuator/health
```

**Expected Response** (All Services):
```json
{
  "status": "UP"
}
```

### Infrastructure Health

```bash
# Check Docker containers
docker-compose ps

# Check Kafka
docker-compose logs kafka | tail -50

# Check MySQL
docker-compose logs mysql-order | tail -50
```

---

## ⚡ Load Testing

### Concurrent Order Creation

Use this script to test multiple concurrent orders:

```bash
# Create 10 orders simultaneously
for i in {1..10}; do
  curl -X POST http://localhost:8081/orders \
    -H "Content-Type: application/json" \
    -d "{
      \"productName\": \"Mouse\",
      \"quantity\": 1,
      \"price\": $((100 + i * 10))
    }" &
done
wait

# Check results
curl http://localhost:8081/orders/1
curl http://localhost:8081/orders/10
```

**Verification**:
- All orders should be processed
- No duplicate payments or inventory reservations
- Saga pattern maintains consistency

---

## 🐛 Troubleshooting Tests

### Test Case: Service Restart

1. **Create an order**
2. **Stop payment-service**:
```bash
# Press Ctrl+C in payment-service terminal
```
3. **Create another order** - should timeout
4. **Restart payment-service**
5. **Verify** events are reprocessed

### Test Case: Kafka Failure

1. **Stop Kafka**:
```bash
docker-compose stop kafka
```
2. **Try creating order** - should fail or timeout
3. **Restart Kafka**:
```bash
docker-compose start kafka
```
4. **Retry** - should work

---

## 📈 Performance Metrics

### Response Time Benchmarks

| Endpoint | Expected Response Time |
|----------|----------------------|
| POST /orders | < 200ms |
| GET /orders/{id} | < 50ms |
| /actuator/health | < 20ms |

### Event Processing Time

| Event | Expected Processing Time |
|-------|-------------------------|
| Order → Payment | 1-2 seconds |
| Order → Inventory | 1-2 seconds |
| Complete Saga | 2-4 seconds |

---

## ✅ Test Checklist

### Pre-Deployment Tests

- [ ] All services start successfully
- [ ] Database connections established
- [ ] Kafka topics created automatically
- [ ] Health checks return UP status
- [ ] Actuator endpoints accessible

### Functional Tests

- [ ] Order creation succeeds
- [ ] Payment processing works
- [ ] Inventory reservation works
- [ ] Order status updates correctly
- [ ] Failed orders marked as FAILED
- [ ] Insufficient inventory handled
- [ ] Payment failure handled

### Integration Tests

- [ ] Events published to correct topics
- [ ] Events consumed by correct services
- [ ] Saga completes successfully
- [ ] Compensation works on failure
- [ ] Database transactions committed

### Resilience Tests

- [ ] Service restart doesn't lose data
- [ ] Kafka restart recovers correctly
- [ ] Database connection pooling works
- [ ] Circuit breaker activates on failure
- [ ] Retry logic functions properly

---

## 🎯 Expected Test Results Summary

| Test Case | Expected Result |
|-----------|----------------|
| Normal order (Laptop x2, $2400) | ✅ COMPLETED |
| High-value order ($15000) | ❌ FAILED (Payment) |
| Excessive quantity (100 laptops) | ❌ FAILED (Inventory) |
| Non-existent product | ❌ FAILED (Inventory) |
| Concurrent orders | ✅ All processed correctly |
| Service restart | ✅ System recovers |
| Kafka failure | ⚠️ Graceful degradation |

---

## 📝 Test Report Template

```markdown
## Test Execution Report

**Date**: [Date]
**Tester**: [Name]
**Environment**: Local Development

### Test Results

| Test ID | Description | Status | Comments |
|---------|-------------|--------|----------|
| TC-001  | Create successful order | ✅ PASS | Order completed in 2.3s |
| TC-002  | Payment failure | ✅ PASS | Correctly marked as FAILED |
| TC-003  | Insufficient inventory | ✅ PASS | Saga compensation worked |
| TC-004  | Health checks | ✅ PASS | All services UP |

### Issues Found

- None

### Recommendations

- Consider adding retry configuration
- Implement timeout handling
```
