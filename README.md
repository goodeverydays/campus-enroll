# CampusEnroll

面向既有高校教务系统的分布式高并发选课平台。项目以增量式现代化为目标：
旧教务系统继续持有身份、学籍和成绩等既有能力，CampusEnroll 独立承担课程查询与
选课链路，并通过 SSO / Token 与 REST API 集成。

> 当前状态：Phase 2.5 SSO / JWT 身份边界。课程与学生基础业务已经可运行；Auth
> Service 可签发一次性 SSO 票据并兑换短期 JWT，Gateway 会验证 JWT、清除客户端
> 伪造的学生身份头并注入可信学生 ID。仍没有 Redis Lua、RabbitMQ 高并发逻辑或
> 真实选课业务。

## 技术基线

| Component | Version / line |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.0 |
| Spring Cloud | 2025.0.0 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Nacos | 3.0.3 |
| MySQL | 8.4 LTS (`8.4.11` image) |
| Redis | 7.4.11 |
| RabbitMQ | 4.2.9 Management |

Spring Cloud Alibaba 官方兼容矩阵将 `2025.0.0.0`、Spring Cloud `2025.0.0`
与 Spring Boot `3.5.0` 配成同一组，并对应 Nacos `3.0.3`，因此 Phase 1 固定
使用这组版本，不盲目追逐最新版本。

## 目录结构

```text
campus-enroll/
├─ frontend/                       # Vue 3 占位；Phase 2 初始化
├─ services/
│  ├─ gateway-service/             # 外部 API 唯一入口与静态路由
│  ├─ auth-service/                # 一次性 SSO 票据、身份映射与 JWT 签发
│  ├─ student-service/             # 学生资料、资格与旧系统幂等同步
│  ├─ course-service/              # 课程、学期、教师、开课班与课表查询
│  ├─ enrollment-service/          # 选课请求入口（仅骨架）
│  └─ enrollment-worker/           # 异步落库进程（仅骨架）
├─ infrastructure/mysql/init/      # 首次建库与授权
├─ scripts/                         # 本地烟雾与恢复验证
├─ .github/workflows/               # 持续集成
├─ .mvn/wrapper/                    # 固定 Maven 发行版
├─ docs/                           # API 与数据库边界说明
├─ compose.yaml
├─ Dockerfile                      # 六个服务共用的多阶段构建文件
├─ mvnw / mvnw.cmd                 # Maven Wrapper 3.3.4
└─ pom.xml                         # Maven 聚合父项目
```

模块间不建立 Java 代码依赖。服务通过 REST、后续的消息契约和稳定 ID 协作，避免
把微服务重新耦合成一个共享类库。

## 快速启动

前置条件：Docker Desktop（含 Compose v2）。首次构建需要访问 Maven Central 和
Docker Hub。

PowerShell：

```powershell
Copy-Item .env.example .env
# 打开 .env，把所有 replace_this_* 示例值改成仅供本机开发的强随机值
.\mvnw.cmd clean verify
docker compose config
docker compose up -d --build
docker compose ps
.\scripts\verify-phase1.ps1
```

所有映射端口默认只绑定 `127.0.0.1`，并使用独立的宿主机端口以避免常见开发服务冲突。
如确实需要局域网访问，可在 `.env` 中显式
设置 `BIND_ADDRESS`，同时补充防火墙和鉴权策略。Nacos 在本地 Compose 中关闭
鉴权，因此不得将其暴露到公网。

## 本地入口

| Purpose | URL |
| --- | --- |
| Gateway | `http://localhost:18000` |
| Nacos console | `http://localhost:18080` |
| RabbitMQ management | `http://localhost:25673` |
| Auth Swagger | `http://localhost:18081/swagger-ui.html` |
| Student Swagger | `http://localhost:18082/swagger-ui.html` |
| Course Swagger | `http://localhost:18083/swagger-ui.html` |
| Enrollment Swagger | `http://localhost:18084/swagger-ui.html` |

服务健康检查示例：

```powershell
Invoke-RestMethod http://localhost:18000/actuator/health
Invoke-RestMethod http://localhost:18083/actuator/health
Invoke-RestMethod http://localhost:18083/internal/info
```

