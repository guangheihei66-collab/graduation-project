CREATE TABLE sys_user (
 id BIGINT NOT NULL AUTO_INCREMENT,
 username VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 real_name VARCHAR(50) NOT NULL,
 role VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 wx_openid VARCHAR(128) COLLATE utf8mb4_bin NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY (id),
 CONSTRAINT uk_user_username UNIQUE (username),
 CONSTRAINT uk_user_openid UNIQUE (wx_openid),
 CONSTRAINT ck_user_role CHECK (role IN ('STUDENT','LAB_ADMIN','SYS_ADMIN')),
 CONSTRAINT ck_user_enabled CHECK (enabled IN (0,1)),
 CONSTRAINT ck_user_wechat CHECK (wx_openid IS NULL OR role='STUDENT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE laboratory (
 id BIGINT NOT NULL AUTO_INCREMENT,
 code VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
 name VARCHAR(100) NOT NULL,
 location VARCHAR(200) NOT NULL,
 description VARCHAR(1000) NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY (id),
 CONSTRAINT uk_lab_code UNIQUE (code),
 CONSTRAINT ck_lab_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lab_admin (
 lab_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,
 PRIMARY KEY (lab_id,user_id),
 KEY idx_admin_user_lab (user_id,lab_id),
 CONSTRAINT fk_admin_lab FOREIGN KEY (lab_id) REFERENCES laboratory(id) ON DELETE RESTRICT,
 CONSTRAINT fk_admin_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE equipment (
 id BIGINT NOT NULL AUTO_INCREMENT,
 lab_id BIGINT NOT NULL,
 asset_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 name VARCHAR(100) NOT NULL,
 category VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
 model VARCHAR(100) NULL,
 location VARCHAR(200) NOT NULL,
 description VARCHAR(1000) NULL,
 status VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY (id),
 CONSTRAINT uk_equipment_asset UNIQUE (asset_no),
 KEY idx_equipment_lab_status_id (lab_id,status,id),
 CONSTRAINT fk_equipment_lab FOREIGN KEY (lab_id) REFERENCES laboratory(id) ON DELETE RESTRICT,
 CONSTRAINT ck_equipment_category CHECK (category IN ('INSTRUMENT','COMPUTER','TOOL','OTHER')),
 CONSTRAINT ck_equipment_status CHECK (status IN ('AVAILABLE','MAINTENANCE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
