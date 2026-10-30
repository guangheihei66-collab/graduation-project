# 主要 REST API 设计

## 1. 协议约定

阶段 1 已实现认证与匿名 GET /health 数据库健康检查；阶段 2 已实现下列用户、实验室、管理员分配及设备管理接口。阶段 3 已实现 /equipment/{id}/slots、预约与统计；微信认证仍待阶段 5。基础路径 /api/v1，JSON 编码 UTF-8，除登录及绑定接口外使用 Authorization: Bearer <JWT>。ID 使用字符串；业务时区 Asia/Shanghai，时间例：2026-11-01T09:00:00+08:00。服务端拒绝无偏移或非 +08:00 的业务时间，客户端显示北京时间。

成功使用 HTTP 200，创建资源使用 201；统一响应：

```json
{"code":"OK","message":"成功","data":{"id":"123"},"requestId":"请求追踪标识"}
```

分页请求 page 从 1 开始，默认 1；size 默认 20，上限 100；响应 data 为 {records: [], total: 0, page: 1, size: 20}。固定排序默认 createdAt 降序、id 降序，未含 createdAt 的列表按 id；不接受未经白名单校验的 SQL 排序字段。日期区间参数 fromDate、toDate 为包含两端的自然日，服务端转换为 [首日零点,末日次日零点)。

失败 data 为 null，message 不泄露堆栈及敏感字段。HTTP 400 参数错误、401 未认证或令牌失效、403 无角色或范围权限、404 资源不存在、409 业务或状态冲突、503 数据库锁超时等临时故障。业务码包括 VALIDATION_ERROR、UNAUTHORIZED、FORBIDDEN、NOT_FOUND、TIME_CONFLICT、INVALID_STATE、QUOTA_EXCEEDED、RESOURCE_UNAVAILABLE、IDEMPOTENCY_CONFLICT、RETRYABLE_ERROR。密码登录及绑定错误统一提示凭据不正确，避免账号枚举。

角色缩写：学生 S、实验室管理员 L、系统管理员 A。表内“L 范围”表示必须拥有对应实验室分配。客户端不可传入角色、申请人或操作者字段改变身份。

## 2. 认证与账号

| 方法和路径 | 权限 | 请求与结果 |
| --- | --- | --- |
| POST /auth/login | 匿名 | username、password；仅 L/A，返回 accessToken、expiresIn（秒）、user |
| POST /auth/wechat/login | 匿名 | code；已绑定返回 needBind=false、accessToken、expiresIn、user；未绑定返回 needBind=true、bindTicket、expiresIn=300 |
| POST /auth/wechat/bind | 匿名 | bindTicket、username、password；仅有效学生账号可绑定，返回 accessToken、expiresIn、user |
| GET /auth/me | S/L/A | 当前 id、username、realName、role，不包含密码哈希及 openid |
| GET /users | A | username、role、enabled 分页筛选，返回脱敏管理信息 |
| POST /users | A | username、realName、role、initialPassword，创建账号，不回显密码 |
| PATCH /users/{id} | A | realName、enabled；不支持改账号及角色，不允许停用自身 |
| PUT /users/{id}/password | A | newPassword，返回 id；不得写入日志 |

密码长度 8—64 字符且 UTF-8 编码不超过 BCrypt 的 72 字节限制；账号 1—64 字符，不允许首尾空白。微信绑定不可覆盖已有不同绑定；重复或冲突返回 409。认证端点后续加入限流和失败次数限制，不能记录 code、绑定凭证或密码。无刷新令牌和服务端登出接口，客户端退出时清除令牌。

## 3. 实验室与设备

| 方法和路径 | 权限 | 说明 |
| --- | --- | --- |
| GET /labs | S/L/A | 分页；name、enabled；S 仅启用，L 仅分配范围，A 全局 |
| GET /labs/{id} | S/L/A | 详情；S 仅启用，L 仅分配范围 |
| POST /labs | A | code、name、location、description，默认启用 |
| PATCH /labs/{id} | L 范围/A | name、location、description、enabled；有未结束占用则停用返回 409 |
| GET /labs/{id}/admins | A | 当前分配管理员 ID 及姓名 |
| PUT /labs/{id}/admins | A | userIds 数组，事务性替换；只能选启用的 L 账号，允许空数组 |
| GET /equipment | S/L/A | labId、keyword、category、status 分页；S 仅启用实验室及 AVAILABLE 设备，L 限分配范围 |
| GET /equipment/{id} | S/L/A | 同列表可见范围；返回资产编号、名称、所属实验室、状态等 |
| POST /equipment | L 范围/A | labId、assetNo、name、category、model、location、description；初始 AVAILABLE |
| PATCH /equipment/{id} | L 范围/A | name、category、model、location、description、status；不修改归属或资产编号 |
| GET /equipment/{id}/slots | S/L/A | date，返回当天占用时间段；S 仅可预约设备，L 限范围；无申请人和用途 |

