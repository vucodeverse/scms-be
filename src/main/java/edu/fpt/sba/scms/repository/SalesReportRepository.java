package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.OrderStatus;
import edu.fpt.sba.scms.common.enums.PaymentMethod;
import edu.fpt.sba.scms.common.enums.PaymentStatus;
import edu.fpt.sba.scms.common.enums.ProductStatus;
import edu.fpt.sba.scms.common.enums.RefundStatus;
import edu.fpt.sba.scms.model.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Truy vấn tổng hợp (chỉ đọc) trên đơn hàng / dòng món / thanh toán / hoàn tiền cho module report (UC34, 35, 39–42).
 * <p>
 * Kế thừa {@link Repository} (không phải {@code JpaRepository}) để không lộ thao tác ghi. Mọi khoảng thời gian là
 * nửa mở {@code [start, end)} theo UTC. Giờ được gom theo <b>giờ UTC</b> rồi tầng service đổi sang giờ VN
 * (VN cố định UTC+7, không có DST) — tránh {@code CONVERT_TZ} không chạy trên H2.
 */
public interface SalesReportRepository extends Repository<Order, Integer> {

    /** Doanh số theo (ca, phương thức thanh toán) — summary + payment mix theo ca (UC35). */
    interface ShiftMethodSalesRow {
        Integer getShiftId();

        PaymentMethod getMethod();

        Long getOrderCount();

        BigDecimal getGrossAmount();

        BigDecimal getDiscountAmount();

        BigDecimal getRevenue();
    }

    /** Tổng hoàn tiền theo (ca, phương thức thanh toán của đơn gốc). */
    interface ShiftMethodRefundRow {
        Integer getShiftId();

        PaymentMethod getMethod();

        BigDecimal getAmount();
    }

    /** Một giờ UTC (năm/tháng/ngày/giờ) — để dựng chuỗi theo ngày hoặc theo khung giờ VN. */
    interface UtcHourRow {
        Integer getYr();

        Integer getMon();

        Integer getDy();

        Integer getHr();

        Long getOrderCount();

        BigDecimal getRevenue();
    }

    interface ProductSalesRow {
        Integer getProductId();

        String getProductName();

        Long getQuantity();

        BigDecimal getRevenue();
    }

    interface ProductRow {
        Integer getProductId();

        String getProductName();
    }

    /** Dòng món đã bán theo (chi nhánh, sản phẩm, size) để tính giá vốn (UC40). */
    interface SoldProductCostRow {
        Integer getBranchId();

        Integer getProductId();

        String getProductName();

        String getSize();

        Long getQuantity();

        /** Số lượng của các dòng chưa có snapshot {@code cogs_amount} — phải tính từ công thức. */
        Long getQuantityWithoutSnapshot();

        BigDecimal getSnapshotCogs();
    }

    interface BranchSalesRow {
        Integer getBranchId();

        Long getOrderCount();

        BigDecimal getRevenue();
    }

    interface BranchAmountRow {
        Integer getBranchId();

        BigDecimal getAmount();
    }

    interface CashierSalesRow {
        Integer getCashierId();

        Long getOrderCount();

        BigDecimal getRevenue();
    }

    @Query("""
            select o.shiftId as shiftId, p.method as method, count(o) as orderCount,
                   sum(o.totalAmount) as grossAmount, sum(o.discountAmount) as discountAmount,
                   sum(o.finalAmount) as revenue
            from Order o
            left join Payment p on p.orderId = o.id and p.status = :paymentSuccess
            where o.branchId = :branchId and o.status in :statuses
              and o.paidAt >= :start and o.paidAt < :end
              and (:shiftId is null or o.shiftId = :shiftId)
              and (:method is null or p.method = :method)
            group by o.shiftId, p.method""")
    List<ShiftMethodSalesRow> sumSalesByShiftAndMethod(
            @Param("branchId") Integer branchId, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end,
            @Param("shiftId") Integer shiftId, @Param("method") PaymentMethod method,
            @Param("paymentSuccess") PaymentStatus paymentSuccess);

    @Query("""
            select o.shiftId as shiftId, p.method as method, sum(r.amount) as amount
            from Refund r
            join Order o on o.id = r.orderId
            left join Payment p on p.orderId = o.id and p.status = :paymentSuccess
            where r.status = :refundDone and o.branchId = :branchId and o.status in :statuses
              and o.paidAt >= :start and o.paidAt < :end
              and (:shiftId is null or o.shiftId = :shiftId)
              and (:method is null or p.method = :method)
            group by o.shiftId, p.method""")
    List<ShiftMethodRefundRow> sumRefundsByShiftAndMethod(
            @Param("branchId") Integer branchId, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end,
            @Param("shiftId") Integer shiftId, @Param("method") PaymentMethod method,
            @Param("paymentSuccess") PaymentStatus paymentSuccess, @Param("refundDone") RefundStatus refundDone);

    /** Doanh số theo giờ UTC của thời điểm thanh toán ({@code paid_at}) — chuỗi theo ngày (UC34, UC35). */
    @Query("""
            select extract(year from o.paidAt) as yr, extract(month from o.paidAt) as mon,
                   extract(day from o.paidAt) as dy, extract(hour from o.paidAt) as hr,
                   count(o) as orderCount, sum(o.finalAmount) as revenue
            from Order o
            where o.branchId = :branchId and o.status in :statuses
              and o.paidAt >= :start and o.paidAt < :end
              and (:shiftId is null or o.shiftId = :shiftId)
              and (:method is null or exists (select 1 from Payment p
                       where p.orderId = o.id and p.status = :paymentSuccess and p.method = :method))
            group by extract(year from o.paidAt), extract(month from o.paidAt),
                     extract(day from o.paidAt), extract(hour from o.paidAt)""")
    List<UtcHourRow> sumSalesByPaidUtcHour(
            @Param("branchId") Integer branchId, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end,
            @Param("shiftId") Integer shiftId, @Param("method") PaymentMethod method,
            @Param("paymentSuccess") PaymentStatus paymentSuccess);

