# Step8：RabbitMQ 延迟队列实现订单超时关闭

## 1. 本阶段目标

本阶段只在 `edu-trade-service` 中实现订单超时关闭：订单成功写入 MySQL 后发送 RabbitMQ 消息，
消息在 TTL 等待队列中过期，经死信交换机进入关闭队列，消费者查询数据库并关闭仍为 `UNPAID` 的订单。

本阶段不实现真实支付、优惠券抵扣、库存回滚、学习进度、排行榜、分布式事务、配置中心、
Docker Compose 或压测。Gateway JWT、Nacos、OpenFeign、课程缓存和优惠券领取链路不变。

## 2. 为什么此时接入 RabbitMQ

Step1 已经建立 Gateway JWT 鉴权和可信用户上下文；Step2 完成 Nacos 注册与发现；Step3 跑通 OpenFeign；
Step4、Step5 完成课程 MySQL 查询和 Redis 缓存；Step6 完成优惠券领取；Step7 已经能够通过 MySQL 本地事务
创建 `UNPAID` 订单，并使用 `unique(user_id, request_id)` 保证接口幂等。

此时订单已经具备稳定的数据库状态、唯一订单号、创建时间和关闭时间字段，可以在不引入支付、库存等额外业务的
前提下，单独学习 RabbitMQ 的异步解耦、TTL、死信路由、重复投递和消费者幂等。

## 3. RabbitMQ 在当前项目中的角色

| 组件 | 本阶段职责 |
| --- | --- |
| Gateway | 校验 JWT，清洗伪造的 `X-User-*`，写入可信用户上下文 |
| trade-service | 创建订单、发送超时消息、消费关闭消息并执行订单关闭业务 |
| MySQL `edu_order` | 保存订单真实状态，是订单状态的最终数据源 |
| RabbitMQ | 保存超时等待消息，并在 TTL 到期后驱动订单状态检查 |
| TTL 等待队列 | 让消息等待固定时间，不直接执行订单关闭 |
| DLX | 将过期消息从等待队列转发到订单关闭交换机 |
| 条件更新 SQL | 保证并发消费和重复消费时最多关闭一次 |

RabbitMQ 消息只表示“现在应该检查这张订单是否超时”，不能证明订单一定仍然未支付。消费者必须以 MySQL 中的
实时订单状态为准，不能直接相信消息内容。

## 4. 涉及模块

### edu-trade-service

- 新增 Spring AMQP 依赖和 RabbitMQ 连接配置
- 新增交换机、队列、Binding、TTL 和 DLX 配置
- 新增独立的 MQ Message、Producer 和 Consumer
- 修改订单创建 Service，在新订单事务提交后发送消息
- 修改 OrderService 和 OrderMapper，实现查询订单和条件关闭
- 保留 Step7 的 requestId 幂等逻辑
- 保留 Step6 的优惠券领取和 Step3 的交易预览代码

### edu-gateway

- 继续校验 JWT、清洗客户端伪造的 `X-User-*` 并写入可信用户头
- 继续通过 `lb://edu-trade-service` 转发 `/trade/**`
- 本阶段没有修改

### edu-course-service、edu-user-service、edu-common

- course-service 继续提供课程查询和 Redis 缓存
- user-service 继续提供登录
- common 继续提供统一返回、异常和 JWT 工具
- 三个模块均没有业务代码改动

## 5. 本阶段核心链路

```text
客户端 -> Gateway JWT 鉴权并清洗伪造请求头
      -> lb://edu-trade-service
      -> OrderController 接收原有创建订单请求
      -> OrderService 保留 Step7 幂等查询和课程校验
      -> OrderTransactionService 提交 UNPAID 订单
      -> OrderTimeoutProducer 发送超时消息
      -> order.timeout.exchange
      -> order.timeout.delay.queue 等待 30 秒
      -> 消息过期并通过 DLX 转发到 order.close.exchange
      -> order.close.queue
      -> OrderTimeoutConsumer 接收消息
      -> OrderService 根据 orderNo 查询 MySQL 实时状态
      -> UNPAID 时执行条件更新为 CLOSED
```

