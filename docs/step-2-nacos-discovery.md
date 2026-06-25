# Step2：Nacos 服务注册与发现

## 1. 本阶段目标

本阶段不接入数据库、Redis、RabbitMQ、OpenFeign、Nacos Config 或 Spring Security。Step2 的目标是先跑通微服务中的服务注册、服务发现和 Gateway 基于服务名转发链路：

- 服务注册：`edu-gateway`、`edu-user-service`、`edu-course-service`、`edu-trade-service` 启动后注册到 Nacos
- 服务发现：Gateway 不再写死后端服务的 `localhost` 地址
- 负载转发：Gateway 使用 `lb://服务名` 转发请求
- 鉴权保留：Step1 已有 JWT 校验、请求头清洗、用户上下文透传逻辑保持不变
- 链路验证：登录和 profile 接口仍然统一通过 Gateway 访问

这个阶段的重点是把“固定地址调用”升级为“服务名发现调用”，为后续服务间调用、横向扩容和微服务治理打基础。

## 2. 当前涉及模块

### edu-common

公共模块，本阶段不修改业务逻辑，继续提供：

- `Result`：统一接口返回对象
- `BizException`：业务异常
- `JwtProperties`：JWT 配置绑定
- `JwtUserInfo`：JWT 中携带的用户上下文
- `JwtUtil`：JWT 生成和解析工具

### edu-gateway

网关模块，端口 `8080`，当前承担：

- 注册到 Nacos，服务名为 `edu-gateway`
- 使用 Spring Cloud Gateway 作为统一入口
- 使用手写 routes 配置业务路由
- 将 `/user/**` 转发到 `lb://edu-user-service`
- 将 `/course/**` 转发到 `lb://edu-course-service`
- 将 `/trade/**` 转发到 `lb://edu-trade-service`
- 继续执行 Step1 的 JWT 鉴权和用户上下文透传

### edu-user-service

用户服务，端口 `8081`，当前承担：

- 注册到 Nacos，服务名为 `edu-user-service`
- 提供 `POST /user/login`
- 提供 `GET /user/profile`
- 继续从 Gateway 写入的 `X-User-*` 请求头中读取用户信息

### edu-course-service

课程服务，端口 `8082`，当前承担：

- 注册到 Nacos，服务名为 `edu-course-service`
- 当前只保留 `GET /health`
- 不实现课程业务

### edu-trade-service

交易服务，端口 `8083`，当前承担：

- 注册到 Nacos，服务名为 `edu-trade-service`
- 当前只保留 `GET /health`
- 不实现订单、优惠券或支付业务

## 3. 核心链路

### 服务注册链路

```text
启动 Nacos Server
  -> 启动 edu-user-service，注册 edu-user-service:8081
  -> 启动 edu-course-service，注册 edu-course-service:8082
  -> 启动 edu-trade-service，注册 edu-trade-service:8083
  -> 启动 edu-gateway，注册 edu-gateway:8080
  -> 在 Nacos 控制台查看服务和健康实例
```

### 登录链路

```text
用户
  -> POST http://localhost:8080/user/login
  -> Gateway 判断 /user/login 是白名单，直接放行
  -> Gateway 匹配 /user/** 路由
  -> Gateway 根据 lb://edu-user-service 从 Nacos 发现 user-service 实例
  -> Gateway 转发到 edu-user-service 的 /user/login
  -> user-service 校验内存用户 test / 123456
  -> user-service 生成 JWT
  -> 返回 token
```

### 鉴权和用户上下文透传链路

```text
用户
  -> GET http://localhost:8080/user/profile
  -> 携带 Authorization: Bearer <token>
  -> Gateway 全局过滤器校验 JWT
  -> Gateway 解析 userId、username、role
  -> Gateway 删除客户端可能伪造的 X-User-* 请求头
  -> Gateway 写入可信 X-User-Id、X-User-Name、X-User-Role
  -> Gateway 根据 lb://edu-user-service 从 Nacos 发现 user-service 实例
  -> Gateway 转发到 edu-user-service 的 /user/profile
  -> user-service 从请求头读取用户信息
  -> 返回 profile
```

## 4. 关键依赖

