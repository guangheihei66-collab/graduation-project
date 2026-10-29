# 后端基础工程（阶段 1）

## 已实现与范围

单体 Spring Boot 应用，已提供管理员账号密码登录、JWT 验签与账号状态重查、当前用户、数据库健康检查、统一响应和异常、参数校验、请求追踪、CORS、分页基础及四张基础表迁移。只有认证和健康接口具有控制器；资源路径的角色门禁已预留，授权通过后返回 404，不表示 CRUD 已实现。实验室归属过滤、资源 CRUD 留到阶段 2；预约、微信登录和客户端均未实现。

## 固定版本

| 组件 | 版本 |
| --- | --- |
| Java | 17（编译目标及实际测试运行版本） |
| Maven / Wrapper | 3.9.9 / 3.3.4 |
| Spring Boot | 3.5.16 |
| Spring Security | 6.5.11（Boot 依赖管理） |
| MyBatis-Plus | 3.5.14 |
| Flyway | 11.7.2（Boot 依赖管理） |
| MySQL Connector/J | 9.7.0（Boot 依赖管理，连接 MySQL 8） |
| JJWT | 0.12.6 |
| Hibernate Validator | 8.0.3.Final（Boot 依赖管理） |
| JUnit Jupiter | 5.12.2（Boot 依赖管理） |
| Testcontainers | 1.21.3 |
| 集成测试 MySQL 镜像 | mysql:8.0.45 |

Maven 通过固定父 POM、显式第三方版本及 Wrapper 固定依赖和工具版本，不使用前端形式的依赖锁文件。Wrapper 发行包固定 SHA-256；该校验和来自通过官方 SHA-512 验证的 Maven 发行包。生产部署前仍需常规依赖漏洞检查，本阶段未执行完整安全扫描。

## 环境与配置

需要 Java 17 和 MySQL 8.0.16 及以上；推荐与测试一致的 MySQL 8.0.45。执行集成测试还需要可用的 Docker。Maven 可使用本机 3.9.9 或随项目的 `./mvnw`；Windows 使用 `mvnw.cmd`。首次 Wrapper 和依赖下载需要网络。

先通过 MySQL 管理工具创建专用数据库和应用账号，库字符集 utf8mb4、排序规则 utf8mb4_0900_ai_ci。账号需要该库的迁移和读写权限，不应使用全局 root。Flyway 自动创建表，不会创建数据库；不要手动执行迁移 SQL 后再让 Flyway 接管，也不要启用 baseline、clean 或删除历史表来掩盖错误。

从 backend 目录准备本地配置：

```bash
cp .env.example .env
# 使用编辑器填写 .env 中的空值，然后导入自己维护的本地配置：
set -a
. ./.env
set +a
```

Spring Boot 不会自动加载 `.env`，上述导入步骤不可省略，也可以直接通过 IDE/云环境设置注入变量。本地 `.env` 已忽略，不提交。带 `&` 的 JDBC URL、密码和密钥等值须正确加引号；不要执行来自不可信来源的环境文件。

| 变量 | 用途 |
| --- | --- |
| DB_URL | 必填 JDBC URL，统一连接时区 Asia/Shanghai |
| DB_USERNAME / DB_PASSWORD | 必填数据库凭据 |
| JWT_SECRET | 必填，至少 32 字节、非空白、通过安全随机源生成；无源码默认值 |
| SPRING_PROFILES_ACTIVE | 本地开发设为 dev；生产设为 prod，不混用 dev |
| CORS_ALLOWED_ORIGINS | 精确来源，用逗号分隔；dev 默认 http://localhost:5173，其他环境默认不允许跨域；禁止 * |
| SERVER_PORT | 可选，默认 8080 |
| DEMO_ENABLED | 仅 dev 可用，默认 false |
| DEMO_PASSWORD | 启用演示数据时必填，自行生成并安全保存；8—64 字符且 UTF-8 不超过 72 字节 |

JWT 可用 `openssl rand -base64 48` 在本地生成，再安全注入；不要将生成值贴入提交、文档或共享日志。生产数据库连接应配置 `sslMode=VERIFY_IDENTITY` 和可信 CA，示例的本机地址仅用于隔离开发，不能替代生产 TLS 配置。

连接池使用 READ COMMITTED，连接初始化设置 +08:00；表中 DATETIME(3) 表示北京时间。MyBatis-Plus 自动填充创建及更新时间。后续写业务事务应明确使用 @Transactional 并遵循架构文档的锁顺序。