相同 `userId + requestId` 幂等命中已有订单时，继续返回已有订单，不重复发送超时消息。

## 6. 版本和新增依赖

| 组件 | 版本 |
| --- | --- |
| Java | `17+` |
| Spring Boot | `3.3.5` |
| Spring Cloud | `2023.0.3` |
| Spring Cloud Alibaba | `2023.0.3.3` |
| Spring AMQP | 由 Spring Boot 管理 |
| RabbitMQ 镜像 | `rabbitmq:3-management` |
| MyBatis Spring Boot Starter | `3.0.3` |
| MySQL | `8.0` |

只在 `edu-trade-service` 中新增 `spring-boot-starter-amqp`，用于 RabbitMQ 连接、拓扑声明、消息发送、
JSON 转换和 `@RabbitListener` 消费。其他模块没有增加 RabbitMQ 依赖。

## 7. 新增和修改文件清单

### edu-trade-service

- `pom.xml`：增加 `spring-boot-starter-amqp`
- `application.yml`：增加 RabbitMQ 连接和本地 30 秒超时配置
- `config/RabbitMqConfig.java`：声明交换机、队列、Binding、TTL、DLX 和 JSON 转换器
- `constant/OrderRabbitConstants.java`：统一保存 RabbitMQ 名称和 routing key
- `mq/message/OrderTimeoutMessage.java`：独立的订单超时消息对象
- `mq/producer/OrderTimeoutProducer.java`：发送订单超时消息
- `mq/consumer/OrderTimeoutConsumer.java`：接收关闭消息并调用 OrderService
- `service/OrderService.java`、`service/impl/OrderServiceImpl.java`：发送消息和关闭超时订单
- `mapper/OrderMapper.java`、`resources/mapper/OrderMapper.xml`：按订单号查询和条件更新

### 文档

- `docs/step-8-rabbitmq-order-timeout.md`
- `README.md`

## 8. Config / Producer / Consumer / Service / Mapper / Message 分层设计

- `RabbitMqConfig`：只声明 RabbitMQ 基础设施，不处理订单业务。
- `OrderTimeoutProducer`：只负责发送消息，不查询和修改订单。
- `OrderTimeoutConsumer`：只负责接收消息、做消息基本校验并调用 Service。
- `OrderServiceImpl`：负责查询数据库实时状态、状态二次校验和关闭订单。
- `OrderMapper` / `OrderMapper.xml`：负责 MySQL 查询和条件更新。
- `OrderTimeoutMessage`：与数据库 Entity、接口 DTO 和返回 VO 分离。
- `OrderController`：请求格式和职责不变，不直接发送 MQ。

订单关闭业务没有堆在 Controller、Consumer、配置类或启动类中。

## 9. edu_order 表是否需要修改

不需要修改。Step7 已有 `status`、`updated_at`、`closed_at`、`deleted` 和 `uk_order_no`，可以直接支持
`UNPAID -> CLOSED` 条件更新。本阶段没有新增 SQL 脚本，也没有改变既有索引。

## 10. RabbitMQ Docker 环境

首次创建容器的一行命令：

```powershell
docker run -d --name rabbitmq-edu-platform -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=edu_mq -e RABBITMQ_DEFAULT_PASS=edu_mq_pass rabbitmq:3-management
```

PowerShell 多行命令：

```powershell
docker run -d --name rabbitmq-edu-platform `
  -p 5672:5672 `
  -p 15672:15672 `
  -e RABBITMQ_DEFAULT_USER=edu_mq `
  -e RABBITMQ_DEFAULT_PASS=edu_mq_pass `
  rabbitmq:3-management
```

已有容器时执行：

```powershell
docker start rabbitmq-edu-platform
```

管理台地址为 `http://localhost:15672`，用户名为 `edu_mq`，密码为 `edu_mq_pass`。

## 11. RabbitMQ 和订单超时配置

连接参数位于 `application.yml`：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: edu_mq
    password: edu_mq_pass
    virtual-host: /
    connection-timeout: 3s
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: true

