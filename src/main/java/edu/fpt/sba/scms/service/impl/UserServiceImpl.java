package edu.fpt.sba.scms.service.impl;

import edu.fpt.sba.scms.common.audit.AuditLogService;
import edu.fpt.sba.scms.common.enums.AuditAction;
import edu.fpt.sba.scms.common.enums.BranchStatus;
import edu.fpt.sba.scms.common.enums.BusinessErrorCode;
import edu.fpt.sba.scms.common.enums.ErrorCode;
import edu.fpt.sba.scms.common.enums.Roles;
import edu.fpt.sba.scms.common.enums.ShiftStatus;
import edu.fpt.sba.scms.common.enums.UserStatus;
import edu.fpt.sba.scms.common.exception.BusinessRuleException;
import edu.fpt.sba.scms.common.exception.ConflictException;
import edu.fpt.sba.scms.common.exception.ForbiddenException;
import edu.fpt.sba.scms.common.exception.InvalidFieldValueException;
import edu.fpt.sba.scms.common.exception.NotFoundException;
import edu.fpt.sba.scms.common.util.ValidationUtil;
import edu.fpt.sba.scms.dto.request.AssignRoleRequest;
import edu.fpt.sba.scms.dto.request.ResetPasswordRequest;
import edu.fpt.sba.scms.dto.request.SaveUserRequest;
import edu.fpt.sba.scms.dto.request.SaveUserStatusRequest;
import edu.fpt.sba.scms.dto.request.query.UserListQuery;
import edu.fpt.sba.scms.dto.response.RoleDto;
import edu.fpt.sba.scms.dto.response.UserDetailDto;
import edu.fpt.sba.scms.dto.response.UserDto;
import edu.fpt.sba.scms.mapper.UserMapper;
import edu.fpt.sba.scms.model.Branch;
import edu.fpt.sba.scms.model.Role;
import edu.fpt.sba.scms.model.User;
import edu.fpt.sba.scms.repository.BranchRepository;
import edu.fpt.sba.scms.repository.RoleRepository;
import edu.fpt.sba.scms.repository.ShiftRepository;
import edu.fpt.sba.scms.repository.UserRepository;
import edu.fpt.sba.scms.repository.UserSpecs;
import edu.fpt.sba.scms.security.BranchContext;
import edu.fpt.sba.scms.security.principal.UserPrincipal;
import edu.fpt.sba.scms.service.UserService;
import edu.fpt.sba.scms.service.support.MasterDataQueries;
import edu.fpt.sba.scms.service.support.MasterDataText;
import edu.fpt.sba.scms.service.support.PinHasher;
import edu.fpt.sba.scms.service.support.PinLockoutService;
import edu.fpt.sba.scms.service.support.RefreshTokenStore;
import edu.fpt.sba.scms.service.support.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * UC02 + quản lý user (IMPLEMENTATION.md §10.2, §10.2.1).
 * <ul>
 *   <li>Phân quyền thô ({@code @PreAuthorize}) ở controller; <b>branch scope</b> kiểm ở đây vì phụ thuộc dữ liệu
 *       ({@code BranchContext}).</li>
 *   <li>Không bao giờ trả hash/PIN; audit không log dữ liệu nhạy cảm.</li>
 *   <li>Đổi role/trạng thái/khoá dùng {@code lockById} để hai request đồng thời không cùng "vượt" kiểm COO cuối.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String ENTITY = "user";
    private static final String RESOURCE_NAME = "người dùng";

    private static final Map<String, Sort> SORTS = Map.of(
            "fullName", Sort.by("fullName"),
            "username", Sort.by("username"),
            "createdAt", Sort.by("createdAt"));
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id"));

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final ShiftRepository shiftRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PinHasher pinHasher;
    private final RefreshTokenStore refreshTokenStore;
    private final UserDirectory userDirectory;
    private final AuditLogService auditLogService;
    private final PinLockoutService pinLockoutService;

    // ------------------------------------------------------------------ query

    @Override
    @Transactional(readOnly = true)
    public Page<UserDto> list(UserListQuery query) {
        UserPrincipal actor = currentActor();
        Integer branchId = scopedBranch(query.branchId(), null);
        Boolean unassigned = resolveUnassignedScope(actor, query.unassigned(), branchId);
        Sort sort = MasterDataQueries.sort(query.sortBy(), query.sortDir(), SORTS, DEFAULT_SORT);
        Page<User> page = userRepository.findAll(
                UserSpecs.managedList(query.keyword(), MasterDataText.trimToNull(query.role()), branchId,
                        query.status(), unassigned),
                MasterDataQueries.pageable(query.page(), query.size(), sort));
        return page.map(user -> toDto(user, null));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailDto detail(Integer id) {
        User user = getScopedUser(id);
        // Không có bảng wage_rate ở v1 (module Payroll) — trả null thay vì bịa số.
        return userMapper.toDetail(user, roleCodeOf(user), branchNameOf(user.getBranchId()), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roleRepository.findAll(Sort.by("id")).stream()
                .map(role -> new RoleDto(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                        userRepository.countByRoleCodesAndStatus(List.of(role.getCode()), UserStatus.ACTIVE)))
                .toList();
    }

    // ------------------------------------------------------------------ write

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDto create(SaveUserRequest request, UserPrincipal actor) {
        Roles position = request.position();
        boolean selfIsCoo = isCoo(actor);
        if (!selfIsCoo && !UserDirectory.BRANCH_STAFF_ROLES.contains(position)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
                    "Quản lý chi nhánh chỉ được tạo Cashier / Barista / Waiter.");
        }
        if (position == Roles.COO) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Tài khoản COO chỉ được seed, không tạo qua API.");
        }

        Integer branchId = scopedBranch(request.branchId(), "branchId");
        if (positionsNeedsBranch(position) && branchId == null) {
            throw new InvalidFieldValueException("branchId", "COO tạo nhân sự phải chọn chi nhánh.");
        }
        requireValidBranch(branchId, request.status());

        String phone = MasterDataText.trimToNull(request.phone());
        String email = lowerOrNull(request.email());
        validateUniqueContact(phone, email, null);

        Role role = userDirectory.roleOf(position);

        User user = User.builder()
                .fullName(MasterDataText.trimToNull(request.fullName()))
                .phone(phone)
                .email(email)
                .position(position)
                .roleId(role.getId())
                .branchId(branchId)
                .status(request.status())
                .build();

        applyLoginAccount(user, request.username(), request.password(), true);
        applyPin(user, branchId, request.pin(), true);
        applyCard(user, request.cardCode(), null);

        Branch assignedBranch = resolveInitialManagerBranch(position, branchId, request.status());
        userRepository.save(user);
        if (assignedBranch != null) {
            assignedBranch.setManagerId(user.getId());
            branchRepository.save(assignedBranch);
        }
        audit(position == Roles.BRANCH_MANAGER ? AuditAction.MANAGER_CREATE : AuditAction.EMPLOYEE_CREATE,
                user.getId(), null, snapshot(user));
        return toDto(user, assignedBranch == null ? null : assignedBranch.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDto update(Integer id, SaveUserRequest request, UserPrincipal actor) {
        User user = lockScopedUser(id);
        ensureNotSelf(actor, user, "chức danh/chi nhánh của chính mình");
        Roles position = request.position();
        if (position != user.getPosition() && !isCoo(actor)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Chỉ COO đổi được chức danh.");
        }
        if (position == Roles.COO) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Tài khoản COO chỉ được seed, không sửa qua API.");
        }
        if (!isCoo(actor) && !UserDirectory.BRANCH_STAFF_ROLES.contains(position)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED,
                    "Quản lý chi nhánh chỉ được sửa Cashier / Barista / Waiter.");
        }
        // Đổi chi nhánh của BRANCH_MANAGER cùng nhóm role đi qua endpoint riêng (§10.3) để audit MANAGER_REASSIGN.
        Integer branchId = isCoo(actor) ? request.branchId() : user.getBranchId();
        if (Objects.equals(branchId, null) && positionsNeedsBranch(position)) {
            throw new InvalidFieldValueException("branchId", "Nhân sự chi nhánh phải thuộc một chi nhánh.");
        }
        if (isCoo(actor) && Objects.equals(position, user.getPosition())
                && !Objects.equals(branchId, user.getBranchId())) {
            throw new InvalidFieldValueException("branchId",
                    "Đổi chi nhánh dùng endpoint riêng, không đổi qua cập nhật thông tin.");
        }
        requireValidBranch(branchId, request.status());

        String phone = MasterDataText.trimToNull(request.phone());
        String email = lowerOrNull(request.email());
        validateUniqueContact(phone, email, id);

        UserStatus previousStatus = user.getStatus();
        Roles previousPosition = user.getPosition();
        Integer previousBranchId = user.getBranchId();
        Object before = snapshot(user);

        if (isCoo(actor)) {
            Role role = userDirectory.roleOf(position);
            user.setPosition(position);
            user.setRoleId(role.getId());
            user.setBranchId(branchId);
        }
        user.setFullName(MasterDataText.trimToNull(request.fullName()));
        user.setPhone(phone);
        user.setEmail(email);
        user.setStatus(request.status());
        applyLoginAccount(user, request.username(), request.password(), false);
        applyPin(user, user.getBranchId(), request.pin(), false);
        applyCard(user, request.cardCode(), user.getId());

        boolean roleChanged = previousPosition != user.getPosition();
        boolean branchChanged = !Objects.equals(previousBranchId, user.getBranchId());
        boolean deactivated = previousStatus == UserStatus.ACTIVE && user.getStatus() != UserStatus.ACTIVE;
        boolean reactivated = previousStatus != UserStatus.ACTIVE && user.getStatus() == UserStatus.ACTIVE;
        if (deactivated) {
            ensureNotLastCoo(user, "ngừng hoạt động");
            ensureNoOpenShiftAsActiveCashier(user.getId());
            refreshTokenStore.revokeAllOfUser(user.getId());
        } else if (roleChanged || branchChanged) {
            refreshTokenStore.revokeAllOfUser(user.getId());
        }
        if (reactivated) {
            validateActiveUniquenessOnReactivation(user);
        }

        if ((deactivated && user.getPosition() == Roles.BRANCH_MANAGER)
                || (previousPosition == Roles.BRANCH_MANAGER && user.getPosition() != Roles.BRANCH_MANAGER)) {
            boolean clearUserBranch = user.getPosition() == Roles.BRANCH_MANAGER;
            detachManagerFromBranch(user, previousBranchId, clearUserBranch);
        }

        Branch assignedBranch = null;
        if (user.getPosition() == Roles.BRANCH_MANAGER
                && user.getStatus() == UserStatus.ACTIVE
                && user.getBranchId() != null
                && (previousPosition != Roles.BRANCH_MANAGER
                    || previousStatus != UserStatus.ACTIVE
                    || !Objects.equals(previousBranchId, user.getBranchId()))) {
            assignedBranch = resolveInitialManagerBranch(user.getPosition(), user.getBranchId(), user.getStatus());
        }

        userRepository.save(user);
        if (assignedBranch != null) {
            assignedBranch.setManagerId(user.getId());
            branchRepository.save(assignedBranch);
        }
        if (request.pin() != null && !request.pin().isBlank() && user.getBranchId() != null) {
            String actorName = actor != null && actor.userId() != null
                    ? userRepository.findById(actor.userId()).map(User::getFullName).orElse(actor.username())
                    : "Quản lý";
            pinLockoutService.unlockPin(user.getBranchId(), user.getId(),
                    actor != null ? actor.userId() : null, actorName, "Đặt lại mã PIN mới");
        }
        audit(roleChanged ? AuditAction.ROLE_ASSIGN
                : user.getPosition() == Roles.BRANCH_MANAGER ? AuditAction.MANAGER_UPDATE : AuditAction.EMPLOYEE_UPDATE,
                user.getId(), before, snapshot(user));
        return toDto(user, assignedBranch == null ? null : assignedBranch.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDto changeStatus(Integer id, SaveUserStatusRequest request, UserPrincipal actor) {
        User user = lockScopedUser(id);
        ensureNotSelf(actor, user, "tài khoản của chính mình");
        if (user.getPosition() == Roles.COO) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Không đổi trạng thái tài khoản COO qua API.");
        }
        Object before = snapshot(user);
        boolean deactivated = request.status() != UserStatus.ACTIVE && user.getStatus() == UserStatus.ACTIVE;
        boolean activated = request.status() == UserStatus.ACTIVE && user.getStatus() != UserStatus.ACTIVE;
        user.setStatus(request.status());
        if (deactivated) {
            ensureNotLastCoo(user, "ngừng hoạt động");
            ensureNoOpenShiftAsActiveCashier(user.getId());
            refreshTokenStore.revokeAllOfUser(user.getId());
            if (user.getPosition() == Roles.BRANCH_MANAGER) {
                detachManagerFromBranch(user, user.getBranchId(), true);
            }
        } else if (activated) {
            if (user.getPosition() == Roles.BRANCH_MANAGER && user.getBranchId() != null) {
                user.setBranchId(null);
            } else if (user.getBranchId() != null) {
                requireValidBranch(user.getBranchId(), UserStatus.ACTIVE);
            }
            validateActiveUniquenessOnReactivation(user);
        }
        userRepository.save(user);
        audit(AuditAction.EMPLOYEE_STATUS, user.getId(), before, snapshot(user));
        return toDto(user, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDto assignRole(Integer id, AssignRoleRequest request, UserPrincipal actor) {
        User user = lockScopedUser(id);
        ensureNotSelf(actor, user, "role của chính mình");
        Roles role = request.roleCode();
        if (role == Roles.COO) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Không gán role COO qua API.");
        }
        // Role khác nhóm (vd. BM -> Barista) làm mất/ thêm tài khoản đăng nhập hoặc PIN theo quy tắc §7.2.
        if (UserDirectory.requiresLoginAccount(role) != UserDirectory.requiresLoginAccount(user.getPosition())
                || UserDirectory.requiresPin(role) != UserDirectory.requiresPin(user.getPosition())) {
            throw new InvalidFieldValueException("roleCode",
                    "Đổi nhóm role cần đi qua cập nhật thông tin để đồng bộ tài khoản đăng nhập / PIN.");
        }
        Object before = snapshot(user);
        Role dbRole = userDirectory.roleOf(role);
        user.setPosition(role);
        user.setRoleId(dbRole.getId());
        userRepository.save(user);
        // Token cũ mang role cũ trong claim: thu hồi để lần đăng nhập sau nhận quyền mới.
        refreshTokenStore.revokeAllOfUser(user.getId());
        audit(AuditAction.ROLE_ASSIGN, user.getId(), before, snapshot(user));
        return toDto(user, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Integer id, ResetPasswordRequest request, UserPrincipal actor) {
        User user = lockScopedUser(id);
        if (!isCoo(actor) && !Objects.equals(user.getBranchId(), actor.branchId())) {
            throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
        }
        if (!UserDirectory.requiresLoginAccount(user.getPosition())) {
            throw new InvalidFieldValueException("password",
                    "Tài khoản không đăng nhập web — đặt lại PIN thay vì mật khẩu.");
        }
        Object before = snapshot(user);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        refreshTokenStore.revokeAllOfUser(user.getId());
        audit(AuditAction.USER_PASSWORD_RESET, user.getId(), before, snapshot(user));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Khi tạo mới {@code BRANCH_MANAGER} kèm {@code branchId != null}: khoá chi nhánh và giữ bất biến
     * 1 chi nhánh chỉ có 1 quản lý chính đang hoạt động. Nếu {@code branchId == null}, quản lý nằm ở danh sách chờ gắn.
     */
    private Branch resolveInitialManagerBranch(Roles position, Integer branchId, UserStatus status) {
        if (position != Roles.BRANCH_MANAGER || branchId == null) {
            return null;
        }
        if (status != UserStatus.ACTIVE) {
            throw new InvalidFieldValueException("branchId",
                    "Tài khoản quản lý ngừng hoạt động không thể gắn vào chi nhánh.");
        }
        Branch branch = branchRepository.lockById(branchId)
                .orElseThrow(() -> new InvalidFieldValueException("branchId", "Chi nhánh không tồn tại."));
        if (branch.getStatus() == BranchStatus.INACTIVE) {
            throw new InvalidFieldValueException("branchId",
                    "Không thể gắn quản lý cho chi nhánh đã ngừng hoạt động.");
        }
        boolean hasActiveManager = branch.getManagerId() != null
                && userRepository.findById(branch.getManagerId()).filter(User::isActive).isPresent();
        if (hasActiveManager) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT,
                    "Chi nhánh đã có quản lý phụ trách. Vui lòng đổi quản lý trong màn hình chi nhánh.");
        }
        return branch;
    }

    /** Gỡ liên kết quản lý ở chi nhánh khi tài khoản quản lý bị ngừng hoạt động hoặc đổi sang chức danh khác. */
    private void detachManagerFromBranch(User user, Integer oldBranchId, boolean clearUserBranch) {
        if (oldBranchId != null) {
            branchRepository.lockById(oldBranchId).ifPresent(branch -> {
                if (Objects.equals(branch.getManagerId(), user.getId())) {
                    branch.setManagerId(null);
                    branchRepository.save(branch);
                }
            });
        }
        if (clearUserBranch) {
            user.setBranchId(null);
            user.setPinHash(null);
            user.setPinLookupHash(null);
            user.setPinSetAt(null);
        }
    }

    private void ensureNoOpenShiftAsActiveCashier(Integer userId) {
        if (shiftRepository.existsByActiveCashierIdAndStatus(userId, ShiftStatus.OPEN)) {
            throw new BusinessRuleException(BusinessErrorCode.SHIFT_ALREADY_OPEN,
                    "Nhân sự đang giữ két cho ca làm việc đang mở, không thể ngừng hoạt động.");
        }
    }

    private void validateUniqueContact(String phone, String email, Integer excludeId) {
        if (phone != null) {
            boolean phoneTaken = excludeId == null
                    ? userRepository.existsByPhone(phone)
                    : userRepository.existsByPhoneAndIdNot(phone, excludeId);
            if (phoneTaken) {
                throw new ConflictException(ErrorCode.DUPLICATE_PHONE);
            }
        }
        if (email != null) {
            boolean emailTaken = excludeId == null
                    ? userRepository.findFirstByEmailIgnoreCase(email).isPresent()
                    : userRepository.findFirstByEmailIgnoreCaseAndIdNot(email, excludeId).isPresent();
            if (emailTaken) {
                throw new ConflictException(ErrorCode.DUPLICATE_EMAIL);
            }
        }
    }

    private void validateActiveUniquenessOnReactivation(User user) {
        if (UserDirectory.requiresPin(user.getPosition())
                && user.getBranchId() != null
                && user.getPinLookupHash() != null) {
            userRepository.findFirstByBranchIdAndPinLookupHashAndStatus(
                            user.getBranchId(), user.getPinLookupHash(), UserStatus.ACTIVE)
                    .filter(holder -> !Objects.equals(holder.getId(), user.getId()))
                    .ifPresent(holder -> {
                        throw new ConflictException(ErrorCode.DUPLICATE_PIN);
                    });
        }
        if (user.getCardCode() != null) {
            userRepository.findFirstByCardCodeAndStatusAndIdNot(
                            user.getCardCode(), UserStatus.ACTIVE, user.getId())
                    .ifPresent(holder -> {
                        throw new ConflictException(ErrorCode.DUPLICATE_CARD);
                    });
        }
    }

    private static Boolean resolveUnassignedScope(UserPrincipal actor, Boolean unassigned, Integer branchId) {
        if (unassigned == null) {
            return null;
        }
        if (!isCoo(actor)) {
            if (Boolean.TRUE.equals(unassigned)) {
                throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
            }
            return null;
        }
        if (branchId != null && Boolean.TRUE.equals(unassigned)) {
            throw new InvalidFieldValueException("unassigned",
                    "Không thể lọc đồng thời chi nhánh và chưa gắn chi nhánh.");
        }
        return unassigned;
    }

    /** Branch scope: BM luôn dùng chi nhánh của mình; COO dùng tham số (có thể null = mọi chi nhánh). */
    private Integer scopedBranch(Integer requestedBranchId, String field) {
        UserPrincipal actor = currentActor();
        if (isCoo(actor)) {
            return requestedBranchId;
        }
        if (requestedBranchId != null && !requestedBranchId.equals(actor.branchId())) {
            throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
        }
        if (actor.branchId() == null) {
            throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
        }
        return actor.branchId();
    }

    private User getScopedUser(Integer id) {
        User user = userRepository.findWithRoleAndBranchById(id)
                .orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
        ensureInScope(user);
        return user;
    }

    private User lockScopedUser(Integer id) {
        User user = userRepository.lockById(id).orElseThrow(() -> new NotFoundException(RESOURCE_NAME));
        ensureInScope(user);
        return user;
    }

    private void ensureInScope(User target) {
        UserPrincipal actor = currentActor();
        if (!isCoo(actor) && !Objects.equals(target.getBranchId(), actor.branchId())) {
            throw new ForbiddenException(ErrorCode.CROSS_BRANCH_DENIED);
        }
    }

    private static void ensureNotSelf(UserPrincipal actor, User target, String what) {
        if (Objects.equals(actor.userId(), target.getId())) {
            throw new BusinessRuleException(BusinessErrorCode.SELF_ACTION_FORBIDDEN,
                    "Không được thay đổi " + what + ".");
        }
    }

    private void ensureNotLastCoo(User target, String action) {
        if (target.getPosition() != Roles.COO) {
            return;
        }
        Role coo = roleRepository.findByCode(Roles.COO.code()).orElse(null);
        if (coo == null) {
            return;
        }
        if (userRepository.countByRoleCodesAndStatus(List.of(Roles.COO.code()), UserStatus.ACTIVE) <= 1) {
            throw new BusinessRuleException(BusinessErrorCode.SELF_ACTION_FORBIDDEN,
                    "Không thể " + action + " tài khoản COO cuối cùng.");
        }
    }

    /** Rule §7.2: COO/BM bắt buộc username + password; role chi nhánh không được có */
    private void applyLoginAccount(User user, String username, String password, boolean creating) {
        String normalizedUsername = MasterDataText.trimToNull(username);
        boolean hasPasswordInput = password != null && !password.isBlank();
        boolean needsAccount = UserDirectory.requiresLoginAccount(user.getPosition());
        if (!needsAccount) {
            if (normalizedUsername != null || hasPasswordInput) {
                throw new InvalidFieldValueException("username",
                        "Cashier / Barista / Waiter không có tài khoản đăng nhập (chỉ dùng PIN).");
            }
            if (user.getUsername() != null || user.getPasswordHash() != null) {
                user.setUsername(null);
                user.setPasswordHash(null);
                if (user.getId() != null) {
                    refreshTokenStore.revokeAllOfUser(user.getId());
                }
            }
            return;
        }
        if (normalizedUsername != null) {
            boolean taken = creating
                    ? userRepository.existsByUsername(normalizedUsername)
                    : userRepository.existsByUsernameAndIdNot(normalizedUsername, user.getId());
            if (taken) {
                throw new ConflictException(ErrorCode.DUPLICATE_USERNAME);
            }
            user.setUsername(normalizedUsername);
        } else if (creating || user.getUsername() == null) {
            throw new InvalidFieldValueException("username", "Tên đăng nhập là bắt buộc với COO / Branch Manager.");
        }
        if (hasPasswordInput) {
            if (!ValidationUtil.PASSWORD.matcher(password).matches()) {
                throw new InvalidFieldValueException("password", "Mật khẩu phải 8–72 ký tự, có chữ và số.");
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        } else if (creating || user.getPasswordHash() == null) {
            throw new InvalidFieldValueException("password", "Mật khẩu là bắt buộc với COO / Branch Manager.");
        }
    }

    /** PIN unique trong chi nhánh (§7.2): chỉ nhân sự chi nhánh (Cashier/Barista/Waiter) dùng PIN; COO/BM không dùng PIN. */
    private void applyPin(User user, Integer branchId, String pin, boolean creating) {
        boolean hasPinInput = pin != null && !pin.isBlank();
        if (!UserDirectory.requiresPin(user.getPosition())) {
            if (hasPinInput) {
                throw new InvalidFieldValueException("pin", "COO / Quản lý chi nhánh không sử dụng mã PIN.");
            }
            user.setPinHash(null);
            user.setPinLookupHash(null);
            user.setPinSetAt(null);
            return;
        }
        if (hasPinInput) {
            if (branchId == null) {
                throw new InvalidFieldValueException("pin", "Chỉ nhân sự thuộc chi nhánh mới có PIN.");
            }
            String trimmedPin = pin.trim();
            String lookupHash = pinHasher.lookupHash(branchId, trimmedPin);
            userRepository.findFirstByBranchIdAndPinLookupHashAndStatus(branchId, lookupHash, UserStatus.ACTIVE)
                    .filter(holder -> !Objects.equals(holder.getId(), user.getId()))
                    .ifPresent(holder -> {
                        throw new ConflictException(ErrorCode.DUPLICATE_PIN);
                    });
            user.setPinHash(pinHasher.hash(trimmedPin));
            user.setPinLookupHash(lookupHash);
            user.setPinSetAt(Instant.now());
            return;
        }
        if (creating || user.getPinHash() == null) {
            throw new InvalidFieldValueException("pin", "PIN là bắt buộc với nhân sự chi nhánh.");
        }
    }

    private void applyCard(User user, String cardCode, Integer excludeId) {
        String normalized = MasterDataText.trimToNull(cardCode);
        if (normalized == null) {
            user.setCardCode(null);
            return;
        }
        if (normalized.length() > 15) {
            throw new InvalidFieldValueException("cardCode", "Mã thẻ tối đa 15 ký tự.");
        }
        userRepository.findFirstByCardCodeAndStatusAndIdNot(normalized, UserStatus.ACTIVE,
                        excludeId == null ? -1 : excludeId)
                .ifPresent(holder -> {
                    throw new ConflictException(ErrorCode.DUPLICATE_CARD);
                });
        user.setCardCode(normalized);
    }

    private void requireValidBranch(Integer branchId, UserStatus targetStatus) {
        if (branchId == null) {
            return;
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new InvalidFieldValueException("branchId", "Chi nhánh không tồn tại."));
        if (targetStatus == UserStatus.ACTIVE && branch.getStatus() == BranchStatus.INACTIVE) {
            throw new InvalidFieldValueException("branchId", "Chi nhánh đã ngừng hoạt động.");
        }
    }

    private UserDto toDto(User user, String branchName) {
        return userMapper.toDto(user, roleCodeOf(user),
                branchName != null ? branchName : branchNameOf(user.getBranchId()), null);
    }

    private static String roleCodeOf(User user) {
        return user.getPosition() == null ? null : user.getPosition().code();
    }

    private String branchNameOf(Integer branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findById(branchId).map(Branch::getName).orElse(null);
    }

    /** Snapshot audit — KHÔNG chứa hash/PIN (IMPLEMENTATION.md §8.4). */
    private static Map<String, Object> snapshot(User user) {
        return Map.of(
                "id", String.valueOf(user.getId()),
                "fullName", String.valueOf(user.getFullName()),
                "role", String.valueOf(roleCodeOf(user)),
                "branchId", String.valueOf(user.getBranchId()),
                "status", String.valueOf(user.getStatus()));
    }

    private void audit(AuditAction action, Object entityId, Object before, Object after) {
        auditLogService.log(action, ENTITY, entityId, before, after);
    }

    private static UserPrincipal currentActor() {
        var info = BranchContext.current().orElse(null);
        if (info == null) {
            throw new ForbiddenException(ErrorCode.AUTH_REQUIRED);
        }
        return new UserPrincipal(info.userId(), null, null, info.role(), info.branchId(), null);
    }

    private static boolean isCoo(UserPrincipal actor) {
        return actor != null && Roles.COO.code().equals(actor.role());
    }

    /** COO và BM mới tạo chưa gắn chi nhánh được phép có {@code branchId == null}; nhân sự ca bắt buộc chọn chi nhánh. */
    private static boolean positionsNeedsBranch(Roles position) {
        return position != Roles.COO && position != Roles.BRANCH_MANAGER;
    }

    private static String lowerOrNull(String email) {
        String trimmed = MasterDataText.trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
