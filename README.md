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
├─ config/           Composition root: SecurityConfig (filter chain), JwtConfig, OpenApiConfig, SwaggerRoutes,
│                    AsyncConfig, AuditConfig, DataSeeder, DevOnlyEndpoints, ProdConfigGuard,
│                    TraceContextFilter (bước 1 filter chain), properties/AppProperties
│  └─ web/           mảnh cấu hình filter chain: CsrfConfig, CorsConfig, HeadersConfig, PublicRoutes
├─ common/           Không import tầng nào của ứng dụng
│  ├─ enums/         ErrorCode, BusinessErrorCode, AuditAction, UserStatus, Roles, AuthTokenType, BranchStatus,
│  │                 ProductStatus, ProductSizeName, DiscountType, RecordStatus, SortDirection
│  ├─ exception/     BaseException + các nhánh (NotFound/Conflict/BusinessRule/Forbidden/...)
│  ├─ util/          MoneyUtil, DateTimeUtil, ValidationUtil, SecurityUtil, Messages, ClientIp,
│  │                 EphemeralCounter, FixedWindowRateLimiter, FieldError
│  ├─ specification/ Specifications, TextMatch
│  ├─ trace/         TraceIds (đọc traceId qua MDC), MdcTaskDecorator
│  ├─ page/          PageResponse
│  └─ audit/         AuditLogService
├─ dto/              Envelope + DTO một chiều (không import tầng nào)
│  ├─ (root)         ApiResponse, ApiErrorResponse, FieldErrorItem, PageResponse, ApiExceptionFactory,
│  │                 PageQueryDefaults
│  ├─ request/       LoginRequest, SaveCategoryRequest, ..., query/ (CategoryListQuery, ProductListQuery, ...)
│  └─ response/      CategoryDto, ProductDetailDto, TokenResponse, MessageResponse, ...
├─ controller/       AuthController, HealthController, MenuCategoryController, ProductController,
│                    ToppingController, VoucherController
│  └─ handler/       GlobalExceptionHandler (nghiệp vụ), ValidationExceptionHandler (400),
│                    ApiResponseAdvice (bọc envelope)
├─ service/          AuthService, CategoryService, ProductService, ProductSizeService, ToppingService, VoucherService
│  ├─ impl/          XxxServiceImpl
│  ├─ support/       LoginAttemptService, LoginIpRateLimiter, RefreshRateLimiter, RefreshTokenStore,
│  │                 MasterDataQueries, MasterDataText, ProductAssembler
│  ├─ port/          TokenService (cổng hexagonal)
│  └─ adapter/       TokenServiceAdapter (service -> security.jwt)
├─ security/         BranchContext, PermissionCatalog
│  ├─ jwt/           JwtTokenProvider
│  ├─ filter/        JwtAuthenticationFilter, BranchContextFilter, CsrfCookieFilter, FilterRegistrations,
│  │                 ScmsAuthenticationEntryPoint, ScmsAccessDeniedHandler, SecurityExceptionHandler (401/403)
│  ├─ principal/     UserPrincipal
│  ├─ web/           RefreshCookieSupport
│  └─ util/          ClientIpResolver
├─ mapper/           CategoryMapper, ProductMapper, ToppingMapper, VoucherMapper (MapStruct)
├─ repository/       *Repository + *Specs (CategorySpecs, ProductSpecs, VoucherSpecs, ...), RecipeReadRepository
├─ integration/      Port/Adapter ra ngoài
│  └─ inventory/     IngredientLookupPort + IngredientLookupConfig + UnavailableIngredientLookup
└─ model/            User (bảng app_user), Role, Branch, AuthToken, Category, Product, ProductSize, Topping,
                     ProductTopping, Voucher, VoucherCategory, VoucherProduct, AuditableEntity
```

> Package gốc là `edu.fpt.sba.scms` (tài liệu `IMPLEMENTATION.md` ghi `com.scms`).
> Chỗ lệch so với bản SDD gốc (handler HTTP, `TraceContextFilter`, `SecurityExceptionHandler`, `config/web`) được ghi ở
> `IMPLEMENTATION.md` §5.6 và §17 quyết định #17–20.
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

## M10 — Reporting & Analytics (UC34–UC42)

Chỉ đọc, `GET`, ngày lọc `from`/`to` theo **giờ VN** (`yyyy-MM-dd`, bỏ trống = hôm nay, tối đa `app.report.max-range-days`).
BM chỉ xem chi nhánh của mình (bỏ trống `branchId`; gửi chi nhánh khác → 403 `CROSS_BRANCH_DENIED`); COO chọn `branchId`.
Doanh thu chỉ tính đơn `PAID` + `PARTIALLY_REFUNDED` (loại `PENDING`/`CANCELLED`/`REFUNDED`).

| Endpoint | UC | Quyền | Ghi chú |
|---|---|---|---|
| `/api/v1/reports/dashboard` | UC34 | COO, BM | KPI hôm nay vs hôm qua, 7 ngày, két ca đang mở, tồn thấp, chấm công; mỗi widget `status` OK/ERROR riêng |
| `/api/v1/reports/sales` | UC35 | COO, BM | `shiftId`, `paymentMethod`; tổng hợp, theo ngày, payment mix theo ca, top sản phẩm |
| `/api/v1/reports/cash-flow` | UC37 | COO, BM | `shiftId`; expected/variance (BR-38/39), ca chưa đóng = `UNRECONCILED` |
| `/api/v1/reports/inventory` | UC38 | COO, BM | tồn đầu/cuối kỳ, nhập/xuất/hủy/điều chỉnh, kiểm kê gần nhất (`UNCOUNTED`), tồn thấp |
| `/api/v1/reports/hr-shifts` | UC39 | COO, BM | giờ công, đi muộn/về sớm (dung sai `app.report.late-grace-minutes`), vắng, quên check-out, năng suất thu ngân |
| `/api/v1/reports/consolidated-financial` | UC40 | COO | `branchIds` (trống = tất cả); doanh thu − giá vốn (snapshot hoặc công thức × đơn giá) |
| `/api/v1/reports/product-sales` | UC41 | COO | `ranking=BEST_SELLING\|SLOW_SELLING`, `limit` (mặc định 10); `branchId` trống = toàn chuỗi |
| `/api/v1/reports/peak-hours` | UC42 | COO, BM | 17 slot 06–23h theo giờ tạo đơn, slot ≥ 80% max = cao điểm; COO bỏ `branchId` = toàn chuỗi |
| `/api/v1/reports/{type}/export?format=XLSX\|PDF` | UC36 | như API xem | `type` ∈ `sales, cash-flow, inventory, hr-shifts, consolidated-financial, product-sales, peak-hours`; cùng bộ lọc với API xem; rỗng → 422 `REPORT_NO_DATA_TO_EXPORT`; ghi audit `REPORT_EXPORT` |

Truy vấn tổng hợp nằm ở `repository/*ReportRepository` (gom giờ theo UTC rồi đổi sang giờ VN ở service — chạy được cả
MySQL và H2), phép tính ở `service/report/*Calculator` (hàm thuần, có unit test), ghi file ở `integration/export`
(Apache POI / OpenPDF + font LiberationSans cho tiếng Việt). Index nên có cho dữ liệu lớn:
`orders(branch_id, status, paid_at)`, `orders(branch_id, created_at)`, `payment(order_id, status)`,
`shift(branch_id, shift_date)`, `attendance(branch_id, check_in_time)`.
