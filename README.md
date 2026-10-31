# SCMS Backend (scms-be)

Spring Boot **4.1.2** · Java 21 · Maven · Spring Data JPA + **MySQL** · Spring Security 7 + JWT · springdoc **3.2** (Swagger UI) · Lombok + MapStruct · Logback JSON.

> Tài liệu nghiệp vụ / API gốc: `IMPLEMENTATION.md` (nguồn sự thật). Repo này giữ **package gốc `edu.fpt.sba.scms`** (tương ứng `com.scms` trong tài liệu).

## Chạy nhanh (dev)

1. Tạo database: `CREATE DATABASE scms CHARACTER SET utf8mb4;`
2. Sửa `src/main/resources/application-dev.yml` nếu cần (mặc định: `root` / không mật khẩu, `localhost:3306/scms`).
3. `./mvnw spring-boot:run` (profile `dev` là mặc định)
4. Mở:
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - OpenAPI JSON: http://localhost:8080/v3/api-docs
   - Health (actuator): http://localhost:8080/actuator/health
5. Tài khoản seed (dev, DB trống): **`coo` / `Admin@123`** — đổi bằng `SEED_COO_PASSWORD`.

Đổi cấu hình qua ENV: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `PORT`,
`JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` (≥ 32 ký tự), `OPENAPI_ENABLED`.

## Profile

| Profile | Swagger | DDL | Seed | Secret JWT |
|---|---|---|---|---|
| `dev` (mặc định) | bật | `update` | bật | có default (chỉ dev) |
| `test` | bật | `create-drop` (H2) | bật | có default |
| `prod` | tắt | `validate` | tắt | **bắt buộc ENV** |

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
├─ config/      SecurityConfig, OpenApiConfig, AppProperties, AsyncConfig, AuditConfig, DataSeeder, JwtConfig  (composition root)
├─ common/      enum tập trung: enums/ (ErrorCode, BusinessErrorCode, AuditAction, UserStatus, UserPosition, AuthTokenType, BranchStatus)
│               exception/, util/ (MoneyUtil, DateTimeUtil, SecurityUtil, ValidationUtil, Messages, PermissionCatalog),
│               trace/, audit/ (AuditLogService), BranchContext
├─ dto/         envelope (ApiResponse, ApiErrorResponse, FieldErrorItem, PageResponse), UserPrincipal, TokenService,
│               request/, response/
├─ controller/  AuthController, HealthController + handler/ (GlobalExceptionHandler, ApiResponseAdvice)
├─ service/     AuthService + impl/ + support/ (LoginAttemptService, TokenServiceAdapter, RefreshCookieSupport)
├─ security/    jwt/ filter/ user/ Roles
├─ repository/  UserRepository, RoleRepository, AuthTokenRepository
└─ model/       User, Role, Branch, AuthToken, ...
```

Quy tắc phụ thuộc được kiểm bằng `ArchitectureTest` (ArchUnit): chạy `./mvnw test`, vi phạm là fail.

## M01 — Auth (UC01) đã có

| Endpoint | Mô tả |
|---|---|
| `POST /api/v1/auth/login` | Đăng nhập; sai 5 lần / 15 phút → khóa 15 phút (`ACCOUNT_LOCKED`) |
| `POST /api/v1/auth/refresh` | Refresh token xoay vòng (token cũ bị thu hồi); nhận body hoặc cookie httpOnly |
| `POST /api/v1/auth/logout` | Thu hồi toàn bộ refresh token + chặn access token hiện tại (204) |
| `GET /api/v1/auth/me` | Thông tin user + danh sách quyền theo role |

Mọi response bọc `ApiResponse<T>` / `ApiErrorResponse` (có `traceId`, header `X-Trace-Id`).
Access token 30 phút (HS256, claim `uid/role/bid/jti/typ`), refresh token 7 ngày, lưu SHA-256 trong bảng `auth_token`.
