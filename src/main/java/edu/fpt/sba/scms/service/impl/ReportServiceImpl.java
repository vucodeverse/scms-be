package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.common.enums.ProductSalesRanking;
import edu.fpt.sba.scms.common.enums.RefundStatus;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.util.DateTimeUtil;
import edu.fpt.sba.scms.config.properties.ReportProperties;
import edu.fpt.sba.scms.dto.request.query.report.CashFlowReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.FinancialReportQuery;
import edu.fpt.sba.scms.dto.request.query.report.ProductSalesQuery;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodFilter;
import edu.fpt.sba.scms.dto.request.query.report.ReportPeriodQuery;
import edu.fpt.sba.scms.dto.request.query.report.SalesReportQuery;
import edu.fpt.sba.scms.dto.response.report.CashFlowReportDto;
import edu.fpt.sba.scms.dto.response.report.FinancialReportDto;
import edu.fpt.sba.scms.dto.response.report.HrShiftReportDto;
import edu.fpt.sba.scms.dto.response.report.InventoryReportDto;
import edu.fpt.sba.scms.dto.response.report.PeakHoursReportDto;
import edu.fpt.sba.scms.dto.response.report.ProductSalesItemDto;
import edu.fpt.sba.scms.dto.response.report.ProductSalesReportDto;
import edu.fpt.sba.scms.dto.response.report.SalesReportDto;
import edu.fpt.sba.scms.dto.response.report.SalesSummaryDto;
import edu.fpt.sba.scms.dto.response.report.ShiftCashFlowDto;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Shift;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.CashFlowReportRepository;
import edu.fpt.sba.scms.repository.InventoryReportRepository;
import edu.fpt.sba.scms.repository.SalesReportRepository;
import edu.fpt.sba.scms.repository.SalesReportRepository.ProductRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.ProductSalesRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.ShiftMethodRefundRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.ShiftMethodSalesRow;
import edu.fpt.sba.scms.repository.SalesReportRepository.SoldProductCostRow;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.service.ReportService;
import edu.fpt.sba.scms.service.report.CashFlowCalculator;
import edu.fpt.sba.scms.service.report.FinancialReportCalculator;
import edu.fpt.sba.scms.service.report.FinancialReportCalculator.RecipeCostLine;
import edu.fpt.sba.scms.service.report.HrReportLoader;
import edu.fpt.sba.scms.service.report.HrShiftCalculator;
import edu.fpt.sba.scms.service.report.InventoryReportCalculator;
import edu.fpt.sba.scms.service.report.PeakHoursCalculator;
import edu.fpt.sba.scms.service.report.ProductRankingCalculator;
import edu.fpt.sba.scms.service.report.ReportPeriod;
import edu.fpt.sba.scms.service.report.ReportRules;
import edu.fpt.sba.scms.service.report.ReportScope;
import edu.fpt.sba.scms.service.report.SalesReportCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ghép phạm vi chi nhánh + khoảng ngày + truy vấn tổng hợp + calculator thuần cho từng report.
 * Toàn bộ là đọc — không ghi dữ liệu nghiệp vụ.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportProperties properties;
    private final BranchRepository branchRepository;
    private final ShiftRepository shiftRepository;
    private final SalesReportRepository salesRepository;
    private final CashFlowReportRepository cashFlowRepository;
    private final InventoryReportRepository inventoryRepository;
    private final HrReportLoader hrReportLoader;

    @Override
    public SalesReportDto getSalesReport(SalesReportQuery query) {
        Branch branch = requireBranch(query.branchId());
        ReportPeriod period = period(query);
        Integer branchId = branch.getId();
        List<ShiftMethodSalesRow> sales = salesRepository.sumSalesByShiftAndMethod(branchId,
                ReportRules.REVENUE_ORDER_STATUSES, period.startInclusive(), period.endExclusive(), query.shiftId(),
                query.paymentMethod(), PaymentStatus.SUCCESS);
        List<ShiftMethodRefundRow> refunds = salesRepository.sumRefundsByShiftAndMethod(branchId,
                ReportRules.REVENUE_ORDER_STATUSES, period.startInclusive(), period.endExclusive(), query.shiftId(),
                query.paymentMethod(), PaymentStatus.SUCCESS, RefundStatus.DONE);
        List<ProductSalesRow> products = salesRepository.sumSalesByProduct(branchId,
                ReportRules.REVENUE_ORDER_STATUSES, period.startInclusive(), period.endExclusive(), query.shiftId(),
                query.paymentMethod(), PaymentStatus.SUCCESS);
        var hours = salesRepository.sumSalesByPaidUtcHour(branchId, ReportRules.REVENUE_ORDER_STATUSES,
                period.startInclusive(), period.endExclusive(), query.shiftId(), query.paymentMethod(),
                PaymentStatus.SUCCESS);

        SalesSummaryDto summary = SalesReportCalculator.summarize(sales, refunds);
        Set<Integer> shiftIds = sales.stream().map(ShiftMethodSalesRow::getShiftId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, Shift> shifts = shiftRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));
        List<ProductSalesItemDto> top = ProductRankingCalculator.rank(products, List.of(),
                ProductSalesRanking.BEST_SELLING, properties.topProductsLimit());
        return new SalesReportDto(branchId, branch.getName(), period.from(), period.to(), query.shiftId(),
                query.paymentMethod(), summary.orderCount() == 0, summary,
                SalesReportCalculator.daily(hours, period),
                SalesReportCalculator.byShift(sales, refunds, shifts), top);
    }

    @Override
    public CashFlowReportDto getCashFlowReport(CashFlowReportQuery query) {
        Branch branch = requireBranch(query.branchId());
        ReportPeriod period = period(query);
        List<Shift> shifts = cashFlowRepository.findShifts(branch.getId(), period.from(), period.to(),
                ReportRules.CASH_SHIFT_STATUSES, query.shiftId());
        Map<Integer, List<CashTransaction>> transactions = shifts.isEmpty() ? Map.of()
                : cashFlowRepository.findByShiftIds(shifts.stream().map(Shift::getId).toList()).stream()
                .collect(Collectors.groupingBy(CashTransaction::getShiftId));
        List<ShiftCashFlowDto> rows = shifts.stream()
                .map(s -> CashFlowCalculator.shift(s, transactions.getOrDefault(s.getId(), List.of())))
                .toList();
        return new CashFlowReportDto(branch.getId(), branch.getName(), period.from(), period.to(), rows.isEmpty(),
                CashFlowCalculator.summarize(rows), rows);
    }

    @Override
    public InventoryReportDto getInventoryReport(ReportPeriodQuery query) {
        Branch branch = requireBranch(query.branchId());
        ReportPeriod period = period(query);
        Integer branchId = branch.getId();
        var stock = inventoryRepository.findStockItems(branchId);
        var inPeriod = inventoryRepository.sumMovements(branchId, period.startInclusive(), period.endExclusive());
        var report = InventoryReportCalculator.calculate(stock, inPeriod,
                inventoryRepository.sumMovementsSince(branchId, period.endExclusive()),
                inventoryRepository.findCountsBefore(branchId, ReportRules.COUNTED_STOCKTAKE_STATUSES,
                        period.endExclusive()));
        return new InventoryReportDto(branchId, branch.getName(), period.from(), period.to(), stock.isEmpty(),
                inPeriod.isEmpty(), report.summary(), report.items());
    }

    @Override
    public HrShiftReportDto getHrShiftReport(ReportPeriodQuery query) {
        Branch branch = requireBranch(query.branchId());
        ReportPeriod period = period(query);
        HrShiftCalculator.HrReport report = hrReportLoader.load(branch.getId(), period, Instant.now());
        boolean empty = report.summary().attendanceCount() == 0 && report.summary().shiftsScheduled() == 0;
        return new HrShiftReportDto(branch.getId(), branch.getName(), period.from(), period.to(), empty,
                report.summary(), report.employees());
    }

    @Override
    public FinancialReportDto getConsolidatedFinancialReport(FinancialReportQuery query) {
        ReportPeriod period = ReportPeriod.of(query.from(), query.to(), properties.maxRangeDays(),
                DateTimeUtil.todayVN());
        List<Branch> branches = selectBranches(query.branchIds());
        List<Integer> ids = branches.stream().map(Branch::getId).toList();
        if (ids.isEmpty()) {
            var empty = FinancialReportCalculator.calculate(List.of(), List.of(), List.of(), List.of(), List.of());
            return new FinancialReportDto(period.from(), period.to(), true, false, empty.chainTotal(), List.of(),
                    List.of());
        }
        var sales = salesRepository.sumSalesByBranch(ids, ReportRules.REVENUE_ORDER_STATUSES,
                period.startInclusive(), period.endExclusive());
        var refunds = salesRepository.sumRefundsByBranch(ids, ReportRules.REVENUE_ORDER_STATUSES,
                period.startInclusive(), period.endExclusive(), RefundStatus.DONE);
        List<SoldProductCostRow> sold = salesRepository.sumSoldProductsForCost(ids,
                ReportRules.REVENUE_ORDER_STATUSES, period.startInclusive(), period.endExclusive());
        var report = FinancialReportCalculator.calculate(branches, sales, refunds, sold, recipeCosts(sold));
        boolean empty = report.chainTotal().orderCount() == 0;
        return new FinancialReportDto(period.from(), period.to(), empty, !report.missingCostProducts().isEmpty(),
                report.chainTotal(), report.branches(), report.missingCostProducts());
    }

    @Override
    public ProductSalesReportDto getProductSalesReport(ProductSalesQuery query) {
        Branch branch = optionalBranch(ReportScope.branchOrChain(query.branchId()));
        ReportPeriod period = period(query);
        ProductSalesRanking ranking = query.ranking() == null ? ProductSalesRanking.BEST_SELLING : query.ranking();
        int limit = query.limit() == null ? properties.topProductsLimit()
                : Math.min(query.limit(), properties.maxTopProducts());
        List<ProductSalesRow> sold = salesRepository.sumSalesByProduct(branch == null ? null : branch.getId(),
                ReportRules.REVENUE_ORDER_STATUSES, period.startInclusive(), period.endExclusive(), null, null,
                PaymentStatus.SUCCESS);
        var menu = ranking == ProductSalesRanking.SLOW_SELLING
                ? salesRepository.findProductsByStatusIn(ReportRules.MENU_PRODUCT_STATUSES) : List.<ProductRow>of();
        return new ProductSalesReportDto(branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(), period.from(), period.to(), ranking, limit, sold.isEmpty(),
                ProductRankingCalculator.totalQuantity(sold), ProductRankingCalculator.totalRevenue(sold),
                ProductRankingCalculator.rank(sold, menu, ranking, limit));
    }

    @Override
    public PeakHoursReportDto getPeakHoursReport(ReportPeriodQuery query) {
        Branch branch = optionalBranch(ReportScope.branchOrChain(query.branchId()));
        ReportPeriod period = period(query);
        var rows = salesRepository.countOrdersByCreatedUtcHour(branch == null ? null : branch.getId(),
                ReportRules.REVENUE_ORDER_STATUSES, period.startInclusive(), period.endExclusive());
        var peak = PeakHoursCalculator.calculate(rows, properties.openHour(), properties.closeHour(),
                properties.peakTierRatio(), period.dayCount());
        return new PeakHoursReportDto(branch == null ? null : branch.getId(), branch == null ? null : branch.getName(),
                period.from(), period.to(), peak.totalOrders() == 0, peak.totalOrders(), period.dayCount(),
                peak.peakOrderCount(), peak.slots());
    }

    private ReportPeriod period(ReportPeriodFilter filter) {
        return ReportPeriod.of(filter, properties.maxRangeDays(), DateTimeUtil.todayVN());
    }

    /** Report cấp chi nhánh: BM → chi nhánh của mình; COO → bắt buộc chọn, chi nhánh phải tồn tại (404). */
    private Branch requireBranch(Integer requestedBranchId) {
        return findBranch(ReportScope.requireBranch(requestedBranchId));
    }

    /** {@code null} = toàn chuỗi (chỉ COO mới nhận được {@code null} từ {@link ReportScope#branchOrChain}). */
    private Branch optionalBranch(Integer branchId) {
        return branchId == null ? null : findBranch(branchId);
    }

    private Branch findBranch(Integer branchId) {
        return branchRepository.findById(branchId).orElseThrow(() -> new NotFoundException("chi nhánh"));
    }

    /** UC40: mặc định mọi chi nhánh; chọn chi nhánh không tồn tại → 404. */
    private List<Branch> selectBranches(List<Integer> branchIds) {
        Sort byCode = Sort.by("code", "id");
        if (branchIds == null || branchIds.isEmpty()) {
            return branchRepository.findAll(byCode);
        }
        Set<Integer> wanted = new HashSet<>(branchIds);
        List<Branch> found = branchRepository.findAllById(wanted).stream()
                .sorted(Comparator.comparing(Branch::getCode).thenComparing(Branch::getId))
                .toList();
        if (found.size() != wanted.size()) {
            throw new NotFoundException("chi nhánh");
        }
        return found;
    }

    private List<RecipeCostLine> recipeCosts(List<SoldProductCostRow> sold) {
        Set<Integer> productIds = sold.stream()
                .filter(r -> r.getQuantityWithoutSnapshot() != null && r.getQuantityWithoutSnapshot() > 0)
                .map(SoldProductCostRow::getProductId)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return List.of();
        }
        return salesRepository.findRecipeCosts(productIds).stream()
                .map(r -> new RecipeCostLine(((Number) r[0]).intValue(), r[1] != null ? r[1].toString() : null, toDecimal(r[3]),
                        toDecimal(r[4])))
                .toList();
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
