# Step9：Learning Service + Redis Bitmap/ZSet 学习进度、签到与排行榜

## 1. 本阶段目标

本阶段新增 `edu-learning-service`，围绕在线教育场景实现三条 Redis 业务链路：使用 Redis Hash 保存用户课程
学习进度，使用 Redis Bitmap 保存用户每月每日签到状态，使用 Redis ZSet 保存签到积分并提供排行榜。

用户仍然先通过 Gateway 登录并携带 JWT 访问 `/learning/**`。Gateway 继续校验 JWT、删除客户端伪造的
`X-User-*` 请求头，再将可信用户上下文转发给 learning-service。learning-service 注册到 Nacos，Gateway 通过
`lb://edu-learning-service` 发现实例。

本阶段不新增 MySQL 表，不新增 RabbitMQ 业务，不实现支付、优惠券抵扣、课程学习权限校验、配置中心、
Docker Compose 或压测。Step1～Step8 已有链路保持不变。

## 2. 为什么此时新增 learning-service

Step1 已经建立 Gateway JWT 鉴权和可信用户上下文；Step2 完成 Nacos 注册与发现；Step3 跑通 OpenFeign；
Step4、Step5 完成课程 MySQL 查询和 Redis 缓存；Step6 完成优惠券领取；Step7、Step8 完成订单创建、幂等和
RabbitMQ 超时关闭。

用户、课程和交易边界已经比较清晰，此时可以增加新的学习域：

- user-service 负责登录和用户信息；
- course-service 负责课程基础信息；
- trade-service 负责优惠券和订单；
- learning-service 负责学习行为、签到和积分排行。

学习进度、签到和积分不属于课程基础数据，也不属于交易订单。独立服务可以避免继续向 course-service 或
trade-service 堆叠职责，并且适合集中学习 Redis Hash、Bitmap、ZSet 和 Lua 原子操作。

## 3. Redis 在当前学习服务中的角色

| Redis 结构 | 本阶段职责 | 主要命令 |
| --- | --- | --- |
| Hash | 保存某用户某课程的完整学习进度 | `HSET`、`HGETALL` |
| Bitmap | 用一个月度 key 保存每日签到状态 | `GETBIT`、`SETBIT` |
| ZSet | 保存用户累计积分并按积分排序 | `ZINCRBY`、`ZREVRANGE`、`ZREVRANK`、`ZSCORE` |
| Lua | 原子完成签到判断、签到设置和积分增加 | `EVAL` |

本阶段 Redis 是 learning-service 的业务数据存储，不是 MySQL 的旁路缓存。Redis 数据被清空后不能从 MySQL
恢复，这是当前 Redis-only 学习方案明确接受的边界。

## 4. 涉及模块

### edu-learning-service

- 新增独立 Maven 模块和 Spring Boot 启动类；
- 使用 Spring MVC 提供学习进度、签到和排行榜接口；
- 使用 Spring Data Redis 访问 Hash、Bitmap 和 ZSet；
- 使用 Lua 保证首次签到和积分增加的原子性；
- 注册到 Nacos，服务名为 `edu-learning-service`，端口为 8084；
- 依赖 `edu-common`，复用统一返回、业务异常和健康状态常量。

### edu-gateway

- 新增 `/learning/** -> lb://edu-learning-service` 显式路由；
- 保留原有 `/user/**`、`/course/**`、`/trade/**` 路由；
- JWT 解析、白名单和伪造请求头清洗逻辑均未修改。

### edu-user-service、edu-course-service、edu-trade-service、edu-common

- user-service 继续提供登录并签发 JWT；
- course-service 继续提供课程 MySQL 查询和 Redis 缓存；
- trade-service 继续提供交易预览、优惠券、订单和超时关闭；
- common 继续提供公共类；
- 四个模块均没有业务代码改动。

## 5. 本阶段核心链路

### 学习进度链路

```text
客户端 -> Gateway JWT 鉴权并清洗伪造请求头
      -> lb://edu-learning-service
      -> LearningProgressController 校验参数并读取 X-User-Id
      -> LearningProgressService
      -> Redis Hash 写入或查询学习进度
```

### 每日签到链路

```text
客户端 -> Gateway JWT 鉴权并清洗伪造请求头
      -> lb://edu-learning-service
      -> LearningSignController 读取 X-User-Id
      -> LearningSignService 计算当前日期、月份和 offset
      -> Redis Lua 执行 GETBIT
      -> 未签到：SETBIT + ZINCRBY 10
      -> 已签到：直接返回，不增加积分
```

