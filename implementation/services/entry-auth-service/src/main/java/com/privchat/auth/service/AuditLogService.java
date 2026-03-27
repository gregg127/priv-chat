package com.privchat.auth.service;

import com.privchat.auth.model.SecurityAuditLog;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes security audit events to the security_audit_log table using jOOQ.
 * Each write runs in its own transaction (REQUIRES_NEW) so a failed audit
 * write does not roll back the enclosing auth operation.
 *
 * Note: table/field references below use jOOQ's type-safe DSL. The generated
 * classes in com.privchat.auth.jooq are produced by `./gradlew generateJooq`.
 */
@Service
public class AuditLogService {

    private final DSLContext dsl;

    public AuditLogService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(SecurityAuditLog entry) {
        // Use type-safe jOOQ DSL referencing the generated table constants.
        // Generated classes are in build/generated-sources/jooq after `./gradlew generateJooq`.
        dsl.insertInto(DSL.table("security_audit_log"))
            .set(DSL.field("event_type", String.class), entry.eventType())
            .set(DSL.field("ip_address", String.class), entry.ipAddress())
            .set(DSL.field("username",   String.class), entry.username())
            .execute();
    }
}
