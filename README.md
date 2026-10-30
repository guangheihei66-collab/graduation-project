# 基于小程序的高校实验室设备预约系统设计与实现

本科毕业设计项目，采用单体后端，为学生提供设备预约，为实验室管理员提供审批和设备管理，为系统管理员提供账号、实验室及权限管理。

## 当前状态

阶段 0—3 已合并。阶段 4 已完成 Vue 管理后台，并通过真实 Spring Boot + MySQL 浏览器联调，待审核合并。正式微信学生登录及小程序仍未实现，阶段 5—7 尚未开始。

## 技术选型

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3、Spring Security、JWT、MyBatis-Plus |
| 数据库 | MySQL 8、Flyway |
| 管理后台 | Vue 3、TypeScript、Vite、Element Plus、ECharts |
| 学生端 | 微信原生小程序 |
| 测试 | JUnit 5、集成测试 |

具体版本在对应工程初始化时锁定，Spring 组件版本由 Spring Boot 统一管理。

## 目录与文档

```text
backend/       Spring Boot 后端、资源与预约业务
admin-web/     Vue 3 管理后台与自动化测试
miniapp/       微信小程序（当前仅占位）
docs/          中文设计及进度文档
AGENTS.md      项目开发规范
```

- [需求分析](docs/REQUIREMENTS.md)
- [总体架构](docs/ARCHITECTURE.md)
- [数据库设计](docs/DATABASE.md)
- [REST API 设计](docs/API.md)
- [八阶段路线图](docs/ROADMAP.md)
- [进度记录](docs/PROGRESS.md)

## 开发与运行

后端需要 Java 17、MySQL 8；配置环境变量、开发演示数据与完整启动步骤见 [后端说明](backend/README.md)。

```bash
cd backend
./mvnw test       # 单元测试
./mvnw package    # 打包
./mvnw verify     # 包含真实 MySQL 集成测试，需要 Docker
# 配好数据库和 JWT 环境变量后：
java -jar target/reservation-backend-0.1.0-SNAPSHOT.jar
```

当前已提供认证、健康检查、资源与用户管理、设备时段、预约状态流转和统计接口，完整清单见 [API 文档](docs/API.md)。正式微信学生认证仍留待阶段 5；当前学生 API 仅通过隔离测试签发的 JWT 验证，不提供生产免密入口。管理后台已在阶段 4 实现；阶段 5 再初始化小程序。

配置通过环境变量或忽略的本地配置提供。禁止提交数据库密码、JWT 密钥、微信 AppSecret、访问令牌及真实个人数据。直接使用现有仓库，不另建 Git 仓库。


## 管理后台

需要推荐版本 Node.js 24.19.0 / npm 11.9.0，并先启动真实后端：

```bash
cd admin-web
npm ci
npm run dev
```

提供登录、统计、实验室、设备、预约和用户管理，按系统管理员/实验室管理员划分导航与操作；没有管理员代学生预约或系统管理员代审批。配置、构建、36 项前端测试和真实 Chromium 联调见 [管理后台说明](admin-web/README.md)。不将 Mock 单元测试等同于真实联调，后端原 103 项测试也已回归通过。
