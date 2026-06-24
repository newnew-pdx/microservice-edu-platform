# 在线教育微服务平台

## 项目定位

本项目是一个用于 Java 后端实习面试的 Spring Cloud Alibaba 微服务在线教育平台项目。项目采用 Java 17、Maven 多模块和 Spring Boot 3.x，目标是通过小步迭代逐步搭建可运行、可解释、可回滚的微服务系统。

## 当前阶段目标

Step 0 只初始化最小可运行项目骨架，不实现复杂业务能力。

当前阶段只包含：

- Maven 多模块父工程
- 公共模块 `edu-common`
- 网关模块 `edu-gateway`
- 用户服务 `edu-user-service`
- 课程服务 `edu-course-service`
- 交易服务 `edu-trade-service`
- 每个服务的独立启动类
- 每个服务的 `GET /health` 接口

当前阶段不接入 MySQL、Redis、RabbitMQ、Nacos、Sentinel、Seata、XXL-JOB，也不实现用户登录、课程查询、优惠券、订单、学习进度等业务功能。

## 模块划分

| 模块 | 说明 | 端口 |
| --- | --- | --- |
| `edu-common` | 公共模块，放统一返回对象、常量、基础异常 | 无 |
| `edu-gateway` | 网关模块，当前阶段只提供启动能力和健康检查 | 8080 |
| `edu-user-service` | 用户服务，当前阶段只提供健康检查 | 8081 |
| `edu-course-service` | 课程服务，当前阶段只提供健康检查 | 8082 |
| `edu-trade-service` | 交易服务，当前阶段只提供健康检查 | 8083 |

## 启动方式

先在项目根目录编译：

```bash
mvn clean compile
```

在 IDEA 中分别启动以下启动类：

- `com.dyl.edu.gateway.EduGatewayApplication`
- `com.dyl.edu.user.EduUserServiceApplication`
- `com.dyl.edu.course.EduCourseServiceApplication`
- `com.dyl.edu.trade.EduTradeServiceApplication`

也可以通过 Maven 分模块启动，例如：

```bash
mvn -pl edu-gateway spring-boot:run
mvn -pl edu-user-service spring-boot:run
mvn -pl edu-course-service spring-boot:run
mvn -pl edu-trade-service spring-boot:run
```

## Health 接口验证

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
```

接口返回服务名、状态、端口等基础信息，用于确认服务启动成功。

## 后续 Step1 计划

Step1 建议继续保持小步迭代，可以优先补充统一异常处理、参数校验基础能力或服务分层目录规范。暂不建议直接接入注册中心、数据库或复杂业务链路。
