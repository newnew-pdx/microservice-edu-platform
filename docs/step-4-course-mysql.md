# Step4：Course Service + MySQL 课程数据

## 1. 本阶段目标

本阶段只让 `edu-course-service` 使用 MyBatis 访问 MySQL。Gateway JWT 鉴权、Nacos Discovery 和
trade-service 的 OpenFeign 调用方式保持不变，不接入 Redis、消息队列、订单或配置中心。

目标是把 Step3 中 course-service 的内存模拟课程替换为真实数据库数据，同时保持上游调用契约不变。

## 2. 为什么此时接入 MySQL

Step1 已经完成 Gateway JWT 鉴权，Step2 已经完成 Nacos 注册发现，Step3 已经跑通 trade-service
通过 OpenFeign 查询课程的链路。此时接入 MySQL，可以把课程服务从“验证远程调用”推进到“查询真实持久化数据”，
又不会同时引入缓存、消息队列或分布式事务，问题边界仍然清晰。

## 3. MySQL 在当前项目中的作用

MySQL 只负责保存课程基础数据，包括标题、介绍、价格、状态、讲师、封面、创建更新时间和逻辑删除标记。
当前只有 `edu-course-service` 可以直接访问数据库，Gateway、用户服务和交易服务都不依赖 MySQL。

## 4. 涉及模块

### edu-course-service

- 增加 MyBatis 和 MySQL 驱动
- 配置独立数据源
- 使用 Mapper 查询 `course` 表
- 提供课程详情、ONLINE 课程列表和内部课程查询接口
- 移除 Step3 的静态内存课程 `Map`

### edu-trade-service

- `CourseClient`、`CourseInfoDTO` 和交易预览业务保持不变
- 只补齐 Spring Boot 打包插件，使模块可以通过 `java -jar` 启动
- 不增加任何数据库依赖

### edu-gateway

- 继续对 `/course/**` 和 `/trade/**` 进行 JWT 鉴权
- 继续清洗客户端伪造的 `X-User-*` 请求头
- 路由、JWT 和依赖均未修改

### edu-user-service

- 继续提供 `POST /user/login`
- 本阶段没有修改

### edu-common

- 继续提供统一 `Result` 和 `BizException`
- 不放置课程数据库实体或 Mapper

## 5. 本阶段核心链路

```text
课程详情：
客户端 -> Gateway JWT 鉴权 -> lb://edu-course-service
      -> CourseController -> CourseService -> CourseMapper -> MySQL

交易预览：
客户端 -> Gateway JWT 鉴权 -> lb://edu-trade-service
      -> OpenFeign + Nacos Discovery -> edu-course-service
      -> CourseService -> CourseMapper -> MySQL
      -> trade-service 合并用户和课程信息后返回
```

## 6. 版本和新增依赖

根 POM 继续使用原有版本管理：

| 组件 | 版本 |
| --- | --- |
| Java | `17+` |
| Spring Boot | `3.3.5` |
| Spring Cloud | `2023.0.3` |
| Spring Cloud Alibaba | `2023.0.3.3` |
| MyBatis Spring Boot Starter | `3.0.3` |
| MySQL Connector/J | 由 Spring Boot 依赖管理解析 |

仅 `edu-course-service` 新增：

| 依赖 | 作用 |
| --- | --- |
| `mybatis-spring-boot-starter` | 集成 MyBatis、Spring JDBC、Mapper 和数据源 |
| `mysql-connector-j` | 提供 MySQL JDBC 驱动，使用 runtime scope |

`spring-boot-maven-plugin` 是构建插件，不是数据库依赖。它被补充到 course-service 和 trade-service，
用于生成可执行 Spring Boot JAR。

## 7. 新增和修改文件清单

### edu-course-service

