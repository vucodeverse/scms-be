# Plan — Reporting & Analytics (UC34–UC42)

> Phụ trách: **Người 5 — Tâm** · Branch: `feature/report-analytics`
> Nguồn: `Requirements_SBA_Group2_2.pdf` (§3.5.1 UC34, §3.6.1 UC40–42, §3.7.1 UC35–39, BR-04/25–27/38–51)
> và `Software Design Document Group 2.pdf` (§5.1 Database Design).
> **Giả định:** các module khác (Order/Payment, Shift/Cash Drawer, Inventory) **chưa có code**.
> Module report phải build, test và chạy được độc lập, rồi "cắm" vào dữ liệu thật khi các module đó merge.
>
> **Cập nhật theo `origin/main` @ `18794d6`:** Attendance (UC03/04) **đã có** — entity `model/Attendance`,
> `AttendanceRepository`, enum `AttendanceStatus {CHECKED_IN, COMPLETED}`. Schema thực tế **khác SDD §5.1.18**:
> cột `user_id`, `branch_id`, `check_in_time`, `check_out_time`, `worked_minutes`, `status`, `notes`;
> **không có** `shift_id`, `work_date`, `method`, và chưa có trạng thái `LATE` / `EARLY_LEAVE`. Bảng `shift` /
> `shift_assignment` vẫn chưa có. Ảnh hưởng tới UC39 / UC34 ghi ở §3 (UC39), Phase 6 và §8.

>
> **Cập nhật 2026-11-08 — đã hoàn thành UC34–UC42 sau khi merge `origin/main` @ `ae38785`.** Lúc này Order/Payment/Refund,
> Shift/CashTransaction/ShiftAssignment, Inventory/StockTransaction/Stocktake đều đã có entity thật, nên **bỏ lớp
> port + adapter rỗng (§2.1–2.3, Phase 2, Phase 6)**: report đọc trực tiếp qua 4 repository chỉ-đọc
> (`SalesReportRepository`, `CashFlowReportRepository`, `InventoryReportRepository`, `HrReportRepository`, kế thừa
> `Repository` — không lộ thao tác ghi). Bỏ `app.report.source` / `ReportSource`; thêm `app.report.late-grace-minutes`.
> Các điểm khác so với plan:
> - Export có thêm **PDF** (OpenPDF, font LiberationSans nhúng để hiển thị tiếng Việt) — ảnh yêu cầu UC36 ghi "Excel hoặc PDF".
>   Endpoint tách theo từng report: `GET /api/v1/reports/{type}/export?format=XLSX|PDF` (mỗi endpoint nhận đúng query của API xem).
> - UC39 (HR) mở cho cả COO vì UC36 cho COO xuất "Employee Hours" (câu hỏi §8.5).
> - UC41 thêm `ranking=SLOW_SELLING` (bán chậm, gồm cả món chưa bán được ly nào).
> - UC40: `order_line.cogs_amount` hiện chưa được ghi → giá vốn tính từ `recipe × unit_cost`; nếu sau này có snapshot thì
>   tự dùng snapshot.
> - Doanh thu: đơn `PAID` + `PARTIALLY_REFUNDED`, thời điểm theo `paid_at`; net = trừ refund `DONE` của các đơn đó.
>   Peak hours theo `created_at` (UC42 BR-03). Ngưỡng cao điểm: ≥ 80% slot cao nhất.
> - Dashboard COO bắt buộc chọn chi nhánh (câu hỏi §8.6).

---

## 1. Phạm vi

