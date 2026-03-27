-- Flyway migration V2: Spring Session JDBC schema (PostgreSQL)
-- Manages HTTP session persistence for entry-auth-service.
-- initialize-schema must be set to 'never' in application.yml (Flyway owns all DDL).
-- IF NOT EXISTS guards allow safe re-run on databases previously auto-initialized.

CREATE TABLE IF NOT EXISTS spring_session (
  PRIMARY_ID            CHAR(36)     NOT NULL,
  SESSION_ID            CHAR(36)     NOT NULL,
  CREATION_TIME         BIGINT       NOT NULL,
  LAST_ACCESS_TIME      BIGINT       NOT NULL,
  MAX_INACTIVE_INTERVAL INT          NOT NULL,
  EXPIRY_TIME           BIGINT       NOT NULL,
  PRINCIPAL_NAME        VARCHAR(100),
  CONSTRAINT spring_session_pk PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX IF NOT EXISTS spring_session_ix1 ON spring_session (SESSION_ID);
CREATE INDEX        IF NOT EXISTS spring_session_ix2 ON spring_session (EXPIRY_TIME);
CREATE INDEX        IF NOT EXISTS spring_session_ix3 ON spring_session (PRINCIPAL_NAME);

CREATE TABLE IF NOT EXISTS spring_session_attributes (
  SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
  ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES    BYTEA        NOT NULL,
  CONSTRAINT spring_session_attributes_pk
    PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT spring_session_attributes_fk
    FOREIGN KEY (SESSION_PRIMARY_ID)
    REFERENCES spring_session (PRIMARY_ID)
    ON DELETE CASCADE
);
