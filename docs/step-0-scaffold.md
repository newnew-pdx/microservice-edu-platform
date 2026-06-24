# Step 0 项目骨架初始化

## 本阶段完成内容

本阶段完成 Spring Boot 3.x + Java 17 + Maven 多模块项目骨架初始化。

已完成：

- 根目录父工程 `pom.xml`
- 公共模块 `edu-common`
- 网关模块 `edu-gateway`
- 用户服务 `edu-user-service`
- 课程服务 `edu-course-service`
- 交易服务 `edu-trade-service`
- 每个服务独立 `application.yml`
- 每个服务独立启动类
- 每个服务 `GET /health` 接口
- 根目录 `README.md`
- 根目录 `.gitignore`

未完成，也不属于本阶段范围：

- MySQL
- Redis
- RabbitMQ
- Nacos
- Sentinel
- Seata
- XXL-JOB
- 用户登录
- 课程查询
- 优惠券
- 订单
- 学习进度

## 目录结构

```text
study_project/
├── .gitignore
├── README.md
├── agent.md
├── pom.xml
├── docs/
│   └── step-0-scaffold.md
├── edu-common/
│   ├── pom.xml
│   └── src/main/java/com/dyl/edu/common/
│       ├── constant/CommonConstants.java
│       ├── exception/BizException.java
│       └── result/Result.java
├── edu-gateway/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dyl/edu/gateway/
│       │   ├── EduGatewayApplication.java
│       │   └── controller/HealthController.java
│       └── resources/application.yml
├── edu-user-service/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dyl/edu/user/
│       │   ├── EduUserServiceApplication.java
│       │   └── controller/HealthController.java
│       └── resources/application.yml
├── edu-course-service/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dyl/edu/course/
│       │   ├── EduCourseServiceApplication.java
│       │   └── controller/HealthController.java
│       └── resources/application.yml
└── edu-trade-service/
    ├── pom.xml
    └── src/main/
        ├── java/com/dyl/edu/trade/
        │   ├── EduTradeServiceApplication.java
        │   └── controller/HealthController.java
        └── resources/application.yml
```

## 模块说明

### edu-common

公共模块，当前只放基础公共类：

- `Result`：统一接口返回对象
- `BizException`：基础业务异常
- `CommonConstants`：公共常量

### edu-gateway

网关模块。当前阶段不接入真实 Spring Cloud Gateway 路由和鉴权，只作为可启动服务保留 `/health` 接口。

### edu-user-service

用户服务。当前阶段不实现登录、注册、用户资料等业务，只提供 `/health`。

### edu-course-service

课程服务。当前阶段不实现课程查询、章节、学习进度等业务，只提供 `/health`。

### edu-trade-service

交易服务。当前阶段不实现订单、优惠券、支付等业务，只提供 `/health`。

## 启动方式

根目录编译：

```bash
mvn clean compile
```

IDEA 中启动：

- `com.dyl.edu.gateway.EduGatewayApplication`
- `com.dyl.edu.user.EduUserServiceApplication`
- `com.dyl.edu.course.EduCourseServiceApplication`
- `com.dyl.edu.trade.EduTradeServiceApplication`

## 验收标准

满足以下条件即可认为 Step 0 完成：

- `mvn clean compile` 编译通过
- `edu-gateway` 启动后可访问 `http://localhost:8080/health`
- `edu-user-service` 启动后可访问 `http://localhost:8081/health`
- `edu-course-service` 启动后可访问 `http://localhost:8082/health`
- `edu-trade-service` 启动后可访问 `http://localhost:8083/health`
- `/health` 返回服务名、状态、端口等基础信息