| UC | Tên | Actor | Endpoint dự kiến |
|---|---|---|---|
| UC34 | Branch Dashboard | BM, COO | `GET /api/v1/reports/dashboard` |
| UC35 | Branch Sales Report (+ Payment Mix theo ca — TAB-07) | BM, COO | `GET /api/v1/reports/sales` |
| UC36 | Export Reports (.xlsx) | BM, COO | `GET /api/v1/reports/{type}/export` |
| UC37 | Branch Cash Flow Report (+ Cash Variance Log — TAB-07) | BM, COO | `GET /api/v1/reports/cash-flow` |
| UC38 | Branch Inventory Report | BM, COO | `GET /api/v1/reports/inventory` |
| UC39 | Branch HR & Shifts Report | BM | `GET /api/v1/reports/hr-shifts` |
| UC40 | Consolidated Financial Report (Chain P&L — TAB-08) | COO | `GET /api/v1/reports/consolidated-financial` |
| UC41 | Product Sales Report (top bán chạy) | COO | `GET /api/v1/reports/product-sales` |
| UC42 | Peak Hours Report | COO, BM | `GET /api/v1/reports/peak-hours` |

Ngoài phạm vi: payroll/lương (BR-47 — UC39 không chứa lương), ghi dữ liệu nghiệp vụ (report chỉ **đọc**).
Export PDF (nút trên SCR-15) **không** có trong UC36 → để optional, làm sau nếu còn thời gian.

---

## 2. Quyết định kiến trúc

### 2.1 Report là module chỉ-đọc, đọc qua **port**, không import entity của module khác

Theo đúng pattern đã có trong repo (`integration/inventory/IngredientLookupPort` + `UnavailableIngredientLookup`
+ `@ConditionalOnMissingBean`) và `RecipeReadRepository` (native query, không entity):

```
controller/ReportController            ─┐
controller/ReportExportController       │  chỉ phụ thuộc service + dto (ArchUnit)
service/ReportService (+ impl)         ─┤  tính toán KPI, rule BR, branch scope
service/report/*Calculator             ─┤  hàm thuần (không Spring) → unit test dễ
integration/report/*ReadPort           ─┤  interface "đọc dữ liệu nguồn" theo từng nguồn
integration/report/Unavailable*Read    ─┤  adapter mặc định: trả rỗng (module nguồn chưa có)
integration/report/sql/Sql*ReadAdapter ─┘  adapter native SQL theo schema SDD §5.1, bật bằng property
```

Lý do:
- Các module khác chưa có entity (`Order`, `Shift`, `Inventory`, `Attendance`…). Nếu report tự tạo entity cho các bảng
  đó sẽ **đụng/conflict** với code của Người 1/2/3 khi merge.
- Port + DTO dạng `record` (row phẳng) giúp: unit test service bằng fake port; khi module nguồn merge chỉ cần
  bật adapter SQL (hoặc viết adapter gọi service của họ), **service report không đổi**.
- Report là truy vấn tổng hợp (GROUP BY, SUM) → native SQL / projection hợp hơn JPA entity.

### 2.2 Các port (mỗi nguồn dữ liệu một port — ISP)

| Port | Bảng nguồn (SDD §5.1) | Module sở hữu | Dùng cho |
|---|---|---|---|
| `SalesReadPort` | `order`, `order_line`, `payment`, `refund`, `product` | Người 2 | UC34, 35, 40, 41, 42 |
| `CashFlowReadPort` | `shift`, `cash_transaction`, `payment`(CASH), `refund` | Người 3 (+ Người 1 shift) | UC34, 37 |
| `InventoryReadPort` | `ingredient`, `inventory`, `stock_transaction`, `stocktake`, `stocktake_line` | Người 3 | UC34, 38 |
| `AttendanceReadPort` | `attendance` (**đã có**), `user`; `shift`, `shift_assignment` (chưa có) | Người 1 | UC34, 39 |
| `CostReadPort` | `order_line.cogs_amount`, `recipe`, `ingredient.unit_cost` | Người 2 + Người 4 | UC40 |
| `BranchLookup` | `branch` (đã có `BranchRepository`) | Người 4 (đã có) | tên chi nhánh, list chi nhánh cho COO |

### 2.3 Bật/tắt adapter SQL

