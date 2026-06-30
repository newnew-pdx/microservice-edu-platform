# Step7：Trade Service 订单创建（MySQL 事务 + requestId 接口幂等）

## 1. 本阶段目标

本阶段只实现课程订单创建：用户携带 JWT 请求 `POST /trade/order/create`，Gateway 校验 JWT、
清洗客户端伪造的 `X-User-*` 并写入可信用户上下文。trade-service 读取 `X-User-Id`，通过
OpenFeign 和 Nacos 查询 course-service，校验课程为 `ONLINE` 后，在 MySQL 本地事务中创建
`UNPAID` 订单。

```text
客户端 -> Gateway -> edu-trade-service -> OpenFeign -> edu-course-service
                                |
                                +-> MySQL edu_order
```

客户端每次下单必须提交 `requestId`。同一用户使用相同 `requestId` 重复请求时，接口返回已经创建的订单，
不重复写入 MySQL。数据库使用 `unique(user_id, request_id)` 作为并发场景下的最终幂等保障。

不实现真实支付、RabbitMQ、订单超时取消、优惠券抵扣、课程库存扣减、分布式事务、配置中心、
Docker Compose 或 JMeter。Gateway、user-service、course-service、Nacos 路由和服务名均未修改。

## 2. 为什么此时实现订单创建

Step1 已经建立 Gateway JWT 鉴权和可信用户上下文；Step2 完成 Nacos 注册与发现；Step3 跑通
trade-service 到 course-service 的 OpenFeign 调用；Step4、Step5 分别完成课程 MySQL 查询和 Redis 缓存；
Step6 为 trade-service 接入了 MySQL、MyBatis 和 Redis，并实现优惠券领取写链路。

此时实现订单创建，可以直接复用已有鉴权、路由、服务发现、课程查询和 MySQL 基础设施，把它们组合成第一条
真正的交易写链路。同时把本阶段重点控制在 MySQL 本地事务和 requestId 接口幂等两个问题上。

## 3. 订单创建在 trade-service 中的角色

| 组件 | 本阶段职责 |
| --- | --- |
| Gateway | 校验 JWT，清洗伪造的 `X-User-*`，写入可信用户上下文 |
| course-service | 提供课程 ID、标题、价格和状态，不写订单数据 |
| OpenFeign | 按 Nacos 服务名调用 course-service，不写死地址和端口 |
| trade-service | 组织幂等判断、课程校验、订单号生成、事务写入和结果返回 |
| MySQL `edu_order` | 保存订单和课程快照 |
| MySQL 唯一索引 | 保证订单号唯一，并为 requestId 幂等提供最终兜底 |

订单保存课程标题和价格快照。课程后续改名或改价，不影响已经创建的订单。

## 4. 涉及模块

### edu-trade-service

- 新增订单 Controller、Service、Mapper、Entity、DTO 和 VO
- 复用现有 `CourseClient` 和 `CourseInfoDTO`
- 复用 Step6 已有 datasource、MyBatis 和 MySQL 驱动
- 保留交易预览和优惠券领取代码

### edu-gateway

- 继续校验 JWT、清洗伪造的 `X-User-*` 并写入可信用户头
- 继续通过 `lb://edu-trade-service` 转发 `/trade/**`
- 本阶段没有修改

### edu-course-service、edu-user-service、edu-common

- course-service 继续提供课程内部查询，不写订单或修改课程
- user-service 继续提供登录
- common 继续提供统一 `Result` 和 `BizException`
- 三个模块均没有业务代码改动

## 5. 本阶段核心链路

```text
客户端 -> Gateway JWT 鉴权并清洗伪造请求头
      -> lb://edu-trade-service
      -> OrderController 校验 courseId、requestId 和 X-User-Id
      -> OrderService 按 userId + requestId 查询已有订单
      -> 未命中时通过 CourseClient 查询 course-service
      -> 校验课程为 ONLINE，组装课程和金额快照
      -> OrderTransactionService 开启 MySQL 本地事务
      -> OrderMapper 插入 edu_order
      -> 返回 OrderVO
```

并发请求同时通过创建前查询时，`uk_user_request` 只允许其中一个请求插入成功。其他请求退出失败事务后，
查询已经创建的订单并返回相同结果。

## 6. 版本和新增依赖

