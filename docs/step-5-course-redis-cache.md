# Step5：Course Service + Redis 课程详情缓存

## 1. 本阶段目标

本阶段只在 `edu-course-service` 中接入 Redis，为单个课程详情增加 Cache Aside 旁路缓存：

```text
客户端携带 JWT
  -> Gateway 校验 JWT 并转发
  -> course-service 查询 Redis
  -> Redis 命中：直接返回课程详情
  -> Redis 未命中：查询 MySQL
  -> MySQL 查询成功：写入 Redis并设置 TTL
  -> MySQL 查询不到：写入短 TTL 空值缓存并返回课程不存在
```

Gateway JWT、Nacos Discovery、OpenFeign 和 MySQL 原链路保持不变。课程列表暂不缓存，也不实现优惠券、订单、
支付、RabbitMQ、Lua、分布式锁、Sentinel、Seata、配置中心、Docker Compose 或 JMeter。

## 2. 为什么此时接入 Redis

Step1 已经完成统一鉴权，Step2 完成服务注册发现，Step3 跑通 OpenFeign，Step4 又将课程数据稳定落到 MySQL。
此时课程详情已经有可靠数据源和完整调用链，可以只增加缓存，不必同时处理订单、优惠券或消息队列。

课程详情按唯一 ID 查询、结构固定且读多写少，是当前最适合作为第一条缓存链路的数据。这个顺序也便于单独观察
缓存命中、缓存未命中、空值缓存和故障降级，出现问题时容易判断是 Redis、MySQL 还是原微服务链路的问题。

## 3. Redis 在当前项目中的角色

Redis 是课程详情的查询加速层，不是最终数据源：

| 组件 | 当前职责 |
| --- | --- |
| MySQL | 保存权威课程数据 |
| Redis | 保存带 TTL 的课程详情副本和短期空值 |
| course-service | 负责缓存读取、MySQL 回源、缓存写入和异常降级 |
| Gateway | 继续负责 JWT 鉴权和请求转发，不直接访问 Redis |
| trade-service | 继续通过 OpenFeign 查询课程，不直接访问 Redis |

即使 Redis 数据被清空，course-service 仍然可以从 MySQL 恢复课程详情。

## 4. 当前涉及模块

### edu-course-service

- 增加 Spring Data Redis 依赖和本地连接配置
- 使用 `StringRedisTemplate` 读写字符串
- 使用 Spring Boot 管理的 `ObjectMapper` 序列化和反序列化 `CourseDTO`
- 在 Service 中实现旁路缓存、空值缓存和 Redis 异常降级

### 其他模块

- `edu-gateway`：不修改，继续执行 JWT 鉴权、请求头清洗和 `lb://` 路由
- `edu-trade-service`：不修改，继续通过 `CourseClient` 调用内部课程接口
- `edu-user-service`：不修改，继续提供登录和 JWT
- `edu-common`：不修改，继续提供统一返回、异常和 JWT 工具

## 5. 本阶段核心链路

### 课程详情链路

```text
客户端 -> Gateway JWT 鉴权 -> lb://edu-course-service
      -> CourseController -> CourseService -> Redis / CourseMapper -> MySQL
```

### 交易预览链路

```text
客户端 -> Gateway JWT 鉴权 -> lb://edu-trade-service
      -> OpenFeign + Nacos Discovery -> /course/internal/{courseId}
      -> CourseService -> Redis / MySQL
      -> trade-service 合并用户和课程信息后返回
```

外部课程详情和内部 Feign 接口最终调用同一个 Service 方法，因此共享同一份课程缓存。

## 6. 版本和新增依赖

根 POM 继续使用原有版本管理：

| 组件 | 版本 |
| --- | --- |
| Java | 17+ |
| Spring Boot | `3.3.5` |
| Spring Cloud | `2023.0.3` |
| Spring Cloud Alibaba | `2023.0.3.3` |
| Redis Docker 镜像 | `redis:7.2` |

只有 `edu-course-service` 新增：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

该依赖提供 Spring Data Redis、`StringRedisTemplate` 和默认 Lettuce 客户端，版本由 Spring Boot 管理。
Gateway、用户服务、交易服务和公共模块均不依赖 Redis。

## 7. 新增和修改文件清单

