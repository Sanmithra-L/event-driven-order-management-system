#!/usr/bin/env bash
set -euo pipefail

# Run from the repository root after packaging and starting Compose infrastructure.
DB_PASSWORD="${DB_PASSWORD:-mypassword}"
mkdir -p target/smoke
pids=()
cleanup() {
  for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT
java -jar target/order-service-0.0.1-SNAPSHOT.jar > target/smoke/order.log 2>&1 &
pids+=("$!")
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar > target/smoke/payment.log 2>&1 &
pids+=("$!")
java -jar inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar > target/smoke/inventory.log 2>&1 &
pids+=("$!")

wait_for() {
  local description="$1"
  shift
  for attempt in {1..120}; do
    if "$@"; then return 0; fi
    sleep 1
  done
  echo "Timed out: $description" >&2
  return 1
}
healthy() { curl --fail --silent "http://localhost:$1/actuator/health" | python3 -c 'import json,sys; sys.exit(json.load(sys.stdin)["status"] != "UP")' 2>/dev/null; }
for port in 8081 8082 8083; do wait_for "service $port" healthy "$port"; done

create_order() {
  curl --fail --silent -X POST http://localhost:8081/orders -H 'Content-Type: application/json' -d "$1" |
    python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}
order_status() {
  local actual
  actual=$(curl --fail --silent "http://localhost:8081/orders/$1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')
  [[ "$actual" == "$2" ]]
}
sql_equals() {
  local actual
  actual=$(docker compose exec -T "$1" mysql -uroot "-p$DB_PASSWORD" -Nse "$2" 2>/dev/null)
  [[ "$actual" == "$3" ]]
}

success=$(create_order '{"productName":"Laptop","quantity":2,"price":2400.00}')
wait_for "successful order" order_status "$success" COMPLETED
wait_for "reserved stock" sql_equals mysql-inventory "SELECT CONCAT(available_stock, ':', reserved_stock) FROM inventorydb.inventory WHERE product_name='Laptop'" "48:2"

failed_payment=$(create_order '{"productName":"Laptop","quantity":1,"price":15000.00}')
wait_for "failed payment order" order_status "$failed_payment" FAILED
wait_for "released reservation" sql_equals mysql-inventory "SELECT status FROM inventorydb.inventory_reservations WHERE order_id=$failed_payment" RELEASED
wait_for "restored stock" sql_equals mysql-inventory "SELECT CONCAT(available_stock, ':', reserved_stock) FROM inventorydb.inventory WHERE product_name='Laptop'" "48:2"

failed_inventory=$(create_order '{"productName":"Laptop","quantity":100,"price":500.00}')
wait_for "failed inventory order" order_status "$failed_inventory" FAILED
wait_for "payment refund" sql_equals mysql-payment "SELECT payment_status FROM paymentdb.payments WHERE order_id=$failed_inventory" REFUNDED

code=$(curl --silent -o /dev/null -w '%{http_code}' -X POST http://localhost:8081/orders -H 'Content-Type: application/json' -d '{"productName":"Laptop","quantity":-1,"price":1}')
[[ "$code" == "400" ]]
echo "Full-stack smoke tests passed: success, release, refund, and validation."
