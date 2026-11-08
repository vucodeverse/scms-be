package edu.fpt.sba.scms.repository;

import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.model.CashTransaction;
import edu.fpt.sba.scms.model.Shift;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Truy vấn chỉ đọc cho báo cáo dòng tiền két (UC37) và widget két tiền của dashboard (UC34).
 */
public interface CashFlowReportRepository extends Repository<CashTransaction, Integer> {

    /** Ca có trạng thái trong {@code statuses} (report bỏ ca chỉ mới lên lịch) trong khoảng ngày, theo thời gian. */
    @Query("""
            select s from Shift s
            where s.branchId = :branchId and s.shiftDate between :from and :to
              and s.status in :statuses
              and (:shiftId is null or s.id = :shiftId)
            order by s.shiftDate, s.plannedStart, s.id""")
    List<Shift> findShifts(@Param("branchId") Integer branchId, @Param("from") LocalDate from,
                           @Param("to") LocalDate to, @Param("statuses") Collection<ShiftStatus> statuses,
                           @Param("shiftId") Integer shiftId);

    @Query("""
            select c from CashTransaction c
            where c.shiftId in :shiftIds
            order by c.createdAt, c.id""")
    List<CashTransaction> findByShiftIds(@Param("shiftIds") Collection<Integer> shiftIds);
}