- `app.report.source=none` (mặc định) → dùng `Unavailable*` → API trả 200 với dữ liệu rỗng + cờ `dataAvailable=false`.
- `app.report.source=sql` → bật `Sql*ReadAdapter` (`@ConditionalOnProperty`). Dùng khi bảng nguồn đã có (dev/prod
  sau khi merge) và trong integration test (schema test riêng, xem §6).
- Toàn bộ tên bảng/cột tập trung ở **một** chỗ (`ReportSql` constants) — nếu team đặt tên khác SDD
  (vd. `order` là từ khoá SQL, có thể bị đổi thành `orders`/`customer_order`) chỉ sửa một file.

### 2.4 Quy ước chung cho mọi report

- **Khoảng thời gian:** query `from`, `to` kiểu `LocalDate` (giờ VN — `DateTimeUtil.VN`).
  Đổi sang `Instant` UTC nửa mở `[from 00:00 VN, to+1 00:00 VN)` vì DB lưu UTC (`serverTimezone=UTC`).
  `from > to` → 400 `VALIDATION_ERROR` field `from` (UC35 AT1). Giới hạn tối đa 366 ngày để bảo vệ DB.
  Mặc định khi thiếu: hôm nay (UC37 "defaults to current date").
- **Branch scope (BR-04):** dùng `BranchContext.resolveBranchScope(branchId)` có sẵn. BM gửi branch khác → 403
  `CROSS_BRANCH_DENIED` (UC36 AT1). COO bắt buộc truyền `branchId` cho report cấp chi nhánh (hoặc `null` = toàn chuỗi
  ở UC41/42).
- **Phân quyền:** `@PreAuthorize("hasAnyAuthority('COO','BRANCH_MANAGER')")`, UC40/41 chỉ `COO`, UC39 chỉ `BRANCH_MANAGER`.
  Permission `REPORT_VIEW` đã có trong `PermissionCatalog` cho cả 2 role.
- **Doanh thu (BR-26/27/48):** chỉ đơn `PAID` (+ `PARTIALLY_REFUNDED`), loại `PENDING`/`CANCELLED`/`REFUNDED`.
  `grossRevenue = Σ final_amount`, `refunds = Σ refund.amount (status DONE)`, `netRevenue = gross − refunds`. *(chốt với Người 2, xem §8)*
- **Tiền:** `BigDecimal` scale 2, dùng `MoneyUtil`. Không dùng `double`.
- **Empty state:** không throw — trả 200 với `empty=true` + list rỗng; FE hiển thị message của UC (AT "no data").
- Response bọc `ApiResponse<T>` như các API khác (đã có `ApiResponseAdvice`).

---

## 3. Đặc tả từng report (rule → công thức)

### UC35 — Sales Report
- Filter: `branchId`, `from`, `to`, `shiftId?`, `paymentMethod? (CASH|VIETQR)`, `groupBy=DAY|HOUR|SHIFT`.
- Output: summary cards (`orderCount`, `grossRevenue`, `discount`, `refunds`, `netRevenue`, `avgOrderValue`),
  series theo `groupBy` (chart), bảng top sản phẩm (qty, revenue), **Payment Mix theo ca** (Cash vs VietQR, %).

### UC37 — Cash Flow Report
- Mỗi ca: `openingCash`, `cashSales` (payment CASH SUCCESS), `cashIn`, `cashOut`, `cashRefunds` (REFUND_OUT),
  `expectedCash = opening + cashSales + cashIn − cashOut − cashRefunds` (BR-39),
  `countedCash`, `variance = counted − expected` (BR-38), `discrepancyReason`.
- Ca `OPEN`/chưa đếm tiền → `status=UNRECONCILED`, `variance=null` (AT2).
- Đánh dấu `hasVariance` khi `variance ≠ 0` (highlight). Tổng hợp summary cho cả kỳ.
- Revenue trong report này chỉ tính **tiền mặt** (BR-04 của UC37).