### 排行榜链路

```text
Top N -> ZSet 按 score 倒序查询 -> 返回 userId、points、rank
我的排名 -> ZSCORE 查询积分 + ZREVRANK 查询倒序排名 -> rank 加 1 后返回
```

## 6. 版本和新增依赖

| 组件 | 版本或来源 |
| --- | --- |
| Java | `17+` |
| Spring Boot | `3.3.5` |
| Spring Cloud | `2023.0.3` |
| Spring Cloud Alibaba | `2023.0.3.3` |
| Spring Data Redis | 由 Spring Boot 管理 |
| Redis | `7.2` 本地容器 |

`edu-learning-service` 只增加以下依赖：

- `edu-common`：复用 `Result`、`BizException` 和公共健康状态；
- `spring-boot-starter-web`：提供 Spring MVC、JSON 转换和 ControllerAdvice；
- `spring-boot-starter-data-redis`：提供 `StringRedisTemplate`、Hash、Bitmap、ZSet 和 Lua 执行能力；
- `spring-cloud-starter-alibaba-nacos-discovery`：将服务注册到 Nacos，并参与服务发现。

本模块没有 MyBatis、MySQL Driver、Spring AMQP、OpenFeign、Spring Security 或其他新组件依赖。

## 7. 新增和修改文件清单

### 根工程和 Gateway

- `pom.xml`：加入 `edu-learning-service` 模块；
- `edu-gateway/src/main/resources/application.yml`：加入 `/learning/**` 路由。

### edu-learning-service

- `pom.xml`：模块依赖和 Spring Boot 打包插件；
- `src/main/resources/application.yml`：端口、服务名、Nacos、Redis 和签到积分配置；
- `src/main/resources/lua/sign_today.lua`：签到与增加积分 Lua 脚本；
- `EduLearningServiceApplication.java`：学习服务启动类；
- `config/SignLuaConfig.java`：加载签到 Lua 脚本；
- `constant/LearningRedisKeys.java`：统一生成学习进度、签到和排行 key；
- `constant/SignLuaResultCode.java`：统一保存 Lua 返回码；
- `controller/HealthController.java`：学习服务健康检查；
- `controller/LearningProgressController.java`：学习进度参数校验和返回；
- `controller/LearningSignController.java`：今日签到和月签到参数校验；
- `controller/LearningRankController.java`：Top N 和个人排名参数校验；
- `dto/ProgressUpdateRequest.java`：学习进度更新请求 DTO；
- `service/LearningProgressService.java` 及实现类：Hash 进度业务；
- `service/LearningSignService.java` 及实现类：Bitmap 和签到 Lua 业务；
- `service/LearningRankService.java` 及实现类：ZSet 排行业务；
- `vo/LearningProgressVO.java`：学习进度返回；
- `vo/SignTodayVO.java`：今日签到返回；
- `vo/SignDayVO.java`、`MonthlySignVO.java`：月签到明细返回；
- `vo/RankItemVO.java`、`MyRankVO.java`：排行榜返回；
- `handler/GlobalExceptionHandler.java`：统一处理业务异常、缺少请求头、参数格式和未预期异常。

### 文档

- `docs/step-9-learning-redis.md`；
- `README.md`。

## 8. Controller / Service / DTO / VO / Config 分层设计

- Controller 只接收请求、读取 `X-User-Id`、校验参数并返回 `Result`；
- Service 负责日期计算、进度字段构造、Redis 命令、Lua 结果判断和 VO 组装；
- Config 只加载 Lua 脚本，不处理签到业务；
- DTO 只承载请求参数，不作为接口响应；
- VO 按进度、签到和排行场景分别定义，不直接返回 Redis 数据结构；
- Handler 统一隐藏 Redis 异常细节，对外返回明确错误；
- 启动类只启动 Spring Boot，不承载业务逻辑。

Controller 中没有 `StringRedisTemplate`、`DefaultRedisScript` 或 Redis 命令，业务没有堆在 Controller、配置类或
启动类中。本阶段数据访问较简单，Redis 操作直接位于对应 Service，没有为了形式额外增加 Repository 层。

## 9. Gateway 路由设计