order:
  timeout:
    seconds: 30
```

真实业务通常可以设置为 15 分钟；本地使用 30 秒只是为了快速观察消息过期和订单关闭。

本阶段没有启用 publisher confirm、publisher returns 或 Nacos Config。

## 12. Exchange、Queue 和 Routing Key 设计

| 类型 | 名称 | Routing key |
| --- | --- | --- |
| 延迟交换机 | `order.timeout.exchange` | `order.timeout` |
| TTL 等待队列 | `order.timeout.delay.queue` | `order.timeout` |
| 订单关闭交换机 | `order.close.exchange` | `order.close` |
| 订单关闭队列 | `order.close.queue` | `order.close` |

`order.timeout.delay.queue` 是持久化队列，并配置：

- `x-message-ttl = 30000`
- `x-dead-letter-exchange = order.close.exchange`
- `x-dead-letter-routing-key = order.close`

等待队列没有业务消费者。消息到期后由 RabbitMQ 作为死信转发到关闭交换机，再进入关闭队列。
本阶段使用 RabbitMQ 原生 TTL + DLX，不安装延迟消息插件。

## 13. TTL + DLX 延迟队列设计

```text
订单超时消息
    -> order.timeout.exchange
    -> order.timeout.delay.queue
    -> 等待 30000ms
    -> 消息过期
    -> order.close.exchange
    -> order.close.queue
```

等待队列没有业务消费者，否则消息会在 TTL 到期前被普通消费者取走。固定队列 TTL 适合当前所有订单使用相同
超时时间的学习场景；如果以后需要每张订单使用不同延迟时间，需要重新评估队列设计或延迟插件方案。

## 14. OrderTimeoutMessage 设计

`OrderTimeoutMessage` 与 Entity、接口 DTO、VO 分离，包含：

| 字段 | 作用 |
| --- | --- |
| `messageId` | 日志追踪消息 |
| `orderId` | 订单主键，辅助日志定位 |
| `orderNo` | 消费者查询和关闭订单的业务标识 |
| `userId` | 辅助日志定位用户 |
| `createdAt` | 订单创建时间 |
| `timeoutSeconds` | 记录本次配置的超时秒数 |

消息不携带订单状态。消费者不能相信消息中的业务状态，必须查询 MySQL。

消息通过 `Jackson2JsonMessageConverter` 序列化为 JSON，并设置为持久化消息。持久化消息和持久化队列可以降低
RabbitMQ 正常重启时的消息丢失风险，但不等于已经实现端到端可靠消息最终一致性。

## 15. 订单创建和消息发送

1. `OrderServiceImpl` 保留 Step7 的前置幂等查询、OpenFeign 课程校验和 MySQL 本地事务。
2. 新订单事务提交后，构造 `OrderTimeoutMessage`。
3. `OrderTimeoutProducer` 将持久化 JSON 消息发送到 `order.timeout.exchange`。
4. 幂等命中已有订单时直接返回，不重复发送消息。

发送发生在订单事务提交之后，避免订单回滚但消息已经被消费。当前没有本地消息表、Outbox 或可靠消息补偿；
如果 RabbitMQ 连接异常，代码记录中文错误日志，订单创建结果仍然返回。此时可能出现订单已经落库但没有超时消息的
一致性缺口，这是本阶段明确保留的限制。

本阶段没有启用 publisher confirm，因此“发送完成”表示客户端调用未抛异常，不等价于完整的可靠消息最终一致性。

## 16. 消费和订单状态二次校验

`OrderTimeoutConsumer` 监听 `order.close.queue`，只负责接收消息并调用 `OrderService`：

1. 消息缺少 `orderNo` 时记录错误并返回。
2. 根据 `orderNo` 查询 `edu_order`。
3. 订单不存在时记录日志并返回。
4. 状态不是 `UNPAID` 时记录幂等日志并返回。
5. 状态为 `UNPAID` 时执行数据库条件更新。

条件更新 SQL：

```sql
UPDATE edu_order
SET status = 'CLOSED',
    closed_at = NOW(),
    updated_at = NOW()
