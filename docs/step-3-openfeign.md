# Step3：OpenFeign 服务间调用

## 1. 本阶段目标

本阶段只跑通下面的最小链路：

```text
客户端 -> Gateway JWT 鉴权 -> edu-trade-service
      -> OpenFeign + Nacos Discovery -> edu-course-service
      -> trade-service 合并用户上下文和课程信息后返回
```

不接入数据库、Redis、RabbitMQ，不创建真实订单，也不实现支付、优惠券、库存、学习进度或排行榜。

## 2. 为什么此时引入 OpenFeign

Step1 已经解决 Gateway 统一鉴权和用户上下文透传，Step2 已经通过 Nacos 解决服务注册、发现和按服务名寻址。此时引入 OpenFeign，可以把调用链从“Gateway 调用业务服务”自然扩展为“业务服务调用业务服务”，并复用 Step2 已经建立的服务发现能力。

## 3. OpenFeign 在当前项目中的作用

OpenFeign 是 trade-service 中的声明式 HTTP 客户端。它把远程 HTTP 接口描述成 Java 接口，让 Service 像调用普通方法一样查询课程信息。本阶段由它声明请求方法、路径、参数和响应类型，并完成 HTTP 调用及 JSON 序列化、反序列化。

## 4. 涉及模块

### edu-course-service

- 提供 `GET /course/internal/{courseId}`
- 使用内存数据保存一条测试课程
- `courseId=1` 返回课程信息
- 其他 ID 返回 `code=404`、`message=课程不存在`

### edu-trade-service

- 提供 `GET /trade/preview/{courseId}`
- 从 Gateway 写入的 `X-User-*` 请求头读取当前用户
- 通过 `CourseClient` 调用 `edu-course-service`
- 合并用户上下文和课程信息
- Feign 调用失败时返回 `code=503` 和明确提示

### edu-gateway

- 继续负责 JWT 鉴权、请求头清洗和可信用户上下文写入
- 继续通过 `lb://edu-trade-service` 转发 `/trade/**`
- 本阶段没有修改 Gateway 业务逻辑

### edu-user-service

- 继续提供 `POST /user/login`
- 继续使用内存用户 `test / 123456` 签发 JWT
- 本阶段没有修改

### edu-common

- 继续提供统一 `Result` 和 `BizException`
- 暂不抽取课程领域 DTO，避免公共模块依赖具体业务领域

## 5. OpenFeign 与 Nacos 的关系

`CourseClient` 使用 Nacos 服务名，不写死地址：

```java
@FeignClient(name = "edu-course-service")
```

调用时，OpenFeign 根据接口声明生成 HTTP 请求，Nacos Discovery 提供课程服务的健康实例，Spring Cloud LoadBalancer 从实例列表中选择目标。根 POM 中已有 Spring Cloud BOM，OpenFeign 不单独写版本。

职责可以概括为：OpenFeign 负责“怎么调用”，Nacos 负责“有哪些可用实例”，LoadBalancer 负责“选择哪个实例”。

当前版本组合：

| 组件 | 版本 |
| --- | --- |
| Spring Boot | `3.3.5` |
| Spring Cloud | `2023.0.3` |
| Spring Cloud Alibaba | `2023.0.3.3` |
| Spring Cloud OpenFeign | 由 Spring Cloud BOM 管理，当前解析为 `4.1.3` |

## 6. 本阶段核心链路

```text
POST /user/login 获取 JWT
  -> GET /trade/preview/1，携带 Authorization: Bearer <token>
  -> Gateway 校验 JWT
  -> 删除客户端伪造的 X-User-Id、X-User-Name、X-User-Role
  -> 根据 JWT 写入可信 X-User-*
  -> lb://edu-trade-service
  -> TradePreviewController 读取用户上下文
  -> TradePreviewService 调用 CourseClient
  -> Nacos 发现 edu-course-service 健康实例
  -> GET /course/internal/1
  -> course-service 返回内存课程信息
  -> trade-service 合并用户和课程信息后返回
```

## 7. 新增和修改文件清单

### edu-course-service

