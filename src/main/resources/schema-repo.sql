CREATE TABLE IF NOT EXISTS dqExecution (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    controlCode        VARCHAR(100)  NOT NULL,
    controlDescription VARCHAR(500),
    issues             INT,
    known              INT,
    elapsed            BIGINT,
    sqlFile            VARCHAR(255),
    status             VARCHAR(45),
    ts                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dqKnownIssue (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    controlCode VARCHAR(100)  NOT NULL,
    issueKey    VARCHAR(255)  NOT NULL,
    description VARCHAR(500),
    isActive    TINYINT DEFAULT 1
);