| 组件 | 版本 |
| --- | --- |
| Java | `17+` |
| Spring Boot | `3.3.5` |
| Spring Cloud | `2023.0.3` |
| Spring Cloud Alibaba | `2023.0.3.3` |
| MyBatis Spring Boot Starter | `3.0.3` |
| MySQL Connector/J | 由 Spring Boot 管理 |
| 本地 MySQL | `8.0` |

本阶段没有新增 Maven 依赖。复用 OpenFeign、LoadBalancer、Nacos Discovery、MyBatis 和 MySQL 驱动。
Redis 依赖继续保留给 Step6 优惠券领取，但不参与订单创建。

## 7. 新增和修改文件清单

### edu-trade-service

- `controller/OrderController.java`：订单创建入口和参数校验
- `dto/OrderCreateRequest.java`：创建订单请求体
- `entity/OrderEntity.java`：`edu_order` 数据库实体
- `vo/OrderVO.java`：订单创建和幂等命中的返回对象
- `mapper/OrderMapper.java`、`resources/mapper/OrderMapper.xml`：订单查询和插入
- `service/OrderService.java`、`service/impl/OrderServiceImpl.java`：订单创建业务
- `service/OrderTransactionService.java`、`service/impl/OrderTransactionServiceImpl.java`：订单事务写入
- `application.yml`：只调整阶段说明，没有新增配置项

### 文档

- `docs/sql/step-7-order.sql`
- `docs/step-7-order-create.md`
- `README.md`

## 8. Controller / Service / Mapper / Entity / DTO / VO 分层设计

- `OrderController`：接收请求，校验 `courseId`、`requestId` 和 `X-User-Id`。
- `OrderServiceImpl`：幂等查询、课程调用与校验、订单号和快照组装。
- `OrderTransactionServiceImpl`：独立 Spring Bean，通过 `@Transactional` 执行订单插入。
- `OrderMapper` / `OrderMapper.xml`：按幂等键查询和写入订单。
- `OrderEntity`、`OrderCreateRequest`、`OrderVO`：数据库实体、请求 DTO 和返回 VO 分离。

请求 DTO 不包含 `userId`，用户身份只能来自 Gateway 透传请求头。

## 9. datasource 和 MyBatis 配置

trade-service 继续使用 Step6 已有 datasource 和 MyBatis 配置。数据库仍为 `edu_platform`，Mapper XML 继续从
`classpath:/mapper/*.xml` 加载，并启用下划线转驼峰。

Spring Boot 根据 DataSource 自动配置事务管理器，不需要新增事务配置类。Nacos 仍只启用 Discovery，
不启用 Nacos Config。

## 10. edu_order 表设计

初始化脚本：`docs/sql/step-7-order.sql`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 订单主键 |
| `order_no` | `VARCHAR(64)` | 业务订单号 |
| `user_id` | `BIGINT` | 下单用户 ID |
| `course_id` | `BIGINT` | 课程 ID |
| `course_title` | `VARCHAR(200)` | 下单时课程标题快照 |
| `original_amount` | `INT UNSIGNED` | 课程原价，单位为分 |
| `discount_amount` | `INT UNSIGNED` | 优惠金额，本阶段固定为 0 |
| `pay_amount` | `INT UNSIGNED` | 应付金额，单位为分 |
| `status` | `VARCHAR(32)` | 本阶段只写入 `UNPAID` |
| `request_id` | `VARCHAR(128)` | 客户端幂等请求标识 |
| `created_at`、`updated_at` | `DATETIME` | 创建和更新时间 |
| `paid_at`、`closed_at` | `DATETIME NULL` | 支付和关闭时间，本阶段为空 |
| `deleted` | `TINYINT` | 逻辑删除标记，默认 0 |

金额统一使用整数分，避免浮点数精度问题。`request_id` 使用 `utf8mb4_bin`，按大小写精确比较。

## 11. edu_order 索引设计

```sql
PRIMARY KEY (id),
UNIQUE KEY uk_order_no (order_no),
UNIQUE KEY uk_user_request (user_id, request_id),
KEY idx_user_id (user_id),
KEY idx_course_id (course_id),
KEY idx_status_created (status, created_at)
```

