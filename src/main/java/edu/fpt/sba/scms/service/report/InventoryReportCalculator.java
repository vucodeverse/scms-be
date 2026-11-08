package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.StockCountStatus;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.dto.response.report.InventoryItemDto;
import edu.fpt.sba.scms.dto.response.report.InventorySummaryDto;
import edu.fpt.sba.scms.dto.response.report.LowStockItemDto;
import edu.fpt.sba.scms.repository.InventoryReportRepository.MovementTotalRow;
import edu.fpt.sba.scms.repository.InventoryReportRepository.StockCountRow;
import edu.fpt.sba.scms.repository.InventoryReportRepository.StockItemRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.fpt.sba.scms.service.report.ReportMath.zeroIfNull;

/**
 * Xuất - nhập - tồn - hao hụt kho (UC38).
 * <ul>
 *   <li>Tồn đầu/cuối kỳ suy ngược từ tồn hiện tại: {@code closing = current − Σ biến động sau kỳ},
 *       {@code opening = closing − Σ biến động trong kỳ}. Dấu: RECEIVE +, ISSUE −, DISPOSAL −, ADJUST theo dấu
 *       chênh lệch kiểm kê đã lưu.</li>
 *   <li>Chênh lệch kiểm kê = số đếm − tồn sổ lúc đếm (BR-44), lấy lần kiểm kê gần nhất tới cuối kỳ;
 *       chưa từng kiểm kê → UNCOUNTED (AT2).</li>
 *   <li>Tồn thấp khi tồn hiện tại ≤ ngưỡng an toàn (BR-45).</li>
 * </ul>
 */
public final class InventoryReportCalculator {

    private static final int QTY_SCALE = 4;
    private static final int PACK_SCALE = 2;

    public record InventoryReport(List<InventoryItemDto> items, InventorySummaryDto summary) {
    }

    private InventoryReportCalculator() {
    }

    /**
     * @param inPeriod  tổng biến động trong kỳ
     * @param afterEnd  tổng biến động từ cuối kỳ tới nay
     * @param counts    các dòng kiểm kê, mới nhất trước
     */
    public static InventoryReport calculate(List<StockItemRow> stock, List<MovementTotalRow> inPeriod,
                                            List<MovementTotalRow> afterEnd, List<StockCountRow> counts) {
        Map<Long, Map<StockTransactionType, BigDecimal>> periodTotals = byIngredient(inPeriod);
        Map<Long, Map<StockTransactionType, BigDecimal>> laterTotals = byIngredient(afterEnd);
        Map<Long, StockCountRow> latestCount = new HashMap<>();
        counts.forEach(c -> latestCount.putIfAbsent(c.getIngredientId(), c));

        List<InventoryItemDto> items = stock.stream()
                .map(s -> item(s, periodTotals.getOrDefault(s.getIngredientId(), Map.of()),
                        laterTotals.getOrDefault(s.getIngredientId(), Map.of()), latestCount.get(s.getIngredientId())))
                .toList();
        return new InventoryReport(items, summarize(items));
    }