- `controller/CourseInternalController.java`：课程内部查询入口
- `service/CourseService.java`：课程服务接口
- `service/impl/CourseServiceImpl.java`：内存课程查询实现
- `vo/CourseInfoVO.java`：课程信息返回对象
- `handler/GlobalExceptionHandler.java`：课程业务异常统一返回

### edu-trade-service

- `pom.xml`：增加 OpenFeign 和 LoadBalancer 依赖
- `EduTradeServiceApplication.java`：启用 Feign Client 扫描
- `client/CourseClient.java`：课程服务 Feign Client
- `dto/CourseInfoDTO.java`：接收远程课程数据
- `controller/TradePreviewController.java`：交易预览接口
- `service/TradePreviewService.java`：交易预览服务接口
- `service/impl/TradePreviewServiceImpl.java`：远程调用和结果组装
- `vo/TradePreviewVO.java`：交易预览返回对象
- `handler/GlobalExceptionHandler.java`：交易业务异常统一返回

### 文档

- `docs/step-3-openfeign.md`
- `README.md`

## 8. 新增依赖说明

| 依赖 | 作用 |
| --- | --- |
| `spring-cloud-starter-openfeign` | 提供声明式 HTTP 客户端和 `@FeignClient` |
| `spring-cloud-starter-loadbalancer` | 根据服务名从已发现的实例中选择调用目标 |

版本由根 POM 的 Spring Cloud BOM 统一管理，没有在 trade-service 中单独写死。

## 9. course-service 提供者接口设计

提供者接口：

```text
GET /course/internal/{courseId}
```

当前仅维护一条内存课程：`courseId=1`、标题为“Java 微服务入门课”、价格 `19900` 分、状态 `ONLINE`。课程不存在时返回明确失败结果。`internal` 当前表示接口用途，尚未实现网络级隔离或服务间认证。

## 10. 接口响应

### 正常课程

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": "1001",
    "username": "test",
    "role": "STUDENT",
    "courseId": 1,
    "courseTitle": "Java 微服务入门课",
    "price": 19900,
    "courseStatus": "ONLINE"
  }
}
```

`price` 单位为分，本阶段不引入复杂金额模型。

### 课程不存在

```json
{
  "code": 404,
  "message": "课程不存在",
  "data": null
}
```

### 课程服务调用失败

```json
{
  "code": 503,
  "message": "课程服务调用失败，请稍后重试",
  "data": null
}
```

这些是当前统一 `Result` 中的业务码；除 Gateway 鉴权失败返回 HTTP 401 外，本阶段没有额外设计 HTTP 状态映射。

## 11. trade-service Feign Client 设计

```java
@FeignClient(name = "edu-course-service")
public interface CourseClient {

    @GetMapping("/course/internal/{courseId}")
    Result<CourseInfoDTO> getCourse(@PathVariable("courseId") Long courseId);
}
```

`name` 与 course-service 的 `spring.application.name` 完全一致；路径与提供者一致；没有配置固定 URL。

## 12. trade-service Controller / Service 分层设计

`TradePreviewController` 只接收 `courseId` 和 `X-User-*` 请求头，调用 `TradePreviewService` 并包装返回结果。`TradePreviewServiceImpl` 负责调用 `CourseClient`、检查远程结果、处理调用失败并组装 `TradePreviewVO`。Controller 不直接调用 Feign Client。

## 13. DTO/VO 设计

- course-service 使用 `CourseInfoVO` 表达提供者响应
- trade-service 使用 `CourseInfoDTO` 接收远程 JSON
- trade-service 使用 `TradePreviewVO` 表达预览结果

当前不把课程对象放入 `edu-common`，避免公共模块依赖具体业务领域。若后续多个服务共同依赖稳定的课程接口契约，可再考虑独立 API 契约模块。

## 14. Gateway 鉴权和用户上下文透传

Gateway 继续负责清洗并写入可信的 `X-User-Id`、`X-User-Name`、`X-User-Role`。trade-service 读取这些请求头，但不继续透传给 course-service，因为课程基础查询只需要 `courseId`。

本阶段没有修改 JWT 校验逻辑，也没有修改 Gateway 的 `lb://` 路由。

