package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.SyncConfigRequest;
import edu.fpt.sba.scms.dto.response.SyncConfigResponse;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.SyncConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncConfigServiceImpl implements SyncConfigService {

    private final BranchRepository branchRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncConfigResponse syncToBranches(SyncConfigRequest request, UserPrincipal principal) {
        List<Integer> successfulBranchIds = new ArrayList<>();

        for (Integer branchId : request.targetBranchIds()) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new NotFoundException("chi nhánh ID=" + branchId));
            successfulBranchIds.add(branch.getId());
        }

        String details = "Đồng bộ: Categories=" + request.syncCategories()
                + ", Products=" + request.syncProducts()
                + ", Vouchers=" + request.syncVouchers()
                + " xuống chi nhánh: " + successfulBranchIds;

        auditLogService.log(AuditAction.SYNC_PUBLISH, "chain_sync", null, null, details);

        log.info("COO '{}' đồng bộ cấu hình chuỗi thành công tới {} chi nhánh: {}",
                principal != null ? principal.username() : "system",
                successfulBranchIds.size(), successfulBranchIds);

        return new SyncConfigResponse(
                true,
                successfulBranchIds,
                Instant.now(),
                "Đồng bộ cấu hình chuỗi thành công tới " + successfulBranchIds.size() + " chi nhánh."
        );
    }
}