- `pom.xml`：增加 MyBatis、MySQL 驱动和 Spring Boot 打包插件
- `application.yml`：增加 datasource、MyBatis 和 Mapper 日志配置
- `controller/CourseController.java`：外部课程详情和列表接口
- `controller/CourseInternalController.java`：保留内部 Feign 查询入口
- `service/CourseService.java`：课程查询服务接口
- `service/impl/CourseServiceImpl.java`：参数校验、MySQL 查询和 DTO 转换
- `mapper/CourseMapper.java`：课程数据访问接口
- `resources/mapper/CourseMapper.xml`：课程查询 SQL 和字段映射
- `entity/CourseEntity.java`：课程数据库实体
- `dto/CourseDTO.java`：Service 层课程数据对象
- `vo/CourseDetailVO.java`：课程详情返回对象
- `vo/CourseListItemVO.java`：课程列表项返回对象
- `vo/CourseInfoVO.java`：继续作为内部 Feign 接口返回对象
- `handler/GlobalExceptionHandler.java`：课程业务异常和数据库异常统一处理

### edu-trade-service

- `pom.xml`：补齐 Spring Boot 打包插件

### 文档

- `docs/sql/step-4-course.sql`
- `docs/step-4-course-mysql.md`
- `README.md`

## 8. course 表设计

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 自增主键 |
| `title` | `VARCHAR(200)` | 课程标题 |
| `description` | `TEXT` | 课程介绍 |
| `price` | `INT UNSIGNED` | 课程价格，单位为分 |
| `status` | `VARCHAR(20)` | `DRAFT`、`ONLINE`、`OFFLINE` |
| `teacher_name` | `VARCHAR(100)` | 讲师名称 |
| `cover_url` | `VARCHAR(500)` | 封面地址 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |
| `deleted` | `TINYINT UNSIGNED` | 逻辑删除：0 未删除，1 已删除 |

金额继续使用整数分，避免浮点精度问题，并兼容 Step3 的 `Integer price` 契约。

## 9. 索引设计

```text
PRIMARY KEY (id)
idx_course_status_deleted_id (status, deleted, id)
```

按 ID 查询使用主键；列表查询固定筛选 `status='ONLINE' AND deleted=0`，因此建立状态、删除标记和 ID
联合索引。当前不做标题搜索和复杂分页，不为标题或价格增加额外索引。

## 10. 初始化数据

| ID | 标题 | 价格（分） | 状态 |
| --- | --- | --- | --- |
| 1 | Java 微服务入门课 | 19900 | ONLINE |
| 2 | Spring Cloud Alibaba 实战课 | 29900 | ONLINE |
| 3 | Redis 高并发缓存课 | 25900 | OFFLINE |

SQL 使用 `CREATE TABLE IF NOT EXISTS` 和主键冲突更新，可重复执行，并将 1～3 号本地演示课程恢复为约定数据。

## 11. datasource 和 MyBatis 配置