WHERE order_no = ?
  AND status = 'UNPAID'
  AND deleted = 0;
```

影响一行表示关闭成功；影响零行表示状态已被其他线程改变，按幂等结果返回。前置查询便于明确业务判断和日志，
条件更新才是并发下避免误关闭的最终保障。

RabbitMQ 可能因为消费者在 ACK 前异常、连接中断或消费异常而重复投递。重复消息再次查询时会看到 `CLOSED`，
或在并发条件更新时只有一个消费者更新成功，因此不会重复关闭。

## 17. 为什么消费者必须以 MySQL 状态为准

消息在等待队列停留期间，订单可能已经支付、被其他流程关闭、被另一个消费者先关闭或被逻辑删除。因此，
RabbitMQ 消息只负责触发检查，MySQL 才负责表达订单当前事实。

本阶段虽然没有真实支付入口，仍然必须保留状态二次校验和条件更新，为后续可能出现的状态竞争建立正确边界。

## 18. 重复消费和幂等设计

RabbitMQ 可能因为消费者在 ACK 前退出、连接中断、数据库临时异常重新入队或生产端重复发送而重复投递。
本阶段通过两层机制保证幂等：

1. 查询发现订单已经不是 `UNPAID` 时直接返回。
2. 更新 SQL 使用 `WHERE order_no = ? AND status = 'UNPAID' AND deleted = 0`。

即使两个消费者同时查询到 `UNPAID`，也只有一个条件更新可以影响一行。另一个消费者得到零行后按幂等结果返回，
不会重复写入 `closed_at`。

## 19. 消息发送失败处理

订单事务已经提交后，如果 RabbitMQ 发送抛出运行时异常，`OrderServiceImpl` 记录中文错误日志并继续返回已创建订单。
当前没有 publisher confirm、本地消息表、Outbox、定时扫描或发送失败补偿。

因此可能出现“订单已经写入 MySQL，但 RabbitMQ 消息没有成功发送”的一致性窗口。幂等命中已有订单时不重新发送消息，
也不能补偿首次发送失败。这是本阶段明确保留的限制。

## 20. 自动确认与消费失败

本阶段使用 Spring AMQP 自动确认：监听方法正常返回后 ACK；数据库异常向外抛出，按照当前配置重新入队。
订单不存在、状态已变化和条件更新未命中都是正常幂等结果，不抛异常。

如果 MySQL 长时间不可用，消息可能反复重投。本阶段没有增加失败队列、重试次数限制和告警系统。

## 21. Step1～Step7 原链路是否变化

- Gateway JWT 鉴权和伪造请求头清洗：未修改
- Nacos 服务名、注册地址和 `lb://edu-trade-service` 路由：未修改
- user-service 登录接口：未修改
- course-service MySQL 查询和 Redis 缓存：未修改
- `CourseClient`、`CourseInfoDTO` 和 `/trade/preview/{courseId}`：继续复用
- Step6 `/trade/coupon/receive/{couponId}`：未修改
- Step7 `/trade/order/create` 请求格式和 requestId 幂等：继续保留
- Redis：继续服务课程缓存和优惠券领取，不参与订单超时关闭
- `edu_order`：继续使用 Step7 已有表，不修改表结构

RabbitMQ 只接入 `edu-trade-service`，自身不注册到 Nacos。

## 22. 本阶段没有做什么

- 没有真实支付、支付状态修改接口、支付回调、退款或支付流水
- 没有优惠券抵扣、核销、冻结或退还
- 没有课程库存或名额扣减、回滚
- 没有学习进度、签到或排行榜
- 没有 RabbitMQ 延迟消息插件
- 没有 publisher confirm、Outbox、本地消息表或可靠消息补偿
- 没有消费失败队列、有限次数重试或监控告警
- 没有 Sentinel、Seata、XXL-JOB、SkyWalking、Prometheus
- 没有 Spring Security、Nacos Config、Docker Compose 或 JMeter
- 没有修改 Gateway、user-service、course-service 业务代码
- 没有伪造压测数据、吞吐量或延迟指标

