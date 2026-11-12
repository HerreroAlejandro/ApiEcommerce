-- Select the existing ecommerce schema. This does not perform refunds or change historical stock.
CREATE TABLE IF NOT EXISTS physical_returns (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NULL,
    order_id BIGINT NOT NULL,
    status ENUM('REQUESTED', 'RECEIVED', 'WAIVED') NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requestedBy BIGINT NOT NULL,
    requestedAt DATETIME(6) NOT NULL,
    resolvedBy BIGINT NULL,
    resolvedAt DATETIME(6) NULL,
    resolutionReason VARCHAR(500) NULL,
    CONSTRAINT uk_physical_return_order UNIQUE (order_id),
    CONSTRAINT fk_physical_return_order FOREIGN KEY (order_id) REFERENCES orders(idOrder)
);

CREATE TABLE IF NOT EXISTS physical_return_lines (
    return_id BIGINT NOT NULL,
    orderItemId BIGINT NOT NULL,
    receivedQuantity INT NOT NULL,
    restockedQuantity INT NOT NULL,
    CONSTRAINT uk_physical_return_item UNIQUE (return_id, orderItemId),
    CONSTRAINT fk_physical_return_line FOREIGN KEY (return_id) REFERENCES physical_returns(id)
);