### UC38 — Inventory Report
- Mỗi nguyên liệu: `openingQty`, `received`, `issued`, `disposed`, `adjusted`, `closingQty`,
  `lastCountedQty`, `variance = counted − book` (BR-44) lấy từ `stocktake_line` gần nhất trong kỳ,
  `countStatus = MATCHED|VARIANCE|UNCOUNTED` (AT2), `lowStock = current ≤ safety_stock_level` (BR-45).
- Tính tồn đầu/cuối kỳ ngược từ tồn hiện tại:
  `closing(to) = current − Σ movement(after to)`, `opening(from) = closing(to) − Σ movement(in period)`.
  Dấu: RECEIVE `+`, ISSUE `−`, DISPOSAL `−`, ADJUST `±` (theo dấu `quantity_base`). *(chốt với Người 3)*
- Hiển thị cả đơn vị kiện + đơn vị cơ bản (`units_per_pack`, BR-40/41).
- Không có giao dịch → vẫn trả tồn hiện tại + `noTransactions=true` (AT1).

### UC39 — HR & Shifts Report (chỉ BM)
- Mỗi nhân viên: `shiftsScheduled`, `shiftsWorked`, `absent` (có `shift_assignment`, không có `attendance` — AT2),
  `hoursWorked` (check-in → check-out, BR-06/07), `lateCount`, `earlyLeaveCount`, `missingCheckoutCount`.
- Cashier: `ordersProcessed`, `revenuePerHour` (BR-09/10). **Không** có lương (BR-47).
- Tổng hợp toàn chi nhánh.
- **Theo schema attendance hiện tại:**
  - `hoursWorked` lấy từ `worked_minutes` (fallback `check_out_time − check_in_time`); lọc kỳ theo `check_in_time` (giờ VN).
  - `missingCheckout` = `status = CHECKED_IN` và `check_in_time` trước ngày hôm nay.
  - `late` / `earlyLeave` / `absent` cần giờ ca dự kiến (`shift.planned_start/end`) + `shift_assignment` → **chưa tính được**,
    trả `null` kèm cờ `scheduleDataAvailable=false` cho tới khi module Shift merge.

### UC40 — Consolidated Financial (chỉ COO)
- Filter: `from`, `to`, `branchIds?` (mặc định tất cả). Per-branch + tổng chuỗi: `revenue`, `cogs`,
  `grossProfit = revenue − cogs`, `grossMargin % ` (KPI TAB-08).
- COGS: ưu tiên snapshot `order_line.cogs_amount` (BR-48); nếu null → tính `Σ recipe.quantity_base × qty × ingredient.unit_cost`
  (BR-19); thiếu recipe/unit_cost → `costIncomplete=true` + danh sách sản phẩm thiếu (AT2).
- Chi nhánh không có đơn → vẫn có dòng với 0 (AT1).

### UC41 — Product Sales (chỉ COO)
- Filter: `from`, `to`, `branchId?` (null = toàn chuỗi), `limit` (mặc định **10**, BR-03).
- Xếp hạng theo **số lượng bán** giảm dần, không theo doanh thu (BR-02); tie-break theo revenue rồi tên.

### UC42 — Peak Hours (COO, BM)
- Bucket 1 giờ, trong giờ hoạt động **06:00–23:00** (17 slot, BR-01/02), theo `order.created_at` giờ VN (BR-03), chỉ đơn PAID.
- Trả đủ 17 slot (slot không có đơn = 0) + `avgOrdersPerDay` mỗi slot.
- "Highest tier" (BR-04): slot có `count ≥ 80% × max` → `peak=true`. Ngưỡng để trong `app.report.peak-tier-ratio`. *(chốt với team)*
- Gom giờ: SQL group theo giờ UTC rồi Java cộng +7 (VN không có DST) → tránh `CONVERT_TZ` không chạy trên H2.

