package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.SaveVoucherRequest;
import edu.fpt.sba.scms.dto.request.SaveVoucherStatusRequest;
import edu.fpt.sba.scms.dto.request.query.VoucherListQuery;
import edu.fpt.sba.scms.dto.response.VoucherDetailDto;
import edu.fpt.sba.scms.dto.response.VoucherDto;
import org.springframework.data.domain.Page;

/** UC13 — Manage Vouchers (IMPLEMENTATION.md §10.8). */
public interface VoucherService {

    Page<VoucherDto> list(VoucherListQuery query);

    VoucherDetailDto detail(Integer id);

    VoucherDetailDto create(SaveVoucherRequest request);

    VoucherDetailDto update(Integer id, SaveVoucherRequest request);

    VoucherDto changeStatus(Integer id, SaveVoucherStatusRequest request);

    /** Xoá mềm: chuyển INACTIVE (đúng đặc tả). */
    VoucherDto softDelete(Integer id);

    /** Xoá cứng (bổ sung ngoài đặc tả): chặn khi voucher đã được dùng cho đơn nào (giữ lịch sử giảm giá). */
    void hardDelete(Integer id);
}