| 索引 | 作用 |
| --- | --- |
| `uk_order_no(order_no)` | 订单号唯一兜底 |
| `uk_user_request(user_id, request_id)` | 同一用户的一次请求只创建一张订单 |
| `idx_user_id(user_id)` | 用户订单查询 |
| `idx_course_id(course_id)` | 课程维度查询 |
| `idx_status_created(status, created_at)` | 状态和创建时间查询 |

`request_id` 使用大小写敏感的 `utf8mb4_bin` 排序规则。

没有使用 `unique(user_id, course_id)`，因为用户以后可能在订单关闭、不同批次或不同交易场景下重新购买课程。

## 12. 执行 SQL

```powershell
docker cp .\docs\sql\step-7-order.sql mysql-edu-platform:/tmp/step-7-order.sql
docker exec mysql-edu-platform mysql --default-character-set=utf8mb4 -uedu_user -pedu_pass123 edu_platform -e "source /tmp/step-7-order.sql"
```

检查：

```powershell
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SHOW CREATE TABLE edu_order; SHOW INDEX FROM edu_order;"
```

脚本只创建表和索引，不插入伪造订单。订单记录必须通过真实接口创建。

## 13. 接口与业务结果

```text
POST /trade/order/create
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "courseId": 1,
  "requestId": "req-order-001"
}
```

返回 `OrderVO`，包含 orderId、orderNo、userId、课程快照、金额、状态、requestId 和 createdAt。

| code | 提示 |
| --- | --- |
| 200 | 创建成功或幂等命中 |
| 400 | 参数或用户请求头不合法 |
| 404 | 课程不存在 |
| 409 | 课程不可购买，或 requestId 已用于其他课程 |
| 503 | 课程服务或订单数据库暂不可用 |

未携带合法 JWT 时由 Gateway 返回 HTTP 401。

## 14. 请求参数和课程校验

Controller 校验请求体、正数 courseId、非空且不超过 128 字符的 requestId，以及合法的 `X-User-Id`。
请求 DTO 不提供 userId 字段。

Service 复用 `CourseClient` 调用 `/course/internal/{courseId}`。课程必须存在、返回 ID 必须一致、状态必须为
`ONLINE`，标题不能为空，价格不能为空且不能为负数。Feign 异常返回明确的 503，trade-service 不直接查询
course-service 数据库。

## 15. requestId 幂等设计

1. 创建前按 `(userId, requestId)` 查询，命中则直接返回已有订单。
2. 未命中后查询课程并尝试插入订单。
3. 并发请求同时通过前置查询时，由唯一索引保证只有一个请求插入成功。
4. 失败请求先退出并回滚插入事务，外层 Service 再查询胜出请求创建的订单。
5. 相同 requestId 如果用于不同课程，返回冲突，避免误返回另一课程的订单。

冲突后的查询不放在失败写事务中，避免受到 MySQL 旧一致性快照影响。

## 16. 为什么使用 unique(user_id, request_id)

`requestId` 表示客户端的一次业务请求。只要重试的是同一次请求，就应该得到同一订单。

不能使用 `unique(user_id, course_id)`，因为它意味着用户永远只能为某门课程创建一张订单。后续可能存在订单关闭后
重新下单、不同批次购买、续费或新的交易类型，因此幂等约束应绑定一次请求，而不是永久绑定用户和课程。

## 17. 课程快照、金额、状态与订单号

- 课程必须存在且状态为 `ONLINE`，标题和价格必须有效。
- `original_amount` 取课程价格，`discount_amount` 固定为 0，`pay_amount` 等于原价。
- 状态只创建为 `UNPAID`，`paid_at`、`closed_at` 保持空值。
- 订单号为 `ORD + yyyyMMddHHmmssSSS + userId + 8 位 UUID 随机片段`。
- 本阶段不引入分布式 ID 组件，由 `uk_order_no` 最终兜底。

订单号格式为：

```text
ORD + yyyyMMddHHmmssSSS + userId + 8 位 UUID 随机片段
```

时间部分便于排查，userId 和 UUID 片段降低冲突概率，唯一索引负责最终兜底。本阶段状态只创建为 `UNPAID`，
不提供 `PAID`、`CLOSED` 或 `CANCELLED` 状态流转接口，也不读取或核销 Step6 优惠券。

## 18. MySQL 事务边界