### edu-course-service

- `pom.xml`：增加 Spring Data Redis Starter
- `application.yml`：增加 Redis 地址、超时和缓存 TTL
- `dto/CourseDTO.java`：增加明确的 Jackson 构造器声明
- `service/impl/CourseServiceImpl.java`：增加缓存查询、回源、写入、空值和降级逻辑

### 文档

- `README.md`：更新 Step5 进度和本地 Redis 说明
- `docs/step-5-course-redis-cache.md`：记录设计、启动、验收、排查和面试复盘

Controller、Mapper、Mapper XML、Gateway、Feign Client 和数据库表均未修改。

## 8. 缓存设计

| 项目 | 设计 |
| --- | --- |
| key | `course:detail:{courseId}` |
| 正常 value | `CourseDTO` 的 JSON 字符串 |
| 正常 TTL | 30 分钟 |
| 空值 value | `**NULL**` |
| 空值 TTL | 1 分钟 |
| Redis 地址 | `localhost:6379`，无密码 |
| Redis 客户端 | Spring Data Redis + Lettuce |

本阶段没有使用 Spring Cache 注解，是为了在学习阶段清楚展示每一步缓存读写和故障降级逻辑。

## 9. 缓存 key 和 TTL 设计

课程详情 key：

```text
course:detail:{courseId}
```

例如 `course:detail:1`。`course` 表示业务域，`detail` 表示数据用途，最后一段是课程 ID。

正常课程缓存 30 分钟。课程基础信息变化频率较低，而且本阶段没有课程修改接口，这个时长可以明显减少重复查询，
又不会形成永久缓存。TTL 放在 `application.yml`，便于后续调整。

## 10. 空值缓存策略

MySQL 查询不到课程时，在相同 key 写入 `**NULL**`，TTL 为 1 分钟。相同无效 ID 再次访问时直接返回课程不存在，
不再查询 MySQL。空值 TTL 明显短于正常 TTL，避免课程刚创建后因为旧空值而长时间不可见。

空值缓存只能降低相同无效 ID 反复查询造成的缓存穿透，不能代替参数校验、限流或安全防护。

## 11. 缓存内容和 JSON 序列化

Redis 缓存 `CourseDTO` 的 JSON，不缓存数据库 `CourseEntity`，继续保持 Entity、DTO、VO 的职责边界。
课程价格 `price` 仍使用整数分，字段含义不变。

时间字段使用 `LocalDateTime`，由 Spring Boot 管理的 `ObjectMapper` 处理；`CourseDTO` 通过 `@JsonCreator`
和 `@JsonProperty` 明确构造参数，保证缓存 JSON 可以稳定还原。

## 12. 课程详情查询流程

```text
/course/{id} 或 /course/internal/{id}
              -> CourseService.getCourseById
              -> Redis GET course:detail:{id}
                 -> 命中 JSON：反序列化并返回
                 -> 命中 **NULL**：返回课程不存在
                 -> 未命中/Redis 异常/JSON 异常：查询 MySQL
                    -> 有数据：写 JSON，TTL 30 分钟，然后返回
                    -> 无数据：写 **NULL**，TTL 1 分钟，然后返回课程不存在
```

Redis 读取或写入失败只输出中文日志，不阻断 MySQL 主链路。缓存写入失败时仍返回已经查到的课程。

缓存逻辑全部位于 Service，Controller 没有直接操作 Redis，Mapper 仍然只负责 MySQL 数据访问。

## 13. `/course/internal/{courseId}` 是否复用缓存

内部接口与外部详情接口原本都调用 `courseService.getCourseById(courseId)`，因此缓存加入 Service 后会自然复用。
trade-service 的 Feign Client、内部接口路径和返回结构都不需要改变，`/trade/preview/{courseId}` 也能受益。

## 14. `/course/list` 为什么不加缓存

课程列表继续直接查询 MySQL。列表缓存还会涉及查询条件、课程上下线后的失效、分页和详情/列表一致性，超出当前
“单个课程详情最小缓存链路”的范围。

## 15. Redis 异常降级

- Redis 读取失败：记录中文日志，继续查询 MySQL
- JSON 反序列化失败：记录日志，查询 MySQL并尝试覆盖旧缓存
- 正常课程缓存写入失败：仍返回 MySQL 结果
- 空值缓存写入失败：仍正常返回课程不存在

