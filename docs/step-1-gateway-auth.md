# Step1：Gateway + JWT 鉴权与用户上下文透传

## 1. 本阶段目标

本阶段不追求完整登录系统，也不接入数据库、Redis、RabbitMQ、Nacos、OpenFeign 或 Spring Security。Step1 的目标是先跑通微服务中的最小鉴权链路：

- 统一入口：所有外部请求先进入 `edu-gateway`
- 登录签发：`edu-user-service` 使用内存用户校验账号密码并生成 JWT
- 网关鉴权：Gateway 校验 JWT 并解析用户信息
- 上下文透传：Gateway 将可信用户信息写入 `X-User-*` 请求头
- 服务消费：`edu-user-service` 从请求头读取当前用户信息

这个阶段的重点是验证“网关统一鉴权 + 用户上下文透传”的基础模式，为后续课程、订单等服务复用当前用户上下文打基础。

## 2. 当前涉及模块

### edu-common

公共模块，当前在 Step1 中承担：

- `Result`：统一接口返回对象
- `BizException`：业务异常
- `JwtProperties`：JWT 配置绑定
- `JwtUserInfo`：JWT 中携带的用户上下文
- `JwtUtil`：JWT 生成和解析工具

### edu-gateway

网关模块，端口 `8080`，当前承担：

- 使用 Spring Cloud Gateway 作为统一入口
- 配置静态路由
- 放行 `/user/login` 和 `/health`
- 拦截其他请求并校验 JWT
- 清理客户端伪造的 `X-User-*` 请求头
- 写入可信的 `X-User-Id`、`X-User-Name`、`X-User-Role`

### edu-user-service

用户服务，端口 `8081`，当前承担：

- 提供 `POST /user/login`
- 使用内存用户 `test / 123456` 做登录校验
- 登录成功后生成 JWT
- 提供 `GET /user/profile`
- 从 Gateway 透传的请求头中读取用户信息

## 3. 核心链路

### 登录链路

```text
用户
  -> POST http://localhost:8080/user/login
  -> Gateway 判断 /user/login 是白名单，直接放行
  -> Gateway 路由到 http://localhost:8081/user/login
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
  -> Gateway 转发到 http://localhost:8081/user/profile
  -> user-service 从请求头读取用户信息
  -> 返回 profile
```

当前内存用户：

| 字段 | 值 |
| --- | --- |
| `userId` | `1001` |
| `username` | `test` |
| `password` | `123456` |
| `role` | `STUDENT` |

## 4. 接口说明

本阶段接口建议统一通过 Gateway 的 `8080` 端口访问，而不是直接访问 `8081`。

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
- 请求最终转发到 `edu-user-service`
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

### GET /health

访问地址：

```text
GET http://localhost:8080/health
GET http://localhost:8081/health
```

说明：

- Gateway 的 `/health` 使用 WebFlux 风格实现
- user-service 的 `/health` 使用 Spring MVC Controller 实现

## 5. JWT 设计

JWT 由 `edu-common` 中的 `JwtUtil` 统一生成和解析。

当前 JWT 中包含：

| 字段 | 说明 |
| --- | --- |
| `userId` | 用户 ID，例如 `1001` |
| `username` | 用户名，例如 `test` |
| `role` | 用户角色，例如 `STUDENT` |
| `exp` | 过期时间 |
| `iat` | 签发时间 |
| `sub` | token 主题，当前使用用户 ID |

当前 JWT 密钥配置在 `application.yml` 中，仅用于本地演示。真实项目中不建议把密钥直接写在配置文件里，后续可以改为：

- 环境变量
- 配置中心
- 密钥管理系统

## 6. Gateway 过滤器流程

`JwtAuthGlobalFilter` 是本阶段 Gateway 鉴权的核心。

处理流程：

1. 获取请求路径。
2. 判断是否是白名单路径。
3. 如果是 `/user/login` 或 `/health`，直接放行。
4. 如果不是白名单，读取 `Authorization` 请求头。
5. 判断是否符合 `Bearer <token>` 格式。
6. 格式不合法时返回 HTTP `401`。
7. 截取 token 字符串。
8. 使用 `JwtUtil.parseToken` 校验签名和过期时间。
9. 解析出 `userId`、`username`、`role`。
10. 删除客户端传来的 `X-User-Id`、`X-User-Name`、`X-User-Role`。
11. 写入 Gateway 解析出的可信 `X-User-*`。
12. 将修改后的请求继续转发给后端服务。

