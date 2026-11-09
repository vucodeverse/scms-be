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
import edu.fpt.sba.scms.dto.request.AssignBranchManagerRequest;
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
import edu.fpt.sba.scms.service.support.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final RefreshTokenStore refreshTokenStore;
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
        User manager = resolveActiveManager(branch);
        return branchMapper.toDetail(branch, manager == null ? null : manager.getId(),
                manager == null ? null : manager.getFullName(), employees.size(), employees);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        if (request.managerUserId() != null) {
            applyManagerAssignment(branch, request.managerUserId());
        }
        audit(AuditAction.BRANCH_CREATE, branch.getId(), null, snapshot(branch));
        if (request.managerUserId() != null) {
            audit(AuditAction.MANAGER_REASSIGN, branch.getId(),
                    Map.of("managerId", "null"), snapshot(branch));
        }
        return toDto(branch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDto update(Integer id, SaveBranchRequest request) {
        Branch branch = lock(id);
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
        boolean becomingInactive = request.status() == BranchStatus.INACTIVE
                && branch.getStatus() != BranchStatus.INACTIVE;
        branch.setStatus(request.status());
        if (becomingInactive) {
            applyManagerAssignment(branch, null);
        } else {
            branchRepository.save(branch);
        }
        audit(AuditAction.BRANCH_UPDATE, branch.getId(), before, snapshot(branch));
        return toDto(branch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDto assignManager(Integer id, AssignBranchManagerRequest request) {
        Branch branch = lock(id);
        Object before = snapshot(branch);
        boolean changed = applyManagerAssignment(branch, request == null ? null : request.managerUserId());
        if (changed) {
            audit(AuditAction.MANAGER_REASSIGN, branch.getId(), before, snapshot(branch));
        }
        return toDto(branch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDto changeStatus(Integer id, SaveBranchStatusRequest request) {
        Branch branch = lock(id);
        Object before = snapshot(branch);
        if (request.status() != BranchStatus.OPEN && branch.getStatus() == BranchStatus.OPEN) {
            ensureNoActiveStaff(branch);
        }
        boolean becomingInactive = request.status() == BranchStatus.INACTIVE
                && branch.getStatus() != BranchStatus.INACTIVE;
        branch.setStatus(request.status());
        if (becomingInactive) {
            applyManagerAssignment(branch, null);
        } else {
            branchRepository.save(branch);
        }
        audit(AuditAction.BRANCH_STATUS, branch.getId(), before, snapshot(branch));
        return toDto(branch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDto softDelete(Integer id) {
        Branch branch = lock(id);
        Object before = snapshot(branch);
        ensureNoActiveStaff(branch);
        branch.setStatus(BranchStatus.INACTIVE);
        applyManagerAssignment(branch, null);
        audit(AuditAction.BRANCH_STATUS, branch.getId(), before, snapshot(branch));
        return toDto(branch);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Đồng bộ hai chiều {@code branch.managerId} ↔ {@code user.branchId} theo quy tắc 1 chi nhánh - 1 quản lý chính.
     * Chỉ chấp nhận tài khoản {@code BRANCH_MANAGER} đang {@code ACTIVE} và chưa phụ trách chi nhánh nào khác.
     */
    private boolean applyManagerAssignment(Branch branch, Integer targetManagerId) {
        User target = targetManagerId == null ? null : loadAssignableManager(targetManagerId, branch);
        boolean unassignChanged = detachOtherManagers(branch, targetManagerId);

        if (target == null) {
            boolean cleared = branch.getManagerId() != null || unassignChanged;
            branch.setManagerId(null);
            branchRepository.save(branch);
            return cleared;
        }

        boolean changed = !Objects.equals(branch.getManagerId(), target.getId())
                || !Objects.equals(target.getBranchId(), branch.getId())
                || unassignChanged;
        if (!Objects.equals(target.getBranchId(), branch.getId())) {
            target.setBranchId(branch.getId());
            userRepository.save(target);
            // JWT mang claim `bid`: thu hồi refresh token để lần đăng nhập/refresh sau nhận đúng branchId.
            refreshTokenStore.revokeAllOfUser(target.getId());
        }
        branch.setManagerId(target.getId());
        branchRepository.save(branch);
        return changed;
    }

    private User loadAssignableManager(Integer targetManagerId, Branch branch) {
        if (branch.getStatus() == BranchStatus.INACTIVE) {
            throw new InvalidFieldValueException("managerUserId",
                    "Không thể gắn quản lý cho chi nhánh đã ngừng hoạt động.");
        }
        User target = userRepository.lockById(targetManagerId)
                .orElseThrow(() -> new InvalidFieldValueException("managerUserId",
                        "Không tìm thấy tài khoản quản lý."));
        if (target.getPosition() != Roles.BRANCH_MANAGER) {
            throw new InvalidFieldValueException("managerUserId",
                    "Chỉ được gắn tài khoản có vai trò Quản lý chi nhánh.");
        }
        if (!target.isActive()) {
            throw new InvalidFieldValueException("managerUserId",
                    "Tài khoản quản lý đang ngừng hoạt động.");
        }
        if (target.getBranchId() != null && !Objects.equals(target.getBranchId(), branch.getId())) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                    "Quản lý này đang phụ trách chi nhánh khác. Vui lòng gỡ khỏi chi nhánh cũ trước.");
        }
        return target;
    }

    private boolean detachOtherManagers(Branch branch, Integer keepManagerId) {
        Map<Integer, User> toDetach = new LinkedHashMap<>();
        for (User u : userRepository.findAllByBranchIdAndPosition(branch.getId(), Roles.BRANCH_MANAGER)) {
            if (!Objects.equals(u.getId(), keepManagerId)) {
                toDetach.put(u.getId(), u);
            }
        }
        Integer currentManagerId = branch.getManagerId();
        if (currentManagerId != null && !Objects.equals(currentManagerId, keepManagerId)
                && !toDetach.containsKey(currentManagerId)) {
            userRepository.lockById(currentManagerId).ifPresent(u -> toDetach.put(u.getId(), u));
        }
        for (User prev : toDetach.values()) {
            prev.setBranchId(null);
            // pin_lookup_hash phụ thuộc branchId (§7.2): xoá PIN khi gỡ khỏi chi nhánh.
            prev.setPinHash(null);
            prev.setPinLookupHash(null);
            prev.setPinSetAt(null);
            userRepository.save(prev);
            refreshTokenStore.revokeAllOfUser(prev.getId());
        }
        return !toDetach.isEmpty();
    }

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

    private Branch lock(Integer id) {
        return branchRepository.lockById(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
    }

    private BranchDto toDto(Branch branch) {
        User manager = resolveActiveManager(branch);
        return branchMapper.toDto(branch, manager == null ? null : manager.getId(),
                manager == null ? null : manager.getFullName(),
                userRepository.countByBranchIdAndStatus(branch.getId(), UserStatus.ACTIVE));
    }

    private User resolveActiveManager(Branch branch) {
        if (branch.getManagerId() != null) {
            User direct = userRepository.findById(branch.getManagerId())
                    .filter(u -> u.isActive() && u.getPosition() == Roles.BRANCH_MANAGER)
                    .orElse(null);
            if (direct != null) {
                return direct;
            }
        }
        return userRepository.findAllByBranchIdAndStatus(branch.getId(), UserStatus.ACTIVE).stream()
                .filter(u -> u.getPosition() == Roles.BRANCH_MANAGER)
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
                "managerId", String.valueOf(branch.getManagerId()),
                "status", String.valueOf(branch.getStatus()));
    }

    private void audit(AuditAction action, Object entityId, Object before, Object after) {
        auditLogService.log(action, ENTITY, entityId, before, after);
    }
}
