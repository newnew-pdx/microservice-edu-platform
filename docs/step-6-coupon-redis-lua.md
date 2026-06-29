# Step6：Trade Service 优惠券领取（Redis + Lua + MySQL 唯一索引）

## 1. 本阶段目标

本阶段只在 `edu-trade-service` 实现优惠券领取：

```text
客户端 -> Gateway JWT 鉴权 -> edu-trade-service
      -> MySQL 校验 coupon
      -> Redis Lua 原子领取
      -> MySQL 插入 user_coupon
      -> 返回统一结果
```

不实现订单、支付、RabbitMQ、超时取消、学习进度、排行榜、分布式事务、配置中心、
Docker Compose 或 JMeter。Gateway、Nacos、OpenFeign 和 course-service 原有链路不变。

## 2. 为什么此时实现优惠券领取

Step1 已经建立 Gateway JWT 鉴权和可信用户上下文；Step2 完成 Nacos 注册发现；Step3 跑通
trade-service 到 course-service 的 OpenFeign 调用；Step4 和 Step5 分别验证了 MySQL 与 Redis。

此时实现优惠券领取，可以在不引入订单和支付的前提下，把鉴权、服务路由、MySQL、Redis 和高并发原子操作组合成
一条完整写链路。优惠券领取的范围比订单小，但能够清楚演示库存竞争、重复领取和 Redis/MySQL 双写一致性问题，
适合作为 Step6。

## 3. Redis、Lua 和 MySQL 的职责

| 组件 | 本阶段职责 |
| --- | --- |
| Redis | 保存实时库存和已领取用户集合，承担并发入口控制 |
| Lua | 把多条 Redis 命令组合成不可被其他命令穿插的原子操作 |
| MySQL `coupon` | 保存优惠券基础信息、状态、时间和库存初始化基准 |
| MySQL `user_coupon` | 保存最终领取记录 |
| MySQL 唯一索引 | Redis 防重失效时，作为最终重复领取兜底 |
| trade-service | 组织校验、Lua 执行、MySQL 写入和失败补偿 |

Redis 在 Step5 中是可以降级的课程查询缓存；在 Step6 中则是优惠券并发控制入口。Redis 异常时不能绕过 Lua
直接插入 MySQL，否则会改变库存和防重规则，因此接口会返回“领取服务暂不可用”。

## 4. 涉及模块

### edu-trade-service

- 增加 MyBatis、MySQL 驱动和 Spring Data Redis
- 配置 datasource、Redis 和 MyBatis
- 新增优惠券 Controller、Service、Mapper、Entity、DTO 和 VO
- 从 `resources/lua` 加载领取和补偿脚本
- 保留原有 OpenFeign 交易预览代码

### edu-gateway

- 继续校验 JWT、清洗客户端伪造的 `X-User-*` 并写入可信用户头
- 继续通过 `lb://edu-trade-service` 转发 `/trade/**`
- 本阶段没有修改

### edu-user-service、edu-course-service、edu-common

- user-service 继续提供登录
- course-service 的 MySQL 查询和 Redis 缓存保持不变
- common 继续提供统一 `Result` 和 `BizException`
- 三个模块均没有业务代码改动

## 5. 本阶段核心链路

```text
客户端 -> Gateway JWT 鉴权并清洗伪造请求头
      -> lb://edu-trade-service
      -> CouponController 校验 couponId 和 X-User-Id
      -> CouponService 查询并校验 MySQL coupon
      -> Redis Lua 原子判断重复领取和库存
      -> Redis 扣减库存并记录领取用户
      -> MySQL 插入 user_coupon
      -> 返回 CouponReceiveVO
```

MySQL 写入失败时执行 `rollback_coupon.lua`，原子移除本次用户标记并恢复库存，然后返回明确失败结果。

## 6. 版本和新增依赖

