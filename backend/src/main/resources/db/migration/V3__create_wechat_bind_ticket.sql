CREATE TABLE wx_bind_ticket (
 ticket_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
 wx_openid VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 PRIMARY KEY (ticket_hash),
 KEY idx_bind_ticket_expiry (expires_at),
 CONSTRAINT ck_bind_ticket_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