### UC34 — Dashboard
- KPI hôm nay của chi nhánh: revenue, order count, AOV, so với hôm qua (%); mini chart doanh thu 7 ngày;
  cash drawer ca hiện tại (opening, cash in/out, refunds, expected); low-stock alerts (top 5 + tổng);
  attendance hôm nay (có mặt/đi muộn/vắng).
- **Mỗi widget độc lập**: một nguồn lỗi không làm hỏng cả dashboard. Response mỗi widget có
  `status=OK|UNAVAILABLE|ERROR` → FE hiển thị empty state + nút Retry đúng widget (AT1).

### UC36 — Export Excel
- `GET /api/v1/reports/{type}/export?...` với `type ∈ sales|cash-flow|inventory|hr-shifts|consolidated-financial|product-sales|peak-hours`,
  **cùng bộ filter** với API xem → gọi lại đúng service method (BR-02: file khớp màn hình).
- Trả `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename=...`.
- Dữ liệu rỗng → 422 `REPORT_NO_DATA_TO_EXPORT` (AT2). Lỗi sinh file → 500 `INTERNAL_SERVER_ERROR` (AT3).
- Ghi audit `AuditAction.REPORT_EXPORT` (đã có) với `entity=report`, `after={type, filters}` (BR-04/BR-51).
- Dùng **Apache POI** `SXSSFWorkbook` (streaming, ít RAM). Mỗi report một `ReportSheetWriter` (strategy) —
  thêm report mới không sửa exporter (Open/Closed).

---

## 4. Breakdown các bước

> Mỗi bước = 1 commit (hoặc 1 PR nhỏ) trên `feature/report-analytics`. Ước lượng tính bằng ngày công.

**Tiến độ (cập nhật 2026-11-08):** Phase 0, 1, 3, 4, 5, 7.1, 7.4 ✅ · Phase 2 & 6 không cần nữa (xem ghi chú đầu file) · Test: unit test cho mọi calculator + writer, `ReportIT` end-to-end trên H2 (9 kịch bản). Còn optional: 7.3 cache dashboard.

### Phase 0 — Chuẩn bị (0.5d)
- [ ] 0.1 Chốt các câu hỏi mở ở §8 với Người 1/2/3 (đặc biệt: tên bảng, status, dấu ADJUST, định nghĩa net revenue).
- [x] 0.2 Thêm dependency `org.apache.poi:poi-ooxml` (5.4.1) vào `pom.xml`.
- [x] 0.3 Cấu hình `app.report.*` (`source`, `max-range-days`, `open-hour`, `close-hour`, `peak-tier-ratio`,
      `top-products-limit`, `max-top-products`) trong `application.yml`.
      Làm thành record riêng `config/properties/ReportProperties` (+ enum `common/enums/ReportSource`) thay vì thêm vào
      `AppProperties` — tránh sửa record dùng chung và các test đang `new AppProperties(...)`. Có mặc định theo SRS và
      fail-fast khi giá trị sai (`ReportPropertiesTest`).

### Phase 1 — Nền tảng chung (1d)
- [x] 1.1 `dto/request/query/report/ReportPeriodQuery` (`branchId`, `from`, `to` — ISO `yyyy-MM-dd`) + interface
      `ReportPeriodFilter` để các query report sau (Sales thêm `shiftId`, `paymentMethod`…) dùng chung xử lý.
      Kiểm `from ≤ to` và `≤ max-range-days` đặt ở `ReportPeriod` (tầng dto không được đọc config).
- [x] 1.2 `service/report/ReportPeriod` — `LocalDate` VN → `Instant` UTC nửa mở `[start, end)`; `dates()`, `dayCount()`,
      `previous()` (so kỳ trước), `contains()`. Mặc định: trống cả hai → hôm nay; thiếu một đầu → bằng đầu còn lại.
      "Hôm nay" truyền vào làm tham số (test không phụ thuộc đồng hồ).