白名单路径：

- `/user/login`
- `/health`

其他路径当前都需要 JWT。

## 7. 为什么要删除客户端传来的 X-User-* 请求头

客户端请求头不可信。用户可以自己构造请求头，例如：

```text
X-User-Id: 9999
X-User-Role: ADMIN
```

如果 Gateway 不清理这些请求头，下游服务就可能读到客户端伪造的身份信息，造成越权风险。

本阶段的设计是：

- 客户端只能提交 JWT
- Gateway 负责校验 JWT
- Gateway 从 JWT 中解析可信用户信息
- 下游服务只信任 Gateway 重新写入的 `X-User-*`

所以 Gateway 必须先删除客户端传来的 `X-User-*`，再写入可信值。这也是本阶段验证“携带伪造 `X-User-Id: 9999`，返回仍然是 `1001`”的原因。

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

### 8.2 启动服务

建议显式使用 `JAVA_HOME` 中的 Java 17+：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-user-service\target\edu-user-service-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-gateway\target\edu-gateway-0.0.1-SNAPSHOT.jar
```

也可以在 IDEA 中分别启动：

- `com.dyl.edu.user.EduUserServiceApplication`
- `com.dyl.edu.gateway.EduGatewayApplication`

### 8.3 登录获取 token

```powershell
$login = Invoke-RestMethod `
  -Uri "http://localhost:8080/user/login" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"username":"test","password":"123456"}'

$token = $login.data.token
$token
```

### 8.4 带 token 访问 profile

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

### 8.5 不带 token 访问 profile

```powershell
Invoke-WebRequest `
  -Uri "http://localhost:8080/user/profile" `
  -Method Get
```

预期结果：HTTP `401`。

### 8.6 携带错误 token

```powershell
Invoke-WebRequest `
  -Uri "http://localhost:8080/user/profile" `
  -Method Get `
  -Headers @{ Authorization = "Bearer wrong-token" }