Redis 是加速层，不应成为课程详情的单点故障。当前连接超时为 1 秒、命令超时为 2 秒，因此 Redis 停止时请求
可能增加短暂等待，但课程数据仍可从 MySQL 查询。

## 16. 为什么不实现分布式锁和 Lua

热点 key 同时过期时可能有多个请求回源 MySQL，但当前没有压测或真实热点流量，本阶段不增加分布式锁，避免提前
引入锁超时、释放和误删等问题。

当前 Redis 操作只是单 key 的 `GET` 和带 TTL 的 `SET`，不需要组合为多步原子操作，因此也不使用 Lua。

## 17. Step1～Step4 原链路是否变化

- Gateway JWT：未修改，课程和交易接口仍需 token
- 请求头清洗：未修改，伪造 `X-User-*` 仍会先删除再由 JWT 重建
- Nacos Discovery：服务名、注册地址和 `lb://` 路由均未修改
- OpenFeign：`CourseClient`、路径和 DTO 均未修改
- MySQL：表结构、Mapper 和 SQL 均未修改，只在详情缓存未命中时回源

## 18. Redis 和缓存配置

`edu-course-service/src/main/resources/application.yml`：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      connect-timeout: 1s
      timeout: 2s

course:
  cache:
    detail-ttl: 30m
    null-ttl: 1m
```

MySQL 数据源、Nacos Discovery、服务名和端口配置均保持不变。本阶段不启用 Redis 密码、Sentinel、Cluster
或 Nacos Config。

## 19. Redis Docker 容器

首次创建的一行命令：

```powershell
docker run -d --name redis-edu-platform -p 6379:6379 redis:7.2
```

PowerShell 多行命令：

```powershell
docker run -d --name redis-edu-platform `
  -p 6379:6379 `
  redis:7.2
```

容器已存在时：

```powershell
docker start redis-edu-platform
```

检查容器和连接：

```powershell
docker ps
docker exec redis-edu-platform redis-cli ping
```

预期 `redis-cli ping` 返回 `PONG`。如果 6379 已被占用，需要更换宿主机端口，并同步修改
course-service 的 `spring.data.redis.port`。

当前不使用 Docker Compose，也不配置 Redis 数据卷。Redis 只是缓存，即使容器重建，课程仍可从 MySQL 回源。

## 20. 编译和启动

```powershell
mvn clean package -DskipTests
docker start nacos-standalone
docker start mysql-edu-platform
docker start redis-edu-platform
docker ps
```

分别在四个终端启动，建议显式使用 `JAVA_HOME` 中的 Java 17+：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-user-service\target\edu-user-service-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-course-service\target\edu-course-service-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-trade-service\target\edu-trade-service-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME\bin\java.exe" -jar .\edu-gateway\target\edu-gateway-0.0.1-SNAPSHOT.jar
```

Nacos 控制台应继续看到 `edu-gateway`、`edu-user-service`、`edu-course-service` 和
`edu-trade-service` 四个健康实例。Redis 本身不注册到 Nacos。

## 21. PowerShell 接口验收

登录并保存 token：

```powershell
$loginBody = @{ username = 'test'; password = '123456' } | ConvertTo-Json
$loginResult = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/user/login' -ContentType 'application/json' -Body $loginBody
$token = $loginResult.data.token
$headers = @{ Authorization = "Bearer $token" }
```

正常课程缓存：

```powershell
docker exec redis-edu-platform redis-cli DEL course:detail:1
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/1' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/1' -Headers $headers
docker exec redis-edu-platform redis-cli GET course:detail:1
docker exec redis-edu-platform redis-cli TTL course:detail:1
```

第一次请求应查询 MySQL 并写缓存，第二次应命中缓存。TTL 应小于等于 1800 秒且大于 0。

空值缓存：

```powershell
docker exec redis-edu-platform redis-cli DEL course:detail:999
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/999' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/999' -Headers $headers
docker exec redis-edu-platform redis-cli GET course:detail:999
docker exec redis-edu-platform redis-cli TTL course:detail:999
```

value 应为 `**NULL**`，TTL 应小于等于 60 秒且大于 0；第二次请求不应再次执行详情 SQL。