| 组件 | 版本 |
| --- | --- |
| Java | `17+` |
| Spring Boot | `3.3.5` |
| Spring Cloud | `2023.0.3` |
| Spring Cloud Alibaba | `2023.0.3.3` |
| MyBatis Spring Boot Starter | `3.0.3` |
| MySQL Connector/J、Spring Data Redis | 由 Spring Boot 管理 |
| 本地 MySQL、Redis | `8.0`、`7.2` |

仅 `edu-trade-service` 新增：

| 依赖 | 作用 |
| --- | --- |
| `spring-boot-starter-data-redis` | 提供 `StringRedisTemplate`、Lettuce 和 Lua 执行能力 |
| `mybatis-spring-boot-starter` | 集成 MyBatis、Mapper、XML SQL 和 Spring JDBC |
| `mysql-connector-j` | 提供 MySQL JDBC 驱动，使用 runtime scope |

原有 OpenFeign、Nacos Discovery 和 LoadBalancer 依赖全部保留。

## 7. 新增和修改文件清单

### edu-trade-service

- `pom.xml`：增加 Redis、MyBatis 和 MySQL 驱动
- `application.yml`：增加 datasource、Redis、MyBatis 和 Mapper 日志配置
- `controller/CouponController.java`：优惠券领取入口
- `service/CouponService.java`、`service/impl/CouponServiceImpl.java`：领取业务和实现
- `mapper/CouponMapper.java`、`mapper/UserCouponMapper.java`：MySQL 数据访问
- `resources/mapper/CouponMapper.xml`、`UserCouponMapper.xml`：查询和插入 SQL
- `entity/CouponEntity.java`、`entity/UserCouponEntity.java`：数据库实体
- `dto/CouponReceiveDTO.java`、`vo/CouponReceiveVO.java`：业务参数和接口返回
- `constant/CouponRedisKeys.java`、`CouponLuaResultCode.java`：Redis key 和 Lua 返回码
- `config/CouponLuaConfig.java`：Lua 脚本配置
- `resources/lua/receive_coupon.lua`、`rollback_coupon.lua`：领取和补偿脚本
- `handler/GlobalExceptionHandler.java`：补充请求头和未知异常处理

### 文档

- `docs/sql/step-6-coupon.sql`
- `docs/step-6-coupon-redis-lua.md`
- `README.md`

## 8. Controller / Service / Mapper / Entity / DTO / VO 分层设计

- `CouponController`：读取路径参数和 Gateway 写入的 `X-User-Id`，完成基础参数校验。
- `CouponService`：校验优惠券活动、执行 Lua、处理返回码、写 MySQL 和失败补偿。
- `CouponMapper`：查询 `coupon`。
- `UserCouponMapper`：插入 `user_coupon`。
- `CouponEntity`、`UserCouponEntity`：数据库实体。
- `CouponReceiveDTO`：Controller 传入 Service 的领取参数。
- `CouponReceiveVO`：领取成功返回数据。
- `CouponLuaConfig`：从 `resources/lua` 加载脚本。

Controller 不接收客户端提交的 userId。用户 ID 只能来自 Gateway 校验 JWT 后写入的请求头。

## 9. datasource、Redis 和 MyBatis 配置

`edu-trade-service` 新增：

- `spring-boot-starter-data-redis`：提供 `StringRedisTemplate`、Lettuce 和 Lua 执行能力。
- `mybatis-spring-boot-starter:3.0.3`：提供 Mapper 与 XML SQL 集成。
- `mysql-connector-j`：MySQL JDBC 驱动。

数据库继续使用 `localhost:3307/edu_platform`，Redis 继续使用 `localhost:6379`。Nacos 仍只启用
Discovery，不启用 Nacos Config。

## 10. MySQL 表设计

初始化脚本：`docs/sql/step-6-coupon.sql`。

### coupon