OpenFeign 查询位于事务外，避免远程调用占用数据库连接并拉长事务。独立的
`OrderTransactionServiceImpl.insertOrder` 公共方法只执行一次订单插入。

唯一索引异常退出事务后由外层 Service 捕获，再按 `(userId, requestId)` 查询已有订单。
本阶段不使用 Seata，也不修改 course-service 数据。

`OrderTransactionServiceImpl` 是独立 Spring Bean，事务方法为 public，并通过注入的 Bean 调用，避免同类自调用导致
`@Transactional` 失效。事务方法返回后，外层 Service 才记录“订单事务提交完成”。

## 19. 唯一索引冲突处理

捕获 `DuplicateKeyException` 后：

1. 记录“订单唯一索引冲突”中文日志；
2. 让插入异常退出失败事务；
3. 外层 Service 按 `(userId, requestId)` 再查询；
4. 查到且课程一致时，记录“幂等命中”并返回已有订单；
5. 查到但课程不一致时返回 409；
6. 查不到时按订单号冲突返回明确错误。

MySQL 默认 `REPEATABLE READ` 下，前置查询可能已建立一致性快照。如果在同一失败事务中再次普通查询，可能看不到
并发请求刚提交的订单，因此本阶段把事务插入拆到独立 Service 中。

## 20. Step1～Step6 原链路是否变化

- Gateway JWT 鉴权和伪造请求头清洗：未修改
- Nacos 服务名、注册地址和 `lb://edu-trade-service` 路由：未修改
- user-service 登录接口：未修改
- course-service MySQL 查询和 Redis 缓存：未修改
- `CourseClient`、`CourseInfoDTO` 和 `/trade/preview/{courseId}`：继续复用
- Step6 `/trade/coupon/receive/{couponId}`：未修改
- Redis：继续服务课程缓存和优惠券领取，不参与订单创建

## 21. 本阶段没有做什么

- 没有真实支付、支付回调、退款或支付流水
- 没有 RabbitMQ、延迟消息或订单超时取消
- 没有优惠券抵扣、核销、冻结或退还
- 没有课程库存或名额扣减
- 没有 Redis 幂等 token 或分布式锁
- 没有 Seata、Sentinel、XXL-JOB、SkyWalking、Prometheus
- 没有 Spring Security、Nacos Config、Docker Compose 或 JMeter
- 没有学习进度、签到、排行榜或伪造性能数据

## 22. 编译、启动与建表

```powershell
mvn clean package -DskipTests
docker start nacos-standalone
docker start mysql-edu-platform
docker start redis-edu-platform
docker ps
```

执行 Step7 SQL 后依次启动 user-service、course-service、trade-service 和 Gateway。Nacos 中应看到
`edu-gateway`、`edu-user-service`、`edu-course-service`、`edu-trade-service` 四个健康实例。

## 23. PowerShell 接口验收

```powershell
$loginBody = @{ username = 'test'; password = '123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/user/login' -ContentType 'application/json' -Body $loginBody
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }

$body = @{ courseId = 1; requestId = 'req-order-001' } | ConvertTo-Json
$first = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $headers -ContentType 'application/json' -Body $body
$second = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $headers -ContentType 'application/json' -Body $body
$first.data
$second.data
```

两次请求应返回相同 `orderId`、`orderNo`，数据库只有一条记录。新 requestId 可创建新订单：

```powershell
$body2 = @{ courseId = 1; requestId = 'req-order-002' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $headers -ContentType 'application/json' -Body $body2
```

检查表、索引和订单：

```powershell
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SHOW CREATE TABLE edu_order; SHOW INDEX FROM edu_order; SELECT * FROM edu_order WHERE user_id=1001 AND request_id='req-order-001';"
```

无 token、伪造请求头、课程不存在和空 requestId：

```powershell
curl.exe -i -X POST http://localhost:8080/trade/order/create -H "Content-Type: application/json" -d '{"courseId":1,"requestId":"req-no-token"}'

$forgedHeaders = @{ Authorization = "Bearer $token"; 'X-User-Id' = '9999'; 'X-User-Name' = 'hacker'; 'X-User-Role' = 'ADMIN' }
$forgedBody = @{ courseId = 1; requestId = 'req-order-forged' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $forgedHeaders -ContentType 'application/json' -Body $forgedBody

$missingBody = @{ courseId = 999; requestId = 'req-order-003' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $headers -ContentType 'application/json' -Body $missingBody

$invalidBody = @{ courseId = 1; requestId = '' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $headers -ContentType 'application/json' -Body $invalidBody
```

