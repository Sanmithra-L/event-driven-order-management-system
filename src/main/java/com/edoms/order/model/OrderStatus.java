package com.edoms.order.model;

/**
 * Enum representing the status of an order throughout its lifecycle.
 * Created to track order state transitions in the event-driven system.
 */
public enum OrderStatus {
    CREATED,
    PROCESSING,
    COMPLETED,
    FAILED
}