保存优惠券名称、总库存、库存基准值、优惠金额、活动状态和领取时间范围。`amount` 单位为分。
领取接口按主键查询，并排除 `deleted=1` 的数据。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` | 优惠券主键 |
| `name` | `VARCHAR(100)` | 优惠券名称 |
| `total_stock` | `INT UNSIGNED` | 初始总库存 |
| `stock` | `INT UNSIGNED` | MySQL 库存初始化基准 |
| `amount` | `INT UNSIGNED` | 优惠金额，单位为分 |
| `status` | `VARCHAR(20)` | `NOT_STARTED`、`ONGOING`、`ENDED`、`DISABLED` |
| `start_time`、`end_time` | `DATETIME` | 领取开始和结束时间 |
| `created_at`、`updated_at` | `DATETIME` | 创建和更新时间 |
| `deleted` | `TINYINT` | 逻辑删除标记 |

本阶段实际并发库存由 Redis 扣减，领取成功后只插入 `user_coupon`，不再同步扣减
`coupon.stock`。因此 `coupon.stock` 是 Redis 首次初始化的基准值，不应把它理解成实时剩余库存。
这项双写简化是 Step6 的明确边界。

### user_coupon

保存领取用户、优惠券、状态和领取时间。唯一索引：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 领取记录主键 |
| `user_id` | `BIGINT` | 用户 ID |
| `coupon_id` | `BIGINT` | 优惠券 ID |
| `status` | `VARCHAR(20)` | `UNUSED`、`USED`、`EXPIRED` |
| `received_at` | `DATETIME` | 领取时间 |
| `used_at` | `DATETIME NULL` | 使用时间，本阶段为空 |
| `created_at`、`updated_at` | `DATETIME` | 创建和更新时间 |
| `deleted` | `TINYINT` | 逻辑删除标记 |

```sql
UNIQUE KEY uk_coupon_user (coupon_id, user_id)
```

即使 Redis 集合丢失，MySQL 仍拒绝同一用户重复插入同一优惠券记录。

## 11. 执行 SQL

```powershell
docker cp .\docs\sql\step-6-coupon.sql mysql-edu-platform:/tmp/step-6-coupon.sql
docker exec mysql-edu-platform mysql --default-character-set=utf8mb4 -uedu_user -pedu_pass123 edu_platform -e "source /tmp/step-6-coupon.sql"
```

检查：

```powershell
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SHOW CREATE TABLE coupon; SHOW CREATE TABLE user_coupon; SHOW INDEX FROM user_coupon; SELECT * FROM coupon; SELECT * FROM user_coupon;"
```

## 12. Redis key 与初始化

```text
coupon:stock:{couponId}       String，剩余库存
coupon:received:{couponId}    Set，已领取用户 ID
```

首次本地初始化：

```powershell
docker exec redis-edu-platform redis-cli SET coupon:stock:1 5
docker exec redis-edu-platform redis-cli DEL coupon:received:1
```

检查：

```powershell
docker exec redis-edu-platform redis-cli GET coupon:stock:1
docker exec redis-edu-platform redis-cli SMEMBERS coupon:received:1
```

不提供管理接口，也不在应用启动时自动覆盖库存，避免服务重启把已经扣减的库存重置。

## 13. 领取 Lua 脚本

脚本位置：`edu-trade-service/src/main/resources/lua/receive_coupon.lua`。

执行顺序：

1. `GET` 库存，key 不存在返回 `3`；
2. `SISMEMBER` 判断用户是否已领取，已领取返回 `2`；
3. 把库存转为整数，小于等于零返回 `1`；
4. `DECR` 库存；
5. `SADD` 当前用户；
6. 返回 `0`。

重复判断在库存判断之前，所以已经领取的用户在库存为零时重试，仍返回“优惠券已领取”。

| 返回码 | 含义 |
| --- | --- |
| 0 | Redis 侧领取成功，继续写 MySQL |
| 1 | 库存不足 |
| 2 | 用户已领取 |
| 3 | Redis 库存未初始化 |
| 其他/空值 | 脚本结果异常 |

## 14. MySQL 写入与补偿

Lua 成功后插入一条状态为 `UNUSED` 的 `user_coupon`：

- 插入成功：返回领取成功；
- `uk_coupon_user` 冲突：返回优惠券已领取；
- 其他数据库异常：返回领取失败。

写入失败后执行 `rollback_coupon.lua`。脚本先 `SREM` 当前用户，只有确实删除了成员才
`INCR` 库存，因此重复补偿不会反复增加库存。补偿失败会记录中文错误日志，供人工核对。

这是尽力补偿，不是跨 Redis/MySQL 的强一致事务。Redis 成功后应用突然退出、提交结果不确定、
补偿失败等情况仍需要后续对账机制；本阶段不引入 MQ 或 Seata。

## 15. 接口与业务结果

```text
POST /trade/coupon/receive/{couponId}
Authorization: Bearer <token>
```

`Result.code` 约定：

| code | 提示 |
| --- | --- |
| 200 | 领取成功 |
| 400 | 参数或用户请求头不合法 |
| 404 | 优惠券不存在 |
| 40901 | 库存不足 |
| 40902 | 优惠券已领取 |
| 40903 | 活动尚未开始 |
| 40904 | 活动结束或状态不可领取 |
| 40905 | Redis 库存未初始化 |
| 503 | MySQL、Redis 或领取服务暂不可用 |

未携带合法 JWT 时由 Gateway 返回 HTTP 401。

## 16. Step1～Step5 原链路是否变化

- Gateway JWT 和客户端伪造请求头清洗：未修改
- Nacos 服务名、注册地址和 `lb://edu-trade-service` 路由：未修改
- `CourseClient`、`CourseInfoDTO` 和 `/trade/preview/{courseId}`：未修改
- course-service MySQL 查询和 Redis 缓存：未修改
- user-service 登录接口：未修改