Step1 的安全边界保持不变：客户端只提交 JWT，Gateway 先删除客户端伪造的 `X-User-*`，再写入 JWT 中的可信用户信息。用户上下文没有继续透传给 course-service，因为当前课程基础查询只需要 `courseId`。

## 15. 本阶段没有做什么

- 没有接入 MySQL、Redis、RabbitMQ
- 没有接入 Spring Security
- 没有接入 Sentinel、Seata、XXL-JOB、SkyWalking、Prometheus
- 没有使用 Nacos Config
- 没有实现真实订单、支付、优惠券、库存扣减、学习进度或排行榜
- 没有实现 Feign 熔断、降级或复杂重试
- 没有进行压测，也没有性能数据

## 16. 编译验证

执行：

```powershell
mvn clean package -DskipTests
```

本地已实际执行成功，Maven Reactor 中 6 个模块均为 `SUCCESS`。

## 17. 本地启动方式

先启动 Nacos 单机模式：

```powershell
startup.cmd -m standalone
```

控制台：

```text
http://localhost:8848/nacos
```

然后在 IDEA 中启动：

- `EduUserServiceApplication`，端口 `8081`
- `EduCourseServiceApplication`，端口 `8082`
- `EduTradeServiceApplication`，端口 `8083`
- `EduGatewayApplication`，端口 `8080`

启动顺序的重点是先启动 Nacos，再启动需要注册的四个应用。

## 18. 本地验证步骤

1. 执行 Maven 编译打包。
2. 启动 Nacos，确认控制台可以访问。
3. 启动四个应用，确认服务实例健康。
4. 通过 Gateway 登录并保存 token。
5. 携带 token 请求 `/trade/preview/1`。
6. 不带 token 请求同一接口，确认返回 HTTP 401。
7. 携带伪造的 `X-User-*`，确认 Gateway 覆盖伪造值。
8. 请求 `/trade/preview/999`，确认返回课程不存在提示。

## 19. PowerShell 验证命令

Nacos 服务列表应包含：

- `edu-gateway`
- `edu-user-service`
- `edu-course-service`
- `edu-trade-service`

### 登录获取 token

```powershell
$login = Invoke-RestMethod `
  -Uri "http://localhost:8080/user/login" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"username":"test","password":"123456"}'

$token = $login.data.token
```

### 正常交易预览

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/trade/preview/1" `
  -Method Get `
  -Headers @{ Authorization = "Bearer $token" } |
  ConvertTo-Json -Depth 5
```

### 不带 token

```powershell
Invoke-WebRequest `
  -Uri "http://localhost:8080/trade/preview/1" `
  -Method Get
```

预期 HTTP `401`。

### 伪造用户请求头

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/trade/preview/1" `
  -Method Get `
  -Headers @{
    Authorization = "Bearer $token"
    "X-User-Id" = "9999"
    "X-User-Name" = "hacker"
    "X-User-Role" = "ADMIN"
  } |
  ConvertTo-Json -Depth 5
```

预期仍返回 `1001/test/STUDENT`。

### 课程不存在

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/trade/preview/999" `
  -Method Get `
  -Headers @{ Authorization = "Bearer $token" } |
  ConvertTo-Json -Depth 5
```

预期返回“课程不存在”。

### course-service 停止

停止 course-service 后再次请求预览，预期 trade-service 返回“课程服务调用失败，请稍后重试”，Console 同时记录中文错误日志。本阶段不实现熔断降级。

## 20. 已验证通过的结果

本地已经验证：

- `mvn clean package -DskipTests` 执行通过
- Nacos 控制台存在 `edu-gateway`、`edu-user-service`、`edu-course-service`、`edu-trade-service`，且都有健康实例
- `POST /user/login` 可以返回 JWT
- 携带正确 token 请求 `GET /trade/preview/1`，可以返回用户和课程信息
- 不带 token 请求交易预览返回 HTTP 401
- 携带伪造请求头时，返回值仍为 JWT 中的 `1001/test/STUDENT`
- 请求 `GET /trade/preview/999` 可以返回明确的课程不存在或失败信息

本阶段没有执行压测，也没有性能指标。

## 21. IDEA Console 观察重点

Gateway：

- `JWT 鉴权成功`
- 请求路径和用户 ID

trade-service：