## 迁移及演示数据

`src/main/resources/db/migration/V1__create_foundation_tables.sql` 创建 sys_user、laboratory、lab_admin、equipment 及约束、索引，Flyway 历史表由框架维护。重复启动只校验迁移，不重建数据；发布后禁止修改 V1，应新增 V2 等迁移。预约相关表不在本阶段创建。

在专用开发库同时设置 `SPRING_PROFILES_ACTIVE=dev`、`DEMO_ENABLED=true` 和 `DEMO_PASSWORD` 后启动，事务性创建：

| 演示账号标识（非真实账号） | 角色 |
| --- | --- |
| demo_sys_admin | SYS_ADMIN |
| demo_lab_admin | LAB_ADMIN |
| demo_student_1 / demo_student_2 | STUDENT |

还会创建两个实验室、三台设备，并把实验室管理员分配到第一个实验室。四个账号使用本地指定的演示密码，数据库仅保存 BCrypt（强度 12）哈希。没有提交可用的固定密码。学生不能使用本阶段密码登录接口。

初始化器仅在 dev 且未启用 prod 时存在，默认或 prod 环境不会自动创建账号。再次启动不覆盖现有演示账号、密码或设备；若四个演示账号部分存在则启动失败并回滚，避免覆盖数据。完整账号已存在时不自动修复人工删除的演示设备。修改 DEMO_PASSWORD 不会重置已有账号。开发种子不承担生产管理员开户；正式部署需另行受控创建初始管理员。

## 构建、启动和接口

在 backend 中执行：

```bash
./mvnw test
./mvnw package
./mvnw verify
java -jar target/reservation-backend-0.1.0-SNAPSHOT.jar
```

`test` 和 `package` 执行单元测试，`verify` 额外执行名称为 `*IT` 的 MySQL 集成测试。Testcontainers 自动创建一次性 MySQL，且测试会清理该临时库中的四张业务表；不读取开发 DB_URL、不连接现有开发或生产库。没有 Docker 时 `verify` 应失败，不能据此声称 MySQL 已验证；仍可单独运行 `test`、`package`。测试密钥和测试账号仅用于测试运行，不是应用默认凭据。

| 方法 | 路径 | 权限及行为 |
| --- | --- | --- |
| GET | /api/v1/health | 匿名，执行 SELECT 1；健康 200，数据库不可用 503 |
| POST | /api/v1/auth/login | 匿名，仅 LAB_ADMIN / SYS_ADMIN 密码登录 |
| GET | /api/v1/auth/me | 有效 JWT；重新检查账号是否存在、启用及当前角色 |

登录请求为 `{"username":"demo_sys_admin","password":"由本地配置提供"}`。响应为 `{code,message,data,requestId}`，data 包含 accessToken、expiresIn=7200 和 user（字符串 id、username、realName、role）。后续请求使用 `Authorization: Bearer <令牌>`，不将令牌放在 URL。过期、无效或停用账号返回 401，角色不足返回 403；校验错误 400、唯一键/约束冲突 409、临时数据库错误 503、未知内部错误 500。错误不返回 SQL、密码或堆栈。

JWT 使用 HS256，有效期两小时，无刷新或即时登出撤销接口。退出只清除客户端令牌，账号停用会在下一次请求阻止访问。后台角色来自数据库，不信任客户端指定角色。微信登录/绑定尚未开放，不提供模拟免密登录。

响应带 X-Request-ID，JSON 含相同追踪标识。日志仅记录请求 ID、方法、状态、耗时和脱敏异常类型；不记录请求体、查询串、JWT 或密码。CORS 拒绝也返回统一 JSON。分页默认 page=1、size=20，上限 100；分页数量保留 JSON 数字，数据库 Long ID 序列化为字符串。分页 DTO 需在未来控制器使用 @Valid，不能跳过校验。

## 本阶段验证与局限

验证记录见 [进度记录](../docs/PROGRESS.md)。已实际执行 Java 17 下的 Maven 构建、JUnit 5 测试、真实 MySQL 8 Testcontainers 集成测试，并使用独立 MySQL 启动打包应用和同库重启进行 HTTP 验证。

本阶段未实现微信、小程序、Vue、资源归属业务、完整资源 CRUD 或预约；未执行生产部署、并发预约压测和完整安全扫描。认证限流和更广泛的安全加固仍需后续联调测试阶段处理，不应直接将此基础工程暴露到公网。
