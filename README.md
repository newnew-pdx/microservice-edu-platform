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

## Step1 当前实现：Gateway + User Service + JWT 鉴权

### 本阶段目标

Step1 的目标是先跑通微服务最小鉴权链路，不接入数据库、Redis、RabbitMQ、Nacos、OpenFeign、Sentinel、Seata、XXL-JOB，也不实现课程、订单、优惠券、学习进度等业务功能。

本阶段实现的核心链路：

1. 用户通过 Gateway 请求 `POST /user/login`。
2. Gateway 将请求转发到 `edu-user-service`。
3. `edu-user-service` 使用内存模拟用户校验账号密码。
4. 登录成功后生成 JWT 并返回。
5. 用户携带 `Authorization: Bearer <token>` 请求 `GET /user/profile`。
6. Gateway 全局过滤器校验 JWT。
7. Gateway 从 JWT 中解析 `userId`、`username`、`role`。
8. Gateway 删除客户端可能伪造的 `X-User-Id`、`X-User-Name`、`X-User-Role`。
9. Gateway 写入可信的 `X-User-Id`、`X-User-Name`、`X-User-Role`。
10. `edu-user-service` 的 `/user/profile` 从请求头读取用户信息并返回。

### 模块改动说明

| 模块 | 端口 | Step1 职责 |
| --- | --- | --- |
| `edu-common` | 无 | 提供统一返回对象、基础异常、JWT 配置类、JWT 用户信息对象和 JWT 工具类 |
| `edu-gateway` | 8080 | 使用 Spring Cloud Gateway 作为统一入口，提供静态路由、JWT 全局鉴权过滤器、WebFlux 风格 `/health` |
| `edu-user-service` | 8081 | 使用 Spring MVC，实现内存用户登录、JWT 签发、从请求头读取用户上下文 |
| `edu-course-service` | 8082 | 当前只保留健康检查，不实现课程业务 |
| `edu-trade-service` | 8083 | 当前只保留健康检查，不实现交易业务 |

本阶段重点改动：

- `edu-gateway` 移除 `spring-boot-starter-web`，改用 `spring-cloud-starter-gateway`。
- `edu-gateway` 不使用 MVC Controller 做健康检查，`/health` 改为 WebFlux `RouterFunction`。
- `edu-common` 新增 JWT 相关能力。
- `edu-user-service` 新增登录、个人信息接口和简单业务分层。
- 父工程使用 Spring Cloud BOM 管理 Gateway 依赖版本。

当前内存模拟用户：

| 字段 | 值 |
| --- | --- |
| `userId` | `1001` |
| `username` | `test` |
| `password` | `123456` |
| `role` | `STUDENT` |

### 登录与鉴权链路

登录链路：

```text
Client
  -> POST /user/login
  -> edu-gateway:8080
  -> edu-user-service:8081
  -> 内存用户校验 test / 123456
  -> 生成 JWT
  -> 返回 token
```

访问个人信息链路：

```text
Client
  -> GET /user/profile
  -> Authorization: Bearer <token>
  -> edu-gateway:8080
  -> 校验 JWT
  -> 删除客户端传入的 X-User-* 请求头
  -> 写入可信 X-User-Id / X-User-Name / X-User-Role
  -> edu-user-service:8081
  -> 从请求头读取用户上下文
  -> 返回用户信息
```

### JWT 中包含的字段

JWT 由 `edu-common` 中的 `JwtUtil` 生成和解析。

当前 token 包含：

| 字段 | 说明 |
| --- | --- |
| `userId` | 用户 ID，例如 `1001` |
| `username` | 用户名，例如 `test` |
| `role` | 用户角色，例如 `STUDENT` |
| `iat` | 签发时间 |
| `exp` | 过期时间 |
| `sub` | token 主题，当前使用用户 ID |

当前过期时间默认由 `JwtProperties.expireSeconds` 控制，默认值为 `7200` 秒。

### Gateway 路由配置

