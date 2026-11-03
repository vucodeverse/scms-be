package edu.fpt.sba.scms.service;

import edu.fpt.sba.scms.dto.request.CreateBranchRequest;
import edu.fpt.sba.scms.dto.request.SaveBranchRequest;
import edu.fpt.sba.scms.dto.request.SaveBranchStatusRequest;
import edu.fpt.sba.scms.dto.request.query.BranchListQuery;
import edu.fpt.sba.scms.dto.response.BranchDetailDto;
import edu.fpt.sba.scms.dto.response.BranchDto;
import org.springframework.data.domain.Page;

/** UC05 — Manage Branches (IMPLEMENTATION.md §10.3). Toàn bộ endpoint COO. */
public interface BranchService {

    Page<BranchDto> list(BranchListQuery query);

    BranchDetailDto detail(Integer id);

    BranchDto create(CreateBranchRequest request);

    /** Mã read-only — {@code code} trong body bị bỏ qua. */
    BranchDto update(Integer id, SaveBranchRequest request);

    /** INACTIVE bị chặn khi còn user ACTIVE (422 BRANCH_NOT_EMPTY). */
    BranchDto changeStatus(Integer id, SaveBranchStatusRequest request);

    /** Xoá mềm (BR-13): chuyển INACTIVE, cùng điều kiện như {@link #changeStatus}. */
    BranchDto softDelete(Integer id);
}
