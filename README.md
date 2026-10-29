# 基于小程序的高校实验室设备预约系统设计与实现

本科毕业设计项目，采用单体后端，为学生提供设备预约，为实验室管理员提供审批和设备管理，为系统管理员提供账号、实验室及权限管理。

## 当前状态

阶段 0 已完成并合并。阶段 1 已实现 Spring Boot 后端基础、MySQL 四张基础表迁移、管理员登录及 JWT 权限基础，并进行了构建、测试及应用启动验证；详细结果见进度记录。阶段 2—7 尚未开始。

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
backend/       Spring Boot 后端基础工程
admin-web/     管理后台（当前仅占位）
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

当前接口：POST /api/v1/auth/login、GET /api/v1/auth/me、GET /api/v1/health。资源 CRUD、预约和微信认证均未实现。阶段 4 再初始化管理后台，阶段 5 初始化小程序。

配置通过环境变量或忽略的本地配置提供。禁止提交数据库密码、JWT 密钥、微信 AppSecret、访问令牌及真实个人数据。直接使用现有仓库，不另建 Git 仓库。
