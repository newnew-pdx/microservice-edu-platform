# 微服务在线教育平台

## 项目定位

本项目是一个用于 Java 后端实习面试准备的 Spring Cloud Alibaba 微服务项目。项目采用小步迭代方式推进，目标是逐步掌握微服务、缓存、消息队列、高并发与工程化能力。

当前项目不追求一次性完整上线，而是每一步都保持可运行、可解释、可回滚。

## 技术栈概览

已使用：

- Java 17+
- Spring Boot 3.x
- Spring Cloud Gateway
- Nacos Discovery
- OpenFeign
- Maven 多模块
- JWT

后续规划：

- Redis
- RabbitMQ
- MySQL
- Docker Compose
- JMeter

## 当前模块说明

| 模块 | 说明 | 端口 |
| --- | --- | --- |
| `edu-common` | 公共模块，统一返回、异常、JWT 工具等 | 无 |
| `edu-gateway` | 统一入口，路由转发，JWT 鉴权 | 8080 |
| `edu-user-service` | 用户服务，当前提供登录和用户信息接口 | 8081 |
| `edu-course-service` | 课程服务，当前提供内存课程查询接口 | 8082 |
| `edu-trade-service` | 交易服务，通过 OpenFeign 调用课程服务 | 8083 |

## 当前已完成能力

- Maven 多模块微服务骨架
- Gateway 统一入口、JWT 鉴权和可信用户上下文透传
- Nacos 服务注册与发现、Gateway `lb://` 路由
- trade-service 通过 OpenFeign 调用 course-service
- 内存用户登录、内存课程查询和交易预览链路

## 阶段进度

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| Step0 | Maven 多模块微服务骨架搭建 | 已完成 |
| Step1 | Gateway + JWT 鉴权与用户上下文透传链路 | 已完成 |
| Step2 | 接入 Nacos 服务注册与发现 | 已完成 |
| Step3 | 接入 OpenFeign 服务间调用 | 已完成 |
| Step4 | 实现 Course Service + Redis 缓存 | 计划中 |
| Step5 | 实现优惠券领取与订单链路 | 计划中 |
| Step6 | 接入 RabbitMQ 订单超时取消 | 计划中 |

## 快速启动

编译：

```bash
mvn clean package -DskipTests
```

Step2、Step3 依赖 Nacos Discovery，启动应用前先启动 Nacos：

```bash
startup.cmd -m standalone
```

启动用户服务：

```bash
java -jar edu-user-service/target/edu-user-service-0.0.1-SNAPSHOT.jar
```

启动课程服务：

```bash
java -jar edu-course-service/target/edu-course-service-0.0.1-SNAPSHOT.jar
```

启动交易服务：

```bash
java -jar edu-trade-service/target/edu-trade-service-0.0.1-SNAPSHOT.jar
```

启动网关服务：

```bash
java -jar edu-gateway/target/edu-gateway-0.0.1-SNAPSHOT.jar
```

如果本机存在多个 JDK，建议确认运行时 Java 版本为 17+。

## Step1 快速验证

登录：

```text
POST http://localhost:8080/user/login
```

请求体：

```json
{
  "username": "test",
  "password": "123456"
}
```

访问用户信息：

```text
GET http://localhost:8080/user/profile
Authorization: Bearer <token>
```

详细验证命令和排查过程见 [docs/step-1-gateway-auth.md](docs/step-1-gateway-auth.md)。

## Step2 快速验证

确认 Nacos 控制台可访问：

```text
http://localhost:8848/nacos
```

服务列表中应能看到：

- `edu-gateway`
- `edu-user-service`
- `edu-course-service`
- `edu-trade-service`

通过 Gateway 访问用户链路：

```text
POST http://localhost:8080/user/login
GET http://localhost:8080/user/profile
Authorization: Bearer <token>
```

通过 Gateway 访问课程和交易健康检查时需要携带 token：

```text
GET http://localhost:8080/course/health
GET http://localhost:8080/trade/health
Authorization: Bearer <token>
```

详细验证命令和排查过程见 [docs/step-2-nacos-discovery.md](docs/step-2-nacos-discovery.md)。

## Step3 快速验证

先通过 `/user/login` 获取 token，再携带 token 访问交易预览：

```text
GET http://localhost:8080/trade/preview/1
Authorization: Bearer <token>
```

调用链为 Gateway -> `edu-trade-service` -> OpenFeign -> `edu-course-service`。当前课程数据来自内存，不创建订单，也不接入数据库、缓存或消息队列。

详细设计、PowerShell 命令和排查方式见 [docs/step-3-openfeign.md](docs/step-3-openfeign.md)。

## 文档索引

- [Step0：项目骨架初始化](docs/step-0-scaffold.md)
- [Step1：Gateway + JWT 鉴权与用户上下文透传](docs/step-1-gateway-auth.md)
- [Step2：Nacos 服务注册与发现](docs/step-2-nacos-discovery.md)
- [Step3：OpenFeign 服务间调用](docs/step-3-openfeign.md)