Gateway 继续使用手写显式路由，未启用 Discovery Locator 自动暴露服务路径。新增配置：

```yaml
- id: edu-learning-service
  uri: lb://edu-learning-service
  predicates:
    - Path=/learning/**
```

learning-service 的 Controller 直接声明完整 `/learning/**` 路径，因此不使用 `StripPrefix`。如果 Nacos 中没有健康的
`edu-learning-service` 实例，Gateway 无法完成负载均衡转发。

## 10. Nacos 注册和 application.yml

```yaml
server:
  port: 8084

spring:
  application:
    name: edu-learning-service
  data:
    redis:
      host: localhost
      port: 6379
      connect-timeout: 1s
      timeout: 2s
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848

learning:
  sign:
    points: 10
```

本阶段只使用 Nacos Discovery，不引入 `spring.config.import`、Nacos Config、共享配置或动态配置刷新。

## 11. Gateway 用户上下文和安全边界

用户通过 `POST /user/login` 获取 JWT，访问 `/learning/**` 时携带 `Authorization: Bearer <token>`。
Gateway 的 `JwtAuthGlobalFilter` 继续执行：

1. 校验 JWT；
2. 删除客户端传入的 `X-User-Id`、`X-User-Name`、`X-User-Role`；
3. 从 JWT 解析可信用户信息；
4. 重新写入 `X-User-*`；
5. 转发到 learning-service。

learning-service 从 `X-User-Id` 获取当前用户，不接收请求体中的 userId。服务自身不重复解析 JWT，也没有引入
Spring Security。可信边界依赖请求经过 Gateway，因此部署时不能把 8084 直接暴露给不可信客户端。

## 12. Redis key 设计

| 场景 | Key | 类型 |
| --- | --- | --- |
| 学习进度 | `learning:progress:{userId}:{courseId}` | Hash |
| 月签到 | `learning:sign:{userId}:{yyyyMM}` | Bitmap |
| 积分排行榜 | `learning:rank:points` | ZSet |

key 由 `LearningRedisKeys` 集中管理。学习进度和月签到按用户隔离，进度继续按课程隔离；排行榜使用一个全局 ZSet。
本阶段没有设置 TTL，便于持续查询进度、历史月份和累计积分。

## 13. Redis Hash 学习进度设计

学习进度 key 为 `learning:progress:{userId}:{courseId}`，字段如下：

| 字段 | 含义 |
| --- | --- |
| `courseId` | 课程 ID |
| `progressPercent` | 进度百分比，范围 0～100 |
| `learnedSeconds` | 累计学习秒数，不能小于 0 |
| `completed` | `progressPercent == 100` 时为 true |
| `updatedAt` | 按 Asia/Shanghai 生成的更新时间 |

同一用户同一课程的更新使用相同 key，新的合法请求覆盖旧字段。本阶段不限制进度回退，也没有版本号、CAS 或学习时长
累加逻辑，因此客户端上传 35% 后再上传 20%，最终会保存 20%。

## 14. 学习进度更新接口

```text
POST /learning/progress/update
```

请求体：

```json
{
  "courseId": 1,
  "progressPercent": 35,
  "learnedSeconds": 1200
}
```

Controller 校验请求体不能为空、courseId 为正数、progressPercent 在 0～100、learnedSeconds 不小于 0，且
`X-User-Id` 能转换为正数。Service 使用一次 Hash 多字段写入保存进度，并返回写入后的 VO。本接口不查询
course-service，不校验课程是否存在，也不判断用户是否购买课程。

## 15. 学习进度查询接口

```text
GET /learning/progress/{courseId}
```

Service 使用 `HGETALL` 对应操作读取 Hash 并转换字段类型。查询成功时返回 userId、courseId、progressPercent、
learnedSeconds、completed 和 updatedAt。key 不存在时返回业务码 404 和“学习进度不存在”，不伪造默认进度。

Hash 字段被手工删除或写入无法转换的值时，记录中文错误日志并返回“学习进度数据异常”。本接口不查询 MySQL，
也不调用 course-service。

## 16. Redis Bitmap 月签到设计

每个用户每个月使用一个 Bitmap：

```text
learning:sign:{userId}:{yyyyMM}
```

offset 计算：

```text
offset = dayOfMonth - 1
```

| 日期 | offset |
| --- | --- |
| 每月 1 日 | 0 |
| 每月 2 日 | 1 |
| 每月 31 日 | 30 |