## 23. 编译和启动

```powershell
mvn clean package -DskipTests

docker start nacos-standalone
docker start mysql-edu-platform
docker start redis-edu-platform
docker start rabbitmq-edu-platform
docker ps
```

如果 RabbitMQ 容器尚不存在，先使用第 10 节的 `docker run` 命令。然后依次启动 user-service、course-service、
trade-service 和 Gateway。Nacos 应继续显示四个健康服务实例，RabbitMQ 本身不注册 Nacos。

## 24. PowerShell 接口验收

登录并创建订单：

```powershell
$loginBody = @{ username = 'test'; password = '123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/user/login' -ContentType 'application/json' -Body $loginBody
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }

$body = @{ courseId = 1; requestId = 'req-timeout-001' } | ConvertTo-Json
$first = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $headers -ContentType 'application/json' -Body $body
$first.data
```

立即检查订单，应为 `UNPAID` 且 `closed_at` 为空：

```powershell
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SELECT order_no,status,created_at,closed_at FROM edu_order WHERE request_id='req-timeout-001';"
```

等待 35 秒后再次检查，应为 `CLOSED` 且 `closed_at` 非空：

```powershell
Start-Sleep -Seconds 35
docker exec mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform -e "SELECT order_no,status,created_at,updated_at,closed_at FROM edu_order WHERE request_id='req-timeout-001';"
```

幂等重试不新增订单，也不重复发送超时消息：

```powershell
$second = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $headers -ContentType 'application/json' -Body $body
$second.data
```

无 token、伪造用户头和原链路回归：

```powershell
curl.exe -i -X POST http://localhost:8080/trade/order/create -H "Content-Type: application/json" -d '{"courseId":1,"requestId":"req-timeout-no-token"}'

$forgedHeaders = @{ Authorization = "Bearer $token"; 'X-User-Id' = '9999'; 'X-User-Name' = 'hacker'; 'X-User-Role' = 'ADMIN' }
$forgedBody = @{ courseId = 1; requestId = 'req-timeout-forged' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/order/create' -Headers $forgedHeaders -ContentType 'application/json' -Body $forgedBody

Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/1' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/1' -Headers $headers
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trade/coupon/receive/1' -Headers $headers
```

优惠券结果取决于 Step6 当前库存和用户领取状态。

## 25. IDEA、RabbitMQ、Nacos 和 MySQL 观察重点

trade-service Run Console 应观察：订单创建成功、开始发送订单超时消息、消息发送完成、收到订单超时消息、
开始查询超时订单状态、订单超时关闭成功，以及重复处理时的幂等跳过日志。

RabbitMQ 管理台应观察：

- Exchanges 中存在 `order.timeout.exchange` 和 `order.close.exchange`；
- Queues 中存在 `order.timeout.delay.queue` 和 `order.close.queue`；
- 等待队列参数中存在 TTL、DLX 和死信 routing key；
- 创建订单后等待队列短暂出现 Ready 消息；
- 到期后消息转入关闭队列并被消费者取走；
- 关闭队列 Consumers 通常为 1。

Nacos 控制台应继续看到 Gateway、user-service、course-service 和 trade-service 四个健康实例。RabbitMQ 不注册
Nacos。MySQL 应重点检查订单是否从 `UNPAID` 变为 `CLOSED`、`closed_at` 是否写入，以及重复消息是否没有再次
修改关闭时间。

常见风险：RabbitMQ 未启动、账号不一致、5672 端口冲突、交换机与队列未绑定、TTL 单位错误、同名队列旧参数
导致 `PRECONDITION_FAILED`、消费者未启动、MySQL 异常反复重投、订单提交后消息发送失败，以及只做前置查询却
遗漏条件更新导致的并发误关闭。

## 26. 分层和面试复盘

