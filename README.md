# auth-service

`auth-service` 是一个可复用的通用用户中心和多系统权限中心。项目目标是为多个业务系统提供统一的用户身份、认证凭证、权限数据和基础安全能力，避免每个系统重复建设账号体系、登录认证、密码加密、权限模型和接口文档能力。

当前仓库处于基础工程阶段，已完成核心技术栈依赖引入。后续业务实现应围绕用户、角色、权限、系统应用、登录会话、令牌签发与校验等领域模型展开。

## 项目定位

`auth-service` 作为独立认证授权服务，主要承担以下职责：

- 统一用户中心：集中管理用户账号、基础资料、账号状态、密码凭证和登录安全策略。
- 多系统权限中心：为不同业务系统维护统一的角色、权限、资源和授权关系。
- 认证入口：处理登录、登出、Token 签发、Token 刷新和认证状态校验。
- 权限支撑：为网关、后端服务或前端应用提供用户身份和权限数据。
- 可复用基础设施：沉淀通用的安全、数据访问、迁移、缓存、接口文档和服务治理能力。

## 技术栈

| 技术 | 当前依赖 | 作用 |
| --- | --- | --- |
| JDK 17 | `java.version=17` | 提供 Spring Boot 3.x 所需的 Java 基线，并支持现代 Java 语言特性。 |
| Spring Boot 3.5.x | `spring-boot-starter-parent:3.5.8` | 作为应用基础框架，统一依赖版本、自动配置、测试和打包能力。 |
| Spring Web | `spring-boot-starter-web` | 支撑 REST API，对外提供登录、用户、角色、权限等 HTTP 接口。 |
| Bean Validation | `spring-boot-starter-validation` | 对请求参数、DTO 和配置对象做声明式校验，减少重复校验代码。 |
| MyBatis-Plus | `mybatis-plus-spring-boot3-starter` | 提供用户、角色、权限等业务表的 ORM 能力和常用 CRUD 基础能力。 |
| MySQL | `mysql-connector-j` | 作为生产环境主数据库，持久化账号、凭证、授权关系和审计数据。 |
| Redis | `spring-boot-starter-data-redis` | 支撑验证码、登录限流、Token 黑名单、权限缓存和临时会话数据。 |
| Flyway | `flyway-core`、`flyway-mysql` | 管理数据库 schema 演进，保证多环境数据库结构可追踪、可回放。 |
| Lombok | `lombok` | 简化实体、DTO、配置类等 Java 样板代码。 |
| JWT | `jjwt-api`、`jjwt-impl`、`jjwt-jackson` | 支撑无状态访问令牌的生成、解析和校验。 |
| BCrypt | `spring-security-crypto` | 提供 BCrypt 密码哈希能力，用于安全存储用户密码。 |
| Knife4j / OpenAPI | `knife4j-openapi3-jakarta-spring-boot-starter` | 生成和展示接口文档，方便前后端联调和多系统接入。 |
| Spring Cloud Alibaba | `spring-cloud-starter-alibaba-nacos-discovery` | 支撑服务注册与发现，便于在微服务架构中被网关和业务服务调用。 |
| Spring Boot Actuator | `spring-boot-starter-actuator` | 暴露健康检查和运行状态，为部署、监控和服务治理提供基础端点。 |

## 技术栈对权限中心的支撑

### API 与服务边界

Spring Boot 和 Spring Web 提供标准的 HTTP 服务能力，适合将认证授权能力封装为独立服务。业务系统不直接处理密码、登录状态和核心权限规则，而是通过 `auth-service` 获取认证结果和授权数据。

Bean Validation 用于统一校验入参，例如登录请求、用户创建、角色授权和权限绑定请求。校验规则靠近 DTO 定义，可以减少控制器和业务代码中的重复判断。

Knife4j / OpenAPI 用于沉淀接口契约。作为多系统共享的用户中心，清晰的接口文档可以降低接入成本，并减少调用方对接口语义的误解。

### 数据与模型演进

MyBatis-Plus 用于实现用户、角色、权限、菜单、资源、应用系统等表的访问层。它能提供常见 CRUD 能力，同时保留 SQL 和 Mapper 的可控性，适合权限系统中复杂查询逐步演进。

MySQL 作为核心持久化存储，保存强一致要求较高的数据，例如用户账号、密码哈希、角色权限绑定、系统应用配置和授权策略。

Flyway 用于维护数据库版本。权限中心通常会被多个环境和多个系统依赖，数据库结构必须可重复初始化、可追踪升级，避免手工改库导致环境不一致。

### 认证、令牌与密码安全

JWT 用于生成访问令牌，适合跨系统传递用户身份、租户、角色或权限摘要等信息。业务系统可以基于签名校验 Token，降低对认证服务的同步依赖。

BCrypt 用于密码哈希。用户原始密码不应被存储，BCrypt 的慢哈希特性可以提高离线暴力破解成本，是用户中心的基础安全能力。

Redis 可用于补足 JWT 的状态控制，例如登录验证码、登录失败计数、Token 黑名单、刷新令牌状态、用户权限缓存和短期风控数据。

### 微服务治理与运维

Spring Cloud Alibaba Nacos Discovery 用于服务注册与发现。在微服务架构中，网关、业务服务和管理后台可以通过服务名调用 `auth-service`，避免硬编码服务地址。

Spring Boot Actuator 用于健康检查和运行状态暴露。部署到容器或微服务平台后，可以通过健康端点判断服务是否可用，并接入监控系统。

## 当前配置

当前 [application.yaml](src/main/resources/application.yaml) 已配置服务名、MySQL 数据源和 Flyway：

```yaml
spring:
  application:
    name: auth-service
  datasource:
    url: jdbc:mysql://localhost:3306/auth?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Tokyo&useSSL=false&allowPublicKeyRetrieval=true
    username: auth_root
    password: authroot123456
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

Flyway 初始化脚本位于 [V1__init_auth_schema.sql](src/main/resources/db/migration/V1__init_auth_schema.sql)，用于创建用户中心和权限中心的基础表，包括应用、用户、角色、权限、用户角色关系、角色权限关系、登录日志和 Token 黑名单。

测试环境使用 [src/test/resources/application.yaml](src/test/resources/application.yaml) 覆盖主数据源为 H2 内存数据库，并关闭 Flyway，避免本地未启动 MySQL 时影响基础测试。

后续仍需要按部署环境补充 Redis、Nacos、JWT、Knife4j/OpenAPI 等配置。建议使用不同 profile 管理本地、测试和生产环境配置，避免敏感信息提交到仓库。

## 后续建设建议

- 定义用户、角色、权限、资源、应用系统等核心领域模型。
- 设计登录、Token 签发、Token 刷新、登出和密码修改接口。
- 引入统一异常处理、响应结构和参数校验错误返回。
- 增加 JWT 配置项，例如签名密钥、访问令牌有效期和刷新令牌有效期。
- 增加 Redis 缓存策略，例如验证码、Token 黑名单和权限缓存。
- 配置 Knife4j 分组和接口元信息，形成稳定的接入文档。