签到只有“未签到”和“已签到”两个状态，Bitmap 比保存每日字符串、Set 成员或独立 key 更紧凑。月份进入 key 后，
不同月份的数据自然隔离。

## 17. 今日签到接口

```text
POST /learning/sign/today
```

Service 使用 `Asia/Shanghai` 计算当前日期，生成 `yyyyMM` 和 `dayOfMonth - 1`。首次签到时设置 Bitmap 并给排行榜
增加 10 分；重复签到不增加积分。

| 返回字段 | 含义 |
| --- | --- |
| `signed` | 今日是否已经处于签到状态 |
| `firstSign` | 本次请求是否完成首次签到 |
| `pointsAdded` | 本次增加积分，首次为 10，重复为 0 |
| `totalPoints` | 当前累计积分 |
| `signDate` | 本次签到日期 |

重复签到仍返回 `signed=true`，表示今天已经签到；通过 `firstSign=false` 和 `pointsAdded=0` 表示本次没有重复奖励。

## 18. 签到 Lua 脚本设计

Lua 脚本位于 `resources/lua/sign_today.lua`，由 `SignLuaConfig` 加载为 `DefaultRedisScript<Long>`。

```text
KEYS[1] = learning:sign:{userId}:{yyyyMM}
KEYS[2] = learning:rank:points
ARGV[1] = dayOfMonth - 1
ARGV[2] = userId
ARGV[3] = 10
```

脚本：

```lua
local signed = redis.call('GETBIT', KEYS[1], ARGV[1])
if signed == 1 then
    return 1
end

redis.call('SETBIT', KEYS[1], ARGV[1], 1)
redis.call('ZINCRBY', KEYS[2], ARGV[3], ARGV[2])
return 0
```

- 返回 `0`：首次签到，Bitmap 已设置，积分已增加；
- 返回 `1`：今日已经签到，没有修改积分。

## 19. 为什么签到必须保证原子性

如果 Java 依次执行 `GETBIT`、`SETBIT` 和 `ZINCRBY`，两个并发请求可能同时读到未签到，然后都增加 10 分。
Bitmap 最终虽然只有一个 bit，但 ZSet 可能错误增加 20 分。

Redis 原子执行整段 Lua，脚本执行期间不会插入另一个签到命令，因此只有第一个请求可以执行 `ZINCRBY`。如果脚本
已经执行成功但客户端在收到响应前超时，重试请求会读到 bit=1，也不会重复增加积分。

本阶段不使用分布式锁。Lua 已经足够解决单机 Redis 中这三个命令的原子性问题，并且链路更短、边界更清晰。

## 20. 月签到查询接口

```text
GET /learning/sign/month?month=yyyyMM
```

- `month` 可以省略，省略时按 Asia/Shanghai 使用当前月份；
- 传入时必须是合法的 6 位 `yyyyMM`；
- 使用 `YearMonth.lengthOfMonth()` 处理 28、29、30、31 天；
- 从 offset 0 开始逐日执行 `GETBIT`；
- 返回整个月每天的 `day` 和 `signed`；
- 同时返回 `signCount`。

当前实现最多执行 31 次 `GETBIT`，目的是保持 Bitmap offset 与每日返回列表的关系直观。当前学习项目数据量很小，
没有为减少命令次数引入复杂的字节解析。

## 21. Redis ZSet 积分排行榜设计

排行榜 key 为 `learning:rank:points`。member 使用字符串形式的 userId，score 使用用户累计积分。当前积分只来自
每日首次签到，每次增加 10 分；学习进度更新不增加积分。

本阶段不保存用户名、积分流水、积分来源明细、积分有效期或等级。Top N 只返回 userId，不为补充用户名调用
user-service。

## 22. 排行榜 Top N 接口

```text
GET /learning/rank/top?limit=10
```

- `limit` 省略时默认为 10；
- 合法范围为 1～100；
- 使用 `reverseRangeWithScores`，对应积分从高到低查询；
- 返回 userId、points 和从 1 开始的 rank。

不能使用普通升序 `ZRANGE`，否则会把积分最低的用户排在前面。本阶段按查询结果顺序分配 1、2、3……名。

## 23. 我的排名接口

```text
GET /learning/rank/me
```

