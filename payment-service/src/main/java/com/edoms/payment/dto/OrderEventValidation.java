package com.edoms.payment.dto;

public final class OrderEventValidation {
    private OrderEventValidation() {}

    public static void validate(OrderCreatedEvent event) {
        if (event == null || event.getOrderId() == null || event.getOrderId() <= 0
                || event.getProductName() == null || event.getProductName().isBlank()
                || event.getProductName().length() > 255 || event.getQuantity() <= 0
                || event.getPrice() == null || event.getPrice().signum() <= 0
                || event.getPrice().scale() > 2
                || event.getPrice().precision() - event.getPrice().scale() > 12
                || !("ORDER_CREATED".equals(event.getEventType())
                    || "ORDER_CANCELLED".equals(event.getEventType()))) {
            throw new IllegalArgumentException("Invalid order event");
        }
    }
}