- `进入交易预览接口`
- `开始调用课程服务查询交易预览`
- `课程服务调用成功`
- 失败时的 `调用课程服务失败` 或 `调用课程服务异常`

course-service：

- `进入课程内部查询接口`
- `查询课程成功`
- 不存在时的 `课程不存在`

## 22. Nacos 控制台观察重点

| 服务名 | 端口 | 预期状态 |
| --- | --- | --- |
| `edu-gateway` | `8080` | 健康 |
| `edu-user-service` | `8081` | 健康 |
| `edu-course-service` | `8082` | 健康 |
| `edu-trade-service` | `8083` | 健康 |

重点检查服务名、IP、端口和健康状态。course-service 下线后，其实例应从健康实例列表中移除或变为不健康。

## 23. 常见问题和排查方式

### Feign Client 没有注册

检查 `spring-cloud-starter-openfeign`、启动类上的 `@EnableFeignClients`，以及 `CourseClient` 是否位于默认扫描包下。

### 找不到 course-service 实例

检查 Nacos 是否启动、`edu-course-service` 是否注册且健康、Feign Client 服务名是否一致，以及 LoadBalancer 依赖是否存在。

### 调用返回 404

确认 Feign Client 和提供者路径都是 `/course/internal/{courseId}`，并检查 `@PathVariable("courseId")` 名称。

### DTO 反序列化失败

检查 course-service VO 与 trade-service DTO 的字段名和类型，确认返回类型是 `Result<CourseInfoDTO>`，并确认 DTO 具有无参构造和 setter。

### 依赖版本不兼容

如果出现 `NoSuchMethodError`、类找不到或自动配置失败，检查 Spring Boot、Spring Cloud、Spring Cloud Alibaba 的版本组合。依赖应由根 POM 的 BOM 管理，不要在子模块随意写死版本。

### PowerShell 中文乱码

接口业务正常但命令行中文显示异常时，通常是 Windows PowerShell 或终端编码问题，不是代码缺陷。可以尝试：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
```

也可以使用 PowerShell 7、Windows Terminal 或 IDEA HTTP Client。

### course-service 停止后调用失败

这是当前阶段可解释的预期结果。trade-service 会记录中文失败日志并返回明确提示；本阶段没有引入 Sentinel 或熔断降级。

## 24. 当前限制

- `/course/internal/**` 只是语义上的内部接口，尚未做网络隔离或服务间认证
- 直接访问 trade-service 端口仍可能伪造 `X-User-*`，真实部署应通过网络边界限制入口
- 没有 Feign 熔断、降级和重试
- 没有继续向 course-service 透传用户上下文，因为当前查询不需要用户身份
- 当前只有一个消费者，跨服务 DTO 暂不抽到公共模块

## 25. 面试复盘要点

可以这样说明：

> Step3 在完成 Gateway 鉴权和 Nacos 服务发现后，引入 OpenFeign 跑通 trade-service 调用 course-service 的链路。Gateway 先校验 JWT，并清洗客户端伪造的用户请求头，再将可信用户上下文传给 trade-service。trade-service 通过声明式的 CourseClient 按服务名调用 edu-course-service，Nacos 提供健康实例，LoadBalancer 选择目标。course-service 暂时返回内存课程数据，trade-service 将用户和课程信息合并成交易预览结果。这个阶段刻意不接数据库、缓存、消息队列和真实订单，目的是单独验证鉴权、服务发现和服务间调用能够串联。

可以重点讲：

1. OpenFeign 与 Nacos 的职责区别。
2. 为什么使用服务名而不是固定 URL。
3. 为什么 Controller 不直接调用 Feign Client。
4. 为什么 Gateway 必须清洗客户端伪造的 `X-User-*`。
5. 为什么当前不把课程 DTO 放入 `edu-common`。

## 26. 后续 Step4 衔接方向

Step3 已经建立稳定的课程查询调用入口。后续 Step4 可以在不改变 trade-service 调用方式的前提下，逐步完善 course-service 内部实现，例如课程数据持久化和 Redis 缓存。

进入 Step4 前仍需先明确范围和方案，保持小步实现；本阶段不提前接入 MySQL、Redis 或其他组件。