Service 先使用 `ZSCORE` 查询当前用户积分，再使用 `ZREVRANK` 查询倒序排名。Redis 的 rank 从 0 开始，接口返回时
加 1 转换为用户习惯的第 1 名、第 2 名。

用户从未签到、ZSet 中没有 member 时返回：

```json
{
  "userId": 1001,
  "points": 0,
  "rank": null,
  "ranked": false
}
```

## 24. Redis 异常处理和中文日志

学习进度写入、查询，签到 Lua，月签到和排行查询均捕获 Redis 运行时异常，记录包含 userId、courseId、月份或 limit
等定位信息的中文错误日志，并转换为明确的业务异常。对外不暴露 Redis 主机、连接堆栈或内部命令细节。

主要中文日志包括：学习进度更新/查询成功、未查询到进度、Redis 访问失败、首次签到并增加积分、重复签到不增加积分、
月签到查询、积分 Top N 查询、个人排名查询。日志不输出 JWT。

## 25. Step1～Step8 原链路是否变化

- Gateway JWT 校验和伪造请求头清洗：未修改；
- user-service 登录和用户信息：未修改；
- course-service MySQL 查询和 Redis 缓存：未修改；
- trade-service OpenFeign 交易预览：未修改；
- Step6 优惠券领取：未修改；
- Step7 订单创建和 requestId 幂等：未修改；
- Step8 RabbitMQ 订单超时关闭：未修改；
- MySQL 表、SQL 和 RabbitMQ 拓扑：未修改；
- edu-common：没有代码改动。

Gateway 只增加 learning-service 路由，根 POM 只增加一个模块。

## 26. 本阶段没有做什么

- 没有新增 MySQL 表、学习进度落库或 Redis/MySQL 双写；
- 没有通过 MQ 异步同步学习进度；
- 没有新增 RabbitMQ Exchange、Queue、Producer 或 Consumer；
- 没有支付、退款、优惠券抵扣或订单新功能；
- 没有校验课程是否存在、是否在线、用户是否购买或是否有学习权限；
- 没有完成课程后加积分；
- 没有补签、连续签到奖励、积分流水、等级或积分过期；
- 没有分布式锁；
- 没有 Sentinel、Seata、XXL-JOB、SkyWalking、Prometheus；
- 没有 Spring Security、Nacos Config、Docker Compose 或 JMeter；
- 没有伪造吞吐量、延迟、压测数据或线上数据。

## 27. 编译和启动

启动本地依赖并检查：

```powershell
docker start nacos-standalone
docker start mysql-edu-platform
docker start redis-edu-platform
docker start rabbitmq-edu-platform
docker ps
```

完整构建：

```powershell
mvn clean package -DskipTests
```

依次启动服务：

```powershell
java -jar edu-user-service/target/edu-user-service-0.0.1-SNAPSHOT.jar
java -jar edu-course-service/target/edu-course-service-0.0.1-SNAPSHOT.jar
java -jar edu-trade-service/target/edu-trade-service-0.0.1-SNAPSHOT.jar
java -jar edu-learning-service/target/edu-learning-service-0.0.1-SNAPSHOT.jar
java -jar edu-gateway/target/edu-gateway-0.0.1-SNAPSHOT.jar
```

Spring Boot 3.3.5 至少需要 Java 17。如果 PATH 中的 `java` 不是 Java 17+，应使用正确 JDK 的绝对路径或修正本地
`JAVA_HOME` 和 PATH 后再启动。

## 28. PowerShell 接口验收

### 28.1 登录并准备请求头

```powershell
$loginBody = @{ username = 'test'; password = '123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/user/login' -ContentType 'application/json' -Body $loginBody
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }
```

### 28.2 更新和查询学习进度

```powershell
$progressBody = @{ courseId = 1; progressPercent = 35; learnedSeconds = 1200 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/learning/progress/update' -Headers $headers -ContentType 'application/json' -Body $progressBody
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/learning/progress/1' -Headers $headers
```

返回的 `progressPercent` 应为 35，`learnedSeconds` 应为 1200，`completed` 应为 false。

### 28.3 今日首次签到和重复签到

```powershell
$firstSign = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/learning/sign/today' -Headers $headers
$repeatSign = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/learning/sign/today' -Headers $headers
$firstSign.data
$repeatSign.data
```

清空对应测试 key 后的首次请求应返回 `firstSign=true`、`pointsAdded=10`；同日第二次应返回
`firstSign=false`、`pointsAdded=0`，总积分不变。不要删除不属于当前测试用户的数据。

