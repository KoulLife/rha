package com.rha.member;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.validation.RequestValidator;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

    private static final List<PermissionResponse> BASE_PERMISSIONS = List.of(
            new PermissionResponse(MemberAdminService.MEMBER_READ, "회원 조회", "회원 목록과 상세 정보를 조회할 수 있다."));

    private final MemberRepository memberRepository;
    private final MemberPermissionRepository permissionRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;

    public PermissionService(MemberRepository memberRepository, MemberPermissionRepository permissionRepository,
            AdminGuard adminGuard, RequestValidator requestValidator) {
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    /**
     * 부여 가능한 권한 목록. 수업 유형별 권한(LESSON_CREATE, LESSON_UPDATE)은
     * 수업 유형 기능이 구현될 때 활성 유형으로부터 만들어 추가한다.
     */
    @Transactional(readOnly = true)
    public PermissionListResponse getPermissions(Long callerId) {
        adminGuard.requireAdmin(callerId);
        return new PermissionListResponse(availablePermissions());
    }

    @Transactional(readOnly = true)
    public MemberPermissionsResponse getMemberPermissions(Long callerId, Long memberId) {
        adminGuard.requireAdmin(callerId);
        findMember(memberId);
        return new MemberPermissionsResponse(codesOf(memberId));
    }

    /**
     * 보유 권한을 전달한 목록으로 교체한다. 유지되는 권한은 부여자와 부여 시각을 그대로 두고,
     * 새로 추가되는 권한만 호출자를 부여자로 기록한다.
     */
    @Transactional
    public MemberPermissionsResponse updateMemberPermissions(Long callerId, Long memberId,
            MemberPermissionsRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        Member target = findMember(memberId);
        if (!target.isRole(MemberRole.INSTRUCTOR)) {
            throw new ApiException(ErrorCode.PERMISSION_TARGET_INVALID);
        }

        Set<String> requested = new HashSet<>(request.permissions());
        if (requested.size() != request.permissions().size() || !availableCodes().containsAll(requested)) {
            throw new ApiException(ErrorCode.INVALID_PERMISSION);
        }

        Set<String> existing = new HashSet<>(codesOf(memberId));
        List<MemberPermission.Key> toRevoke = existing.stream()
                .filter(code -> !requested.contains(code))
                .map(code -> new MemberPermission.Key(memberId, code))
                .toList();
        permissionRepository.deleteAllById(toRevoke);

        Instant now = Instant.now();
        List<MemberPermission> toGrant = requested.stream()
                .filter(code -> !existing.contains(code))
                .map(code -> MemberPermission.grant(memberId, code, callerId, now))
                .toList();
        permissionRepository.saveAll(toGrant);

        return new MemberPermissionsResponse(requested.stream().sorted(Comparator.naturalOrder()).toList());
    }

    private List<PermissionResponse> availablePermissions() {
        return BASE_PERMISSIONS.stream().sorted(Comparator.comparing(PermissionResponse::code)).toList();
    }

    private Set<String> availableCodes() {
        Set<String> codes = new HashSet<>();
        availablePermissions().forEach(permission -> codes.add(permission.code()));
        return codes;
    }

    private List<String> codesOf(Long memberId) {
        return permissionRepository.findByMemberIdInOrderByPermissionCode(List.of(memberId))
                .stream().map(MemberPermission::getPermissionCode).toList();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