Step6 只扩展 trade-service 内部能力，没有改变已有服务间契约。

## 17. 本阶段没有做什么

- 没有创建订单、支付、退款或订单超时取消
- 没有引入 RabbitMQ、事务消息或 Seata
- 没有实现优惠券核销、过期任务和退还
- 没有实现学习进度、签到和排行榜
- 没有引入 Spring Security、Sentinel、XXL-JOB、SkyWalking、Prometheus
- 没有引入 Nacos Config、Docker Compose 或 JMeter
- 没有实现 Redis Cluster、Sentinel 或 Redisson 分布式锁
- 没有自动对账和一致性修复任务
- 没有伪造压测数据或性能指标

## 18. 编译与启动

```powershell
mvn clean package -DskipTests
docker start nacos-standalone
docker start mysql-edu-platform
docker start redis-edu-platform
docker ps
```

按顺序启动 `edu-user-service:8081`、`edu-course-service:8082`、`edu-trade-service:8083` 和
`edu-gateway:8080`。Nacos 控制台应继续看到四个健康服务实例。

## 19. PowerShell 验收

登录并领取：

```powershell
$loginBody = @{ username = 'test'; password = '123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/user/login' -ContentType 'application/json' -Body $loginBody
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }

Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/coupon/receive/1' -Headers $headers
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/coupon/receive/1' -Headers $headers
```

第一次应成功，第二次应返回重复领取。随后检查：

```powershell
docker exec redis-edu-platform redis-cli GET coupon:stock:1
docker exec redis-edu-platform redis-cli SMEMBERS coupon:received:1
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SELECT * FROM user_coupon WHERE coupon_id=1 AND user_id=1001;"
```

伪造头验证：

```powershell
$forgedHeaders = @{
    Authorization = "Bearer $token"
    'X-User-Id' = '9999'
    'X-User-Name' = 'hacker'
    'X-User-Role' = 'ADMIN'
}
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/coupon/receive/1' -Headers $forgedHeaders
```

Gateway 会先删除伪造头，再写入 JWT 中的用户 `1001`。

库存不足：

```powershell
docker exec redis-edu-platform redis-cli SET coupon:stock:1 0
docker exec redis-edu-platform redis-cli DEL coupon:received:1
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/coupon/receive/1' -Headers $headers
```

未初始化：

```powershell
docker exec redis-edu-platform redis-cli DEL coupon:stock:1 coupon:received:1
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/coupon/receive/1' -Headers $headers
```

不带 token：

```powershell
curl.exe -i -X POST http://localhost:8080/trade/coupon/receive/1
```