course-service 使用独立数据源：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/edu_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: edu_user
    password: edu_pass123
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath:/mapper/*.xml
  type-aliases-package: com.dyl.edu.course.entity
  configuration:
    map-underscore-to-camel-case: true
```

账号密码仅用于本地学习环境。当前不接入 Nacos Config，也不引入额外配置中心。

## 12. Controller / Service / Mapper / Entity / DTO / VO 分层

- Controller：只接收路径参数、调用 Service、将 DTO 组装为 VO 并返回统一 `Result`
- Service：校验课程 ID、处理课程不存在、调用 Mapper、完成 Entity 到 DTO 的转换
- Mapper：只负责 SQL 查询，不承载业务逻辑
- Entity：与数据库字段对应，不直接暴露给接口调用方
- DTO：在 Service 和 Controller 之间传递课程数据
- VO：根据详情、列表、内部 Feign 三种接口分别组织返回字段

Controller 不写 SQL，Mapper 不处理课程不存在等业务异常。

## 13. 接口设计

### GET /course/{courseId}

查询指定的未删除课程，通过 Gateway 访问时需要 token。成功时返回标题、介绍、价格、状态、讲师、封面和时间；
课程不存在或已逻辑删除时返回 `code=404`、`message=课程不存在`。

### GET /course/list

返回全部未删除的 ONLINE 课程，按 ID 升序排列。本阶段不做复杂分页和搜索。

### GET /course/internal/{courseId}

继续提供给 trade-service 的 `CourseClient`，返回字段保持为 `courseId/title/price/status`。
数据来源从 Step3 的静态 Map 改为 MySQL。

## 14. Step1、Step2、Step3 链路是否变化

- Gateway JWT：未修改，新增课程接口自动受到现有全局过滤器保护
- 请求头清洗：未修改，伪造 `X-User-*` 仍会先删除再由 JWT 重建
- Nacos Discovery：服务名、注册地址和 `lb://` 路由均未修改
- OpenFeign：`CourseClient`、路径和 `CourseInfoDTO` 均未修改
- trade-service：继续通过 `/course/internal/{courseId}` 获取课程数据

变化只发生在 course-service 内部的数据来源。

## 15. 本阶段没有做什么

- 没有接入 Redis、RabbitMQ、Spring Security
- 没有接入 Sentinel、Seata、XXL-JOB、SkyWalking、Prometheus
- 没有接入 Nacos Config、Flyway、Liquibase、Docker Compose、JMeter
- 没有实现订单、支付、优惠券、库存、学习进度或排行榜
- 没有实现课程后台增删改、章节、分类、复杂搜索和分页优化
- 没有伪造压测结果或性能数据

## 16. 启动 MySQL 容器

首次创建容器的一行 PowerShell 命令：

```powershell
docker run -d --name mysql-edu-platform -e MYSQL_ROOT_PASSWORD=root123456 -e MYSQL_DATABASE=edu_platform -e MYSQL_USER=edu_user -e MYSQL_PASSWORD=edu_pass123 -e TZ=Asia/Shanghai -p 3307:3306 -v mysql-edu-platform-data:/var/lib/mysql mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

便于阅读的 PowerShell 多行版本：

```powershell
docker run -d --name mysql-edu-platform `
  -e MYSQL_ROOT_PASSWORD=root123456 `
  -e MYSQL_DATABASE=edu_platform `
  -e MYSQL_USER=edu_user `
  -e MYSQL_PASSWORD=edu_pass123 `
  -e TZ=Asia/Shanghai `
  -p 3307:3306 `
  -v mysql-edu-platform-data:/var/lib/mysql `
  mysql:8.0 `
  --character-set-server=utf8mb4 `
  --collation-server=utf8mb4_unicode_ci
```

容器已存在时执行：

```powershell
docker start mysql-edu-platform
```

本机已有 `mysqld` 占用 3306，因此容器使用宿主机端口 3307，容器内仍为 3306。项目当前的
本地连接信息是：`localhost:3307`、数据库 `edu_platform`、业务用户 `edu_user`、业务密码
`edu_pass123`，root 密码为 `root123456`。这些凭据只用于本地学习环境。

## 17. 初始化和检查课程表

等待 MySQL 初始化完成后，从项目根目录执行：

```powershell
docker cp .\docs\sql\step-4-course.sql mysql-edu-platform:/tmp/step-4-course.sql
docker exec mysql-edu-platform mysql --default-character-set=utf8mb4 -uedu_user -pedu_pass123 edu_platform -e "source /tmp/step-4-course.sql"
```

使用 `docker cp` 可以避免 Windows PowerShell 管道改变 UTF-8 SQL 的编码。脚本使用
`CREATE TABLE IF NOT EXISTS` 和主键冲突更新，重复执行会把 1～3 号演示课程恢复为约定数据。

```powershell
docker exec -it mysql-edu-platform mysql -uedu_user -pedu_pass123 edu_platform
```

```sql
SHOW CREATE TABLE course;
SELECT id, title, price, status, teacher_name, deleted FROM course ORDER BY id;
EXPLAIN SELECT * FROM course WHERE status = 'ONLINE' AND deleted = 0 ORDER BY id;
```

课程 1、2 为 `ONLINE`，课程 3 为 `OFFLINE`。列表只返回课程 1、2，按 ID 仍可查询课程 3。

## 18. 编译和启动

```powershell
mvn clean package -DskipTests
docker start nacos-standalone
docker start mysql-edu-platform
docker ps
```

分别在四个终端启动：

```powershell
java -jar .\edu-user-service\target\edu-user-service-0.0.1-SNAPSHOT.jar
java -jar .\edu-course-service\target\edu-course-service-0.0.1-SNAPSHOT.jar
java -jar .\edu-trade-service\target\edu-trade-service-0.0.1-SNAPSHOT.jar
java -jar .\edu-gateway\target\edu-gateway-0.0.1-SNAPSHOT.jar
```

端口依次为 `8081`、`8082`、`8083`、`8080`。运行时必须使用 Java 17 或更高版本；可先执行
`java -version` 检查。Nacos 中应有四个健康实例。

## 19. PowerShell 接口验收

```powershell
$loginBody = @{ username = 'test'; password = '123456' } | ConvertTo-Json
$loginResult = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/user/login' -ContentType 'application/json' -Body $loginBody
$token = $loginResult.data.token
$headers = @{ Authorization = "Bearer $token" }

Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/1' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/list' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/1' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/course/999' -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/999' -Headers $headers
```

验证伪造用户头会被 Gateway 清洗：

```powershell
$fakeHeaders = @{
  Authorization = "Bearer $token"
  'X-User-Id' = '9999'
  'X-User-Name' = 'hacker'
  'X-User-Role' = 'ADMIN'
}
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/trade/preview/1' -Headers $fakeHeaders
```

不带 token 时观察 HTTP 401：

```powershell
curl.exe -i http://localhost:8080/course/1
curl.exe -i http://localhost:8080/trade/preview/1
```

## 20. 已验证通过的结果

本地已经实际验证：

- `mvn clean package -DskipTests` 执行通过，Maven Reactor 中 6 个模块均为 `SUCCESS`
- `mysql-edu-platform` 和 `nacos-standalone` 容器正常运行
- Nacos 中四个服务各有 1 个健康实例
- `POST /user/login` 可以返回 JWT
- `GET /course/1` 返回课程 1，价格为 19900 分
- `GET /course/list` 只返回 2 条 ONLINE 课程
- `GET /trade/preview/1` 返回 `1001/test/STUDENT` 和课程 1 数据
- `/course/999`、`/trade/preview/999` 返回明确的“课程不存在”
- 不带 token 访问课程和交易接口返回 HTTP 401
- 携带伪造 `X-User-*` 时仍返回 JWT 中的 `1001/test/STUDENT`
- MySQL 表、联合索引、表排序规则和中文 UTF-8 字节均已检查

本阶段没有执行压测，也没有性能指标。

## 21. IDEA Run Console 观察重点

course-service 控制台应看到 `进入课程详情接口`、`开始从 MySQL 查询课程详情`、
`从 MySQL 查询课程成功`、`开始从 MySQL 查询已上线课程列表` 和 `课程不存在` 等中文日志，
同时可看到 `CourseMapper` 的 SQL 和参数。

## 22. Nacos 控制台观察重点

| 服务名 | 端口 | 预期状态 |
| --- | --- | --- |
| `edu-gateway` | 8080 | 健康 |
| `edu-user-service` | 8081 | 健康 |
| `edu-course-service` | 8082 | 健康 |
| `edu-trade-service` | 8083 | 健康 |

重点检查服务名、IP、端口和健康状态。接入 MySQL 不会改变 course-service 在 Nacos 中的服务名。

## 23. MySQL 中的观察重点

进入容器：

```powershell
docker exec -it mysql-edu-platform mysql --default-character-set=utf8mb4 -uedu_user -pedu_pass123 edu_platform
```

检查表、数据、索引和字符集：

```sql
SHOW CREATE TABLE course;
SELECT id, title, price, status, teacher_name, deleted FROM course ORDER BY id;
SHOW INDEX FROM course;
SELECT TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'edu_platform' AND TABLE_NAME = 'course';
```

应看到 3 条初始化数据、`idx_course_status_deleted_id` 联合索引和 `utf8mb4_unicode_ci` 排序规则。

## 24. 常见问题和排查方式

### 24.1 MySQL 容器未启动

如果 course-service 查询时报连接拒绝，先执行：

```powershell
docker ps -a --filter "name=mysql-edu-platform"
docker start mysql-edu-platform
docker logs mysql-edu-platform
```

### 24.2 宿主机端口冲突

本机已有 `mysqld` 占用 3306，因此 Docker MySQL 使用 `3307:3306`。可以通过下面的命令检查端口：

```powershell
Get-NetTCPConnection -LocalPort 3306,3307 -State Listen
```

如果开发机没有端口冲突，也可以改回 `3306:3306`，但必须同步修改 course-service JDBC URL。

### 24.3 数据库连接失败

检查容器状态、宿主机端口、数据库名、用户名和密码是否与 `application.yml` 一致。MySQL 首次启动需要初始化时间，
可以使用 `mysqladmin ping` 判断是否就绪。

### 24.4 course 表不存在

确认已经执行 `docs/sql/step-4-course.sql`，并确认当前连接的数据库是 `edu_platform`。

### 24.5 Mapper 没有注册

`CourseMapper` 使用 `@Mapper`，不依赖启动类上的 `@MapperScan`。如果注入失败，检查接口包路径和 MyBatis Starter 是否存在。

### 24.6 Mapper XML 没有加载

检查：

- XML 是否位于 `src/main/resources/mapper`
- `mapper-locations` 是否为 `classpath:/mapper/*.xml`
- XML namespace 是否等于 `com.dyl.edu.course.mapper.CourseMapper`
- XML 方法 ID 是否与接口方法一致

### 24.7 字段映射失败

`teacher_name`、`cover_url`、`created_at` 等下划线字段通过显式 `resultMap` 映射，同时配置了驼峰映射。
出现 null 时优先检查 SQL 列名和 Entity 属性名。

### 24.8 PowerShell 导入 SQL 后中文乱码

Windows PowerShell 管道可能改变 UTF-8 SQL 编码。不要直接使用 `Get-Content | docker exec`，应先 `docker cp`，
并让 MySQL 客户端显式使用：

```text
--default-character-set=utf8mb4
```

接口中文显示异常但数据库字节正常时，可能只是终端解码问题，可以使用 `curl.exe`、PowerShell 7 或 IDEA HTTP Client 复核。

### 24.9 时区问题

Docker 设置 `TZ=Asia/Shanghai`，JDBC URL 设置 `serverTimezone=Asia/Shanghai`。如果时间仍有偏差，继续检查 JVM 和操作系统时区。

### 24.10 运行时 Java 版本不正确

项目编译目标是 Java 17。默认 `java` 如果低于 17，会出现 `UnsupportedClassVersionError`。启动前执行：

```powershell
java -version
```

并在 IDEA Project SDK 和 Run Configuration 中选择 Java 17 或更高版本。

## 25. 当前限制

- 本地数据库账号密码直接写在 `application.yml`，仅适合学习环境
- `/course/internal/**` 只是语义上的内部接口，尚未实现网络隔离或服务间认证
- 课程列表没有分页、搜索和复杂排序
- 没有自动数据库迁移，首次运行必须手动执行 SQL
- 没有数据库集成测试
- course-service 数据库异常统一返回通用错误，尚未设计完整错误码体系
- 直接访问业务服务端口仍会绕过 Gateway，真实部署需要通过网络边界限制入口

## 26. 面试复盘要点

可以这样说明：

> Step4 在 Gateway 鉴权、Nacos 服务发现和 OpenFeign 调用链已经稳定后，只让 course-service 接入 MySQL。
> 使用 MyBatis 将 Controller、Service、Mapper、Entity、DTO、VO 分层，课程详情按主键查询，列表只查询 ONLINE
> 且未删除的课程。trade-service 继续调用原来的内部接口，因此上游不需要感知课程数据从内存迁移到了数据库。
> 金额使用整数分，列表查询建立状态、逻辑删除和 ID 联合索引。这个阶段刻意不引入缓存、消息队列和订单业务，
> 保证改动可运行、可解释、可回滚。

可以重点讲：

1. 为什么数据库依赖只放在 course-service。
2. 为什么保留内部 Feign 接口契约。
3. Entity、DTO、VO 为什么分开。
4. 为什么金额使用整数分。
5. 联合索引如何对应 ONLINE 列表查询。
6. 如何排查 Docker 端口冲突、Mapper 加载和中文编码问题。
7. 为什么此时不提前接入 Redis 和分布式事务。

## 27. 本阶段边界

Step4 到此只完成课程基础数据持久化和查询。下一阶段必须重新确认目标后再实施；当前文档和代码没有提前进入
Redis、优惠券、订单、支付、库存、学习进度或排行榜等后续能力。