- [x] 1.3 Mã lỗi — **đổi so với dự kiến**, theo quy ước repo (400 = client tự sửa được, 422 = vi phạm trạng thái nghiệp vụ):
      - `from > to`, vượt `max-range-days`, COO không chọn chi nhánh → **400 `VALIDATION_ERROR`** (`InvalidFieldValueException`
        kèm field). Không tạo `REPORT_INVALID_DATE_RANGE`.
      - Thêm `BusinessErrorCode.REPORT_NO_DATA_TO_EXPORT` (422, UC36 AT2).
      - Lỗi sinh file (UC36 AT3) dùng 500 `INTERNAL_SERVER_ERROR` có sẵn; `type` export không hỗ trợ → 400. Không tạo
        `REPORT_EXPORT_FAILED` / `REPORT_TYPE_UNSUPPORTED` (`BusinessErrorCode` chỉ dành cho 422).
- [x] 1.4 `service/report/ReportScope` — `requireBranch()` (report cấp chi nhánh, COO bỏ trống → 400),
      `branchOrChain()` (UC41/42, COO bỏ trống = toàn chuỗi), `isCoo()`. BM xem chi nhánh khác → 403 `CROSS_BRANCH_DENIED`.
      *Còn lại cho Phase 3:* kiểm chi nhánh COO gửi có tồn tại không (404) — cần `BranchRepository`, làm trong service.
- [x] 1.5 Unit test: `ReportPeriodTest` (11), `ReportScopeTest` (9), `ReportPeriodQueryTest` (2).
      Lưu ý: surefire của repo chỉ chạy class khớp `**/*Test.java` → **không dùng `@Nested`** (class lồng bị bỏ qua lặng lẽ).

### Phase 2 — Ports + adapter rỗng (0.5d)
- [ ] 2.1 `integration/report/` : 5 interface port ở §2.2 + row `record` (vd. `PaidOrderRow`, `ShiftCashRow`,
      `StockMovementRow`, `AttendanceRow`, `ProductSalesRow`, `HourlyCountRow`).
- [ ] 2.2 `Unavailable*ReadAdapter` + `ReportPortConfig` (`@ConditionalOnMissingBean`) — app khởi động được khi chưa có bảng.
- [ ] 2.3 Cập nhật `ArchitectureTest` nếu cần (service → integration đã được phép; controller **không** được gọi port).

### Phase 3 — Calculators + Service (3d) — trọng tâm, test kỹ
Hàm thuần, nhận row từ port, trả DTO. Mỗi calculator có unit test bám đúng BR.
- [ ] 3.1 `SalesReportCalculator` (UC35) + Payment Mix.
- [ ] 3.2 `CashFlowCalculator` (UC37) — BR-38/39, UNRECONCILED.
- [ ] 3.3 `InventoryReportCalculator` (UC38) — tồn đầu/cuối, variance, UNCOUNTED, low stock, quy đổi đơn vị.
- [ ] 3.4 `HrShiftReportCalculator` (UC39) — giờ công, đi muộn/về sớm/thiếu check-out, vắng, năng suất cashier.
- [ ] 3.5 `FinancialReportCalculator` (UC40) — COGS snapshot/fallback, costIncomplete, branch 0.
- [ ] 3.6 `ProductRankingCalculator` (UC41) — rank theo qty, top N.
- [ ] 3.7 `PeakHoursCalculator` (UC42) — 17 slot, +7h, peak tier.
- [ ] 3.8 `ReportService` / `ReportServiceImpl` — ghép scope + period + port + calculator; `@Transactional(readOnly = true)`.
- [ ] 3.9 `DashboardService` (UC34) — gọi từng widget trong try/catch riêng, trả `status` theo widget.