    /** Nguyên liệu tồn thấp, thiếu nhiều nhất (tồn / ngưỡng nhỏ nhất) trước — widget dashboard (UC34). */
    public static List<LowStockItemDto> lowStock(List<StockItemRow> stock) {
        return stock.stream()
                .filter(InventoryReportCalculator::isLowStock)
                .sorted(Comparator.comparing(InventoryReportCalculator::fillRatio)
                        .thenComparing(StockItemRow::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> new LowStockItemDto(s.getIngredientId(), s.getName(), s.getBaseUnit(),
                        zeroIfNull(s.getCurrentQty()), zeroIfNull(s.getSafetyStockLevel())))
                .toList();
    }

    private static InventoryItemDto item(StockItemRow stock, Map<StockTransactionType, BigDecimal> period,
                                         Map<StockTransactionType, BigDecimal> later, StockCountRow count) {
        BigDecimal current = zeroIfNull(stock.getCurrentQty());
        BigDecimal closing = current.subtract(netChange(later));
        BigDecimal opening = closing.subtract(netChange(period));
        BigDecimal countVariance = count == null ? null
                : qty(zeroIfNull(count.getCountedQuantity()).subtract(zeroIfNull(count.getBookQuantity())));
        return new InventoryItemDto(stock.getIngredientId(), stock.getName(), stock.getBaseUnit(), stock.getPackUnit(),
                stock.getUnitsPerPack(), qty(opening),
                qty(amount(period, StockTransactionType.RECEIVE)),
                qty(amount(period, StockTransactionType.ISSUE)),
                qty(amount(period, StockTransactionType.DISPOSAL)),
                qty(zeroIfNull(period.get(StockTransactionType.ADJUST))),
                qty(closing), packs(closing, stock.getUnitsPerPack()), qty(current),
                qty(zeroIfNull(stock.getSafetyStockLevel())), isLowStock(stock),
                countStatus(countVariance),
                count == null ? null : count.getCountedAt(),
                count == null ? null : qty(count.getBookQuantity()),
                count == null ? null : qty(count.getCountedQuantity()),
                countVariance);
    }

    private static InventorySummaryDto summarize(List<InventoryItemDto> items) {
        int low = 0;
        int variance = 0;
        int uncounted = 0;
        BigDecimal disposed = BigDecimal.ZERO;
        for (InventoryItemDto item : items) {
            low += item.lowStock() ? 1 : 0;
            variance += item.countStatus() == StockCountStatus.VARIANCE ? 1 : 0;
            uncounted += item.countStatus() == StockCountStatus.UNCOUNTED ? 1 : 0;
            disposed = disposed.add(item.disposedQty());
        }
        return new InventorySummaryDto(items.size(), low, variance, uncounted, disposed);
    }

    private static Map<Long, Map<StockTransactionType, BigDecimal>> byIngredient(List<MovementTotalRow> rows) {
        Map<Long, Map<StockTransactionType, BigDecimal>> result = new HashMap<>();
        for (MovementTotalRow row : rows) {
            result.computeIfAbsent(row.getIngredientId(), id -> new EnumMap<>(StockTransactionType.class))
                    .merge(row.getType(), zeroIfNull(row.getQuantity()), BigDecimal::add);
        }
        return result;
    }

    /** Biến động ròng (có dấu) của một nguyên liệu. */
    private static BigDecimal netChange(Map<StockTransactionType, BigDecimal> totals) {
        return amount(totals, StockTransactionType.RECEIVE)
                .subtract(amount(totals, StockTransactionType.ISSUE))
                .subtract(amount(totals, StockTransactionType.DISPOSAL))
                .add(zeroIfNull(totals.get(StockTransactionType.ADJUST)));
    }

    /** Lượng (luôn dương) của loại nhập/xuất/huỷ — module kho lưu số dương, lấy trị tuyệt đối cho chắc. */
    private static BigDecimal amount(Map<StockTransactionType, BigDecimal> totals, StockTransactionType type) {
        return zeroIfNull(totals.get(type)).abs();
    }

    private static StockCountStatus countStatus(BigDecimal variance) {
        if (variance == null) {
            return StockCountStatus.UNCOUNTED;
        }
        return variance.signum() == 0 ? StockCountStatus.MATCHED : StockCountStatus.VARIANCE;
    }

    private static boolean isLowStock(StockItemRow stock) {
        return zeroIfNull(stock.getCurrentQty()).compareTo(zeroIfNull(stock.getSafetyStockLevel())) <= 0;
    }

    private static BigDecimal fillRatio(StockItemRow stock) {
        BigDecimal ratio = ReportMath.divide(stock.getCurrentQty(), stock.getSafetyStockLevel());
        return ratio == null ? BigDecimal.ZERO : ratio;
    }

    private static BigDecimal packs(BigDecimal baseQty, BigDecimal unitsPerPack) {
        if (unitsPerPack == null || unitsPerPack.signum() <= 0) {
            return null;
        }
        return baseQty.divide(unitsPerPack, PACK_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal qty(BigDecimal value) {
        return value == null ? null : value.setScale(QTY_SCALE, RoundingMode.HALF_UP);
    }
}