枚举见数据库文档。PATCH 未传字段保持原值，可空字段允许显式 null；本阶段可清空字段仅为实验室 description、设备 model 和 description。必填字段显式 null、空白必填字符串、空 PATCH 对象及未知/不可变字段返回 400。资源停用不删除历史，历史预约详情仍能显示其资源信息。接口无 DELETE 资源操作。

slots 返回示例：

```json
{"date":"2026-11-01","openTime":"08:00","closeTime":"22:00","slots":[{"startTime":"2026-11-01T09:00:00+08:00","endTime":"2026-11-01T10:00:00+08:00","status":"PENDING"}]}
```

slots 只表示当前占用，不能保证随后的提交一定成功。

## 4. 预约与审批

| 方法和路径 | 权限 | 说明 |
| --- | --- | --- |
| POST /reservations | S | 创建自己的待审批预约，必须携带 Idempotency-Key 请求头 |
| GET /reservations/mine | S | status、equipmentId、labId、fromDate、toDate 分页；日期按预约开始日过滤 |
| GET /reservations | L 范围/A | labId、equipmentId、status、fromDate、toDate 分页；含授权范围内学生姓名及学号 |
| GET /reservations/{id} | 本人 S/L 范围/A | 预约信息及按时间、ID 升序的操作日志；S 访问非本人记录返回 404 |
| POST /reservations/{id}/cancel | 本人 S | 开始前取消 PENDING/APPROVED；可传 reason（最多 500 字） |
| POST /reservations/{id}/review | L 范围 | decision=APPROVE 或 REJECT，REJECT 必填 reason |
| POST /reservations/{id}/revoke | L 范围 | 开始前撤销 APPROVED，必填 reason |
| POST /reservations/{id}/complete | L 范围 | 结束后登记归还，仅 APPROVED 可转 COMPLETED |

创建请求：

```http
POST /api/v1/reservations
Authorization: Bearer <JWT>
Idempotency-Key: 4f3096e2-d12c-47f2-a190-c5095762fd27
Content-Type: application/json
```

```json
{"equipmentId":"12","startTime":"2026-11-01T09:00:00+08:00","endTime":"2026-11-01T10:00:00+08:00","purpose":"完成课程实验"}
```

响应 data 包含 id、equipmentId、equipmentName、labId、startTime、endTime、purpose、status、createdAt。首次成功 201，相同键及标准化请求重放 200，返回同一预约的当前状态；相同键不同内容返回 409 IDEMPOTENCY_CONFLICT。幂等键保存至预约记录保留期结束，本项目不自动删除预约历史。

创建按需求验证 7 天窗口、半点对齐、每日 08:00—22:00、单次最长 4 小时及最多 3 条未结束占用。最终冲突检查在数据库事务中完成，冲突返回 409 TIME_CONFLICT，不返回他人个人信息。

处理接口返回预约当前状态及 updatedAt；同一操作者重复执行已成功的相同动作返回 200，不新增日志。服务端通过最近状态日志识别重复操作；若前次由其他操作者执行则返回 409，不伪造本次成功。取消与撤销虽然均转 CANCELLED，但属于不同操作，不能互相视为幂等重试。不同审批决策、终态再次转换、开始后审批或取消返回 409 INVALID_STATE。系统过期转换不暴露人工接口。

## 5. 统计

GET /statistics/overview：L 范围/A，参数 labId（可选）、fromDate、toDate（必填，最多 90 天），返回 totalCount、pendingCount、approvedCount、dailyTrend、topEquipment（最多 10 台）。L 不传 labId 时仅汇总其分配实验室，传未授权 ID 返回 403。

统计以预约 created_at 的自然日归属，按查询时当前状态计算；approvedCount 为当前 APPROVED 数量，热门设备按当前 APPROVED 或 COMPLETED 数量排序，相同数量按设备 ID 升序。不将预约数量或预约时长称为真实设备利用率。

