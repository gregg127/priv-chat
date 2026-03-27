package com.privchat.auth.model;

/**
 * Value object representing a security audit event.
 * Persisted to the security_audit_log table via jOOQ (see AuditLogService).
 */
public record SecurityAuditLog(String eventType, String ipAddress, String username) {}