可以说明：Step8 使用 RabbitMQ 原生 TTL + DLX 实现固定延迟，不依赖插件。新订单事务提交后发送持久化 JSON 消息，
等待队列到期后将消息死信路由到关闭队列。消费者以 MySQL 实时状态为准，先查询再执行
`WHERE status='UNPAID'` 的条件更新。查询用于业务判断，条件更新用于最终并发幂等。RabbitMQ 至少一次投递可能产生
重复消息，但已关闭订单会被幂等跳过。当前没有 Outbox 和 publisher confirm，数据库与 MQ 双写仍有一致性窗口，
这是后续可靠消息治理可以解决的问题，而不是本阶段伪装成已经解决的能力。

可以重点讲：

1. 为什么 RabbitMQ 消息不是订单状态的最终数据源。
2. TTL、死信、DLX 和最终消费队列之间的关系。
3. 为什么等待队列不设置业务消费者。
4. 为什么选择 TTL + DLX 而不是安装延迟插件。
5. 为什么订单事务提交后再发送消息。
6. 为什么消费者要先查询状态，再执行条件更新。
7. RabbitMQ 为什么可能重复投递，以及如何保证幂等。
8. 自动 ACK、异常重新入队和快速重投风险。
9. 当前消息发送失败的一致性缺口，以及 Outbox 可以如何演进。
10. 为什么本阶段不引入支付、库存、Seata 或复杂补偿。

## 27. 本地实际验证结果

2026-06-30 在本地开发环境实际执行并确认：

- 新建并启动 `rabbitmq-edu-platform`，镜像为 `rabbitmq:3-management`；
- Nacos、MySQL、Redis 和 RabbitMQ 四个容器均保持运行，RabbitMQ diagnostics ping 成功；
- 临时启动新编译的 trade-service 后，RabbitMQ 实际出现两个持久化交换机和两个持久化队列；
- 等待队列实际参数为 TTL 30000、DLX `order.close.exchange`、死信 routing key `order.close`；
- 两组 Binding 均正确，关闭队列消费者数量为 1；
- 本地测试订单 `req-step8-runtime-20260630160602` 初始状态实际为 `UNPAID`；
- 测试消息发布到超时交换机后，等待队列实际出现一条 Ready 消息；
- TTL 到期后等待队列归零，消费者查询订单并将其更新为 `CLOSED`，`closed_at` 实际写入；
- 向关闭交换机重复投递同一订单后，日志实际输出“超时订单状态已变化，幂等跳过关闭”，数据库时间未被重复更新；
- `mvn --% -pl edu-trade-service -am compile -DskipTests -Dmaven.resources.skip=true` 实际编译成功。

`mvn clean package -DskipTests` 已实际执行，但 Windows 无法删除被本机进程占用的
`edu-common/target/.../inputFiles.lst`。随后普通 package 又分别遇到 Gateway JAR、trade-service Lua 资源和 JAR
被占用，因此不能声明完整 package 成功。没有擅自终止来源不明的进程。跳过资源复制只用于验证 Java 源码编译，
正常运行仍应在释放 target 文件占用后执行完整构建。

核心 MQ 验证使用一条明确标识的本地测试订单和 RabbitMQ 管理 API，因为验证时 8080～8083 四个业务服务均未运行。
因此本次没有伪称重新执行了 Gateway 登录、OpenFeign 下单、伪造请求头及三个原接口回归；这些命令已在第 24 节提供，
待通过 IDEA 正常启动四个服务后执行。

## 28. 常见问题和排查方式

### 28.1 RabbitMQ 容器未启动

```powershell
docker ps --filter "name=rabbitmq-edu-platform"
docker start rabbitmq-edu-platform
docker logs rabbitmq-edu-platform
```

确认 5672 和 15672 已映射，并检查容器启动日志。

### 28.2 RabbitMQ 登录失败

确认容器账号 `edu_mq / edu_mq_pass` 与 `application.yml` 一致。已有同名容器不会因为重新执行环境变量而自动修改账号。

### 28.3 交换机或队列没有声明

先检查 trade-service 是否成功启动并连接 RabbitMQ，再查看启动日志中是否存在认证失败、连接超时或拓扑声明异常。
RabbitMQ 拓扑由 `RabbitMqConfig` 在应用启动时声明。

### 28.4 PRECONDITION_FAILED