伪造头会被 Gateway 覆盖，写入用户仍应为 JWT 中的 1001。错误请求不得写入订单。

回归原链路：

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/1' -Headers $headers
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/coupon/receive/1' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/1' -Headers $headers
```

优惠券结果取决于 Step6 当前库存及用户领取状态。

## 24. IDEA、Nacos 和 MySQL 观察重点

trade-service Run Console 应观察：开始创建课程订单、开始通过 OpenFeign 查询课程、课程查询成功、订单号生成、
订单事务提交、订单创建成功、幂等命中、订单唯一索引冲突等中文日志。

Nacos 控制台应看到 Gateway、user-service、course-service、trade-service 四个健康实例。MySQL 应重点检查唯一索引、
金额、`UNPAID` 状态、空支付时间以及同一 `(user_id, request_id)` 是否只有一条记录。

## 25. 本地实际验证结果

2026-06-30 在本地开发环境实际执行并确认：

- Nacos、MySQL、Redis 三个已有容器启动成功，Step7 SQL 执行成功。
- `edu_order` 表、`uk_order_no`、`uk_user_request` 和三个普通索引均存在。
- Nacos API 返回四个服务：Gateway、user-service、course-service、trade-service。
- 登录成功，课程 1 首次下单返回 `UNPAID`，金额为 19900、0、19900，用户为 1001。
- 相同 requestId 顺序重试返回相同订单；新 requestId 可以创建新订单。
- 20 个并发请求使用相同 requestId 时全部返回同一 `orderId` 和 `orderNo`，MySQL 只有一行。
- 并发日志实际出现“订单唯一索引冲突”及“来源=唯一索引冲突”的幂等命中。
- 伪造 `X-User-Id=9999` 后，接口和 MySQL 中仍使用 JWT 用户 1001。
- 课程 999 返回业务码 404；OFFLINE 课程和 requestId 跨课程复用返回业务码 409。
- 空 requestId 返回业务码 400；不带 token 通过 Gateway 请求返回 HTTP 401。
- `/trade/preview/1`、`/course/1` 回归成功；优惠券领取接口实际返回成功。
- `mvn -pl edu-trade-service -am compile -DskipTests` 编译成功。

`mvn clean package -DskipTests` 已实际执行，但清理 `edu-common/target` 时因本地进程占用
`inputFiles.lst` 失败。随后模块 package 已完成 Java 编译和普通 JAR 生成，但 Spring Boot 重打包时已有
trade-service JAR 被占用；这些是本地文件占用问题，不计为完整 package 成功，也未擅自停止未知进程。

上述 20 并发请求只用于功能性验证唯一索引幂等，没有执行压测，也不产生吞吐量或延迟结论。

## 26. 常见问题和排查方式

### 26.1 edu_order 表不存在

```powershell
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SHOW TABLES LIKE 'edu_order';"
```

如果不存在，重新执行 `docs/sql/step-7-order.sql`。

### 26.2 Mapper XML 没有加载

检查：

- `mybatis.mapper-locations` 是否为 `classpath:/mapper/*.xml`
- `OrderMapper.xml` namespace 是否与 Mapper 全限定名一致
- XML 方法 ID 是否与接口方法名一致
- XML 是否已复制到 `target/classes/mapper`

### 26.3 创建订单返回 503

先观察 trade-service 日志，区分 OpenFeign 调用失败、MySQL 连接失败、表不存在、Mapper SQL 异常或订单号冲突。

```powershell
docker ps --filter "name=mysql-edu-platform"
Get-NetTCPConnection -LocalPort 8082,8083 -State Listen
```

### 26.4 课程不存在或不可购买

课程 999 应返回不存在，课程 3 当前为 `OFFLINE`，应返回不可购买。检查课程表：

```powershell
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SELECT id,title,price,status,deleted FROM course ORDER BY id;"
```

### 26.5 重复请求创建多条订单

确认唯一索引存在：

```powershell
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SHOW INDEX FROM edu_order WHERE Key_name='uk_user_request';"
```

检查重复数据：

```sql
SELECT user_id, request_id, COUNT(*)
FROM edu_order
GROUP BY user_id, request_id
HAVING COUNT(*) > 1;
```

### 26.6 唯一索引冲突后没有查到订单

确认插入异常已经退出 `OrderTransactionServiceImpl`，冲突后查询由外层 `OrderServiceImpl` 执行。不要把前置查询、
插入异常和冲突后查询全部放进同一个 `REPEATABLE READ` 事务。

### 26.7 @Transactional 没有生效

确认事务方法为 public、实现类由 Spring 管理，并通过注入的 Bean 调用，而不是使用 `this.insertOrder()` 同类自调用。

### 26.8 Gateway 返回 404 或连接失败

```powershell
Get-NetTCPConnection -LocalPort 8080,8083 -State Listen
```

并在 Nacos 控制台确认 Gateway 和 trade-service 健康。Step7 没有新增路由，继续复用 `/trade/**`。

### 26.9 不带 token 没有返回 401

确认请求访问 Gateway 8080，而不是直连 trade-service 8083。JWT 鉴权和可信请求头边界位于 Gateway。

### 26.10 Maven clean 无法删除 target

先在 IDEA 中正常停止占用 target 或 JAR 的服务，再执行：

```powershell
mvn clean package -DskipTests
```

不要直接强制结束来源不明的 Java 进程。

### 26.11 运行时 Java 版本不正确

```powershell
java -version
mvn -version
$env:JAVA_HOME
```

Spring Boot 3 至少需要 Java 17。

## 27. 风险和限制

- 课程查询和订单插入之间存在时间窗口，课程可能在查询后立即下线；订单保存查询时快照。
- 没有课程库存或名额扣减，因此没有解决课程超卖问题。
- 订单号随机碰撞概率很低，但仍依赖唯一索引兜底。
- requestId 由客户端生成；每次新的下单意图应使用新的 requestId。
- requestId 首尾空格会被去除，大小写按 `utf8mb4_bin` 精确区分。
- 逻辑删除订单后，`uk_user_request` 仍占用原 requestId。
- 没有真实支付和状态流转，订单会一直保持 `UNPAID`。
- 没有超时关闭、退款、对账、消息通知或补偿任务。
- 可信用户边界依赖请求经过 Gateway；直连 8083 需要部署层网络隔离。
- 没有执行压测，因此不提供吞吐量和延迟数字。

## 28. 分层和面试复盘

面试时可以说明：

> Step7 在已有 Gateway JWT、Nacos、OpenFeign 和 MySQL 基础上实现课程下单。Gateway 删除客户端伪造的
> X-User-* 请求头，再从 JWT 写入可信 userId。trade-service 先按 userId 和 requestId 查询已有订单，未命中时
> 通过 OpenFeign 查询 course-service，并校验课程为 ONLINE。远程调用放在事务外，避免拉长数据库事务；订单快照
> 在 trade-service 的 MySQL 本地事务中插入。数据库使用 unique(user_id, request_id) 作为并发幂等最终保障。
> 并发插入冲突时，失败事务先退出，再查询并返回胜出请求创建的订单。本阶段只创建 UNPAID，不引入支付、MQ、
> 优惠券抵扣或 Seata。

可以重点讲：

1. 为什么不能只依赖“先查询再插入”。
2. 为什么数据库唯一索引是幂等最终保障。
3. 为什么不用 `unique(user_id, course_id)`。
4. 为什么 OpenFeign 调用放在 MySQL 事务外。
5. 为什么唯一索引冲突后要退出失败事务再查询。
6. Spring 事务代理和同类自调用失效问题。
7. 为什么订单保存课程标题和价格快照。
8. Gateway 如何保证 `X-User-Id` 可信。
9. 当前订单号方案的优点、限制和唯一索引兜底。
10. 为什么 Step7 不引入 MQ、支付、优惠券抵扣和分布式事务。

## 29. 本阶段边界

- 只实现 `POST /trade/order/create`；
- 只创建 `UNPAID` 订单；
- 只使用 MySQL 本地事务和唯一索引幂等；
- Redis 不参与订单创建；
- 不修改 Gateway、user-service、course-service 业务代码；
- 不改变 Step3 交易预览和 Step6 优惠券领取链路；
- 不进入真实支付、RabbitMQ、订单超时取消或后续阶段。
