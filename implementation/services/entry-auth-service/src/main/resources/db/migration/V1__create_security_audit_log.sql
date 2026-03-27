-- Flyway migration V1: Create security audit log table
-- This table records security events for rate limiting and auditing purposes

CREATE TABLE security_audit_log (
  id          BIGSERIAL PRIMARY KEY,
  event_type  VARCHAR(50)  NOT NULL CHECK (event_type IN ('JOIN_SUCCESS', 'JOIN_FAILURE', 'RATE_LIMITED')),
  ip_address  VARCHAR(45)  NOT NULL,
  username    VARCHAR(64),
  occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for lookups by IP address (rate limiting queries)
CREATE INDEX idx_sal_ip ON security_audit_log (ip_address, occurred_at DESC);

-- Index for lookups by event type (audit/monitoring queries)
CREATE INDEX idx_sal_type ON security_audit_log (event_type, occurred_at DESC);
