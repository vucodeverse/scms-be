# SCMS Backend (scms-be)

Spring Boot **4.1.2** · Java 21 · Maven · Spring Data JPA + **MySQL** · Spring Security 7 + JWT · springdoc **3.2** (Swagger UI) · Lombok + MapStruct · Logback JSON.

> Tài liệu nghiệp vụ / API gốc: `IMPLEMENTATION.md` (nguồn sự thật). Repo này giữ **package gốc `edu.fpt.sba.scms`** (tương ứng `com.scms` trong tài liệu).

## Chạy nhanh (dev)

1. Tạo database: `CREATE DATABASE scms CHARACTER SET utf8mb4;`
2. Sửa `src/main/resources/application-dev.yml` nếu cần (mặc định: `root` / không mật khẩu, `localhost:3306/scms`).
3. `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` (không còn profile mặc định — phải set `dev`)
4. Mở:
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - OpenAPI JSON: http://localhost:8080/v3/api-docs
   - Health (actuator): http://localhost:8080/actuator/health
5. Tài khoản seed (dev, DB trống): **`coo` / `Admin@123`** — đổi bằng `SEED_COO_PASSWORD`.

Đổi cấu hình qua ENV: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSL_MODE`, `PORT`,
`JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` (≥ 32 ký tự, **phải khác nhau**), `JWT_ISSUER`, `OPENAPI_ENABLED`,
`LOGIN_IP_MAX_PER_WINDOW` / `LOGIN_IP_WINDOW_MIN` / `LOGIN_IP_BLOCK_MIN` (rate-limit login theo IP, mặc định 10 / 5 phút).

> **Secret JWT không còn default trong `application.yml`**: file base để trống để app fail-fast nếu thiếu ENV.
> Giá trị chạy-được-ngay (`dev-only-...`) chỉ nằm ở `application-dev.yml` và bị `ProdConfigGuard` chặn ở prod.

## Profile

| Profile | Swagger | DDL | Seed | Secret JWT |
|---|---|---|---|---|
| `dev` | bật | `update` | bật | có default (chỉ dev) |
| `test` | bật | `create-drop` (H2) | bật | có default |
| `prod` | tắt | `validate` | tắt | **bắt buộc ENV** |

Kết nối MySQL: `sslMode` mặc định `PREFERRED` (TLS nếu server hỗ trợ). **Prod mặc định `VERIFY_IDENTITY`** và
`ProdConfigGuard` chặn nếu `DB_SSL_MODE` không thuộc `REQUIRED` / `VERIFY_CA` / `VERIFY_IDENTITY`.

> Nguyên tắc: **dev chạy được ngay, prod bắt buộc cấu hình thật**. Khi từ dev chuyển sang môi trường thật,
> đặt `spring.profiles.active=prod` + `DDL_AUTO=validate` + thay Flyway/migration quản lý schema
> (`IMPLEMENTATION.md` §7.0: không bao giờ `ddl-auto=update` trên prod).

## Test

```bash
./mvnw test        # unit + ArchUnit + integration (H2)
./mvnw verify      # như trên (surefire đã include *IT)
```

## Cấu trúc package

```
edu.fpt.sba.scms
├─ config/           SecurityConfig (filter chain), JwtConfig, OpenApiConfig, SwaggerRoutes, AsyncConfig,
│                    AuditConfig, DataSeeder, DevOnlyEndpoints, ProdConfigGuard, properties/AppProperties
│  └─ security/      CsrfConfig, CorsConfig, HeadersConfig, PublicRoutes
├─ common/           enums/ (ErrorCode, BusinessErrorCode, AuditAction, UserStatus, Roles, AuthTokenType, BranchStatus),
│                    exception/, util/ (MoneyUtil, DateTimeUtil, SecurityUtil, ValidationUtil, Messages, ClientIp, EphemeralCounter),
│                    trace/, audit/ (AuditLogService)
├─ dto/              envelope (ApiResponse, ApiErrorResponse, FieldErrorItem, PageResponse, ApiExceptionFactory),
│                    request/, response/
├─ controller/       AuthController, HealthController
│  └─ handler/       GlobalExceptionHandler (nghiệp vụ), ValidationExceptionHandler (400),
│                    SecurityExceptionHandler (401/403), ApiResponseAdvice
├─ service/          AuthService +
│  └─ impl/ support/ AuthServiceImpl, LoginAttemptService, LoginIpRateLimiter, RefreshRateLimiter,
│                    RefreshTokenStore, dto/RefreshRequest
├─ security/         BranchContext, PermissionCatalog, jwt/ (JwtTokenProvider, RefreshTokenStore),
│                    jwt/JwtTokenProvider, filter/ (TraceContext, JWT, BranchContext, CSRF, entry point, denied handler),
│                    principal/, web/ (RefreshCookieSupport), util/ (ClientIpResolver)
├─ repository/       UserRepository, RoleRepository, AuthTokenRepository
└─ model/            User, Role, Branch, AuthToken, ...
```

> **Profile bắt buộc set tường minh** (`SPRING_PROFILES_ACTIVE=dev` hoặc `prod`): không có default.
> `ProdConfigGuard` chặn khởi động ở profile `prod` nếu thiếu secret JWT / webhook secret, bật seed,
> hoặc cookie refresh không `Secure`.

Quy tắc phụ thuộc được kiểm bằng `ArchitectureTest` (ArchUnit): chạy `./mvnw test`, vi phạm là fail.

## M01 — Auth (UC01) đã có

| Endpoint | Mô tả |
|---|---|
| `POST /api/v1/auth/login` | Đăng nhập; sai 5 lần / 15 phút → khóa 15 phút (`ACCOUNT_LOCKED`); > 10 lần / 5 phút từ 1 IP → 429 (`Retry-After`) |
| `POST /api/v1/auth/refresh` | Refresh token xoay vòng (token cũ bị thu hồi); nhận body hoặc cookie httpOnly |
| `POST /api/v1/auth/logout` | Thu hồi toàn bộ refresh token + chặn access token hiện tại |
| `GET /api/v1/auth/me` | Thông tin user + danh sách quyền theo role |

Mọi response bọc `ApiResponse<T>` / `ApiErrorResponse`: khoá mã kết quả dùng chung tên **`code`** (cả thành công và
lỗi), timestamp ISO-8601 UTC, `traceId` luôn có + header `X-Trace-Id`.
Refresh rotation **thu hồi nguyên tử ở DB** (`UPDATE ... WHERE revoked_at IS NULL`): nhiều request đồng thời cùng một
token thì chỉ một request được cấp cặp token mới (test `AuthConcurrencyIT`).
Access token 30 phút (HS256, claim `uid/role/bid/jti/typ`), refresh token 7 ngày, lưu SHA-256 trong bảng `auth_token`.