RabbitMQ 不允许使用不同参数重新声明同名队列。如果旧队列的 TTL、DLX 或 durable 参数不同，会出现
`PRECONDITION_FAILED`。本地学习环境中应先确认队列没有需要保留的消息，再删除冲突队列并重启 trade-service。

### 28.5 TTL 没有生效

确认 `order.timeout.seconds` 为正数、管理台显示 `x-message-ttl=30000`、等待队列没有业务消费者，并确认消息确实进入
`order.timeout.delay.queue`。RabbitMQ TTL 使用毫秒，代码会将配置的秒数乘以 1000。

### 28.6 消息没有进入关闭队列

确认等待队列配置了 `x-dead-letter-exchange=order.close.exchange` 和
`x-dead-letter-routing-key=order.close`，并确认关闭交换机通过 `order.close` 绑定关闭队列。

### 28.7 关闭队列消息没有被消费

查看 `order.close.queue` 的 Consumers。如果为 0，检查 trade-service 是否启动、`@RabbitListener` 是否创建成功以及
RabbitMQ 连接是否正常。

### 28.8 Invalid bound statement

确认 `OrderMapper.xml` 已复制到 `target/classes/mapper`，XML namespace、`selectByOrderNo`、`closeUnpaidOrder` 与
Mapper 接口一致。如果应用仍加载旧 target 资源，应停止旧服务、释放文件占用后重新构建。

### 28.9 消息持续重复消费

先查看 trade-service 根异常。MySQL 持续不可用、Mapper 未加载或 SQL 错误时，当前自动重新入队配置可能形成快速重投。
不要只关注 RabbitMQ 堆积数量而忽略消费者异常日志。

### 28.10 订单一直保持 UNPAID

依次检查发送消息日志、等待队列、TTL/DLX 参数、关闭队列消费者、消费异常和 MySQL 中的 `order_no`。如果发送阶段
已经失败，本阶段没有本地消息表或补偿任务，订单可能长期保持 `UNPAID`。

### 28.11 Maven clean 无法删除 target

先在 IDEA 中正常停止占用 target 或 JAR 的服务，再执行：

```powershell
mvn clean package -DskipTests
```

不要直接强制结束来源不明的 Java 进程。

### 28.12 运行时 Java 版本不正确

```powershell
java -version
mvn -version
$env:JAVA_HOME
```

Spring Boot 3 至少需要 Java 17。Maven 使用的 JDK 和直接执行 `java` 使用的 JDK 可能不同。

## 29. 风险和限制

- 订单事务提交和 RabbitMQ 发送之间存在数据库与 MQ 双写一致性窗口
- 发送失败只记录日志，没有本地消息表、Outbox 或定时补偿
- 幂等命中已有订单时不重新发送消息，不能补偿首次发送失败
- 没有 publisher confirm，普通发送调用未抛异常不代表端到端可靠送达
- 自动重新入队在持续异常时可能形成快速重投和日志刷屏
- 没有失败队列、最大重试次数、退避和告警
- 固定队列 TTL 只适合所有订单使用相同超时时间
- 修改同名队列 TTL 或 DLX 参数可能触发 `PRECONDITION_FAILED`
- 持久化 exchange、queue 和 message 不等于可靠消息最终一致性
- 完整 Gateway、OpenFeign 和原接口回归需要四个业务服务全部启动后执行

## 30. 本阶段边界

- 只在 `edu-trade-service` 接入 RabbitMQ
- 只实现新订单发送超时消息和 `UNPAID -> CLOSED` 状态流转
- 只使用固定 30 秒 TTL + DLX 进行本地验证
- 消费者必须查询 MySQL，并通过条件更新完成最终幂等
- requestId 幂等命中时不重复发送消息
- Redis 不参与订单超时关闭
- 不修改 Gateway、user-service、course-service 业务代码
- 不修改 `edu_order` 表结构，不新增 SQL 脚本
- 不实现真实支付、优惠券抵扣、库存回滚或学习业务
- 不引入延迟插件、可靠消息表、分布式事务、配置中心、Docker Compose 或 JMeter
- 不进入下一阶段功能