回归原有链路：

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/1' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/1' -Headers $headers
```

## 20. IDEA、Nacos、MySQL 和 Redis 观察重点

trade-service Run Console 应能看到中文日志：开始领取、Lua 返回码、库存不足、重复领取、
MySQL 写入成功、唯一索引冲突、Redis 补偿成功或失败。

MySQL 检查 `user_coupon` 是否只有一条 `(coupon_id=1,user_id=1001)` 记录，并确认
`uk_coupon_user` 存在。Redis 检查库存是否只减少一次、received Set 是否包含 `1001`。

Nacos 控制台应看到以下健康实例：

| 服务名 | 端口 |
| --- | --- |
| `edu-gateway` | 8080 |
| `edu-user-service` | 8081 |
| `edu-course-service` | 8082 |
| `edu-trade-service` | 8083 |

MySQL 和 Redis 不注册到 Nacos。Step6 不改变服务名或路由。

## 21. 本地实际验证结果

2026-06-29 在本地开发环境实际执行并确认：

- Nacos、MySQL、Redis 三个已有容器启动成功。
- `docs/sql/step-6-coupon.sql` 执行成功，演示优惠券和 `uk_coupon_user` 均存在。
- Redis 初始化后库存为 5、领取集合为空。
- 当前 Step6 源码临时启动在 18083，并关闭 Nacos 注册，健康检查成功。
- 用户 1001 第一次领取返回 `code=200`，Redis 库存变为 4，Set 包含 1001，MySQL 插入一条 UNUSED 记录。
- 同一用户第二次领取返回 `code=40902`，库存未再次减少。
- 库存为 0 时用户 1002 领取返回 `code=40901`；删除库存 key 后返回 `code=40905`。
- 优惠券 999 返回 `code=404`；缺少 `X-User-Id` 返回 `code=400`。
- 故意删除 Redis 用户标记后再次领取，MySQL 唯一索引成功兜底，补偿 Lua 将库存恢复为 4。
- Run Console 实际输出了领取开始、Lua 返回码、库存不足、重复领取、MySQL 写入、唯一索引冲突和补偿日志。
- 验收结束后临时 18083 实例已停止，最终 Redis 库存为 4、Set 包含 1001，MySQL 保留一条领取记录。
- `mvn -pl edu-trade-service -am compile -DskipTests` 执行成功。

`mvn clean package -DskipTests` 也已实际执行，但清理 `edu-common/target` 时因现有本地进程占用
`inputFiles.lst` 而失败，尚未进入模块编译。没有停止用户已有的其他服务进程。

当时 Gateway 8080 未启动，因此本轮没有实际完成登录、无 token 401、伪造头清洗、Nacos 四服务健康状态和原链路
回归。以上项目必须在四个正式服务启动后按第 19 节继续验证，不能把直连 18083 的结果当成 Gateway 端到端结果。

本次没有执行压测，也没有生成性能指标。

## 22. 常见问题和排查方式

### 22.1 Redis 库存未初始化

接口返回 `code=40905` 时检查：

```powershell
docker exec redis-edu-platform redis-cli GET coupon:stock:1
docker exec redis-edu-platform redis-cli SET coupon:stock:1 5
```

已有领取记录时不能随意按总库存重置。

### 22.2 Redis 库存值不是整数

```powershell
docker exec redis-edu-platform redis-cli GET coupon:stock:1
docker exec redis-edu-platform redis-cli TYPE coupon:stock:1
```

库存必须是 String 类型整数。无法被 `tonumber` 转换时，当前 Lua 按库存不足处理。

### 22.3 Redis 容器未启动

```powershell
docker ps -a --filter "name=redis-edu-platform"
docker start redis-edu-platform
docker exec redis-edu-platform redis-cli PING
```

优惠券领取不会像 Step5 课程缓存那样降级到 MySQL。

### 22.4 数据表或 Mapper 不可用

确认已经执行 Step6 SQL，并检查：

- `mybatis.mapper-locations` 是否为 `classpath:/mapper/*.xml`
- XML namespace 是否与 Mapper 全限定名一致
- XML id 是否与接口方法名一致
- `type-aliases-package` 是否为 `com.dyl.edu.trade.entity`

### 22.5 重复领取没有被拦截

```powershell
docker exec redis-edu-platform redis-cli SISMEMBER coupon:received:1 1001
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SHOW INDEX FROM user_coupon; SELECT * FROM user_coupon WHERE coupon_id=1 AND user_id=1001;"
```

Redis 是第一层快速防重，`uk_coupon_user` 是最终兜底。

### 22.6 Redis 与 MySQL 不一致

先停止继续领取，分别导出 `user_coupon`、Redis 库存和 Set，再人工核对。Step6 没有自动对账任务，不应直接清空
Redis 并按总库存重置。

### 22.7 Gateway 返回 404 或连接失败

```powershell
Get-NetTCPConnection -LocalPort 8080,8083 -State Listen
```

并在 Nacos 控制台确认 Gateway 和 trade-service 健康。Step6 没有修改 `/trade/**` 路由。

### 22.8 Maven clean 无法删除 target

先在 IDEA 中正常停止占用文件的服务，再执行：

```powershell
mvn clean package -DskipTests
```

### 22.9 运行时 Java 版本不正确

```powershell
java -version
mvn -version
$env:JAVA_HOME
```

Spring Boot 3 至少需要 Java 17。

## 23. 风险和限制

- Redis 和 MySQL 之间没有分布式事务，只做尽力补偿。
- 应用在 Lua 成功后突然退出时，可能出现 Redis 已扣减但 MySQL 未落库。
- 补偿失败需要人工核对。
- `coupon.stock` 当前不是实时库存。
- Redis 数据丢失后没有自动重建机制。
- 唯一索引冲突补偿后会移除 Redis 用户标记，重复请求可能继续访问 MySQL。
- 活动校验与 Lua 执行之间存在极短时间窗口。
- 没有活动结束自动清理、优惠券核销、过期或退还流程。
- 没有压测，因此不提供吞吐量或延迟数字。
- 可信用户边界仍依赖请求经过 Gateway；直连 8083 需要部署层网络隔离。

## 24. 分层和面试复盘

面试时可以说明：

> Step6 在已有 Gateway JWT、Nacos 和 MySQL/Redis 基础上实现优惠券领取。Gateway 清洗客户端伪造的用户头，
> trade-service 只读取 JWT 生成的可信 userId。领取前先从 MySQL 校验活动，再执行 Redis Lua。Lua 原子完成库存
> 检查、重复领取判断、库存扣减和用户 Set 写入，避免 Java 多命令并发造成超发。Redis 成功后插入 user_coupon，
> 并使用 unique(coupon_id,user_id) 最终防重。MySQL 写入失败时执行补偿 Lua，原子移除标记并恢复库存。这个方案
> 不是强一致分布式事务，崩溃窗口和补偿失败仍需后续对账或可靠消息机制，但 Step6 刻意不引入 MQ、Seata 和订单。

可以重点讲：

1. 为什么 Redis 多条命令需要 Lua。
2. Lua 为什么不能保证 Redis/MySQL 原子性。
3. 为什么先判断重复领取，再判断库存。
4. 为什么 Redis 防重后仍需要 MySQL 唯一索引。
5. 补偿为什么要保证幂等。
6. 为什么 Redis 故障时不直接降级到 MySQL领取。
7. Gateway 如何保证 `X-User-Id` 可信。
8. 当前 `coupon.stock` 的定位和后续改进方向。

## 25. 本阶段边界

- Redis 与 MySQL 双写不是强一致事务；
- Redis 数据丢失后需要按 MySQL 记录人工重建库存和领取集合；
- `coupon.stock` 本阶段不随领取实时扣减；
- 没有自动对账、活动结束清理或优惠券核销；
- 直接访问 8083 并伪造用户头需要部署层网络隔离，本阶段可信边界仍是 Gateway；
- 不进入订单或 Step7。