## 仅构建后端

需要 JDK 21；Maven 由 Wrapper 固定为 3.9.16：

```powershell
.\mvnw.cmd clean verify
```

Wrapper 使用 Apache 官方 `only-script` 发行方式，不在仓库中提交 Wrapper JAR，
并通过 `distributionSha256Sum` 校验下载的 Maven 发行包。

## 数据库边界

单个本地 MySQL 实例承载四个逻辑数据库：`campus_auth`、`campus_student`、
`campus_course`、`campus_enrollment`。每张表只有一个服务所有者，跨服务引用只保存
ID，不创建跨库外键。详细设计见 [docs/database-design.md](docs/database-design.md)。

Compose 初始化 SQL 仅负责创建数据库和授权。各服务通过自己的 Flyway 迁移维护表：

- `auth-service`: `V1__create_auth_schema.sql`
- `student-service`: `V1__create_student_schema.sql`
- `course-service`: `V1__create_course_schema.sql`
- `enrollment-service`: `V1__create_enrollment_schema.sql`

已经执行过的迁移文件不可修改；后续变更必须新增更高版本迁移。`baseline-on-migrate`
仅用于兼容 Phase 1 早期创建的本地数据库卷。

## Gateway 路由

Gateway 通过 Nacos 和 `lb://` 服务名转发下列边界：

| Path | Target |
| --- | --- |
| `/api/v1/auth/**` | auth-service |
| `/api/v1/students/**` | student-service |
| `/api/v1/courses/**` | course-service |
| `/api/v1/semesters/**` | course-service |
| `/api/v1/teachers/**` | course-service |
| `/api/v1/course-offerings/**` | course-service |
| `/api/v1/enrollments/**` | enrollment-service |
| `/api/v1/enrollment-requests/**` | enrollment-service |
| `/_internal/smoke/course` | course-service `/internal/info` |

课程目录和认证兑换路由已经连接真实 Controller。学生同步与 SSO 票据签发是内部系统
接口，不经过 Gateway。`GET /api/v1/students/me` 必须携带 Auth Service 签发的 JWT；
Gateway 不信任客户端传来的 `X-Student-Id`。完整约定见
[docs/api-conventions.md](docs/api-conventions.md)。

Phase 2 查询接口验证：

```powershell
.\scripts\verify-phase2.ps1
```

该脚本验证 Gateway 课程目录、统一 400/404 错误、内部学生同步校验和 OpenAPI 路径。

Phase 2.5 认证链路验证：

```powershell
.\scripts\verify-auth.ps1
```

脚本验证内部系统密钥、一次性票据、JWT 签名边界、网关统一 401、伪造学生身份头覆盖、
票据重放拒绝和数据库哈希存储，并在结束时删除本次创建的临时数据。当前 Compose 的
`LEGACY_SYSTEM_API_KEY` 仅用于本地系统间认证；生产环境应改为 mTLS 或受管的服务身份。

需要验证基础设施重启恢复时运行：

```powershell
.\scripts\verify-phase1.ps1 -IncludeRecovery
```

该参数会依次重启 MySQL、Redis、RabbitMQ 和 Nacos，只应用于本地开发环境。

## 停止环境

```powershell
docker compose down
```

该命令保留数据库与中间件卷。只有在明确接受丢失本地数据时才使用
`docker compose down -v`。

## 分阶段路线

1. Phase 1：项目骨架、基础设施、注册发现、路由、Schema、OpenAPI。
2. Phase 2：课程与学生基础业务、旧系统适配接口。
3. Phase 2.5：一次性 SSO 票据、短期 JWT 与 Gateway 可信身份边界。
4. Phase 3：基于 MySQL 事务的普通选课基线。
5. Phase 4：Redis 缓存与 Lua 原子预占。
6. Phase 5：RabbitMQ 异步削峰。
7. Phase 6：Confirm、ACK、幂等、重试、DLQ 与补偿。
8. Phase 7：Prometheus/Grafana、压测和实验数据分析。

## License

本项目采用 [MIT License](LICENSE)。
