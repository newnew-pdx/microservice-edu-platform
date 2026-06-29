# 微服务在线教育平台

## 项目定位

本项目是一个用于 Java 后端实习面试准备的 Spring Cloud Alibaba 微服务项目。项目采用小步迭代方式推进，目标是逐步掌握微服务、缓存、消息队列、高并发与工程化能力。

当前项目不追求一次性完整上线，而是每一步都保持可运行、可解释、可回滚。

## 技术栈概览

已使用：

- Java 17+
- Spring Boot 3.x
- Spring Cloud Gateway
- Nacos Discovery
- OpenFeign
- MySQL 8.0
- MyBatis
- Redis 7.x
- Spring Data Redis
- Maven 多模块
- JWT

后续规划：

- RabbitMQ
- Docker Compose
- JMeter

## 当前模块说明

| 模块 | 说明 | 端口 |
| --- | --- | --- |
| `edu-common` | 公共模块，统一返回、异常、JWT 工具等 | 无 |
| `edu-gateway` | 统一入口，路由转发，JWT 鉴权 | 8080 |
| `edu-user-service` | 用户服务，当前提供登录和用户信息接口 | 8081 |
| `edu-course-service` | 课程服务，通过 MyBatis 查询 MySQL，并使用 Redis 缓存课程详情 | 8082 |
| `edu-trade-service` | 交易服务，通过 OpenFeign 调用课程服务，并使用 Redis + Lua 完成优惠券领取 | 8083 |

## 当前已完成能力

- Maven 多模块微服务骨架
- Gateway 统一入口、JWT 鉴权和可信用户上下文透传
- Nacos 服务注册与发现、Gateway `lb://` 路由
- trade-service 通过 OpenFeign 调用 course-service
- 内存用户登录、MySQL 课程查询和交易预览链路
- 课程详情 Redis 旁路缓存、空值缓存和 Redis 异常降级
- 优惠券 Redis + Lua 原子领取、MySQL 唯一索引兜底和失败补偿

## 阶段进度

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| Step0 | Maven 多模块微服务骨架搭建 | 已完成 |
| Step1 | Gateway + JWT 鉴权与用户上下文透传链路 | 已完成 |
| Step2 | 接入 Nacos 服务注册与发现 | 已完成 |
| Step3 | 接入 OpenFeign 服务间调用 | 已完成 |
| Step4 | Course Service + MySQL 课程数据 | 已完成 |
| Step5 | Course Service + Redis 课程详情缓存 | 已完成 |
| Step6 | Trade Service 优惠券领取：Redis + Lua + MySQL 唯一索引 | 已完成 |

## 快速启动

编译：

```bash
mvn clean package -DskipTests
```

当前本地运行依赖 Nacos、MySQL 和 Redis，Docker 容器启动命令：

```powershell
docker start nacos-standalone
docker start mysql-edu-platform
docker start redis-edu-platform
```

首次创建 Redis 容器：

```powershell
docker run -d --name redis-edu-platform -p 6379:6379 redis:7.2
```

启动用户服务：

```bash
java -jar edu-user-service/target/edu-user-service-0.0.1-SNAPSHOT.jar
```

启动课程服务：

```bash
java -jar edu-course-service/target/edu-course-service-0.0.1-SNAPSHOT.jar
```

启动交易服务：

```bash
java -jar edu-trade-service/target/edu-trade-service-0.0.1-SNAPSHOT.jar
```

启动网关服务：

```bash
java -jar edu-gateway/target/edu-gateway-0.0.1-SNAPSHOT.jar
```

如果本机存在多个 JDK，建议确认运行时 Java 版本为 17+。

## Step1 快速验证

登录：

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

访问用户信息：

```text
GET http://localhost:8080/user/profile
Authorization: Bearer <token>
```

详细验证命令和排查过程见 [docs/step-1-gateway-auth.md](docs/step-1-gateway-auth.md)。

## Step2 快速验证

确认 Nacos 控制台可访问：

```text
http://localhost:8848/nacos
```

服务列表中应能看到：

- `edu-gateway`
- `edu-user-service`
- `edu-course-service`
- `edu-trade-service`

通过 Gateway 访问用户链路：

```text
POST http://localhost:8080/user/login
GET http://localhost:8080/user/profile
Authorization: Bearer <token>
```

通过 Gateway 访问课程和交易健康检查时需要携带 token：

```text
GET http://localhost:8080/course/health
GET http://localhost:8080/trade/health
Authorization: Bearer <token>
```

详细验证命令和排查过程见 [docs/step-2-nacos-discovery.md](docs/step-2-nacos-discovery.md)。

## Step3 快速验证

先通过 `/user/login` 获取 token，再携带 token 访问交易预览：

```text
GET http://localhost:8080/trade/preview/1
Authorization: Bearer <token>
```

调用链为 Gateway -> `edu-trade-service` -> OpenFeign -> `edu-course-service`。Step4 已将课程数据来源从内存替换为 MySQL。

详细设计、PowerShell 命令和排查方式见 [docs/step-3-openfeign.md](docs/step-3-openfeign.md)。

## Step4 快速验证

使用 Docker 启动 MySQL 并执行课程初始化 SQL 后，携带 token 查询课程详情、列表或交易预览：

```text
GET http://localhost:8080/course/1
GET http://localhost:8080/course/list
GET http://localhost:8080/trade/preview/1
Authorization: Bearer <token>
```

详细命令和排查方式见 [docs/step-4-course-mysql.md](docs/step-4-course-mysql.md)。

## Step5 快速验证

Redis 使用 `localhost:6379`，课程详情 key 为 `course:detail:{courseId}`。清空课程 1 缓存后连续请求两次，
第一次应回源 MySQL 并写入 Redis，第二次应直接命中缓存：

```powershell
docker exec redis-edu-platform redis-cli DEL course:detail:1
docker exec redis-edu-platform redis-cli GET course:detail:1
docker exec redis-edu-platform redis-cli TTL course:detail:1
```

不存在的课程使用 `**NULL**` 空值标记并缓存 1 分钟。详细启动、接口测试和故障降级验证见
[docs/step-5-course-redis-cache.md](docs/step-5-course-redis-cache.md)。

## Step6 快速验证

先执行 `docs/sql/step-6-coupon.sql`，再手动初始化 Redis 库存：

```powershell
docker exec redis-edu-platform redis-cli SET coupon:stock:1 5
docker exec redis-edu-platform redis-cli DEL coupon:received:1
```

登录取得 token 后，通过 Gateway 领取优惠券：

```text
POST http://localhost:8080/trade/coupon/receive/1
Authorization: Bearer <token>
```

第一次应领取成功，第二次应返回重复领取。完整 SQL、PowerShell 命令、一致性边界和排查方式见
[docs/step-6-coupon-redis-lua.md](docs/step-6-coupon-redis-lua.md)。

## 文档索引

- [Step0：项目骨架初始化](docs/step-0-scaffold.md)
- [Step1：Gateway + JWT 鉴权与用户上下文透传](docs/step-1-gateway-auth.md)
- [Step2：Nacos 服务注册与发现](docs/step-2-nacos-discovery.md)
- [Step3：OpenFeign 服务间调用](docs/step-3-openfeign.md)
- [Step4：Course Service + MySQL 课程数据](docs/step-4-course-mysql.md)
- [Step5：Course Service + Redis 课程详情缓存](docs/step-5-course-redis-cache.md)
- [Step6：Trade Service 优惠券领取](docs/step-6-coupon-redis-lua.md)