### Phase 4 — API (1d)
- [ ] 4.1 `dto/response/report/*` (record, không phụ thuộc model — ArchUnit).
- [ ] 4.2 `ReportController` — 8 endpoint GET ở §1, `@PreAuthorize` theo bảng, Swagger `@Tag(name = "xx. Reports")`.
- [ ] 4.3 `@WebMvcTest`/IT kiểm tra 401/403 theo role, 400 date range, BM truy cập branch khác → 403.

### Phase 5 — Export Excel UC36 (1.5d)
- [ ] 5.1 `service/report/export/ReportSheetWriter<T>` + 1 writer mỗi report (header, format tiền/ngày, tổng cuối).
- [ ] 5.2 `ReportExportService` — chọn writer theo `type`, rỗng → 422, lỗi → 500, ghi audit `REPORT_EXPORT`.
- [ ] 5.3 `ReportExportController` — trả `ResponseEntity<StreamingResponseBody>` (bypass envelope JSON; kiểm
      `ApiResponseAdvice` không bọc file).
- [ ] 5.4 Test: mở lại file bằng POI, so số liệu với API JSON cùng filter (BR-02), audit được ghi.

### Phase 6 — Adapter SQL thật (2d) — làm song song/sau khi module nguồn chốt schema
- [ ] 6.1 `ReportSql` — gom tên bảng/cột.
- [ ] 6.1b **Làm được ngay:** `AttendanceReadAdapter` đọc bảng `attendance` thật (query projection/aggregate mới,
      đặt trong adapter của report — **không sửa** `AttendanceRepository` của Người 1). Bật mặc định, không cần
      property; test bằng IT trên H2 vì Hibernate đã tạo bảng này.
- [ ] 6.2 `SqlSalesReadAdapter`, `SqlCashFlowReadAdapter`, `SqlInventoryReadAdapter`, `SqlShiftScheduleReadAdapter`,
      `SqlCostReadAdapter` (`NamedParameterJdbcTemplate`, chỉ SQL chuẩn chạy được cả MySQL & H2).
- [ ] 6.3 `src/test/resources/report/schema.sql` + `data.sql`: tạo các bảng nguồn theo SDD §5.1 + dữ liệu mẫu
      (2 chi nhánh, nhiều ca, đơn PAID/CANCELLED/REFUNDED, ca chưa đóng, nguyên liệu chưa kiểm kê…).
- [ ] 6.4 `ReportIT` (profile test + `app.report.source=sql`) — kiểm số liệu end-to-end từng report với số tính tay.
- [ ] 6.5 Index đề xuất cho các module nguồn (ghi vào README/issue, **không** tự sửa bảng của họ):
      `order(branch_id, status, paid_at)`, `order(branch_id, created_at)`, `payment(order_id, method, status)`,
      `shift(branch_id, shift_date)`, `stock_transaction(branch_id, ingredient_id, created_at)`,
      `attendance(shift_id, employee_id)`.

### Phase 7 — Hoàn thiện (1d)
- [ ] 7.1 Cập nhật `README.md` (mục Reports: endpoint, filter, quyền, property).
- [ ] 7.2 Swagger example cho từng response.
- [ ] 7.3 (Optional) cache ngắn hạn cho dashboard (Caffeine 30–60s / key = branch+ngày).
- [ ] 7.4 (Optional) Export PDF.
- [ ] 7.5 `./mvnw verify` xanh (unit + ArchUnit + IT) → mở PR vào `main`.

**Tổng ước lượng:** ~10–11 ngày công (Phase 6 phụ thuộc tiến độ module khác).

---

## 5. Cấu trúc file dự kiến