    /**
     * Số đơn theo giờ UTC của thời điểm <b>tạo đơn</b> (UC42 BR-03). {@code branchId = null} = toàn chuỗi.
     */
    @Query("""
            select extract(year from o.createdAt) as yr, extract(month from o.createdAt) as mon,
                   extract(day from o.createdAt) as dy, extract(hour from o.createdAt) as hr,
                   count(o) as orderCount, sum(o.finalAmount) as revenue
            from Order o
            where (:branchId is null or o.branchId = :branchId) and o.status in :statuses
              and o.createdAt >= :start and o.createdAt < :end
            group by extract(year from o.createdAt), extract(month from o.createdAt),
                     extract(day from o.createdAt), extract(hour from o.createdAt)""")
    List<UtcHourRow> countOrdersByCreatedUtcHour(
            @Param("branchId") Integer branchId, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end);

    /** Số lượng &amp; doanh thu theo sản phẩm (UC35, UC41). {@code branchId = null} = toàn chuỗi. */
    @Query("""
            select ol.productId as productId, pr.name as productName,
                   sum(ol.quantity) as quantity, sum(ol.lineTotal) as revenue
            from OrderLine ol
            join ol.order o
            join ol.product pr
            where (:branchId is null or o.branchId = :branchId) and o.status in :statuses
              and o.paidAt >= :start and o.paidAt < :end
              and (:shiftId is null or o.shiftId = :shiftId)
              and (:method is null or exists (select 1 from Payment p
                       where p.orderId = o.id and p.status = :paymentSuccess and p.method = :method))
            group by ol.productId, pr.name""")
    List<ProductSalesRow> sumSalesByProduct(
            @Param("branchId") Integer branchId, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end,
            @Param("shiftId") Integer shiftId, @Param("method") PaymentMethod method,
            @Param("paymentSuccess") PaymentStatus paymentSuccess);

    /** Sản phẩm đang kinh doanh — để báo cáo bán chậm có cả món không bán được ly nào (UC41). */
    @Query("select p.id as productId, p.name as productName from Product p where p.status in :statuses")
    List<ProductRow> findProductsByStatusIn(@Param("statuses") Collection<ProductStatus> statuses);

    @Query("""
            select o.branchId as branchId, count(o) as orderCount, sum(o.finalAmount) as revenue
            from Order o
            where o.branchId in :branchIds and o.status in :statuses
              and o.paidAt >= :start and o.paidAt < :end
            group by o.branchId""")
    List<BranchSalesRow> sumSalesByBranch(
            @Param("branchIds") Collection<Integer> branchIds, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select o.branchId as branchId, sum(r.amount) as amount
            from Refund r
            join Order o on o.id = r.orderId
            where r.status = :refundDone and o.branchId in :branchIds and o.status in :statuses
              and o.paidAt >= :start and o.paidAt < :end
            group by o.branchId""")
    List<BranchAmountRow> sumRefundsByBranch(
            @Param("branchIds") Collection<Integer> branchIds, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end, @Param("refundDone") RefundStatus refundDone);

    @Query("""
            select o.branchId as branchId, ol.productId as productId, pr.name as productName, ol.size as size,
                   sum(ol.quantity) as quantity,
                   sum(case when ol.cogsAmount is null then ol.quantity else 0 end) as quantityWithoutSnapshot,
                   sum(coalesce(ol.cogsAmount, 0)) as snapshotCogs
            from OrderLine ol
            join ol.order o
            join ol.product pr
            where o.branchId in :branchIds and o.status in :statuses
              and o.paidAt >= :start and o.paidAt < :end
            group by o.branchId, ol.productId, pr.name, ol.size""")
    List<SoldProductCostRow> sumSoldProductsForCost(
            @Param("branchIds") Collection<Integer> branchIds, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end);

    /**
     * Công thức + đơn giá nguyên liệu của các sản phẩm (BR-19). Bảng {@code recipe} chưa có entity
     * (xem {@link RecipeReadRepository}) nên dùng native query.
     *
     * @return mỗi dòng {@code [product_id, size, ingredient_id, quantity_base, unit_cost]}
     */
    @Query(value = """
            select r.product_id, r.size, r.ingredient_id, r.quantity_base, i.unit_cost
            from recipe r
            left join ingredient i on i.ingredient_id = r.ingredient_id
            where r.product_id in (:productIds)""", nativeQuery = true)
    List<Object[]> findRecipeCosts(@Param("productIds") Collection<Integer> productIds);

    /** Số đơn và doanh thu theo thu ngân — chỉ số năng suất UC39. */
    @Query("""
            select o.cashierId as cashierId, count(o) as orderCount, sum(o.finalAmount) as revenue
            from Order o
            where o.branchId = :branchId and o.status in :statuses and o.cashierId is not null
              and o.paidAt >= :start and o.paidAt < :end
            group by o.cashierId""")
    List<CashierSalesRow> sumSalesByCashier(
            @Param("branchId") Integer branchId, @Param("statuses") Collection<OrderStatus> statuses,
            @Param("start") Instant start, @Param("end") Instant end);
}