## 6. 后续契约验证

实现时验证统一错误格式、分页边界、ID 精度、时间偏移、未知字段白名单、角色与归属、重放及状态竞争；集成测试需真实 MySQL 事务。认证错误使用 401、授权错误使用 403，业务冲突使用 409，不能全部返回 HTTP 200。阶段 2 已实现资源管理与归属校验；预约与统计已在阶段 3 实现，微信接口仍未实现。基础统一错误码另含 CONFLICT（409）、METHOD_NOT_ALLOWED（405）、UNSUPPORTED_MEDIA_TYPE（415）、INTERNAL_ERROR（500）；响应不包含内部异常详情。


## 7. 阶段 2 实际契约与边界

已实现：GET/POST /users、PATCH /users/{id}、PUT /users/{id}/password；GET/POST /labs、GET/PATCH /labs/{id}；GET/PUT /labs/{id}/admins；GET/POST /equipment、GET/PATCH /equipment/{id}。所有路径均有 /api/v1 前缀，创建返回 201，查询和更新返回 200。

- 用户列表返回 id、username、realName、role、enabled、createdAt、updatedAt；账号默认启用，用户名和角色创建后不可变，停用当前自己返回 409 CONFLICT。重置返回字符串 id，密码哈希和 wxOpenid 不出现在任何管理响应中。重置不撤销已签发 JWT，旧密码不再能登录，符合当前无即时撤销机制的设计。
- 实验室列表/详情返回表中公开字段的驼峰名称；设备列表/详情额外含字符串 labId 和 labName。时间为 +08:00 的 ISO 8601。分页 total、page、size 为数字，所有数据库 ID 为字符串。
- username、name、keyword 采用包含匹配，搜索中的 %、_ 和反斜杠按字面量处理；keyword 匹配设备名称或资产编号。空白搜索、非法枚举、非正 ID、page<1、size 不在 1—100 内、enabled 非 0/1 返回 400；默认页和大小沿用 1/20。排序固定 createdAt 降序、id 降序，不提供自定义排序。
- STUDENT 列表只包含启用实验室及其 AVAILABLE 设备。对列表传 enabled=0 或 status=MAINTENANCE/DISABLED 得到范围内的空结果，不扩大权限；详情访问受限资源返回 403。LAB_ADMIN 的 SQL 查询包含数据库分配范围，未分配任何实验室时列表为空。
- 显式传入 labId 的设备查询会检查实验室存在与可见范围：不存在 404，存在但未授权或学生访问停用实验室 403；未指定 labId 时在 SQL 中过滤范围。资源详情不存在为 404、存在但无归属权限为 403。
- 管理员分配请求示例：{"userIds":["12","13"]}，最多 100 个不同 ID；空数组撤销全部。重复或非法 ID 返回 400，用户不存在 404，角色不是 LAB_ADMIN 或已停用返回 409。替换响应及查询响应为 [{id,realName}]，按 ID 升序。停用账号不自动删除分配，但禁止后续访问；再次启用恢复原分配。
- 管理员仍可管理停用实验室及其设备，设备 AVAILABLE 状态本身不会覆盖实验室停用限制。实验室停用不批量修改设备状态，重新启用也不会把 MAINTENANCE/DISABLED 设备改为 AVAILABLE。
- 用户、实验室和设备均没有物理删除接口。编号、资产编号、设备所属实验室不能通过 PATCH 修改。唯一约束冲突返回 409 CONFLICT，锁等待或数据库暂时失败返回 503 RETRYABLE_ERROR。
- 阶段 3 已用 ReservationResourceStateGuard 替换基础空实现，实验室停用和设备切换不可用时查询真实有效预约，有占用返回 409 RESOURCE_UNAVAILABLE；/slots 返回真实有效时段。


## 8. 阶段 3 已实现接口与响应

新增十个方法/路径组合：GET /equipment/{id}/slots、POST /reservations、GET /reservations/mine、GET /reservations、GET /reservations/{id}、POST /reservations/{id}/review、POST /reservations/{id}/cancel、POST /reservations/{id}/revoke、POST /reservations/{id}/complete、GET /statistics/overview。均沿用 /api/v1 前缀与现有 Bearer 校验，不开放新的学生登录。