### 28.4 月签到和排行榜

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/learning/sign/month' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/learning/sign/month?month=202607' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/learning/rank/top?limit=10' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/learning/rank/me' -Headers $headers
```

### 28.5 无 token 和伪造请求头

```powershell
try {
  Invoke-WebRequest -Uri 'http://localhost:8080/learning/rank/me' -ErrorAction Stop
} catch {
  $_.Exception.Response.StatusCode.value__
}

$forgedHeaders = @{
  Authorization = "Bearer $token"
  'X-User-Id' = '9999'
  'X-User-Name' = 'hacker'
  'X-User-Role' = 'ADMIN'
}

Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/learning/progress/update' -Headers $forgedHeaders -ContentType 'application/json' -Body $progressBody
```

无 token 应返回 HTTP 401。伪造请求头后，响应和 Redis key 中的用户仍应来自 JWT，而不是 9999。

### 28.6 Step1～Step8 回归

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/1' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/1' -Headers $headers

$orderBody = @{ courseId = 1; requestId = 'step9-regression-001' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $headers -ContentType 'application/json' -Body $orderBody
```

## 29. Redis、Nacos 和 IDEA 观察重点

### Redis Hash

```powershell
docker exec redis-edu-platform redis-cli HGETALL learning:progress:1001:1
```

### Redis Bitmap

以 2026-07-01 为例，月份为 202607，offset 为 0：

```powershell
docker exec redis-edu-platform redis-cli GETBIT learning:sign:1001:202607 0
docker exec redis-edu-platform redis-cli BITCOUNT learning:sign:1001:202607
docker exec redis-edu-platform redis-cli KEYS learning:sign:1001:*
```

### Redis ZSet

```powershell
docker exec redis-edu-platform redis-cli ZSCORE learning:rank:points 1001
docker exec redis-edu-platform redis-cli ZREVRANGE learning:rank:points 0 9 WITHSCORES
docker exec redis-edu-platform redis-cli ZREVRANK learning:rank:points 1001
```

Nacos 应看到 Gateway、user-service、course-service、trade-service 和 learning-service 五个健康实例。IDEA 中应观察
进度更新、查询、首次签到、重复签到、积分增加、月签到、排行和 Redis 异常等中文日志。

## 30. 本地实际验证结果

2026-07-01 在本地开发环境实际执行并确认：

- Nacos、MySQL、Redis、RabbitMQ 四个已有容器启动成功；
- `mvn clean package -DskipTests` 七模块完整构建成功；
- 8080～8084 五个端口均进入监听；
- Nacos API 返回 Gateway、user-service、course-service、trade-service、learning-service 五个服务；
- 登录成功，`/learning/health` 返回 `edu-learning-service`、`UP`、8084；
- 用户 1001 更新课程 1 进度成功，查询返回 35% 和 1200 秒；
- Redis Hash 实际包含五个预期字段；
- 当日首次签到增加 10 分；同日第二次签到增加 0 分，总积分仍为 10；
- 月签到返回 `202607`、31 个每日状态、签到天数 1、当天为已签到；
- Top N 和个人排名均返回用户 1001、10 分、第 1 名；
- Redis 中 offset 0 为 1、`BITCOUNT` 为 1、`ZSCORE` 为 10、`ZREVRANK` 为 0；
- 不带 token 返回 HTTP 401；伪造 `X-User-Id=9999` 后仍使用 JWT 用户 1001；
- `/course/1`、`/trade/preview/1` 和 `/trade/order/create` 回归成功；
- learning-service 实际输出进度、签到和排行中文日志。

本机 PATH 中默认 `java` 为 Java 11，直接运行 JAR 会出现 class file version 61 错误。本次运行验收使用
`D:\java\jdk\bin\java.exe`，版本为 Java 21。本阶段没有执行压测，以上请求只用于功能验收。

## 31. 常见问题和排查方式

### 31.1 Gateway 返回 404 或找不到实例

检查 Gateway 是否有 `/learning/**` 路由、是否已重启，并确认 Nacos 中 `edu-learning-service` 实例健康。路由中的
服务名必须与 `spring.application.name` 完全一致。

### 31.2 learning-service 启动失败或接口返回 503

```powershell
docker ps --filter "name=redis-edu-platform"
docker exec redis-edu-platform redis-cli PING
Get-NetTCPConnection -LocalPort 6379,8084 -State Listen
```