根工程统一管理版本：

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `spring-cloud-dependencies` | `2023.0.3` | 管理 Spring Cloud 组件版本 |
| `spring-cloud-alibaba-dependencies` | `2023.0.3.3` | 管理 Spring Cloud Alibaba 组件版本 |

各模块新增依赖：

| 模块 | 依赖 | 说明 |
| --- | --- | --- |
| `edu-gateway` | `spring-cloud-starter-alibaba-nacos-discovery` | 注册到 Nacos，并支持从 Nacos 发现服务 |
| `edu-gateway` | `spring-cloud-starter-loadbalancer` | 支持 Gateway 解析 `lb://服务名` |
| `edu-user-service` | `spring-cloud-starter-alibaba-nacos-discovery` | 注册到 Nacos |
| `edu-course-service` | `spring-cloud-starter-alibaba-nacos-discovery` | 注册到 Nacos |
| `edu-trade-service` | `spring-cloud-starter-alibaba-nacos-discovery` | 注册到 Nacos |

本阶段没有新增：

- OpenFeign
- MySQL
- Redis
- RabbitMQ
- Nacos Config
- Spring Security
- Sentinel
- Seata

## 5. Nacos 配置

四个应用都配置 Nacos Discovery 地址：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
```

说明：

- `server-addr` 指向本地 Nacos Server
- 本阶段只使用 Discovery，不使用 Config
- 服务名来自各模块的 `spring.application.name`

当前服务名：

| 模块 | 服务名 | 端口 |
| --- | --- | --- |
| `edu-gateway` | `edu-gateway` | `8080` |
| `edu-user-service` | `edu-user-service` | `8081` |
| `edu-course-service` | `edu-course-service` | `8082` |
| `edu-trade-service` | `edu-trade-service` | `8083` |

## 6. Gateway 路由配置

Step1 中 Gateway 使用静态地址：

```yaml
uri: http://localhost:8081
```

Step2 改为服务名地址：

```yaml
uri: lb://edu-user-service
```

当前路由：

| 路径 | 转发目标 |
| --- | --- |
| `/user/**` | `lb://edu-user-service` |
| `/course/**` | `lb://edu-course-service` |
| `/trade/**` | `lb://edu-trade-service` |

本阶段没有开启复杂的自动路由：

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
```

原因：

- 当前只需要三条明确业务路由
- 避免自动暴露 `/edu-user-service/**` 这类额外入口
- 更方便面试时讲清楚 Gateway 路由和 Nacos 服务发现的关系

## 7. 接口说明

本阶段接口仍然建议统一通过 Gateway 的 `8080` 端口访问。

### POST /user/login

访问地址：

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

说明：

- 该接口由 Gateway 白名单放行
- Gateway 通过 `lb://edu-user-service` 转发到用户服务
- 登录成功后返回 JWT

### GET /user/profile

访问地址：

```text
GET http://localhost:8080/user/profile
```

请求头：

```text
Authorization: Bearer <token>
```

说明：

- 该接口必须携带合法 JWT
- Gateway 校验 JWT 后写入可信 `X-User-*` 请求头
- user-service 从请求头读取用户信息并返回

### GET /course/health

访问地址：

```text
GET http://localhost:8080/course/health
```

说明：

- 该请求匹配 `/course/**`
- Gateway 通过 `lb://edu-course-service` 转发
- 当前 Gateway 白名单只有 `/user/login` 和 `/health`
- 因此通过 Gateway 访问 `/course/health` 时需要携带合法 JWT

### GET /trade/health

访问地址：

```text
GET http://localhost:8080/trade/health
```

说明：

- 该请求匹配 `/trade/**`
- Gateway 通过 `lb://edu-trade-service` 转发
- 当前 Gateway 白名单只有 `/user/login` 和 `/health`
- 因此通过 Gateway 访问 `/trade/health` 时需要携带合法 JWT

## 8. 验证命令

以下命令使用 PowerShell。

### 8.1 编译

```powershell
mvn clean package -DskipTests
```

如果本地已有服务进程占用 target 中的 jar，`clean` 或 `package` 可能因为 Windows 文件锁失败。可以先停止正在运行的服务，或仅用下面命令验证源码编译：

```powershell
mvn compile -DskipTests
```

### 8.2 启动 Nacos

进入 Nacos 的 `bin` 目录，启动单机模式：

```powershell
startup.cmd -m standalone
```

控制台地址：

```text
http://localhost:8848/nacos
```

默认账号密码通常是：

```text
nacos / nacos
```

### 8.3 启动服务

建议先启动 Nacos，再启动业务服务和 Gateway：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-user-service\target\edu-user-service-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-course-service\target\edu-course-service-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-trade-service\target\edu-trade-service-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-gateway\target\edu-gateway-0.0.1-SNAPSHOT.jar
```

也可以在 IDEA 中分别启动：

- `com.dyl.edu.user.EduUserServiceApplication`
- `com.dyl.edu.course.EduCourseServiceApplication`
- `com.dyl.edu.trade.EduTradeServiceApplication`
- `com.dyl.edu.gateway.EduGatewayApplication`

### 8.4 登录获取 token

```powershell
$login = Invoke-RestMethod `
  -Uri "http://localhost:8080/user/login" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"username":"test","password":"123456"}'

$token = $login.data.token
$token
```

### 8.5 带 token 访问 profile

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/user/profile" `
  -Method Get `
  -Headers @{ Authorization = "Bearer $token" }
```

预期返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": "1001",
    "username": "test",
    "role": "STUDENT"
  }
}
```

### 8.6 不带 token 访问 profile

```powershell
Invoke-WebRequest `
  -Uri "http://localhost:8080/user/profile" `
  -Method Get
```

预期结果：HTTP `401`。

### 8.7 携带错误 token

```powershell
Invoke-WebRequest `
  -Uri "http://localhost:8080/user/profile" `
  -Method Get `
  -Headers @{ Authorization = "Bearer wrong-token" }
```

预期结果：HTTP `401`。

### 8.8 携带伪造 X-User-Id

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/user/profile" `
  -Method Get `
  -Headers @{
    Authorization = "Bearer $token"
    "X-User-Id" = "9999"
    "X-User-Name" = "hacker"
    "X-User-Role" = "ADMIN"
  }
```

预期返回仍然是 JWT 中的真实用户：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": "1001",
    "username": "test",
    "role": "STUDENT"
  }
}
```

### 8.9 通过 Gateway 访问 course 和 trade 健康检查

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/course/health" `
  -Method Get `
  -Headers @{ Authorization = "Bearer $token" }

Invoke-RestMethod `
  -Uri "http://localhost:8080/trade/health" `
  -Method Get `
  -Headers @{ Authorization = "Bearer $token" }
```

预期：

- `/course/health` 返回 `edu-course-service`、`UP`、`8082`
- `/trade/health` 返回 `edu-trade-service`、`UP`、`8083`

## 9. Nacos 控制台可以观察的内容

访问：

```text
http://localhost:8848/nacos
```

在服务列表中可以观察：

- `edu-gateway`
- `edu-user-service`
- `edu-course-service`
- `edu-trade-service`

每个服务应该至少有 1 个健康实例。

重点确认：

- 服务名是否和 `spring.application.name` 一致
- 实例端口是否分别为 `8080`、`8081`、`8082`、`8083`
- 实例状态是否健康
- 服务下线后实例是否从 Nacos 中移除或变为不健康

## 10. IDEA 中可以观察的内容

### Console

可以观察：

- 服务是否启动成功
- 服务端口是否为预期值
- Nacos 注册相关日志
- Gateway 路由转发是否成功
- JWT 鉴权成功或失败日志
- user-service 是否读取到 Gateway 写入的 `X-User-*` 请求头

### Environment

可以确认配置是否生效：

- `server.port`
- `spring.application.name`
- `spring.cloud.nacos.discovery.server-addr`
- `spring.cloud.gateway.routes`
- `spring.cloud.gateway.discovery.locator.enabled`
- `edu.jwt.secret`

### Beans

可以确认以下对象是否被 Spring 管理：

- `JwtAuthGlobalFilter`
- `JwtProperties`
- `UserController`
- `UserServiceImpl`
- Nacos Discovery 相关自动配置对象
- Gateway LoadBalancer 相关对象

## 11. 问题排查与经验总结

### 11.1 Nacos 未启动

现象：

```text
localhost:8848 无法访问
```

或服务启动时出现 Nacos 连接失败日志。

原因：

本地 Nacos Server 没有启动，或端口不是 `8848`。

解决：

先启动 Nacos，再启动各个服务。

### 11.2 lb:// 无法解析

现象：

```text
503 Service Unavailable
```

或 Gateway 找不到目标服务实例。

可能原因：

- Nacos 中没有对应服务
- 服务名写错
- Gateway 缺少 LoadBalancer 依赖
- 业务服务没有成功注册到 Nacos

排查重点：

- Gateway 路由中的 `lb://edu-user-service`
- user-service 的 `spring.application.name`
- Nacos 控制台服务列表
- Gateway 是否引入 `spring-cloud-starter-loadbalancer`

### 11.3 服务名不一致

现象：

Gateway 路由配置看起来正确，但仍然找不到服务。

原因：

`lb://` 后面的服务名必须和服务的 `spring.application.name` 一致。

示例：

```yaml
uri: lb://edu-user-service
```

必须对应：

```yaml
spring:
  application:
    name: edu-user-service
```

### 11.4 Gateway WebFlux 与 Spring MVC 依赖冲突

现象：

Gateway 启动异常，或 Web 应用类型混乱。

原因：

Gateway 基于 WebFlux/Netty，不应该引入 `spring-boot-starter-web`。

解决：

Gateway 只保留 `spring-cloud-starter-gateway`，不要加入 MVC Web Starter。

### 11.5 版本不兼容

现象：

启动时报 `NoSuchMethodError`、类找不到、自动配置失败等。

原因：

Spring Boot、Spring Cloud、Spring Cloud Alibaba 版本需要匹配。

当前组合：

- Spring Boot `3.3.5`
- Spring Cloud `2023.0.3`
- Spring Cloud Alibaba `2023.0.3.3`

经验：

接入 Spring Cloud Alibaba 时，不要在每个模块里单独写版本，优先在根 POM 的 `dependencyManagement` 中统一管理。

## 12. 当前限制与后续优化

- 当前只使用 Nacos Discovery，不使用 Nacos Config。
- 当前没有接入 OpenFeign，服务间调用后续再做。
- 当前没有接入 MySQL、Redis、RabbitMQ。
- 当前 course-service 和 trade-service 仍然只提供 `/health`，没有业务接口。
- 当前 Gateway 白名单只有 `/user/login` 和 `/health`，通过 Gateway 访问 `/course/health`、`/trade/health` 需要携带 token。
- 当前 user-service 直接访问 `8081` 时仍可能伪造 `X-User-*`，真实环境需要内网隔离或服务间鉴权。
- 当前 JWT 密钥写在配置文件中，仅用于本地演示，后续应改为环境变量或配置中心管理。

## 13. 面试复盘要点

可以这样说明本阶段：

> Step2 接入 Nacos Discovery，把 Gateway 中写死的 `http://localhost:8081` 改成 `lb://edu-user-service`。这样 Gateway 不再关心后端服务的具体 IP 和端口，而是通过服务名从 Nacos 获取健康实例，再由 Spring Cloud LoadBalancer 选择实例完成转发。这个阶段只做注册发现，不做 OpenFeign、Redis 或数据库，是为了先把微服务寻址链路跑通。

可以重点讲：

1. Nacos 在当前项目中只承担服务注册与发现，不承担配置中心职责

2. Gateway 为什么使用手写 routes，而不是开启自动服务发现路由

3. `lb://服务名`、Nacos 服务列表、LoadBalancer 三者之间的关系

4. 为什么 Gateway 不能引入 `spring-boot-starter-web`

5. 为什么 Step2 不做 OpenFeign、Redis、MySQL

   # 源码阅读记录

   ### 1. Nacos 注册配置
   application.yml -> spring.application.name -> spring.cloud.nacos.discovery.server-addr

   ### 2. Gateway 服务发现转发链路
   /user/login -> Gateway routes -> lb://edu-user-service -> Nacos 服务发现 -> user-service

   ### 3. 鉴权链路
   /user/profile -> JwtAuthGlobalFilter -> JwtUtil.parseToken -> 写入 X-User-* -> lb://edu-user-service -> UserController.profile

   ### 4. 为什么关闭 discovery locator
   当前只需要手写业务路由，避免自动暴露额外服务名路径。

   ### 5. 当前不足
   只完成服务注册发现，还没有做服务间调用、数据库、缓存和消息队列。
