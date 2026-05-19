package com.lubover.singularity.product.event;

import java.time.LocalDateTime;
import java.util.UUID;

public class ProductUpdatedEvent {

    public enum Action {
        CREATED,
        UPDATED,
        DELETED
    }

    private String eventId;
    private String productId;
    private Action action;
    private LocalDateTime eventTime;

    public ProductUpdatedEvent() {
    }

    public ProductUpdatedEvent(String productId, Action action) {
        this.eventId = UUID.randomUUID().toString();
        this.productId = productId;
        this.action = action;
        this.eventTime = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }
}
