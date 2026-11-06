# CODING RULES — SCMS

> **Bắt buộc với mọi thành viên và mọi AI (Claude, Copilot, Cursor, Antigravity...) khi viết, sửa, review code trong repo này.**
> Nếu một yêu cầu mâu thuẫn với file này, **dừng lại và nêu rõ mâu thuẫn** thay vì tự ý vi phạm.
>
> - **Backend**: Spring Boot 3.5, Java 21 — Package gốc `edu.fpt.sba.scms`
> - **Frontend**: ReactJS (Vite)
> - **Quy tắc hệ thống & nghiệp vụ**: Tham khảo song song file [`SYSTEM_RULES.md`](./SYSTEM_RULES.md) (BR-01 → BR-53).

---

## 0. Cách làm việc (Dành cho Dev & AI)

1. **Đọc code xung quanh trước khi viết**: Bám theo convention đã có trong module, không tự chế style mới.
2. **Thay đổi nhỏ nhất đủ giải quyết yêu cầu**: Không refactor lan man ngoài phạm vi được giao (YAGNI).
3. **Không bịa API, class, thư viện**: Chưa chắc thì kiểm tra trong code hoặc hỏi lại.
4. **Trước khi trả kết quả / tạo PR, bắt buộc tự chạy checklist ở [Mục 6](#6-checklist-trước-khi-commit--trả-kết-quả).**
5. Khi có nhiều cách, chọn cách **đơn giản nhất mà vẫn đúng** (KISS), rồi mới nghĩ tới tổng quát hóa.

**Thứ tự ưu tiên khi các nguyên tắc xung đột:**
$$\text{Đúng \& An toàn} > \text{Dễ đọc (KISS)} > \text{Không lặp (DRY)} > \text{Dễ mở rộng (SOLID)} > \text{Tối ưu}$$
*Ví dụ: Không gộp hai đoạn code "trông giống nhau" nếu chúng thay đổi vì lý do và ngữ cảnh khác nhau.*

---

## 1. Nguyên lý SOLID

### S — Single Responsibility (Đơn trách nhiệm)
- Một class chỉ có **một lý do để thay đổi**. Một method chỉ làm **một việc**.
- **Dấu hiệu vi phạm**: Tên có chữ `And`/`Manager`/`Helper`/`Utils` chung chung; class > ~300 dòng; method > ~30 dòng; service vừa validate, vừa tính toán, vừa gọi API ngoài, vừa gửi mail.
- **Giải pháp**: Tách theo trách nhiệm: validate / tính toán nghiệp vụ / truy cập dữ liệu / giao tiếp ngoài / map dữ liệu.

### O — Open/Closed (Mở rộng thay vì sửa đổi)
- Thêm hành vi mới bằng cách **thêm class mới**, không sửa chuỗi `if/else`/`switch` dài trong code cũ.
- Dùng **Strategy Pattern** (interface + nhiều implementation, inject theo `Map<Type, Strategy>`) khi có $\ge 3$ nhánh theo "loại" (vd: loại thanh toán CASH, VIETQR, SEPAY).

```java
// ❌ Sửa code cũ mỗi lần thêm loại thanh toán
if (type == CASH) {...} else if (type == CARD) {...} else if (type == QR) {...}

// ✅ Thêm class mới kế thừa interface là xong
public interface PaymentStrategy {
    PaymentType type();
    PaymentResult pay(PaymentRequest request);
}
```

### L — Liskov Substitution (Thay thế Liskov)
- Class con **thay thế được** class cha mà không đổi ý nghĩa: Không ném `UnsupportedOperationException`, không siết precondition, không nới postcondition.
- Nếu class con "không làm được" việc của class cha $\rightarrow$ Quan hệ kế thừa sai, chuyển sang dùng Composition.

### I — Interface Segregation (Phân tách Interface)
- Interface **nhỏ, vừa vặn theo nhu cầu của client**. Không ép class implement method nó không dùng.
- Tách `OrderReader` / `OrderWriter` thay vì một `OrderService` ôm tất cả khi các client sử dụng khác nhau.

### D — Dependency Inversion (Đảo ngược phụ thuộc)
- Tầng nghiệp vụ phụ thuộc vào **abstraction (interface/port)**, không phụ thuộc chi tiết kỹ thuật (HTTP client, SDK, JPA cụ thể…).
- **Constructor injection**, khai báo field `final`. **CẤM** dùng `@Autowired` trên field.
- Khi gọi hệ thống ngoài: Định nghĩa `port` trong `service/port`, triển khai trong `service/adapter` (hoặc `integration`).

---

## 2. Quy tắc Clean Code thực chiến

### DRY — Don't Repeat Yourself
- Cùng **một tri thức nghiệp vụ** chỉ nằm ở **một nơi duy nhất** (một rule tính giá, một rule validate…).
- Lặp 2 lần: chấp nhận được. Lặp lần thứ 3: trích xuất ra method/class chung.
- ⚠️ DRY áp dụng cho **logic có cùng lý do thay đổi**, không áp dụng gộp bừa mọi đoạn code trông giống nhau.
- Hằng số/magic number/chuỗi lặp $\rightarrow$ Khai báo `enum` hoặc hằng số `public static final` có tên rõ nghĩa.

### KISS — Keep It Simple
- Chọn giải pháp **đọc xong hiểu ngay**. Không dùng pattern phức tạp, generic quá đà, reflection, hay stream lồng nhau khi một vòng `for` rõ ràng hơn.
- Tên rõ nghĩa thay cho comment. Comment chỉ giải thích **VÌ SAO làm thế**, không giải thích **LÀM CÁI GÌ**.
- Method $\le 3$ tham số; nếu nhiều hơn $\rightarrow$ Gom thành object (`record`).

### YAGNI — You Aren't Gonna Need It
- **Không viết code cho tương lai "biết đâu cần"**: Field thừa, interface chỉ có 1 implementation mà không có lý do tách, config chưa ai dùng, endpoint chưa ai gọi.
- Không thêm tham số/flag "để sau này mở rộng".
- Không comment-out code cũ — xóa thẳng tay, Git đã lưu lịch sử.

### Law of Demeter — Chỉ nói chuyện với "bạn bè gần"
- Method chỉ gọi: Chính object đó, tham số truyền vào, field của nó, object do nó tự tạo ra.
- **Cấm chuỗi gọi xuyên tầng**: `a.getB().getC().doX()`.

```java
// ❌ Vi phạm Demeter
String city = order.getCustomer().getAddress().getCity();

// ✅ Order tự cung cấp thông tin caller cần
String city = order.shippingCity();
```
*(Ngoại lệ: Fluent API, Builder, Stream pipeline không vi phạm).*

### Fail Fast — Phát hiện lỗi sớm, báo ngay
- **Validate ở biên** (Controller/DTO, constructor, đầu method). Dữ liệu sai thì **ném lỗi ngay**, không đi tiếp rồi hỏng ở giữa.
- Dùng Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`…), kiểm tra bất biến trong constructor/factory của domain.
- **CẤM nuốt lỗi**: Bắt `catch (Exception e) {}` rỗng; `return null` để báo lỗi; log rồi tiếp tục chạy như không có gì.
- Ném **Exception có nghĩa** kế thừa từ `BaseException` kèm `ErrorCode` cụ thể (vd: `NotFoundException(ErrorCode.ORDER_NOT_FOUND)`); không ném `RuntimeException("error")` chung chung.
- Không trả `null` cho collection (trả list rỗng) và không trả `null` cho trường hợp không tìm thấy (dùng `Optional`).

### Early Exit — Guard Clause, Return sớm
- Xử lý **trường hợp không hợp lệ / ngoại lệ trước**, rồi `return` hoặc `throw` ngay. Luồng chính nằm sát lề trái.
- **Tối đa 2 cấp indent** trong một method; nếu vượt quá $\rightarrow$ Đảo điều kiện hoặc tách method con.
- Cấm dùng `else` sau khi đã `return` hoặc `throw`.

```java
// ❌ Lồng nhiều tầng khó đọc
public void checkout(Order order) {
    if (order != null) {
        if (order.hasItems()) {
            if (order.isUnpaid()) {
                // logic chính bị thụt sâu
            }
        }
    }
}

// ✅ Guard Clause sạch sẽ
public void checkout(Order order) {
    Objects.requireNonNull(order, "order must not be null");
    if (!order.hasItems()) throw new BusinessRuleException(ErrorCode.EMPTY_ORDER);
    if (!order.isUnpaid()) throw new ConflictException(ErrorCode.ORDER_ALREADY_PAID);

    // Logic chính nằm sát lề trái
}
```

### Composition over Inheritance
- Mặc định **dùng Composition**: Giữ đối tượng khác làm field và ủy quyền (delegate), thay vì `extends`.
- Chỉ kế thừa khi thỏa **cả hai**: Quan hệ "is-a" thật sự **và** thỏa nguyên lý Liskov. Tối đa **1 cấp** kế thừa.
- Cần dùng chung hành vi $\rightarrow$ Trích thành Bean/Strategy được inject, **không** tạo `BaseXxx` khổng lồ.
- Cấm tạo class `BaseService`/`BaseController` chứa logic nghiệp vụ.

### Separation of Concerns (Tách biệt mối quan tâm)
- Mỗi tầng/package một mối quan tâm riêng: Không trộn HTTP $\leftrightarrow$ Nghiệp vụ $\leftrightarrow$ Truy cập dữ liệu $\leftrightarrow$ Định dạng hiển thị.
- Tầng nghiệp vụ **không biết** HTTP (`HttpServletRequest`, `ResponseEntity`), không biết JSON.
- Controller **không chứa** logic nghiệp vụ; Repository **không chứa** logic nghiệp vụ.

### Tell, Don't Ask
- **Ra lệnh** cho object làm việc, đừng hỏi trạng thái rồi tự xử lý bên ngoài.
- Logic liên quan đến dữ liệu của object phải nằm **trong chính object đó** (tránh Anemic Domain Model).

```java
// ❌ Ask: Lấy trạng thái ra rồi tự quyết bên ngoài
if (order.getStatus() == Status.PENDING && !order.getItems().isEmpty()) {
    order.setStatus(Status.CONFIRMED);
}

// ✅ Tell: Object tự bảo vệ tính đúng đắn của nó
order.confirm(); // Bên trong method tự kiểm tra và ném lỗi nếu không hợp lệ
```

---

## 3. Kiến trúc & Quy tắc phụ thuộc (`edu.fpt.sba.scms`)

### 3.1. Ma trận phụ thuộc giữa các tầng

| Package | Trách nhiệm | Được phép phụ thuộc | CẤM |
|---|---|---|---|
| `controller` | Nhận request, validate cú pháp, ủy quyền service, trả response | `service` (interface), `dto`, `security` (chỉ `UserPrincipal`), `common` | Gọi `repository`, chứa nghiệp vụ, trả `model`/entity ra ngoài |
| `controller.handler` | Bắt lỗi tập trung, tự bọc response envelope | `dto`, `common`, `security.filter`, `config.TraceContextFilter` | Import `service`, `repository`, `model` |
| `dto` | Dữ liệu truyền vào/ra API (dùng Java 21 `record`) | `common`, thư viện JDK | Chứa nghiệp vụ, dùng entity làm field |
| `service` (+ `impl`, `support`) | Điều phối nghiệp vụ use case, quản lý `@Transactional` | `domain`, `repository`, `mapper`, `dto`, `model`, `service/port`, `common`, `security.BranchContext` | Biết HTTP (`HttpServletRequest`, `ResponseEntity`), gọi `controller` |
| `service/port`, `adapter` | `port`: Interface ra ngoài; `adapter`: Triển khai | `integration`, `common` | Để nghiệp vụ gọi thẳng SDK bên thứ ba |
| `domain` | Quy tắc & hành vi nghiệp vụ cốt lõi (Pure Java) | `common/enums`, JDK | Phụ thuộc Spring, JPA annotations, Repository, DTO |
| `model` | JPA Entity ánh xạ Database | `common/enums` | Chứa logic nghiệp vụ nặng, lộ ra Controller |
| `repository` | Truy cập Database (Spring Data JPA + Specs) | `model`, `common` (đọc `BranchContext`) | Chứa logic nghiệp vụ, gọi `service` |
| `mapper` | MapStruct chuyển đổi `model` $\leftrightarrow$ `dto` | `model`, `dto` | Chứa nghiệp vụ, truy vấn Database |
| `security` | Xác thực JWT, Filters, Principal, BranchContext | `repository`, `model` (chỉ `User`), `common`, `dto`, `config.properties` | Chứa nghiệp vụ không liên quan bảo mật |
| `integration` | Client gọi hệ sinh thái ngoài (SePay, VietQR, Printer) | `common`, `config.properties` | Bị nghiệp vụ gọi trực tiếp (phải qua `port`) |
| `config` | Composition root nối dây Spring Bean | Các package cần cấu hình | Không package nào được import `config` (trừ ngoại lệ `TraceContextFilter` & `AppProperties`) |
| `common` | Tiện ích, Enum, BaseException, Audit, Trace | **KHÔNG import bất kỳ package nội bộ nào của dự án** | Phụ thuộc ngược vào bất kỳ tầng nào |

**Quy tắc luồng bắt buộc:** `controller → service → (domain, repository, port)` — **tuyệt đối không đi ngược**. Cấm phụ thuộc vòng (circular dependency).

### 3.2. Quy tắc Spring Boot đặc thù dự án `scms-be`

1. **Injection**: Bắt buộc Constructor Injection (`@RequiredArgsConstructor` của Lombok), field khai báo `final`. Cấm dùng field injection (`@Autowired`).
2. **Transaction**:
   - Chỉ đặt `@Transactional` ở tầng **Service**. Không đặt ở Controller hoặc Repository.
   - Hàm đọc danh sách/chi tiết bắt buộc dùng `@Transactional(readOnly = true)`.
   - Hàm ghi (thêm/sửa/xóa) dùng `@Transactional(rollbackFor = Exception.class)`.
3. **Response Envelope**:
   - Controller **chỉ trả DTO trần** (hoặc `PageResponse<T>`).
   - Class `ApiResponseAdvice` sẽ tự động bọc thành `ApiResponse<T>` chuẩn. Controller không tự bọc envelope.
4. **Xử lý Exception**:
   - Xử lý lỗi tập trung tại `controller.handler.GlobalExceptionHandler`.
   - Mọi exception nghiệp vụ bắt buộc kế thừa `BaseException` kèm `ErrorCode`. Không `try/catch` nuốt lỗi rải rác.
5. **Cô lập Chi nhánh (`BranchContext`)**:
   - Lấy `branchId` qua `BranchContext.requireBranchId()` từ Token JWT.
   - Tuyệt đối không cho phép tài khoản Branch Manager hoặc Cashier tự ý truyền `branchId` qua request để đọc chéo chi nhánh khác.
6. **Audit Log**:
   - Các hành động tài chính, phân quyền, hủy đơn, hoàn tiền, nạp/rút tiền, kho bắt buộc gọi `AuditLogService.log(...)` đồng bộ trong cùng transaction.
7. **Bảo mật & Cấu hình**:
   - Không commit mật khẩu, JWT secret lên Git. Cấu hình qua biến môi trường ENV và `@ConfigurationProperties`.
   - Log có ngữ cảnh (`traceId`, `userId`, `branchId` qua MDC). **Tuyệt đối không log dữ liệu nhạy cảm** (Mật khẩu, PIN, Token).
8. **Định dạng Dữ liệu chuẩn**:
   - Tiền tệ: Bắt buộc dùng số nguyên VND `decimal(19,0)` hoặc `Long`, định dạng qua `MoneyUtil`. Cấm dùng `float`/`double`.
   - Thời gian: Bắt buộc chuẩn ISO-8601 UTC (`Instant`).

---

## 4. Quy ước Đặt tên & Hình thức Code

- **Tên nói rõ ý định**: `calculateOrderTotal()` thay vì `process()`, `handle()`, `doIt()`.
- **Class**: Danh từ PascalCase (`ProductService`, `VoucherRepository`).
- **Method**: Động từ camelCase (`confirmOrder`, `findActiveCategories`).
- **Boolean**: Bắt đầu bằng `is`, `has`, `can` (`isPaid`, `hasVoucher`).
- **Không viết tắt khó hiểu**: Tránh `ord`, `tmp`, `mgr`, `c`. Không dùng chữ số vô nghĩa làm khác biệt (`data1`, `data2`).
- **Hậu tố định danh vai trò**: Class kết thúc đúng tầng (`...Controller`, `...Service`, `...ServiceImpl`, `...Repository`, `...Specs`, `...Mapper`, `...Request`, `...Dto`).
- Mỗi file một class top-level. Giữ format thống nhất theo formatter của dự án.

---

## 5. Quy chuẩn Frontend (ReactJS)

Áp dụng cùng tinh thần Clean Code cho phần Frontend:

- **SRP**: Một component làm một việc. Tách giao diện (Presentational UI) khỏi logic nghiệp vụ và gọi API (Custom Hook).
- **Component Composition**: Tái sử dụng giao diện bằng Composition + Props, không copy-paste JSX, không kế thừa component.
- **Separation of Concerns**: Toàn bộ thao tác gọi API nằm trong tầng `api/` riêng biệt, không gọi `axios`/`fetch` trực tiếp trong component.
- **Early Exit**: Render sớm cho các trạng thái `isLoading`, `isError`, `isEmpty` trước khi vào luồng render chính.
- **Fail Fast**: Validate form ở Client trước khi submit, hiển thị thông báo lỗi rõ ràng theo `message` của API; không nuốt lỗi.
- **KISS/YAGNI**: Không lạm dụng Global State (Redux/Zustand) cho các state cục bộ của màn hình; `useState` + props là đủ.
- **Law of Demeter**: Chỉ truyền vào component đúng dữ liệu nó cần hiển thị, không truyền cả object to lớn rồi chấm sâu `a.b.c.d`.
- Component $\le ~150$ dòng; nếu dài hơn $\rightarrow$ Tách sub-components. Tránh magic string (role, status) $\rightarrow$ Dùng file `constants.ts`.
- **Không lưu secret** trong code FE.

---

## 6. Checklist trước khi commit / trả kết quả

> 💡 **Dành cho Lập trình viên và AI**: Trước khi bàn giao code hoặc tạo PR, hãy tự đối chiếu từng mục trong checklist này.

### 🏗️ 1. Kiến trúc & Ranh giới (ArchUnit & Layering)
- [ ] Phụ thuộc đi đúng chiều: `controller -> service -> (domain, repository, port)`?
- [ ] Tầng `common` và `dto` tuyệt đối **KHÔNG import** bất kỳ package nội bộ nào của dự án?
- [ ] Controller mỏng: Không chứa logic nghiệp vụ, không gọi `repository`, không trả `entity`?
- [ ] Logic giao tiếp hệ thống ngoài đi qua `port`/`adapter` (DIP)?
- [ ] Đã chạy `./mvnw test` và toàn bộ **ArchUnit test chạy XANH**?

### 🛡️ 2. Bảo mật, Phân quyền & Chi nhánh
- [ ] Controller/Endpoint đã có `@PreAuthorize` kiểm tra đúng Role?
- [ ] Dữ liệu của BM/Cashier đã được cô lập bằng `BranchContext` (chống truy cập chéo chi nhánh)?
- [ ] Không log hoặc để lộ thông tin nhạy cảm: Password, PIN, Token, Full số thẻ?
- [ ] Thao tác nhạy cảm (đổi role, hủy đơn, hoàn tiền, nạp/rút tiền, kho) đã gọi `AuditLogService.log(...)`?

### ⚡ 3. Transaction & Concurrency
- [ ] Mọi method Service đọc dữ liệu có `@Transactional(readOnly = true)`?
- [ ] Mọi method Service ghi dữ liệu có `@Transactional(rollbackFor = Exception.class)`?
- [ ] Thao tác trừ kho, nạp/rút tiền ca đã có cơ chế chống race condition (Pessimistic Lock hoặc `@Version` Optimistic Lock)?

### ⚠️ 4. Xử lý Lỗi & Validation
- [ ] DTO đầu vào đã có Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Size`...)?
- [ ] Ném lỗi sớm (Fail Fast) bằng Exception kế thừa `BaseException` kèm `ErrorCode` rõ ràng?
- [ ] **KHÔNG nuốt exception** (`catch` rỗng)? **KHÔNG** ném `RuntimeException("error")` chung chung?
- [ ] Controller trả DTO trần, để `ApiResponseAdvice` tự động bọc `ApiResponse<T>`?

