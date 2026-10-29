# 后端工程（阶段 3）

## 已实现与范围

单体 Spring Boot 应用，保留阶段 1 的认证、统一基础设施和四表迁移，新增用户、实验室、管理员分配及设备管理。Service 层验证角色与实验室归属，列表在 SQL 中限制范围，修改使用数据库事务和行锁。阶段 3 已实现预约、审批、过期和统计；微信认证和客户端仍未实现。

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

本阶段未实现微信、小程序、Vue；未执行生产部署和完整安全扫描。预约正确性已用真实并发测试验证，不代表生产性能基准通过。认证限流和更广泛的安全加固仍需后续联调测试阶段处理，不应直接将此基础工程暴露到公网。


## 阶段 2 管理接口

下列均带 /api/v1 前缀，使用现有 Bearer JWT；创建返回 201，其他成功操作返回 200：

| 模块 | 接口 | 权限 |
| --- | --- | --- |
| 用户 | GET/POST /users；PATCH /users/{id}；PUT /users/{id}/password | 仅 SYS_ADMIN |
| 实验室 | GET /labs；GET /labs/{id} | 学生仅启用；实验室管理员仅分配范围；系统管理员全局 |
| 实验室 | POST /labs；PATCH /labs/{id} | 创建仅 SYS_ADMIN；更新为 SYS_ADMIN 或所属 LAB_ADMIN |
| 分配 | GET/PUT /labs/{id}/admins | 仅 SYS_ADMIN |
| 设备 | GET /equipment；GET /equipment/{id} | 学生仅启用实验室的 AVAILABLE 设备；实验室管理员仅分配范围；系统管理员全局 |
| 设备 | POST /equipment；PATCH /equipment/{id} | SYS_ADMIN 或所属 LAB_ADMIN |

用户创建示例使用字段 username、realName、role、initialPassword，密码通过本地输入提供，不要将真实值复制进文档或共享日志。实验室创建使用 code、name、location、description；设备创建使用 labId（字符串）、assetNo、name、category、model、location、description。完整字段、查询筛选和错误行为见 [API 文档](../docs/API.md)。

管理员分配用 {"userIds":["账号ID"]} 一次性替换，传空数组清空。先校验所有账号，再在同一事务删除/插入；任何写失败回滚旧授权。资源更新与分配替换均先锁 laboratory 行，设备修改随后锁 equipment 行；替换分配再按 ID 升序锁定管理员账号。实验室管理员在获取资源锁后重新查询真实分配，从而避免检查后撤权仍写入。账号启停和密码重置锁定目标 sys_user 行，不在之后反向获取实验室锁。

PATCH 请求区分省略字段和显式 null：例如 {"name":"新名称"} 不会清空 description；{"description":null} 可以清空可空描述。null 不能用于清空名称、地点、状态、启用标记等必填字段。编号、用户名、角色和设备所属实验室为不可变字段，传入返回 400。搜索参数采用字面量包含匹配，固定稳定排序，默认分页 1/20、最大 100。

阶段 2 当时沿用 V1 四表；阶段 3 已新增 V2 预约与审计表，V1 不变，现有开发演示数据可以继续使用。系统未开放学生认证，测试中学生 JWT 仅在隔离环境按本地测试密钥签发，不提供任何免密或模拟微信登录接口。

ResourceStateGuard 在阶段 3 已由 ReservationResourceStateGuard 实现，FoundationResourceStateGuard 已移除，真实查询有效占用并沿用调用方持有的实验室/设备锁和事务。实验室停用只限制学生可见性，不把设备内部状态批量覆盖；维修、停用设备不会出现在学生查询中。已有有效预约时的资源停用阻断及其与创建的竞争已通过 MySQL 测试。

新增 ResourceValidationTest 和 ResourceManagementIT；保留 FoundationTest 的 8 项测试及 FoundationIT 的 24 项测试。原先两个资源不存在的 404 断言更新为真实空列表 200，测试本身没有删除。MySQL 授权回滚测试仅在一次性容器关闭 binlog，以普通测试账号建立短效故障注入触发器；不改变正式配置、V1 或完整性约束。并发测试在真实事务中持有实验室行锁撤权，确认等待中的设备写入随后返回 FORBIDDEN 且数据未变。

最终数量、命令结果及 HTTP 验证见进度记录；认证限流、安全扫描、正式部署和 Windows Wrapper 验证仍未完成，保留阶段 1 的局限。


## 阶段 3 预约与统计

V2__create_reservation_tables.sql 创建 reservation、reservation_log。直接启动应用会校验 V1 并应用 V2，禁止修改历史迁移或清库重建。已在保留的阶段 1 隔离开发库验证 V1→V2 升级，演示管理员账号和旧资源仍能访问；微信绑定表不在此迁移中。

新增接口均带 /api/v1：GET /equipment/{id}/slots、POST /reservations、GET /reservations/mine、GET /reservations、GET /reservations/{id}、POST /reservations/{id}/review、/cancel、/revoke、/complete，以及 GET /statistics/overview。完整字段、权限、响应与错误见 API 文档。学生登录仍未开放，不要用管理员令牌冒充学生；自动化测试和隔离 HTTP 验证使用本地测试密钥签发学生 JWT，没有生产免密入口。

学生提交设备 ID、+08:00 的 startTime/endTime、purpose，必须带 UUID Idempotency-Key。每天 08:00—22:00、半小时粒度、30 分钟至 4 小时、不跨日、开始晚于现在且至多未来 7 天；最多三条有效未结束预约。PENDING 未来时段和 APPROVED 未结束时段占用 [开始,结束)，相邻允许，数据库事务中最终检查交集。相同规范化请求同键重放 200、首次 201、同键不同内容 409。

状态机：PENDING 可同意、驳回、本人取消或到开始时间过期；APPROVED 可开始前取消/管理员撤销，结束后登记完成。REJECTED/CANCELLED/EXPIRED/COMPLETED 不能恢复。动作及日志同事务；角色、归属、时间和前态均在服务端检查。只有最后一次相同操作者、动作、规范化原因的重试为幂等成功；SYS_ADMIN 只能查看，不能审批。

预约写入按实验室→设备→学生→预约加锁，READ COMMITTED，跨设备配额通过同学生账号行锁串行保护。资源变更与预约共用实验室/设备锁。数据库死锁或锁超时返回 503，事务回滚，客户端沿用原键有限重试；不依赖 synchronized 或分布式中间件。

过期作业默认每分钟运行，每批 200、每轮最多 2000，每条独立事务；查询在任务延迟时也将已到开始时间的 PENDING 显示为 EXPIRED，且不占配额/时段。测试配置 app.expiry-enabled=false 禁止自动调度，直接执行同一作业测试边界和重复运行；正式应用默认启用，不应依赖任务精确准点。

统计按创建日期范围、当前有效状态和管理员范围从 MySQL 聚合，日期包含两端至多 90 天；趋势逐日补零，热门设备最多十台，只计 APPROVED/COMPLETED，不声称真实利用率。各聚合是 READ COMMITTED 下已提交结果，不承诺并发写入中的冻结快照。

阶段 3 新增 ReservationRulesTest（5 项）及 ReservationCoreIT（32 项），保留旧 66 项。最终共 103 项通过：19 项单元、84 项 MySQL 集成，失败/错误/跳过均 0。完整命令、并发统计、HTTP 及限制见进度记录。实际并发正确性不等于吞吐或 P95 达标；当前没有生产容量基准结论。