当前使用静态路由，不接入 Nacos：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: edu-user-service
          uri: http://localhost:8081
          predicates:
            - Path=/user/**
        - id: edu-course-service
          uri: http://localhost:8082
          predicates:
            - Path=/course/**
        - id: edu-trade-service
          uri: http://localhost:8083
          predicates:
            - Path=/trade/**
```

### Gateway 全局过滤器处理流程

`JwtAuthGlobalFilter` 的处理流程：

1. 获取当前请求路径。
2. 如果路径是 `/user/login` 或 `/health`，直接放行。
3. 其他路径读取 `Authorization` 请求头。
4. 判断格式是否为 `Bearer <token>`。
5. 使用 JWT 密钥校验 token 签名和过期时间。
6. 解析 `userId`、`username`、`role`。
7. 从请求中删除原始的 `X-User-Id`、`X-User-Name`、`X-User-Role`。
8. 写入从 JWT 解析出的可信用户上下文请求头。
9. 将请求继续转发到后端服务。
10. token 缺失、格式错误、签名错误或过期时，直接返回 HTTP `401`。

放行路径：

- `/user/login`
- `/health`

其他路径当前都需要携带合法 JWT。

### 为什么要删除客户端伪造的 X-User-* 请求头

`X-User-Id`、`X-User-Name`、`X-User-Role` 是服务内部传递用户上下文的请求头，不应该由外部客户端决定。

如果 Gateway 只追加请求头，而不删除客户端原始请求头，就可能出现用户伪造身份的问题。例如客户端直接传：

```text
X-User-Id: 9999
X-User-Role: ADMIN
```

如果后端服务错误读取了伪造值，就会造成越权风险。

因此 Gateway 必须先删除外部传入的 `X-User-*`，再根据 JWT 中可信的用户信息重新写入。这样 user-service 读取到的用户上下文只来自 Gateway 校验后的 token，而不是客户端自报身份。

### 启动与接口测试命令

编译：

```powershell
mvn clean package -DskipTests
```

启动服务：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-user-service\target\edu-user-service-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-gateway\target\edu-gateway-0.0.1-SNAPSHOT.jar
```

如果在 IDEA 中启动，可以分别运行：

- `com.dyl.edu.user.EduUserServiceApplication`
- `com.dyl.edu.gateway.EduGatewayApplication`

Health 验证：

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/health"
Invoke-RestMethod -Uri "http://localhost:8081/health"
```

登录：

```powershell
$login = Invoke-RestMethod `
  -Uri "http://localhost:8080/user/login" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"username":"test","password":"123456"}'

$token = $login.data.token
$token
```

携带 token 访问个人信息：

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

不带 token 访问：

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/user/profile" -Method Get
```

预期返回 HTTP `401`。

伪造请求头验证：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/user/profile" `
  -Method Get `
  -Headers @{
    Authorization = "Bearer $token"
    "X-User-Id" = "9999"
    "X-User-Name" = "fake"
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

### Step1 实际验收结果

本地已执行：

```powershell
mvn clean package -DskipTests
```

结果：`BUILD SUCCESS`。

端到端验证结果：

```json
{
  "loginCode": 200,
  "tokenPresent": true,
  "noTokenStatus": 401,
  "profileUserId": "1001",
  "spoofProfileUserId": "1001",
  "spoofProfileUsername": "test",
  "spoofProfileRole": "STUDENT"
}
```

说明：

- 登录成功并返回 token。
- 不带 token 访问 `/user/profile` 返回 `401`。
- 携带伪造 `X-User-Id: 9999` 时，返回的仍然是 JWT 中的 `1001`。

### 问题排查与解决过程

#### 1. Maven 无法写入本地仓库

现象：

```text
AccessDeniedException: C:\Users\...\ .m2\repository\org\springframework\cloud
```

原因：

当前执行环境默认只允许写项目目录，而 Maven 下载依赖需要写入用户目录下的 `.m2` 仓库。

解决：

允许 Maven 访问本地仓库后重新执行：

```powershell
mvn clean package -DskipTests
```

经验：

微服务项目第一次引入新依赖时，Maven 大概率需要下载依赖。遇到 `.m2` 写入失败时，优先判断是不是环境权限问题，而不是代码问题。

#### 2. `edu-common` 编译找不到 `ConfigurationProperties`

现象：

```text
程序包 org.springframework.boot.context.properties 不存在
找不到符号: 类 ConfigurationProperties
```

原因：

`JwtProperties` 放在 `edu-common` 中，并使用了 Spring Boot 的 `@ConfigurationProperties` 注解，但 `edu-common` 原本没有 Spring Boot 编译依赖。

解决：

在 `edu-common` 中补充 Spring Boot 基础依赖，使 common 可以编译配置绑定类。

经验：

公共模块应尽量轻量。如果 common 中放纯 POJO，不需要 Spring Boot 依赖；如果 common 中放 `@ConfigurationProperties`、自动配置类等 Spring Boot 类型，就需要显式声明相关依赖。

#### 3. 普通 jar 不能直接运行

现象：

```text
edu-user-service-0.0.1-SNAPSHOT.jar 中没有主清单属性
```

原因：

模块原来只执行普通 Maven jar 打包，没有配置 `spring-boot-maven-plugin`，生成的 jar 不是可执行 Spring Boot jar。

解决：

给 `edu-gateway` 和 `edu-user-service` 增加 `spring-boot-maven-plugin`，重新打包后可以通过 `java -jar` 启动。

经验：

如果准备用 `java -jar` 验收 Spring Boot 服务，需要确认模块是否经过 Spring Boot repackage。否则可以用 IDEA 或 `mvn spring-boot:run` 启动。

#### 4. 运行时 Java 版本不对

现象：

```text
JarLauncher has been compiled by a more recent version of the Java Runtime
class file version 61.0, this version only recognizes up to 55.0
```

原因：

项目需要 Java 17+，Maven 实际使用 Java 21 编译成功，但命令行默认 `java` 指向 JDK 11。

排查命令：

```powershell
mvn -version
where java
$env:JAVA_HOME
```

解决：

显式使用：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar xxx.jar
```

经验：

编译用的 Java 和运行用的 Java 可能不是同一个。Spring Boot 3 项目启动前，应确认运行时 Java 至少是 17。

#### 5. Gateway 运行时报 `NoSuchMethodError`

现象：

```text
NoSuchMethodError: 'java.util.Set org.springframework.http.HttpHeaders.headerSet()'
```

原因：

Spring Cloud Gateway 与 Spring Framework 版本不兼容。最开始使用 Spring Cloud `2023.0.5`，解析到 Gateway `4.1.6`，它调用了当前 Spring Boot `3.3.5` 搭配的 Spring Framework 中不存在的方法。

尝试过程：

- 先从 `2023.0.5` 降到 `2023.0.4`，但仍解析到 Gateway `4.1.6`，问题没有解决。
- 再降到 `2023.0.3`，Gateway 降到 `4.1.5`，与当前 Spring Boot 组合可以正常转发。

最终解决：

父工程中使用：

```xml
<spring-cloud.version>2023.0.3</spring-cloud.version>
```

经验：

看到 `NoSuchMethodError`，优先怀疑依赖版本冲突，而不是业务代码错误。Spring Boot、Spring Cloud、Spring Framework 是一组强关联版本，不能只追求最新版本。

### 当前遗留问题

- JWT 密钥当前写在配置文件中，只适合本地学习和 Step1 演示，后续应改为环境变量或配置中心管理。
- user-service 如果被绕过 Gateway 直接访问，理论上仍可以伪造 `X-User-*` 请求头；真实部署时需要通过网络隔离或服务间鉴权限制入口。
- 当前用户数据写死在内存中，没有接入数据库，服务重启后也没有真实用户体系。
- Gateway 使用静态路由，没有接入注册中心，所以服务地址写死为 `localhost:8081/8082/8083`。
- `/course/**` 和 `/trade/**` 只配置了路由，不实现业务功能。
- 当前未引入 Spring Security，鉴权逻辑由 Gateway 全局过滤器手写完成，适合当前 Step1 面试讲解，但后续复杂权限模型需要重新设计。

### 面试复盘要点

可以这样解释 Step1：

> 这一阶段我没有急着接数据库和注册中心，而是先实现微服务最小鉴权链路：Gateway 统一入口、user-service 登录签发 JWT、Gateway 校验 JWT、解析用户上下文，并通过可信请求头传给后端服务。这样后续课程、订单等服务都可以复用统一的用户上下文传递方式。

可以重点强调：

- Gateway 和业务服务的职责边界：Gateway 做统一鉴权和上下文透传，业务服务只消费可信用户上下文。
- 为什么 Gateway 要删除客户端传入的 `X-User-*`：防止客户端伪造身份和角色。
- 为什么 Gateway 不使用 `spring-boot-starter-web`：Spring Cloud Gateway 基于 WebFlux，混用 MVC 容易造成依赖和运行模型冲突。
- 为什么暂时不接入 Nacos、Redis、MySQL：本阶段目标是验证最小链路，避免一次引入太多变量。
- 遇到环境问题时如何排查：先区分编译问题、运行 Java 版本问题、依赖版本兼容问题，再逐个收敛。