```

预期结果：HTTP `401`。

### 8.7 携带伪造 X-User-Id

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

## 9. IDEA 中可以观察的内容

### Console

可以观察：

- 服务是否启动成功
- 服务端口是否为预期值
- 登录成功、登录失败、JWT 鉴权成功或失败等日志

### Mappings

在 user-service 中可以查看注册的接口：

- `GET /health`
- `POST /user/login`
- `GET /user/profile`

Gateway 使用 WebFlux 和路由配置，不应按传统 MVC Controller 的方式理解业务路由。

### Beans

可以确认以下对象是否被 Spring 管理：

- `UserController`
- `UserServiceImpl`
- `GlobalExceptionHandler`
- `JwtAuthGlobalFilter`
- `JwtProperties`

### Environment

可以确认配置是否生效：

- `server.port`
- `spring.application.name`
- `edu.jwt.secret`
- `edu.jwt.expireSeconds`
- Gateway routes

### Health

可以查看服务状态是否为 `UP`，用于判断服务是否启动成功。

## 10. 问题排查与经验总结

### 10.1 Maven 无法写入本地仓库

现象：

```text
AccessDeniedException: C:\Users\...\ .m2\repository\org\springframework\cloud
```

原因：

执行环境只允许写项目目录，而 Maven 下载依赖需要写入用户目录下的 `.m2` 仓库。

解决：

允许 Maven 访问本地仓库后重新执行编译。

经验：

第一次引入新依赖时，Maven 大概率需要下载依赖。遇到 `.m2` 写入失败，应先判断是否是本地权限或沙箱问题。

### 10.2 edu-common 编译找不到 ConfigurationProperties

现象：

```text
程序包 org.springframework.boot.context.properties 不存在
找不到符号: 类 ConfigurationProperties
```

原因：

`JwtProperties` 放在 `edu-common` 中，并使用 Spring Boot 的 `@ConfigurationProperties`，但 `edu-common` 原本没有 Spring Boot 编译依赖。

解决：

给 `edu-common` 补充 Spring Boot 基础依赖，使公共模块可以编译配置绑定类。

经验：

公共模块要尽量轻量。如果 common 中只放普通 POJO，不需要 Spring Boot 依赖；如果放配置绑定类，就需要显式声明相关依赖。

### 10.3 普通 jar 不能直接运行

现象：

```text
edu-user-service-0.0.1-SNAPSHOT.jar 中没有主清单属性
```

原因：

普通 Maven jar 不是可执行 Spring Boot jar。

解决：

给需要通过 `java -jar` 启动的服务增加 Spring Boot 打包插件。

经验：

如果要通过 jar 验收服务，需要确认 jar 已经过 Spring Boot repackage。否则可以使用 IDEA 或 `mvn spring-boot:run` 启动。

### 10.4 运行时 Java 版本不对

现象：

```text
JarLauncher has been compiled by a more recent version of the Java Runtime
class file version 61.0, this version only recognizes up to 55.0
```

原因：

Maven 使用 Java 21 编译成功，但命令行默认 `java` 指向 JDK 11。Spring Boot 3 需要 Java 17+。

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

编译用 Java 和运行用 Java 可能不是同一个。启动 Spring Boot 3 服务前，应确认运行时 Java 至少是 17。

### 10.5 Gateway 运行时报 NoSuchMethodError

现象：

```text
NoSuchMethodError: 'java.util.Set org.springframework.http.HttpHeaders.headerSet()'
```

原因：

Spring Cloud Gateway 与 Spring Framework 版本不兼容。最开始使用 Spring Cloud `2023.0.5`，解析到 Gateway `4.1.6`，它调用了当前 Spring Boot `3.3.5` 搭配的 Spring Framework 中不存在的方法。

尝试过程：

- 从 `2023.0.5` 降到 `2023.0.4`，但仍解析到 Gateway `4.1.6`，问题未解决。
- 再降到 `2023.0.3`，Gateway 降到 `4.1.5`，可以正常转发。

经验：

看到 `NoSuchMethodError` 时，优先怀疑依赖版本冲突。Spring Boot、Spring Cloud、Spring Framework 是强关联版本，不能只追求最新版。

## 11. 当前限制与后续优化

- 当前用户数据是内存模拟，后续接 MySQL。
- 当前 Gateway 路由写死 `localhost`，后续接 Nacos 后改为 `lb://服务名`。
- 当前 user-service 直接访问 `8081` 时仍可能伪造 `X-User-*`，真实环境需要内网隔离或服务间鉴权。
- 当前没有接入 Spring Security，目的是聚焦微服务基础链路。
- 当前 JWT 密钥写在配置文件中，仅用于本地演示，后续应改为环境变量或配置中心管理。
- 当前只实现用户登录和 profile 链路，没有实现课程、订单、优惠券、学习进度等业务。

## 12. 面试复盘要点

可以这样说明本阶段：

> Step1 没有急着接数据库和注册中心，而是先实现微服务最小鉴权链路：Gateway 统一入口，user-service 登录签发 JWT，Gateway 校验 JWT 并解析用户上下文，再通过可信请求头传给后端服务。这样后续课程、订单等服务都可以复用统一的用户上下文传递方式。

可以重点讲：

1. Gateway 和业务服务的职责边界

2. 为什么 Gateway 要删除客户端传入的 `X-User-*`

3. 为什么 Gateway 不能使用 `spring-boot-starter-web`

4. 为什么本阶段暂不接入 Nacos、Redis、MySQL

5. 遇到环境问题时如何区分编译问题、运行 Java 版本问题、依赖版本兼容问题

   # 源码阅读记录

   ### 1. 登录链路
   /user/login -> Gateway 路由 -> UserController -> UserService -> JwtUtil

   ### 2. 鉴权链路
   /user/profile -> Gateway JwtAuthGlobalFilter -> JwtUtil.parseToken -> 写入 X-User-* -> UserController.profile

   ### 3. Gateway 与 user-service 的区别
   Gateway 使用 Spring Cloud Gateway，底层 WebFlux/Netty；user-service 使用 Spring MVC/Tomcat。

   ### 4. 为什么要删除 X-User-* 请求头
   客户端请求头不可信，需要由 Gateway 根据 JWT 重新生成可信用户上下文。

   ### 5. 当前不足
   服务可被直接访问，真实环境需要内网隔离或服务间鉴权。
   路由地址写死，后续接入 Nacos 改为服务发现。