回归原有链路：

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/list' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/internal/1' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/1' -Headers $headers
curl.exe -i http://localhost:8080/course/1
curl.exe -i http://localhost:8080/trade/preview/1
```

伪造用户头验证：

```powershell
$fakeHeaders = @{
  Authorization = "Bearer $token"
  'X-User-Id' = '9999'
  'X-User-Name' = 'hacker'
  'X-User-Role' = 'ADMIN'
}
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/1' -Headers $fakeHeaders
```

返回用户仍应为 JWT 中的 `1001/test/STUDENT`。

## 22. Redis 异常降级验证

```powershell
docker stop redis-edu-platform
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/1' -Headers $headers
docker start redis-edu-platform
```

Redis 停止后，course-service 应记录“Redis 查询课程详情缓存失败，降级查询 MySQL”，并从 MySQL 返回课程。
随后写缓存也可能失败，但不应改变接口结果。

## 23. IDEA、Nacos、MySQL 和 Redis 观察重点

### IDEA Run Console

IDEA 的 course-service Run Console 重点观察：

- `课程详情缓存未命中，开始查询 MySQL`
- `从 MySQL 查询课程成功`
- `课程详情写入 Redis 成功`
- `课程详情缓存命中`
- `课程详情空值写入 Redis 成功`
- `课程详情空值缓存命中`
- `Redis 查询课程详情缓存失败，降级查询 MySQL`
- Mapper SQL 是否只在缓存未命中时出现

Redis 检查命令：

```powershell
docker exec redis-edu-platform redis-cli --scan --pattern "course:detail:*"
docker exec redis-edu-platform redis-cli GET course:detail:1
docker exec redis-edu-platform redis-cli TTL course:detail:1
docker exec redis-edu-platform redis-cli GET course:detail:999
docker exec redis-edu-platform redis-cli TTL course:detail:999
```

验收时使用 `SCAN`，避免把 `KEYS` 作为生产环境习惯。

### Nacos 控制台

| 服务名 | 端口 | 预期状态 |
| --- | --- | --- |
| `edu-gateway` | 8080 | 健康 |
| `edu-user-service` | 8081 | 健康 |
| `edu-course-service` | 8082 | 健康 |
| `edu-trade-service` | 8083 | 健康 |

Redis 不注册到 Nacos，接入缓存不会改变 course-service 的服务名或端口。

### MySQL

第一次详情缓存未命中时应执行按 ID 查询；第二次命中缓存时不再执行对应 SQL。`/course/list` 没有缓存，
所以每次仍查询 MySQL。

## 24. 风险和限制

- Redis 未启动时会降级 MySQL，但每次请求仍会产生一次短连接等待和异常日志。
- 启动服务的 `java` 必须是 Java 17 或更高版本；如果系统 `PATH` 指向旧 JDK，应使用
  `$env:JAVA_HOME\bin\java.exe -jar ...` 或修正 IDEA Run Configuration。
- 当前没有分布式锁，热点 key 同时失效时可能有多个请求回源 MySQL。
- 当前没有课程更新接口和主动失效逻辑，只依赖 TTL；后续更新课程时应删除缓存。延迟双删可作为特定并发场景下的后续方案，本阶段不实现。
- 错误 JSON 会降级查询 MySQL，并在查到数据后覆盖原缓存。
- 空值缓存只能降低重复无效 ID 查询，不能代替参数校验、限流或安全防护。
- `/course/list` 没有缓存，每次仍访问 MySQL。
- 本阶段没有压测，也不提供性能指标。

## 25. 分层和面试复盘

- Controller：保持不变，只接收参数、调用 Service、转换 VO并返回。
- Service：新增缓存读取、MySQL 回源、缓存写入、空值和异常降级。
- Mapper：保持不变，只访问 MySQL。
- DTO：作为缓存 JSON 数据结构，不缓存 Entity。

面试时可以说明：课程详情是读多写少、按 ID 查询的稳定数据，因此先采用 Cache Aside；正常数据缓存 30 分钟，
不存在课程缓存 1 分钟以降低缓存穿透；Redis 只是加速层，失败时回源 MySQL。外部详情和 Feign 内部接口复用同一
Service 方法，所以调用契约无需改变。本阶段不实现列表缓存、分布式锁、Lua和复杂一致性，保持改动可运行、
可解释、可回滚。

可以重点讲：

1. Cache Aside 的查询顺序，以及为什么 MySQL 是权威数据源。
2. 为什么 Redis 依赖只放在 course-service。
3. 为什么缓存 DTO 而不是数据库 Entity。
4. 正常 TTL 与空值 TTL 为什么不同。
5. 缓存穿透和缓存击穿的区别。
6. Redis 异常为什么降级 MySQL，以及降级带来的延迟和数据库压力。
7. 为什么内部 Feign接口能自然复用缓存。
8. 为什么当前不缓存列表、不使用分布式锁和 Lua。

## 26. 本地实际验证结果

2026-06-29 在本地开发环境执行并确认：

- `mvn clean package -DskipTests` 通过，Maven Reactor 六个模块均为 `SUCCESS`。
- 创建并启动 `redis-edu-platform`（`redis:7.2`），`redis-cli ping` 返回 `PONG`；Nacos 和 MySQL 容器保持原配置。
- 四个服务使用 Java 21 启动，8080～8083 均正常监听，Nacos API 返回四个预期服务名。
- 清除 `course:detail:1` 后首次请求查询 MySQL并写缓存，TTL 为 1800；第二次请求日志显示缓存命中。
- 课程 999 首次请求写入 `**NULL**`，检查时 TTL 为 59；第二次请求日志显示空值缓存命中。
- `/course/list`、`/course/internal/1` 和 `/trade/preview/1` 返回成功；交易预览包含用户 `1001/test/STUDENT`、
  课程 1 和价格 19900。
- 不带 token 访问课程详情和交易预览均返回 HTTP 401；携带伪造 `X-User-*` 后仍返回 JWT 中的用户。
- Redis 停止时访问课程 2，日志显示 Redis 命令超时后降级查询 MySQL，接口仍返回成功；随后 Redis 已重新启动并返回 `PONG`。

本次没有执行压测，也没有生成性能指标。首次尝试使用系统默认 `java` 时发现其为 Java 11，不能运行 Java 17
字节码；改用 Maven 所使用的 `JAVA_HOME`（Java 21）后启动正常。

## 27. 常见问题和排查方式

### 27.1 Redis 容器未启动

```powershell
docker ps -a --filter "name=redis-edu-platform"
docker start redis-edu-platform
docker logs redis-edu-platform
docker exec redis-edu-platform redis-cli ping
```

course-service 出现连接失败或命令超时时会降级 MySQL，但仍应及时恢复 Redis，避免所有详情查询持续访问数据库。

### 27.2 6379 端口冲突

```powershell
Get-NetTCPConnection -LocalPort 6379 -State Listen
```

如果更换 Docker 宿主机端口，必须同步修改 `spring.data.redis.port`。

### 27.3 TTL 未生效

```powershell
docker exec redis-edu-platform redis-cli TTL course:detail:1
```

返回 `-1` 表示没有过期时间，`-2` 表示 key 不存在。正常课程 TTL 应在 1～1800 秒之间，空值 TTL 应在
1～60 秒之间。

### 27.4 JSON 序列化失败

检查 Redis value 是否为完整 JSON、`CourseDTO` 字段与构造参数是否一致，以及 `LocalDateTime` 是否由 Spring Boot
的 `ObjectMapper` 处理。错误 JSON 会触发降级查询 MySQL，并在查询成功后尝试覆盖旧缓存。

### 27.5 运行时 Java 版本不正确

本地实际遇到 Maven 使用 Java 21，但命令行默认 `java` 指向 Java 11。排查：

```powershell
java -version
mvn -version
$env:JAVA_HOME
```

Spring Boot 3 项目至少需要 Java 17，版本不一致时使用 `$env:JAVA_HOME\bin\java.exe` 启动。

## 28. 本阶段边界

Step5 到此只完成课程详情 Redis 旁路缓存。没有实现优惠券、订单、支付、RabbitMQ、学习进度、签到、排行榜、
Lua 秒杀、分布式锁、Sentinel、Seata、Nacos Config、Docker Compose 或 JMeter。

下一阶段必须重新确认目标后再实施，不在 Step5 中提前扩展其他业务。