预约响应除前文已有字段外包含 studentId、studentName、username、updatedAt、logs；学生只能读取本人记录，管理员列表在 SQL 限定分配范围。列表和创建响应 logs 为空数组，详情及动作响应包含按 createdAt、id 升序的日志 [{id,operatorId,action,fromStatus,toStatus,reason,createdAt}]；系统 EXPIRE 的 operatorId 为 null。所有 ID 字符串化，所有时间带 +08:00。

- 创建必填标准 UUID 格式 Idempotency-Key，不区分 UUID 字母大小写；同学生同键同规范化请求首次 201、重放 200，同键不同内容 409 IDEMPOTENCY_CONFLICT。重放返回原记录的当前有效状态，允许时间过去、预约取消、设备或实验室后来不可用，但仍要求当前学生账号有效。不同幂等键走正常冲突和配额判断。
- 时间字符串必须可解析为带 +08:00 的 ISO 8601，拒绝无偏移、Z 或其他偏移；开始/结束整点或半点且秒与小数秒为零。时间和用途非法 400，时段冲突 TIME_CONFLICT、配额 QUOTA_EXCEEDED、资源不可用 RESOURCE_UNAVAILABLE、非法状态 INVALID_STATE 均为 HTTP 409。数据库锁超时/死锁回滚后返回 503 RETRYABLE_ERROR，客户端应保留原幂等键重试。
- review 请求 decision=APPROVE/REJECT；REJECT 和 revoke 必须有非空 reason，最多 500 字。cancel 可无请求体或 {reason}，complete 无请求体。重复动作仅在最后一条日志的动作、操作者及规范化原因相同的情况下返回 200，不新增日志；不同操作者、原因或后续状态变化均不能覆盖历史。授权始终重新检查。
- 仅开始前可审批、取消或撤销，结束到达后才可完成；已通过且结束后未登记的预约仍为 APPROVED，但不再占用未来时段或配额。系统管理员只能查看预约，不能提交或审批。
- 已到开始时间的 PENDING 在查询、筛选和统计中有效状态为 EXPIRED，即使定时任务暂未落库；GET 不做无锁状态修改。slots 只含有效尚未结束的 PENDING/APPROVED 时段，不含申请人、用途或预约 ID，也不能替代提交时的事务冲突检查。
- mine 增加可选 labId 筛选以满足阶段 3 的实验室过滤要求，始终同时限制当前学生；管理员指定未授权 labId 返回 403。日期过滤按预约开始自然日，fromDate/toDate 可独立使用，成对时开始不得大于结束。

统计必须提供 fromDate、toDate，包含两端最多 90 天；labId 可选。响应为：

```json
{"totalCount":4,"pendingCount":1,"approvedCount":2,"dailyTrend":[{"date":"2026-11-02","count":4}],"topEquipment":[{"equipmentId":"12","equipmentName":"示波器","count":1}]}
```

示例仅说明契约，不是内置数据。数量为 JSON 数字；趋势按日升序，范围内没有预约的日期补零；热门设备仅计当前 APPROVED/COMPLETED，最多十台，数量降序、设备 ID 升序。日期归属按 created_at，待审批排除已过开始时间但尚未持久化过期的记录。三项统计查询使用 READ COMMITTED 的已提交数据，持续并发写入时不承诺多个聚合查询构成同一冻结快照。统计结果不是实际设备利用率。


## 9. 阶段 4 管理后台对接

未修改后端路径、DTO、Security 或业务规则。管理端统一 Axios 层对接 22 个方法/路径：认证 login/me；statistics/overview；用户四项；实验室四项及管理员分配两项；设备四项；预约列表、详情、review、revoke、complete 五项。学生创建/取消、mine、微信登录及设备 slots 不是管理后台的学生操作入口。

前端保持 ID 字符串、分页数字与 +08:00 时间口径；PATCH 只提交变化字段，明确清空可空字段时发送 null。密码重置只提交 newPassword，不回显。管理员替换提交完整 userIds 数组，并由页面二次确认。系统管理员不提供审批按钮，实验室管理员导航不含用户管理，最终权限仍由后端检查。

登录后和管理路由进入时调用 /auth/me；401 清理浏览器会话，403 不当作过期，409 INVALID_STATE 提示后刷新预约和统计。写入不自动重试；界面不直接修改 status。浏览器真实联调和测试记录见 PROGRESS，未增加任何兼容性放宽或假数据接口。
