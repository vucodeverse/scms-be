package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.StockTransactionType;
import edu.fpt.sba.scms.model.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repository truy xuất biến động kho (StockTransaction).
 * Tuân thủ nguyên tắc 16 cột: tra cứu mã hóa đơn và mã phiếu qua trường reason.
 */
@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long>, JpaSpecificationExecutor<StockTransaction> {

    @Query("SELECT COUNT(t) > 0 FROM StockTransaction t " +
            "WHERE t.branch.id = :branchId " +
            "AND t.type = :type " +
            "AND t.createdAt >= :since " +
            "AND t.reason LIKE %:invoicePattern%")
    boolean existsRecentInvoice(@Param("branchId") Integer branchId,
                                @Param("type") StockTransactionType type,
                                @Param("since") Instant since,
                                @Param("invoicePattern") String invoicePattern);

    @Query("SELECT t.reason FROM StockTransaction t " +
            "WHERE t.branch.id = :branchId " +
            "AND t.createdAt >= :startOfDay " +
            "AND t.reason LIKE %:prefixPattern%")
    List<String> findTodayReasonsByPrefix(@Param("branchId") Integer branchId,
                                         @Param("startOfDay") Instant startOfDay,
                                         @Param("prefixPattern") String prefixPattern);

    List<StockTransaction> findByBranch_IdOrderByCreatedAtDesc(Integer branchId);

    List<StockTransaction> findByReasonContainingOrderByCreatedAtDesc(String reasonPattern);

    default boolean existsByBranchIdAndInvoiceRef(Integer branchId, String invoiceRef) {
        if (invoiceRef == null || invoiceRef.isBlank()) {
            return false;
        }
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        return existsRecentInvoice(branchId, StockTransactionType.RECEIVE, sevenDaysAgo, "[HĐ: " + invoiceRef.trim() + "]");
    }

    default boolean existsByBranchIdAndInvoiceRef(Long branchId, String invoiceRef) {
        return existsByBranchIdAndInvoiceRef(branchId != null ? branchId.intValue() : null, invoiceRef);
    }

    default long countTodaySlipsByPrefix(Integer branchId, String prefix) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<String> reasons = findTodayReasonsByPrefix(branchId, startOfDay, prefix);
        if (reasons == null || reasons.isEmpty()) {
            return 0L;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "-?(\\d+)");
        long max = 0;
        for (String r : reasons) {
            if (r != null) {
                Matcher m = pattern.matcher(r);
                if (m.find()) {
                    try {
                        long seq = Long.parseLong(m.group(1));
                        if (seq > max) {
                            max = seq;
                        }
                    } catch (NumberFormatException ignored) {
                        // Bỏ qua giá trị không đúng định dạng
                    }
                }
            }
        }
        return max;
    }

    default long countTodaySlipsByPrefix(Long branchId, String prefix) {
        return countTodaySlipsByPrefix(branchId != null ? branchId.intValue() : null, prefix);
    }
}
