-- Existing ecommerce schema. Inspect SHOW CREATE TABLE before running:
-- Hibernate ddl-auto=update may already have created the columns/table.
-- Existing accounts are grandfathered; no historical account is deactivated.
ALTER TABLE users
    MODIFY COLUMN status ENUM('ACTIVE', 'DEACTIVATED', 'BLOCKED', 'PENDING_VERIFICATION') NOT NULL,
    ADD COLUMN emailVerified BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE account_status_audit
    MODIFY COLUMN previousStatus ENUM('ACTIVE', 'DEACTIVATED', 'BLOCKED', 'PENDING_VERIFICATION') NOT NULL,
    MODIFY COLUMN newStatus ENUM('ACTIVE', 'DEACTIVATED', 'BLOCKED', 'PENDING_VERIFICATION') NOT NULL;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    user_id BIGINT NOT NULL PRIMARY KEY,
    tokenHash VARCHAR(64) NOT NULL UNIQUE,
    requestedAt DATETIME(6) NOT NULL,
    expiresAt DATETIME(6) NOT NULL,
    usedAt DATETIME(6) NULL,
    windowStartedAt DATETIME(6) NOT NULL,
    requestCount INT NOT NULL,
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Raw verification tokens are never stored in this table.
