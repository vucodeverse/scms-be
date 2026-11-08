package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.dto.request.CreateBranchRequest;
import edu.fpt.sba.scms.dto.request.SaveBranchRequest;
import edu.fpt.sba.scms.dto.request.SaveBranchStatusRequest;
import edu.fpt.sba.scms.dto.request.query.BranchListQuery;
import edu.fpt.sba.scms.dto.response.BranchDetailDto;
import edu.fpt.sba.scms.dto.response.BranchDto;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.mapper.BranchMapper;
import edu.fpt.sba.scms.mapper.UserMapper;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.BranchSpecs;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.service.BranchService;
import edu.fpt.sba.scms.service.support.MasterDataQueries;
import edu.fpt.sba.scms.service.support.MasterDataText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** UC05 — Manage Branches (IMPLEMENTATION.md §10.3). BR-13: không xoá cứng, chỉ ngừng hoạt động. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private static final String ENTITY = "branch";
    private static final String RESOURCE_NAME = "chi nhánh";

    private static final Map<String, Sort> SORTS = Map.of(
            "code", Sort.by("code"),
            "name", Sort.by("name"),
            "createdAt", Sort.by("createdAt"));
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("code"));

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final BranchMapper branchMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<BranchDto> list(BranchListQuery query) {
        Sort sort = MasterDataQueries.sort(query.sortBy(), query.sortDir(), SORTS, DEFAULT_SORT);
        Page<Branch> page = branchRepository.findAll(BranchSpecs.managedList(query.keyword(), query.status()),
                MasterDataQueries.pageable(query.page(), query.size(), sort));
        return page.map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchDetailDto detail(Integer id) {
        Branch branch = get(id);
        List<UserDto> employees = userRepository.findAllByBranchIdAndStatus(id, UserStatus.ACTIVE).stream()
                .map(user -> userMapper.toDto(user, roleCodeOf(user), branch.getName(), null))
                .toList();
        return branchMapper.toDetail(branch, managerName(branch), employees.size(), employees);
    }

    @Override
    @Transactional
    public BranchDto create(CreateBranchRequest request) {
        ensureTimesValid(request.openingTime(), request.closingTime());
        if (branchRepository.existsByCode(request.code())) {
            throw new ConflictException(ErrorCode.DUPLICATE_CODE);
        }
        Branch branch = Branch.builder()
                .code(request.code())
                .name(MasterDataText.trimToNull(request.name()))
                .address(MasterDataText.trimToNull(request.address()))
                .phone(MasterDataText.trimToNull(request.phone()))
                .email(MasterDataText.trimToNull(request.email()))
                .taxCode(MasterDataText.trimToNull(request.taxCode()))
                .openingTime(request.openingTime())
                .closingTime(request.closingTime())
                .tableCount(request.tableCount())
                .status(request.status())
                .build();
        branchRepository.save(branch);
        audit(AuditAction.BRANCH_CREATE, branch.getId(), null, snapshot(branch));
        return toDto(branch);
    }

    @Override
    @Transactional
    public BranchDto update(Integer id, SaveBranchRequest request) {
        Branch branch = get(id);
        ensureTimesValid(request.openingTime(), request.closingTime());
        // code read-only (đặc tả §10.3): không nhận từ body dù client có gửi.
        Object before = snapshot(branch);
        branch.setName(MasterDataText.trimToNull(request.name()));
        branch.setAddress(MasterDataText.trimToNull(request.address()));
        branch.setPhone(MasterDataText.trimToNull(request.phone()));
        branch.setEmail(MasterDataText.trimToNull(request.email()));
        branch.setTaxCode(MasterDataText.trimToNull(request.taxCode()));
        branch.setOpeningTime(request.openingTime());
        branch.setClosingTime(request.closingTime());
        branch.setTableCount(request.tableCount());
        boolean closing = request.status() != BranchStatus.OPEN && branch.getStatus() == BranchStatus.OPEN;
        if (closing) {
            ensureNoActiveStaff(branch);
        }
        branch.setStatus(request.status());
        branchRepository.save(branch);
        audit(AuditAction.BRANCH_UPDATE, branch.getId(), before, snapshot(branch));
        return toDto(branch);
    }

    @Override
    @Transactional
    public BranchDto changeStatus(Integer id, SaveBranchStatusRequest request) {
        Branch branch = get(id);
        Object before = snapshot(branch);
        if (request.status() != BranchStatus.OPEN && branch.getStatus() == BranchStatus.OPEN) {
            ensureNoActiveStaff(branch);
        }
        branch.setStatus(request.status());
        branchRepository.save(branch);
        audit(AuditAction.BRANCH_STATUS, branch.getId(), before, snapshot(branch));
        return toDto(branch);
    }

    @Override
    @Transactional
    public BranchDto softDelete(Integer id) {
        Branch branch = get(id);
        Object before = snapshot(branch);
        ensureNoActiveStaff(branch);
        branch.setStatus(BranchStatus.INACTIVE);
        branchRepository.save(branch);
        audit(AuditAction.BRANCH_STATUS, branch.getId(), before, snapshot(branch));
        return toDto(branch);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Ngừng/inactive bị chặn khi còn nhân sự ACTIVE — tránh chi nhánh "mồ côi" mà người vẫn đăng nhập.
     * Ngoại lệ: chỉ còn đúng một Branch Manager ACTIVE (quản lý của chính chi nhánh).
     */
    private void ensureNoActiveStaff(Branch branch) {
        long active = userRepository.countByBranchIdAndStatus(branch.getId(), UserStatus.ACTIVE);
        User manager = branch.getManagerId() == null ? null
                : userRepository.findById(branch.getManagerId()).filter(User::isActive).orElse(null);
        if (active > (manager == null ? 0 : 1)) {
            throw new BusinessRuleException(BusinessErrorCode.BRANCH_NOT_EMPTY);
        }
    }

    private static void ensureTimesValid(LocalTime opening, LocalTime closing) {
        if (!opening.isBefore(closing)) {
            throw new InvalidFieldValueException("closingTime",
                    "Giờ đóng cửa phải sau giờ mở cửa (không hỗ trợ ca qua đêm).");
        }
    }

    private Branch get(Integer id) {
        return branchRepository.findById(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
    }

    private BranchDto toDto(Branch branch) {
        return branchMapper.toDto(branch, managerName(branch),
                userRepository.countByBranchIdAndStatus(branch.getId(), UserStatus.ACTIVE));
    }

    private String managerName(Branch branch) {
        if (branch.getManagerId() != null) {
            return userRepository.findById(branch.getManagerId()).map(User::getFullName).orElse(null);
        }
        return userRepository.findAllByBranchIdAndStatus(branch.getId(), UserStatus.ACTIVE).stream()
                .filter(u -> u.getPosition() == Roles.BRANCH_MANAGER)
                .map(User::getFullName)
                .findFirst()
                .orElse(null);
    }

    private static String roleCodeOf(User user) {
        return user.getPosition() == null ? null : user.getPosition().code();
    }

    private static Map<String, Object> snapshot(Branch branch) {
        return Map.of(
                "id", String.valueOf(branch.getId()),
                "code", String.valueOf(branch.getCode()),
                "name", String.valueOf(branch.getName()),
                "status", String.valueOf(branch.getStatus()));
    }

    private void audit(AuditAction action, Object entityId, Object before, Object after) {
        auditLogService.log(action, ENTITY, entityId, before, after);
    }
}
