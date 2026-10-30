# Vue 管理后台（阶段 4）

已对接现有 Spring Boot 与 MySQL，提供中文管理工作台，不包含微信小程序、学生预约创建或代审批功能。

## 环境与固定版本

实际验证：Node.js 24.19.0、npm 11.9.0、Linux、Chromium 154.0.8037.57。推荐按 .nvmrc 使用 Node 24.19.0；工程 engines 与测试依赖保持一致：Node ^22.22.2 或 ^24.15.0 或 >=26.0.0，其他版本未验证。统一使用 npm，直接依赖固定具体版本，提交 package-lock.json。

| 依赖 | 固定版本 |
| --- | --- |
| vue | 3.5.44 |
| vue-router | 4.6.4 |
| pinia | 3.0.4 |
| axios | 1.21.0 |
| element-plus | 2.14.7 |
| @element-plus/icons-vue | 2.3.2 |
| echarts | 6.1.0 |
| @vitejs/plugin-vue | 6.0.10 |
| vite | 7.3.7 |
| typescript | 5.9.3 |
| vue-tsc | 3.1.1 |
| vitest | 4.1.12 |
| @vue/test-utils | 2.5.1 |
| jsdom | 30.1.3 |
| eslint | 10.13.0 |
| @eslint/js | 10.0.1 |
| typescript-eslint | 8.72.1 |
| eslint-plugin-vue | 10.12.0 |
| globals | 17.13.0 |
| @types/node | 24.9.1 |
| @playwright/test | 1.64.1 |

## 安装、配置与运行

先按 [后端说明](../backend/README.md) 启动 MySQL 和 Spring Boot，安全注入数据库、JWT、开发演示密码；不要将后端密钥放入前端配置。

在 admin-web 中：

```bash
npm ci
# 按需复制并编辑本地非敏感配置，Vite 会自动读取 .env.local：
cp .env.example .env.local
npm run dev
```

| 配置 | 用途 |
| --- | --- |
| VITE_API_BASE_URL | 浏览器 API 基础路径，默认 /api/v1；生产推荐同源反向代理 |
| API_PROXY_TARGET | 仅开发/本地预览服务器的后端目标，默认 http://127.0.0.1:8080，不打包为浏览器 API 地址 |

开发端口固定 5173，/api 通过 Vite Proxy 到真实 Spring Boot，保留 /api/v1 路径。推荐浏览器使用 localhost:5173；如果改为 127.0.0.1 或其他来源，应在后端 CORS_ALLOWED_ORIGINS 中明确配置对应来源。不要以放宽 Spring Security 或通配 CORS 解决配置问题。

```bash
npm run typecheck
npm run lint
npm run test
npm run build
npm run preview -- --port 5173
```

preview 用于本机检查 dist，不是生产部署方案；同端口的 dev 和 preview 不能同时运行。生产需按实际服务配置静态资源、前端 History 路由回退以及 /api 反向代理，不能将生产 API 固定为 localhost。本阶段未发布线上站点。

Windows 使用 Node 安装包或 nvm-windows 安装推荐版本，使用上述 npm 命令；PowerShell 可用 `Copy-Item .env.example .env.local` 复制本地配置，使用编辑器修改 URL。后端仍需 Java 17 与 MySQL。Windows 开发和浏览器流程未实际验证。

## 页面与角色

| 路由 | 页面 | 权限 |
| --- | --- | --- |
| /login | 登录 | 仅两种管理员可完成登录 |
| /dashboard | 统计看板 | 系统管理员全局，实验室管理员仅授权范围 |
| /laboratories | 实验室列表、详情、编辑、启停 | 系统管理员另可创建和替换管理员分配 |
| /equipment | 设备列表、详情、创建、编辑、状态维护 | 实验室管理员仅授权实验室 |
| /reservations | 预约列表、详情和审计 | 系统管理员只读，实验室管理员可按状态与时间审批、撤销、完成 |
| /users | 用户列表、创建、姓名、启停、密码重置 | 仅系统管理员；禁止停用当前自己 |
| /forbidden | 权限不足 | 拒绝不符合角色的管理路由 |
| /connection-error | 服务异常与重试 | 临时网络错误保留已有会话，不冒充过期 |
| 其他地址 | 404 | 提供返回入口 |

实验室管理员没有用户管理和授权替换菜单，也不能创建实验室；系统管理员没有预约审批按钮。前端仅负责交互，资源归属和业务合法性仍由后端重新验证。学生不能恢复后台会话。

## 工程结构

```text
src/api/          Axios、响应/错误与22个管理端接口方法
src/types/        DTO、枚举、分页、字符串ID
src/stores/       Pinia认证与统计缓存
src/router/       路由、身份恢复与角色守卫
src/layout/       导航、顶栏、退出与移动端布局
src/components/   分页、状态标签、详情、图表和请求状态组合函数
src/utils/        北京时间、密码/原因校验、PATCH差异和确认
src/views/        登录、统计、实验室、设备、预约、用户及错误页
tests/            Vitest隔离测试
e2e/              Playwright真实后端浏览器测试
```