### 31.3 签到日期整体偏移一天

确认 offset 使用 `dayOfMonth - 1`。每月 1 日必须写 offset 0，不能直接使用 dayOfMonth。

### 31.4 重复签到重复增加积分

确认签到通过 Lua 一次执行 `GETBIT`、`SETBIT` 和 `ZINCRBY`，没有在 Java 中先判断再单独增加积分。

### 31.5 排行榜顺序或排名错误

Top N 必须使用倒序查询。Redis `ZREVRANK` 从 0 开始，接口返回前必须加 1。

### 31.6 月份参数错误

月份必须是合法六位 `yyyyMM`，例如 `202607`。`202613`、`2026-07` 应返回参数错误。

### 31.7 伪造用户头被使用

确认请求访问 Gateway 8080，而不是直连 learning-service 8084。可信用户头清洗位于 Gateway。

### 31.8 Redis Cluster 提示跨槽

本阶段使用单机 Redis。Lua 同时访问月签到 key 和全局排行榜 key，迁移 Cluster 时需要使用 hash tag 或重新设计分片。

### 31.9 Maven clean 无法删除 target

先在 IDEA 中正常停止占用 target 或 JAR 的服务，再执行构建，不要直接强制结束来源不明的 Java 进程。

### 31.10 运行时 Java 版本不正确

```powershell
java -version
mvn -version
$env:JAVA_HOME
```

出现 `class file version 61` 说明运行时低于 Java 17。

## 32. 风险和限制

- Redis 是唯一学习数据源，数据丢失后无法从 MySQL 恢复；
- 学习进度允许回退，没有版本控制、requestId 或并发冲突检测；
- 不校验课程是否存在、是否在线或用户是否有学习权限；
- 签到只按服务端 Asia/Shanghai 日期处理；
- 月签到 key、进度 key 和排行榜没有 TTL；
- 月查询最多执行 31 次 Redis 命令；
- 同分用户由 Redis member 排序规则决定先后，不实现并列排名；
- 排行榜只返回 userId，不返回用户名；
- 没有积分流水，无法审计每次积分变化；
- Lua 多 key 设计面向单机 Redis，未适配 Cluster hash slot；
- Redis 不可用时接口直接失败，没有 MySQL 降级；
- 可信用户边界依赖 Gateway，直连 8084 需要网络隔离；
- 没有执行压测，因此不提供吞吐量和延迟数字。

## 33. 分层和面试复盘

面试时可以说明：

> Step9 在用户、课程和交易服务边界稳定后新增 learning-service。Gateway 继续负责 JWT 鉴权并清洗客户端伪造的
> X-User-* 请求头，learning-service 只读取可信 X-User-Id。学习进度是包含多个字段的小对象，因此使用 Hash；
> 每日签到只有两种状态，因此按用户和月份使用 Bitmap；积分需要累计并按分数排序，因此使用 ZSet。签到同时涉及
> GETBIT、SETBIT 和 ZINCRBY，分开执行会在并发下重复加分，所以使用 Lua 将三个动作放进一次原子执行。Top N
> 使用倒序范围查询，个人排名使用 ZREVRANK 并加 1。本阶段刻意不增加 MySQL 和 MQ，先把 Redis 数据结构和
> 原子性讲清楚，代价是数据恢复、积分流水和 Cluster 适配仍未解决。

可以重点讲：为什么独立学习服务、三种 Redis 结构的选择、Bitmap offset、Lua 原子性、ZSet 倒序排名、Redis-only
的数据恢复风险、Gateway 可信用户边界，以及单机 Lua 迁移 Redis Cluster 时的 hash slot 问题。

## 34. 本阶段边界

- 只新增 `edu-learning-service` 和 Gateway `/learning/**` 路由；
- 只实现学习进度、今日签到、月签到、Top N 和个人排名；
- 只使用 Redis Hash、Bitmap、ZSet 和一段签到 Lua；
- 只通过每日首次签到增加 10 分；
- MySQL 和 RabbitMQ 不参与 Step9 新业务；
- 不修改 user-service、course-service、trade-service 和 common 业务代码；
- 不改变 Step1～Step8 已有链路；
- 不实现课程权限、支付、优惠券抵扣、积分流水、配置中心或压测；
- 不进入 Step10。