```
src/main/java/edu/fpt/sba/scms/
├─ controller/ReportController.java, ReportExportController.java
├─ dto/request/query/ReportPeriodQuery.java, SalesReportQuery.java, ProductSalesQuery.java, ...
├─ dto/response/report/DashboardDto, SalesReportDto, CashFlowReportDto, InventoryReportDto,
│                      HrShiftReportDto, FinancialReportDto, ProductSalesDto, PeakHoursDto, ...
├─ service/ReportService.java, DashboardService.java, ReportExportService.java
├─ service/impl/ReportServiceImpl.java, DashboardServiceImpl.java, ReportExportServiceImpl.java
├─ service/report/ReportPeriod, ReportScope, *Calculator
├─ service/report/export/ReportSheetWriter + *SheetWriter
└─ integration/report/*ReadPort, Unavailable*ReadAdapter, ReportPortConfig
   └─ sql/ReportSql, Sql*ReadAdapter
src/test/java/.../service/report/*CalculatorTest, ReportScopeTest, ReportPeriodTest
src/test/java/.../controller/ReportIT, ReportExportIT
src/test/resources/report/schema.sql, data.sql
```

---

## 6. Chiến lược test

| Tầng | Cách test | Phụ thuộc module khác? |
|---|---|---|
| Calculator | JUnit thuần, input là list row | Không |
| Service | Mockito / fake port | Không |
| Controller + security | MockMvc, JWT test như `UserBranchIT` | Không (adapter Unavailable) |
| SQL adapter | IT + H2 `schema.sql` riêng của report | Không (tự tạo bảng theo SDD) |
| Export | Đọc lại .xlsx bằng POI | Không |

Case bắt buộc: from > to; BM xem branch khác; kỳ không có dữ liệu; đơn CANCELLED/REFUNDED bị loại; ca chưa đóng;
nguyên liệu chưa kiểm kê; sản phẩm thiếu recipe; đơn lúc 05:59 / 23:00 (ngoài giờ); đơn ở biên ngày theo giờ VN
(vd. 23:30 VN = 16:30 UTC cùng ngày, 00:30 VN = 17:30 UTC ngày trước).

---

## 7. Điểm phụ thuộc / tích hợp với các người khác

- **Người 2 (Sales & Payment):** cấu trúc `order`, `order_line.cogs_amount` có được ghi lúc thanh toán không,
  status đơn, `payment.method`, `refund.status`.
- **Người 3 (Operations):** `shift` (expected/counted/variance), `cash_transaction.type`, `stock_transaction` (dấu ADJUST,
  `quantity_base`), `stocktake_line`.
- **Người 1 (Attendance & Shift):** attendance đã merge (UC03/04). Còn cần: bảng `shift` + `shift_assignment`
  (giờ ca dự kiến để tính đi muộn/về sớm/vắng), và việc có liên kết `attendance` ↔ `shift` hay không.
- **Người 4 (Master Data):** `branch` (đã có), `recipe`, `ingredient.unit_cost`.

Khi module nào merge vào `main`, rebase branch này và bật/chỉnh adapter SQL tương ứng — service/controller report không cần sửa.

---

## 8. Câu hỏi mở (cần chốt)

1. Tên bảng đơn hàng thực tế (`order` là từ khoá SQL) — `orders`?
2. Net revenue: trừ refund theo ngày refund hay theo ngày đơn gốc? Đơn `PARTIALLY_REFUNDED` tính phần còn lại?
3. `order_line.cogs_amount` có được snapshot khi PAID không (BR-48)? Nếu có, UC40 không cần tính lại từ recipe.
4. "Highest tier" giờ cao điểm: ngưỡng 80% max, hay top-3 slot?
5. UC39 chỉ có BM, nhưng UC36 cho COO export "Employee Hours" → COO có được xem HR report của chi nhánh không?
6. Dashboard COO: xem 1 chi nhánh được chọn hay tổng chuỗi?
7. Có cần Export PDF (SCR-15 có nút, UC36 không nhắc)?
8. Bảng `attendance` hiện tại không có `shift_id` và trạng thái LATE/EARLY_LEAVE như SDD: Người 1 có định bổ sung không,
   hay report tự so `check_in_time` với giờ ca dự kiến?