不在页面重复构造 Axios 实例，不存在 Mock 登录、静态业务列表、随机统计或本地数组冒充写入。所有写操作在真实 API 成功后重新查询；审批后刷新列表、已打开详情和统计缓存。分页从 1 开始、默认 20、最大 100，BIGINT ID 全程为字符串，不转 Number。

编辑请求只提交变化的白名单字段。实验室 description 和设备 model/description 可以显式 null 清空；其他字段不随空值清除。用户名、角色、实验室编号、资产编号及设备归属创建后不能修改。启停、密码重置和授权替换有确认，授权操作明确为整体替换，且最多选 100 人。

预约动作只向 LAB_ADMIN 展示：PENDING 开始前同意或带原因驳回；APPROVED 开始前带原因撤销，结束到达后完成登记。客户端时间仅决定交互展示，服务端最终判定；409 INVALID_STATE 会提示并重载，403 不清理令牌。请求处理中禁止重复写入，无自动重试。

统计使用 fromDate/toDate 自然日字符串，按北京时间初始化及格式化，范围最多 90 天。ECharts 仅接入真实 API 数据，空数据明确显示，支持 ResizeObserver/窗口重绘及销毁释放；热门设备不是实际利用率。

## 认证与安全边界

JWT 仅保存在 sessionStorage，账号信息保留在 Pinia 内存；登录后以及每次进入管理路由都会调用 /auth/me 检查身份。退出或 401 清理令牌、账号和统计缓存；403 保留会话，网络/503 提供重试，不伪造刷新令牌。

sessionStorage 可被同源恶意脚本读取，因此不能消除 XSS 风险。前端没有 v-html 注入业务数据，图表使用富文本而非 HTML 提示框，不把令牌放进 URL、页面文本或日志。生产仍需 HTTPS、合理 CSP 和后续安全测试。退出仅清除浏览器会话，不代表服务端 JWT 被即时撤销。

密码仅在登录或创建/重置弹窗中临时输入，成功、失败登录或弹窗关闭后清理，不保存密码哈希。所有 VITE_ 变量都可能进入浏览器，禁止存放数据库密码、JWT 密钥或其他凭据。

## 实际验证

- npm ci、typecheck、lint、test、build 均实际通过；ESLint 零错误、零警告。
- 36 项 Vitest 全部通过：登录/失败、会话、401/403、导航和路由、响应与字符串ID、分页、枚举、动作边界、密码/原因、重复操作、409、图表空数据/销毁、日期和请求体契约。
- Playwright 4 项真实流程在 Vite 开发服务器全部通过；同一组流程在最终 dist 预览也全部通过，均无跳过。两次执行不累加为八项独立测试。
- 浏览器覆盖两种管理员、真实资源创建/编辑/清空/授权、用户创建/姓名/密码重置/启停、同意/驳回/撤销/完成、统计刷新、授权隔离、移动端导航、退出/错误登录及404。
- 原后端 103 项测试回归全部通过，后端源码与 V1/V2 未修改。
- 维护版本下 npm audit 与 npm audit --omit=dev 均报告 0 项公告；只是当时依赖审计结果，不代表完整安全审查。

真实环境使用隔离 MySQL 8.0.45、原 Spring Boot Jar 和 Chromium，没有拦截业务网络返回假响应。历史完成样本仅向隔离库按真实表结构准备；学生 JWT 仅在 Node 测试进程使用测试密钥签发，不进入 src、dist 或生产入口。

### 重跑真实浏览器测试

先启动专用隔离后端及 Vite dev 或 preview。以安全的本地环境变量提供，不写入源码：

- E2E_BASE_URL：默认 http://localhost:5173。
- E2E_SYS_USERNAME / E2E_SYS_PASSWORD：隔离库真实系统管理员账号和密码。
- E2E_TEST_JWT_SECRET：与隔离后端一致的测试密钥，仅供 Node 测试准备学生样本，绝不能使用生产密钥。
- CHROMIUM_PATH：系统浏览器可执行文件路径；未设置时使用 Playwright 自带 Chromium，先运行 `npx playwright install chromium`。
- E2E_MYSQL_CONTAINER：可选的专用测试 MySQL 容器名。设置后向该隔离库准备一条已结束预约及审计，以验收完成登记；不设置时此项明确跳过，不能宣称完成登记已联调。

```bash
npm run test:e2e
```

脚本会真实创建脱敏测试账号、实验室、设备及预约，不自动删除历史，不能对生产运行。测试日志不应公开，playwright-report、test-results、node_modules、dist 已忽略；关闭网络 trace、录像和失败截图以减少凭据留存。测试允许在 /tmp 保存不含令牌的看板截图，不是部署入口。

## 已知限制

- Element Plus 分块约 921 kB、图表分块约 519 kB（未压缩），Vite 给出大于 500 kB 的体积提示；未调高阈值掩盖，低带宽首屏和完整性能基准未验证。
- 没有实现正式微信登录、学生预约/取消界面、线上部署或阶段 5。未验证 Windows、Firefox、Safari、完整无障碍与安全测试。
- 统计沿用后端已提交数据口径，不保证持续写入时多个聚合是同一冻结快照。设备和预约错误提示反映真实后端结果，不提供绕过占用的操作。
