package edu.fpt.sba.scms.service.report;

import edu.fpt.sba.scms.common.enums.StockCountStatus;
import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.dto.response.report.InventoryItemDto;
import edu.fpt.sba.scms.dto.response.report.LowStockItemDto;
import edu.fpt.sba.scms.repository.InventoryReportRepository.MovementTotalRow;
import edu.fpt.sba.scms.repository.InventoryReportRepository.StockCountRow;
import edu.fpt.sba.scms.repository.InventoryReportRepository.StockItemRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReportCalculatorTest {

    private static StockItemRow stock(long id, String name, String current, String safety, String perPack) {
        return Rows.of(StockItemRow.class, "ingredientId", id, "name", name, "baseUnit", "g", "packUnit", "bao",
                "unitsPerPack", perPack == null ? null : new BigDecimal(perPack), "currentQty", new BigDecimal(current),
                "safetyStockLevel", new BigDecimal(safety));
    }

    private static MovementTotalRow move(long id, StockTransactionType type, String qty) {
        return Rows.of(MovementTotalRow.class, "ingredientId", id, "type", type, "quantity", new BigDecimal(qty));
    }

    private static StockCountRow count(long id, String book, String counted) {
        return Rows.of(StockCountRow.class, "ingredientId", id, "countedAt", Instant.parse("2026-10-15T05:00:00Z"),
                "bookQuantity", new BigDecimal(book), "countedQuantity", new BigDecimal(counted));
    }

    private final List<StockItemRow> stock = List.of(
            stock(1, "Cà phê hạt", "1000", "200", "500"),
            stock(2, "Sữa đặc", "50", "100", null));

    @Test
    void tonDauCuoiKySuyNguocTuTonHienTai() {
        List<MovementTotalRow> inPeriod = List.of(
                move(1, StockTransactionType.RECEIVE, "500"),
                move(1, StockTransactionType.ISSUE, "100"),
                move(1, StockTransactionType.DISPOSAL, "50"),
                move(1, StockTransactionType.ADJUST, "-10"));
        List<MovementTotalRow> after = List.of(move(1, StockTransactionType.RECEIVE, "200"));
        // Kiểm kê cũ hơn đứng sau: chỉ lấy lần gần nhất.
        List<StockCountRow> counts = List.of(count(1, "810", "800"), count(1, "300", "300"));

        InventoryReportCalculator.InventoryReport report = InventoryReportCalculator.calculate(stock, inPeriod, after,
                counts);

        InventoryItemDto coffee = report.items().get(0);
        assertThat(coffee.closingQty()).isEqualByComparingTo("800");
        assertThat(coffee.openingQty()).isEqualByComparingTo("460");
        assertThat(coffee.receivedQty()).isEqualByComparingTo("500");
        assertThat(coffee.issuedQty()).isEqualByComparingTo("100");
        assertThat(coffee.disposedQty()).isEqualByComparingTo("50");
        assertThat(coffee.adjustedQty()).isEqualByComparingTo("-10");
        assertThat(coffee.closingPacks()).isEqualByComparingTo("1.60");
        assertThat(coffee.countStatus()).isEqualTo(StockCountStatus.VARIANCE);
        assertThat(coffee.countVariance()).isEqualByComparingTo("-10");
        assertThat(coffee.lowStock()).isFalse();
    }

    @Test
    void chuaKiemKeVaTonThap() {
        InventoryReportCalculator.InventoryReport report = InventoryReportCalculator.calculate(stock, List.of(),
                List.of(), List.of());

        InventoryItemDto milk = report.items().get(1);
        assertThat(milk.countStatus()).isEqualTo(StockCountStatus.UNCOUNTED);
        assertThat(milk.lowStock()).isTrue();
        assertThat(milk.closingPacks()).isNull();
        assertThat(milk.openingQty()).isEqualByComparingTo("50");
        assertThat(report.summary().lowStockCount()).isEqualTo(1);
        assertThat(report.summary().uncountedCount()).isEqualTo(2);
    }

    @Test
    void canhBaoTonThapSapTheoMucThieu() {
        List<LowStockItemDto> low = InventoryReportCalculator.lowStock(List.of(
                stock(1, "A", "90", "100", null), stock(2, "B", "10", "100", null), stock(3, "C", "500", "100", null)));

        assertThat(low).extracting(LowStockItemDto::name).containsExactly("B", "A");
    }
}
