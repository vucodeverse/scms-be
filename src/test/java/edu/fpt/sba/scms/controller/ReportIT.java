package edu.fpt.sba.scms.controller;

import com.jayway.jsonpath.JsonPath;
import edu.fpt.sba.scms.common.enums.AttendanceStatus;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.CashTransactionType;
import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RecordStatus;
import edu.fpt.sba.scms.common.enums.RefundStatus;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.ShiftType;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.common.enums.StocktakeStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.model.Attendance;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Category;
import edu.fpt.sba.scms.model.Ingredient;
import edu.fpt.sba.scms.model.Inventory;
import edu.fpt.sba.scms.model.Order;
import edu.fpt.sba.scms.model.OrderLine;
import edu.fpt.sba.scms.model.Payment;
import edu.fpt.sba.scms.model.Product;
import edu.fpt.sba.scms.model.Refund;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.model.ShiftAssignment;
import edu.fpt.sba.scms.model.ShiftAssignmentId;
import edu.fpt.sba.scms.model.StockTransaction;
import edu.fpt.sba.scms.model.Stocktake;
import edu.fpt.sba.scms.model.StocktakeLine;
import edu.fpt.sba.scms.model.StocktakeLineId;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.AttendanceRepository;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.CashTransactionRepository;
import edu.fpt.sba.scms.repository.CategoryRepository;
import edu.fpt.sba.scms.repository.IngredientRepository;
import edu.fpt.sba.scms.repository.InventoryRepository;
import edu.fpt.sba.scms.repository.OrderLineRepository;
import edu.fpt.sba.scms.repository.OrderRepository;
import edu.fpt.sba.scms.repository.PaymentRepository;
import edu.fpt.sba.scms.repository.ProductRepository;
import edu.fpt.sba.scms.repository.RefundRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftAssignmentRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.StockTransactionRepository;
import edu.fpt.sba.scms.repository.StocktakeLineRepository;
import edu.fpt.sba.scms.repository.StocktakeRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Báo cáo UC34–UC42 chạy end-to-end trên H2 với dữ liệu tính tay. Ngày báo cáo cố định {@link #DAY} (quá khứ)
 * để không phụ thuộc đồng hồ; riêng dashboard dùng "hôm nay".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportIT {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 10);
    private static final String D = DAY.toString();
    private static final String PASSWORD = "Password@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BranchRepository branchRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private ShiftAssignmentRepository shiftAssignmentRepository;
    @Autowired private CashTransactionRepository cashTransactionRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderLineRepository orderLineRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private StockTransactionRepository stockTransactionRepository;
    @Autowired private StocktakeRepository stocktakeRepository;
    @Autowired private StocktakeLineRepository stocktakeLineRepository;
    @Autowired private AttendanceRepository attendanceRepository;

    private String tag;
    private Branch branch;
    private Branch otherBranch;
    private User manager;
    private User cashier;
    private Product coffee;
    private Product peachTea;
    private Product unsold;
    private String cooToken;
    private String managerToken;
    private String cashierToken;

    @BeforeEach
    void seed() throws Exception {
        tag = String.valueOf(ThreadLocalRandom.current().nextInt(10_000, 99_999));
        branch = branch("RA" + tag, "Bến Thành " + tag);
        otherBranch = branch("RB" + tag, "Hà Nội " + tag);
        manager = user("rp_bm_" + tag, "Quản lý " + tag, Roles.BRANCH_MANAGER, branch);
        cashier = user("rp_cs_" + tag, "Thu ngân " + tag, Roles.CASHIER, branch);
        seedMenu();
        seedSalesAndCash();
        seedInventory();
        seedAttendance();
        cooToken = login("coo", "Admin@123");
        managerToken = login(manager.getUsername(), PASSWORD);
        cashierToken = login(cashier.getUsername(), PASSWORD);
    }

    // ---------------------------------------------------------------- UC35

    @Test
    @DisplayName("UC35: chỉ đơn PAID/PARTIALLY_REFUNDED trong ngày VN; payment mix theo ca; top sản phẩm")
    void salesReport() throws Exception {
        mockMvc.perform(asManager(get("/api/v1/reports/sales").param("from", D).param("to", D)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.data.empty").value(false))
                .andExpect(jsonPath("$.data.summary.orderCount").value(3))
                .andExpect(jsonPath("$.data.summary.grossAmount").value(225000))
                .andExpect(jsonPath("$.data.summary.discountAmount").value(10000))
                .andExpect(jsonPath("$.data.summary.revenue").value(215000))
                .andExpect(jsonPath("$.data.summary.refundAmount").value(10000))
                .andExpect(jsonPath("$.data.summary.netRevenue").value(205000))
                .andExpect(jsonPath("$.data.daily", hasSize(1)))
                .andExpect(jsonPath("$.data.daily[0].orderCount").value(3))
                .andExpect(jsonPath("$.data.shifts", hasSize(2)))
                .andExpect(jsonPath("$.data.shifts[0].shiftType").value("MORNING"))
                .andExpect(jsonPath("$.data.shifts[0].cashAmount").value(100000))
                .andExpect(jsonPath("$.data.shifts[0].vietQrAmount").value(70000))
                .andExpect(jsonPath("$.data.shifts[0].cashPercent").value(58.82))
                .andExpect(jsonPath("$.data.shifts[1].shiftId").value(nullValue()))
                .andExpect(jsonPath("$.data.topProducts[0].productId").value(coffee.getId()))
                .andExpect(jsonPath("$.data.topProducts[0].quantity").value(4))
                .andExpect(jsonPath("$.data.topProducts[1].quantity").value(2));
    }

    @Test
    @DisplayName("UC35: lọc phương thức thanh toán; ngày hôm sau chỉ có đơn 00:30 VN")
    void salesFiltersAndVietnamDayBoundary() throws Exception {
        mockMvc.perform(asManager(get("/api/v1/reports/sales").param("from", D).param("to", D)
                        .param("paymentMethod", "CASH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.orderCount").value(2))
                .andExpect(jsonPath("$.data.summary.revenue").value(145000));

        String nextDay = DAY.plusDays(1).toString();
        mockMvc.perform(asManager(get("/api/v1/reports/sales").param("from", nextDay).param("to", nextDay)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.orderCount").value(1))
                .andExpect(jsonPath("$.data.summary.revenue").value(20000));
    }

    @Test
    @DisplayName("Phạm vi & validate: from > to → 400; BM xem chi nhánh khác → 403; COO thiếu chi nhánh → 400; thu ngân → 403")
    void scopeAndValidation() throws Exception {
        mockMvc.perform(asManager(get("/api/v1/reports/sales").param("from", "2025-03-11").param("to", D)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(asManager(get("/api/v1/reports/sales").param("branchId", otherBranch.getId().toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_BRANCH_DENIED"));
        mockMvc.perform(asCoo(get("/api/v1/reports/sales").param("from", D)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asCoo(get("/api/v1/reports/sales").param("branchId", "99999999")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/reports/sales").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(asManager(get("/api/v1/reports/consolidated-financial")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- UC37

    @Test
    @DisplayName("UC37: tiền dự kiến (BR-39), chênh lệch (BR-38), ca đang mở = UNRECONCILED")
    void cashFlowReport() throws Exception {
        mockMvc.perform(asManager(get("/api/v1/reports/cash-flow").param("from", D).param("to", D)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shifts", hasSize(2)))
                .andExpect(jsonPath("$.data.shifts[0].expectedCash").value(620000))
                .andExpect(jsonPath("$.data.shifts[0].variance").value(-5000))
                .andExpect(jsonPath("$.data.shifts[0].reconcileStatus").value("RECONCILED"))
                .andExpect(jsonPath("$.data.shifts[0].hasVariance").value(true))
                .andExpect(jsonPath("$.data.shifts[0].movements", hasSize(3)))
                .andExpect(jsonPath("$.data.shifts[1].reconcileStatus").value("UNRECONCILED"))
                .andExpect(jsonPath("$.data.shifts[1].variance").value(nullValue()))
                .andExpect(jsonPath("$.data.summary.unreconciledCount").value(1));
    }

    // ---------------------------------------------------------------- UC38

    @Test
    @DisplayName("UC38: tồn đầu/cuối kỳ, chênh lệch kiểm kê, UNCOUNTED, tồn thấp")
    void inventoryReport() throws Exception {
        mockMvc.perform(asManager(get("/api/v1/reports/inventory").param("from", D).param("to", D)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noTransactions").value(false))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].name").value("Cà phê hạt " + tag))
                .andExpect(jsonPath("$.data.items[0].openingQty").value(460))
                .andExpect(jsonPath("$.data.items[0].receivedQty").value(500))
                .andExpect(jsonPath("$.data.items[0].closingQty").value(800))
                .andExpect(jsonPath("$.data.items[0].countStatus").value("VARIANCE"))
                .andExpect(jsonPath("$.data.items[0].countVariance").value(-10))
                .andExpect(jsonPath("$.data.items[1].countStatus").value("UNCOUNTED"))
                .andExpect(jsonPath("$.data.items[1].lowStock").value(true));
    }

    // ---------------------------------------------------------------- UC39

    @Test
    @DisplayName("UC39: giờ công, đi muộn, vắng, quên check-out, năng suất thu ngân")
    void hrShiftReport() throws Exception {
        mockMvc.perform(asManager(get("/api/v1/reports/hr-shifts").param("from", D).param("to", D)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.absentCount").value(1))
                .andExpect(jsonPath("$.data.employees[?(@.userId == " + cashier.getId() + ")].shiftsScheduled")
                        .value(hasItem(2)))
                .andExpect(jsonPath("$.data.employees[?(@.userId == " + cashier.getId() + ")].lateCount")
                        .value(hasItem(1)))
                .andExpect(jsonPath("$.data.employees[?(@.userId == " + cashier.getId() + ")].hoursWorked")
                        .value(hasItem(4.67)))
                .andExpect(jsonPath("$.data.employees[?(@.userId == " + cashier.getId() + ")].ordersProcessed")
                        .value(hasItem(3)))
                .andExpect(jsonPath("$.data.employees[?(@.userId == " + manager.getId() + ")].missingCheckoutCount")
                        .value(hasItem(1)));
    }

    // ---------------------------------------------------------------- UC40, 41, 42

    @Test
    @DisplayName("UC40/41/42: tài chính hợp nhất, sản phẩm bán chạy/chậm, giờ cao điểm")
    void chainReports() throws Exception {
        mockMvc.perform(asCoo(get("/api/v1/reports/consolidated-financial").param("from", D).param("to", D)
                        .param("branchIds", branch.getId().toString(), otherBranch.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branches", hasSize(2)))
                .andExpect(jsonPath("$.data.branches[0].figures.revenue").value(205000))
                .andExpect(jsonPath("$.data.branches[0].figures.cogs").value(8000))
                .andExpect(jsonPath("$.data.branches[0].figures.grossProfit").value(197000))
                .andExpect(jsonPath("$.data.branches[0].costIncomplete").value(true))
                .andExpect(jsonPath("$.data.branches[1].figures.orderCount").value(0))
                .andExpect(jsonPath("$.data.costIncomplete").value(true))
                .andExpect(jsonPath("$.data.missingCostProducts[0].productId").value(peachTea.getId()))
                .andExpect(jsonPath("$.data.missingCostProducts[0].reason").value("NO_RECIPE"));

        mockMvc.perform(asCoo(get("/api/v1/reports/product-sales").param("branchId", branch.getId().toString())
                        .param("from", D).param("to", D)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranking").value("BEST_SELLING"))
                .andExpect(jsonPath("$.data.limit").value(10))
                .andExpect(jsonPath("$.data.totalQuantity").value(6))
                .andExpect(jsonPath("$.data.items[0].productId").value(coffee.getId()));

        mockMvc.perform(asCoo(get("/api/v1/reports/product-sales").param("branchId", branch.getId().toString())
                        .param("from", D).param("to", D).param("ranking", "SLOW_SELLING").param("limit", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(0))
                .andExpect(jsonPath("$.data.items[*].productId", hasItem(unsold.getId())));

        mockMvc.perform(asManager(get("/api/v1/reports/peak-hours").param("from", D).param("to", D)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots", hasSize(17)))
                .andExpect(jsonPath("$.data.totalOrders").value(2))
                .andExpect(jsonPath("$.data.slots[2].hour").value(8))
                .andExpect(jsonPath("$.data.slots[2].orderCount").value(2))
                .andExpect(jsonPath("$.data.slots[2].peak").value(true))
                .andExpect(jsonPath("$.data.slots[3].peak").value(false));
    }

    // ---------------------------------------------------------------- UC34

    @Test
    @DisplayName("UC34: dashboard hôm nay — KPI, két ca đang mở, tồn thấp, chấm công")
    void dashboard() throws Exception {
        Order today = order(null, OrderStatus.PAID, "50000", "0", Instant.now());
        payment(today, PaymentMethod.CASH);

        mockMvc.perform(asManager(get("/api/v1/reports/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(DateTimeUtil.todayVN().toString()))
                .andExpect(jsonPath("$.data.sales.status").value("OK"))
                .andExpect(jsonPath("$.data.sales.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.sales.data.revenue").value(50000))
                .andExpect(jsonPath("$.data.sales.data.last7Days", hasSize(7)))
                .andExpect(jsonPath("$.data.cashDrawer.status").value("OK"))
                .andExpect(jsonPath("$.data.cashDrawer.data.shiftOpen").value(true))
                .andExpect(jsonPath("$.data.cashDrawer.data.expectedCash").value(300000))
                .andExpect(jsonPath("$.data.lowStock.data.lowStockCount").value(1))
                .andExpect(jsonPath("$.data.attendance.status").value("OK"));

        mockMvc.perform(asCoo(get("/api/v1/reports/dashboard")))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- UC36

    @Test
    @DisplayName("UC36: xuất Excel khớp số liệu màn hình, PDF, rỗng → 422, chi nhánh khác → 403")
    void exportReports() throws Exception {
        MvcResult xlsx = mockMvc.perform(asManager(get("/api/v1/reports/sales/export").param("from", D).param("to", D)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
            Sheet summary = workbook.getSheet("Tổng hợp");
            int dataRow = summary.getLastRowNum();
            assertThat(summary.getRow(dataRow).getCell(0).getNumericCellValue()).isEqualTo(3d);
            assertThat(summary.getRow(dataRow).getCell(5).getNumericCellValue()).isEqualTo(205000d);
        }

        MvcResult pdf = mockMvc.perform(asManager(get("/api/v1/reports/cash-flow/export").param("from", D)
                        .param("to", D).param("format", "PDF")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");

        for (String type : List.of("inventory", "hr-shifts", "peak-hours")) {
            mockMvc.perform(asManager(get("/api/v1/reports/" + type + "/export").param("from", D).param("to", D)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(asCoo(get("/api/v1/reports/consolidated-financial/export").param("from", D).param("to", D)
                        .param("branchIds", branch.getId().toString()).param("format", "PDF")))
                .andExpect(status().isOk());
        mockMvc.perform(asCoo(get("/api/v1/reports/product-sales/export").param("from", D).param("to", D)))
                .andExpect(status().isOk());

        mockMvc.perform(asManager(get("/api/v1/reports/sales/export").param("from", "2024-01-01")
                        .param("to", "2024-01-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detailCode").value("REPORT_NO_DATA_TO_EXPORT"));
        mockMvc.perform(asManager(get("/api/v1/reports/sales/export").param("branchId", otherBranch.getId().toString())))
                .andExpect(status().isForbidden());
    }

    // ================================================================= seed

    /**
     * Ngày {@link #DAY}: ca sáng S1 (đã đóng), ca chiều S2 (đang mở).
     * O1 08:10 CASH 100k (S1) · O2 08:40 VIETQR 80k−10k giảm, hoàn 10k (S1) · O3 huỷ · O4 hoàn toàn bộ ·
     * O5 23:30 CASH 45k không gắn ca · O6 00:30 VN ngày hôm sau (17:30Z cùng ngày UTC) CASH 20k.
     */
    private void seedSalesAndCash() {
        Shift morning = shiftRepository.save(Shift.builder().branchId(branch.getId()).shiftDate(DAY)
                .shiftType(ShiftType.MORNING).plannedStart(vn(7, 0)).plannedEnd(vn(12, 0))
                .openedAt(vn(7, 0)).closedAt(vn(12, 5)).openedBy(cashier.getId()).closedBy(cashier.getId())
                .openingCash(money("500000")).cashRevenue(money("100000")).expectedCash(money("620000"))
                .countedCash(money("615000")).variance(money("-5000")).discrepancyReason("Thiếu tiền lẻ")
                .status(ShiftStatus.CLOSED).build());
        Shift evening = shiftRepository.save(Shift.builder().branchId(branch.getId()).shiftDate(DAY)
                .shiftType(ShiftType.EVENING).plannedStart(vn(13, 0)).plannedEnd(vn(18, 0)).openedAt(vn(13, 0))
                .openedBy(cashier.getId()).openingCash(money("300000")).cashRevenue(BigDecimal.ZERO)
                .expectedCash(money("300000")).status(ShiftStatus.OPEN).build());
        cash(morning, CashTransactionType.OPEN, "500000");
        cash(morning, CashTransactionType.CASH_IN, "50000");
        cash(morning, CashTransactionType.CASH_OUT, "20000");
        cash(morning, CashTransactionType.REFUND_OUT, "10000");
        cash(morning, CashTransactionType.CLOSE, "615000");
        cash(evening, CashTransactionType.OPEN, "300000");

        Order o1 = order(morning.getId(), OrderStatus.PAID, "100000", "0", vn(8, 10));
        payment(o1, PaymentMethod.CASH);
        line(o1, coffee, 2, "60000");
        line(o1, peachTea, 1, "40000");

        Order o2 = order(morning.getId(), OrderStatus.PARTIALLY_REFUNDED, "80000", "10000", vn(8, 40));
        Payment p2 = payment(o2, PaymentMethod.VIETQR);
        line(o2, coffee, 2, "80000");
        refundRepository.save(Refund.builder().paymentId(p2.getId()).orderId(o2.getId()).amount(money("10000"))
                .reason("Khách đổi ý").status(RefundStatus.DONE).requestedBy(cashier.getId()).build());

        Order cancelled = order(evening.getId(), OrderStatus.CANCELLED, "50000", "0", vn(14, 0));
        line(cancelled, coffee, 5, "50000");
        Order refunded = order(evening.getId(), OrderStatus.REFUNDED, "30000", "0", vn(14, 30));
        payment(refunded, PaymentMethod.CASH);
        line(refunded, peachTea, 3, "30000");

        Order late = order(null, OrderStatus.PAID, "45000", "0", vn(23, 30));
        payment(late, PaymentMethod.CASH);
        line(late, peachTea, 1, "45000");

        Order nextDay = order(null, OrderStatus.PAID, "20000", "0",
                DAY.plusDays(1).atTime(0, 30).atZone(DateTimeUtil.VN).toInstant());
        payment(nextDay, PaymentMethod.CASH);
        line(nextDay, coffee, 1, "20000");

        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(morning.getId(), cashier.getId())).shift(morning).employee(cashier).build());
        shiftAssignmentRepository.save(ShiftAssignment.builder()
                .id(new ShiftAssignmentId(evening.getId(), cashier.getId())).shift(evening).employee(cashier).build());
    }

    /** Cà phê hạt: hiện 1000, trong kỳ +500 −100 −50 −10, sau kỳ +200; kiểm kê sổ 810 đếm 800. Sữa: tồn thấp. */
    private void seedInventory() {
        Ingredient beans = ingredientRepository.save(Ingredient.builder().name("Cà phê hạt " + tag).baseUnit("g")
                .packUnit("bao").unitsPerPack(new BigDecimal("500")).unitCost(new BigDecimal("100"))
                .status("ACTIVE").build());
        Ingredient milk = ingredientRepository.save(Ingredient.builder().name("Sữa đặc " + tag).baseUnit("ml")
                .status("ACTIVE").build());
        inventory(beans, "1000", "200");
        inventory(milk, "50", "100");
        stock(beans, StockTransactionType.RECEIVE, "500", vn(9, 0));
        stock(beans, StockTransactionType.ISSUE, "100", vn(10, 0));
        stock(beans, StockTransactionType.DISPOSAL, "50", vn(11, 0));
        stock(beans, StockTransactionType.ADJUST, "-10", vn(12, 0));
        stock(beans, StockTransactionType.RECEIVE, "200", vn(9, 0).plusSeconds(2 * 86_400));

        Stocktake stocktake = stocktakeRepository.save(Stocktake.builder().branch(branch).startedBy(manager)
                .startedAt(vn(11, 30)).closedAt(vn(12, 0)).status(StocktakeStatus.ADJUSTED).build());
        stocktakeLineRepository.save(StocktakeLine.builder()
                .id(new StocktakeLineId(stocktake.getId(), beans.getId())).stocktake(stocktake).ingredient(beans)
                .bookQuantity(new BigDecimal("810")).countedQuantity(new BigDecimal("800"))
                .variance(new BigDecimal("-10")).build());

        // UC40: Cà phê sữa có công thức 20 g × 100 đ = 2.000 đ/ly; Trà đào chưa có công thức.
        jdbcTemplate.update("insert into recipe (product_id, ingredient_id, quantity_base, size) values (?, ?, ?, null)",
                coffee.getId(), beans.getId(), new BigDecimal("20"));
    }

    /** Thu ngân vào ca sáng muộn 20 phút, làm 280 phút, vắng ca chiều; quản lý quên check-out. */
    private void seedAttendance() {
        attendanceRepository.save(Attendance.builder().userId(cashier.getId()).branchId(branch.getId())
                .checkInTime(vn(7, 20)).checkOutTime(vn(12, 0)).workedMinutes(280L).workDate(DAY)
                .status(AttendanceStatus.COMPLETED).build());
        attendanceRepository.save(Attendance.builder().userId(manager.getId()).branchId(branch.getId())
                .checkInTime(vn(8, 0)).workDate(DAY).status(AttendanceStatus.CHECKED_IN).build());
    }

    private void seedMenu() {
        Category category = categoryRepository.save(Category.builder().name("Báo cáo " + tag).sortOrder(1)
                .status(RecordStatus.ACTIVE).build());
        coffee = product(category, "Cà phê sữa " + tag);
        peachTea = product(category, "Trà đào " + tag);
        unsold = product(category, "Matcha " + tag);
    }

    // ================================================================= helpers

    private static Instant vn(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(DateTimeUtil.VN).toInstant();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private Branch branch(String code, String name) {
        return branchRepository.save(Branch.builder().code(code).name(name).address("1 Report St")
                .openingTime(LocalTime.of(6, 0)).closingTime(LocalTime.of(23, 0)).status(BranchStatus.OPEN).build());
    }

    private User user(String username, String fullName, Roles roleType, Branch userBranch) {
        Role role = roleRepository.findByCode(roleType.code())
                .orElseGet(() -> roleRepository.save(Role.builder().code(roleType.code()).name(roleType.name()).build()));
        return userRepository.save(User.builder().branchId(userBranch.getId()).roleId(role.getId()).role(role)
                .username(username).fullName(fullName).passwordHash(passwordEncoder.encode(PASSWORD))
                .position(roleType).status(UserStatus.ACTIVE).build());
    }

    private Product product(Category category, String name) {
        return productRepository.save(Product.builder().categoryId(category.getId()).name(name)
                .status(ProductStatus.AVAILABLE).build());
    }

    private Order order(Integer shiftId, OrderStatus status, String total, String discount, Instant at) {
        BigDecimal finalAmount = money(total).subtract(money(discount));
        boolean paid = status != OrderStatus.CANCELLED;
        return orderRepository.save(Order.builder().branchId(branch.getId()).shiftId(shiftId).cashierId(cashier.getId())
                .status(status).totalAmount(money(total)).discountAmount(money(discount)).finalAmount(finalAmount)
                .createdAt(at).paidAt(paid ? at : null).build());
    }

    private Payment payment(Order order, PaymentMethod method) {
        return paymentRepository.save(Payment.builder().orderId(order.getId()).method(method)
                .amount(order.getFinalAmount()).status(PaymentStatus.SUCCESS).build());
    }

    private void line(Order order, Product product, int quantity, String lineTotal) {
        orderLineRepository.save(OrderLine.builder().orderId(order.getId()).productId(product.getId())
                .quantity(quantity).size("M").unitPrice(money(lineTotal).divide(BigDecimal.valueOf(quantity), 2,
                        java.math.RoundingMode.HALF_UP))
                .toppingTotal(BigDecimal.ZERO).lineTotal(money(lineTotal)).build());
    }

    private void cash(Shift shift, CashTransactionType type, String amount) {
        cashTransactionRepository.save(CashTransaction.builder().shiftId(shift.getId()).type(type)
                .amount(money(amount)).reason(type.name()).createdBy(cashier.getId())
                .createdAt(shift.getOpenedAt()).build());
    }

    private void inventory(Ingredient ingredient, String current, String safety) {
        inventoryRepository.save(Inventory.builder().branch(branch).ingredient(ingredient)
                .currentQtyBase(new BigDecimal(current)).currentPackUnits(BigDecimal.ZERO)
                .safetyStockLevel(new BigDecimal(safety)).lowStockFlag(false).lastUpdated(Instant.now()).build());
    }

    private void stock(Ingredient ingredient, StockTransactionType type, String quantity, Instant at) {
        stockTransactionRepository.save(StockTransaction.builder().branch(branch).ingredient(ingredient).type(type)
                .createdBy(manager).createdAt(at).quantityBase(new BigDecimal(quantity)).build());
    }

    private String login(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private MockHttpServletRequestBuilder asManager(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + managerToken);
    }

    private MockHttpServletRequestBuilder asCoo(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + cooToken);
    }
}
