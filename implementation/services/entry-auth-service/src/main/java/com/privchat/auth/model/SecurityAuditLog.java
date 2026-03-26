package com.privchat.auth.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "security_audit_log")
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    public SecurityAuditLog() {}

    public SecurityAuditLog(String eventType, String ipAddress, String username) {
        this.eventType = eventType;
        this.ipAddress = ipAddress;
        this.username = username;
        this.occurredAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
