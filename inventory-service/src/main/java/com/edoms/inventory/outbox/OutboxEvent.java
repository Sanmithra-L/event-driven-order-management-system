package com.edoms.inventory.outbox;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String topic;
    @Column(nullable = false)
    private String eventKey;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    public OutboxEvent(String topic, String eventKey, String payload) {
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
    }
}