### 🧼 5. Clean Code & Chuẩn hóa
- [ ] Không có `a.getB().getC().doX()` (Law of Demeter)?
- [ ] Tối đa 2 cấp indent, dùng Guard Clause (Early Exit), không `else` sau `return`?
- [ ] Số tiền dùng số nguyên VND (`MoneyUtil`, không dùng `float`/`double`)?
- [ ] Thời gian trả ra API dùng chuẩn ISO-8601 UTC?
- [ ] Không còn `System.out.println`, code comment-out thừa, hoặc `TODO` không có người chịu trách nhiệm?
- [ ] Code build thành công (`./mvnw compile`), unit/integration test liên quan chạy xanh?

---

## 7. Cách dùng file này với AI và Review PR

1. **Cấu hình AI Agent**:
   - Trỏ tới file này trong file cấu hình agent (ví dụ `AGENTS.md`, `.cursorrules`, hoặc `CLAUDE.md` thêm `@rule/CODING_RULES.md`).
   - Yêu cầu AI: *"Tuân thủ nghiêm ngặt rule/CODING_RULES.md và tự đối chiếu Checklist ở Mục 6 trước khi xuất code."*
2. **Review Pull Request**:
   - Người review PR sử dụng **Checklist Mục 6** làm tiêu chuẩn nghiệm thu (Definition of Done).
   - Nếu phát hiện vi phạm, comment trực tiếp tên nguyên tắc vi phạm (ví dụ: *"Vi phạm Rule 2 - Fail Fast: Đang nuốt exception ở dòng 45"*, hoặc *"Vi phạm Rule 3.2 - Thiếu @Transactional(readOnly = true)"*).
