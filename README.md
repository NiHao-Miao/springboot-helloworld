# Spring Boot 3.2.0 + Spring Security：Hello World 受保护接口

一个可直接运行的后端示例，演示：

- 基于 Spring Security 的**无状态 JWT 认证**
- 用户名/密码登录接口，演示账号：`test` / `123456`
- 需要认证才能访问的 `GET /api/hello`，返回纯文本 `Hello World`
- 统一的 JSON 错误响应、请求参数校验、单测/集成测试

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Java | 17+ |
| Spring Boot | 3.2.0 |
| Spring Security | 6.2.x（由 Spring Boot 管理） |
| JWT | JJWT 0.12.3 |
| 构建工具 | Maven 3.9+（仓库包含 Maven Wrapper） |
| 测试 | JUnit 5 + MockMvc |

## 快速开始

```bash
./mvnw spring-boot:run
```

服务默认监听 `http://localhost:8080`。

> Windows 使用 `mvnw.cmd spring-boot:run`。如果本机已有 Maven，也可以直接执行 `mvn spring-boot:run`。

## API 使用

### 1. 登录，获取 JWT

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"123456"}'
```

成功响应（`200 OK`）：

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-11-13T12:33:28Z"
}
```

### 2. 携带 JWT 访问 Hello World 接口

下面的命令使用 `jq` 提取 Token；如果本机没有安装 `jq`，也可以从登录响应中手动复制 `accessToken`。

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"123456"}' | jq -r '.accessToken')

curl -i http://localhost:8080/api/hello \
  -H "Authorization: Bearer $TOKEN"
```

成功响应（`200 OK`，`Content-Type: text/plain`）：

```text
Hello World
```

### 3. 未认证访问

```bash
curl -i http://localhost:8080/api/hello
```

返回 `401 Unauthorized`，并带有 `WWW-Authenticate: Bearer`：

```json
{
  "timestamp": "2026-11-13T11:33:28Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required",
  "path": "/api/hello"
}
```

### 接口一览

| 方法 | 路径 | 是否需要认证 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/auth/login` | 否 | `{"username":"...","password":"..."}` | `200` + JWT JSON |
| `GET` | `/api/hello` | 是（Bearer Token） | - | `200` + `Hello World` |

## 配置

配置位于 `src/main/resources/application.yml`：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `app.jwt.secret` | 内置演示密钥 | Base64 编码，解码后至少 32 字节（256 bit） |
| `app.jwt.expiration` | `PT1H` | Token 有效期，ISO-8601 Duration 格式 |

生产环境请通过环境变量覆盖演示密钥：

```bash
export APP_JWT_SECRET="$(openssl rand -base64 32)"
export APP_JWT_EXPIRATION="PT1H"
./mvnw spring-boot:run
```

> 仓库中的默认密钥仅用于本地演示，不能用于生产环境。生产环境还应把内存用户替换为数据库用户，并对密码使用 BCrypt 等慢哈希存储。

## 测试

```bash
./mvnw test
```

测试覆盖：

- 正确用户名/密码登录并返回 JWT
- 错误密码返回 `401`
- 空用户名返回 `400`
- 非法 JSON 返回 `400`
- 未携带 Token 访问 `/api/hello` 返回 `401`
- 伪造/格式错误的 Token 返回 `401`
- 携带有效 Token 访问 `/api/hello` 返回 `Hello World`

## 设计说明

- **认证流程**：登录接口通过 `AuthenticationManager` 校验用户名和密码；校验成功后由 `JwtService` 签发 HS256 JWT。`JwtAuthenticationFilter` 在每次请求时校验 `Authorization: Bearer <token>`，并把认证信息写入 `SecurityContext`。
- **无状态**：`SessionCreationPolicy.STATELESS`，服务端不保存会话；适合水平扩展的 API 服务。由于不使用 Cookie 会话，CSRF 被关闭，认证依赖 Bearer Token。
- **密码存储**：演示用内存用户也使用 `BCryptPasswordEncoder` 存储哈希，不保存明文密码。
- **统一错误**：认证失败、授权失败、参数校验失败都返回结构一致的 JSON，避免把底层异常细节暴露给客户端。
- **JWT 校验**：同时校验签名、签发方（issuer）和过期时间；无效 Token 按未认证处理，不会放行请求。
- **可替换性**：`UserDetailsService` 目前是 `InMemoryUserDetailsManager`，接入数据库时只需替换该 Bean，认证和 JWT 逻辑无需改动。

## 项目结构

```text
springboot-helloworld/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/wrapper/              # Maven Wrapper 配置
├── src/main/java/com/example/helloworld/
│   ├── HelloWorldApplication.java
│   ├── auth/                 # 登录接口、请求/响应模型
│   ├── config/               # Security 配置、JWT 配置属性
│   ├── hello/                # 受保护的 Hello World 接口
│   ├── security/             # JWT 签发/校验、认证过滤器、401/403 处理
│   └── web/                  # 统一错误模型和异常处理
├── src/main/resources/application.yml
├── src/test/java/...         # MockMvc 集成测试
└── src/test/resources/application.yml
```
