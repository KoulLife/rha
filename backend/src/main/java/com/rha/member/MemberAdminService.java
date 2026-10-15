package com.rha.member;

import com.rha.auth.PasswordResetService;
import com.rha.auth.RefreshTokenRepository;
import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.lesson.LessonRepository;
import com.rha.lesson.LessonStatus;
import com.rha.reservation.ReservationRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAdminService {

    static final String MEMBER_READ = "MEMBER_READ";

    private final MemberRepository memberRepository;
    private final MemberPermissionRepository permissionRepository;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LessonRepository lessonRepository;
    private final ReservationRepository reservationRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;

    public MemberAdminService(MemberRepository memberRepository,
            MemberPermissionRepository permissionRepository,
            PasswordResetService passwordResetService,
            RefreshTokenRepository refreshTokenRepository,
            LessonRepository lessonRepository,
            ReservationRepository reservationRepository,
            AdminGuard adminGuard,
            RequestValidator requestValidator) {
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
        this.passwordResetService = passwordResetService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.lessonRepository = lessonRepository;
        this.reservationRepository = reservationRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    /**
     * 강사 또는 관리자 계정을 비밀번호 없이 등록하고 초대 메일을 보낸다.
     * 메일 발송이 실패하면 트랜잭션이 롤백되어 계정도 만들어지지 않는다.
     */
    @Transactional
    public Member createMember(Long callerId, MemberCreateRequest request) {
        requireAdmin(callerId);

        if (memberRepository.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Member member = Member.invite(request.email(), request.name(), request.phoneNumber(),
                request.birthDate(), request.address(), MemberRole.valueOf(request.role()), Instant.now());
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // existsByEmail 이후 같은 이메일이 동시에 등록된 경우
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        passwordResetService.invite(member);
        return member;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMember(Long callerId, Long memberId) {
        requireMemberReader(callerId);

        Member member = findMember(memberId);
        return MemberResponse.from(member, permissionsOf(memberId));
    }

    /**
     * 회원 정보를 부분 수정한다. 역할이 실제로 바뀌면 부여된 권한을 회수하고 Refresh Token을 폐기한다.
     * 확인 순서는 관리자 여부(403) → 입력(400) → 회원 존재(404) → 본인 역할 변경(403)
     * → 강사의 시작 전 담당 수업(409)이다.
     */
    @Transactional
    public MemberResponse updateMember(Long callerId, Long memberId, MemberAdminUpdateRequest request) {
        requireAdmin(callerId);
        validate(request);
        if (!request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        Member member = findMember(memberId);

        MemberRole requestedRole = request.getRole() == null ? null : MemberRole.valueOf(request.getRole());
        boolean roleChanged = requestedRole != null && !member.isRole(requestedRole);
        if (roleChanged && callerId.equals(memberId)) {
            throw new ApiException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }
        if (roleChanged && member.isRole(MemberRole.INSTRUCTOR)) {
            requireNoUpcomingLessons(memberId);
        }

        Instant now = Instant.now();
        member.updateProfile(request.getName(), request.getPhoneNumber(), request.getBirthDate(),
                request.isAddressSpecified(), request.getAddress(), now);
        if (roleChanged) {
            member.changeRole(requestedRole, now);
            permissionRepository.deleteAllOf(memberId);
            refreshTokenRepository.revokeAllOf(memberId, now);
        }
        return MemberResponse.from(member, permissionsOf(memberId));
    }

    /**
     * 계정을 ACTIVE 또는 INACTIVE로 바꾼다. 비활성화하면 Refresh Token을 모두 폐기한다.
     * 비활성화하려는 회원에게 시작 전 예약이 있으면 거절한다.
     */
    @Transactional
    public MemberResponse changeMemberStatus(Long callerId, Long memberId, MemberStatusChangeRequest request) {
        requireAdmin(callerId);
        validate(request);
        Member member = findMember(memberId);
        if (callerId.equals(memberId)) {
            throw new ApiException(ErrorCode.CANNOT_CHANGE_OWN_STATUS);
        }

        MemberStatus requested = MemberStatus.valueOf(request.status());
        if (member.getStatus() == requested) {
            return MemberResponse.from(member, permissionsOf(memberId));
        }
        if (requested == MemberStatus.INACTIVE) {
            if (member.getStatus() == MemberStatus.WITHDRAWN) {
                throw new ApiException(ErrorCode.MEMBER_WITHDRAWN);
            }
            if (member.getStatus() == MemberStatus.RESIGNED) {
                throw new ApiException(ErrorCode.MEMBER_RESIGNED);
            }
            if (member.isRole(MemberRole.INSTRUCTOR)) {
                requireNoUpcomingLessons(memberId);
            }
            if (reservationRepository.existsUpcomingOf(memberId, Instant.now())) {
                throw new ApiException(ErrorCode.MEMBER_HAS_UPCOMING_RESERVATIONS);
            }
        }

        Instant now = Instant.now();
        member.changeStatus(requested, now);
        if (requested == MemberStatus.INACTIVE) {
            refreshTokenRepository.revokeAllOf(memberId, now);
        }
        return MemberResponse.from(member, permissionsOf(memberId));
    }

    /** 시작 전이고 취소되지 않은 담당 수업이 있으면 강사의 역할 변경·비활성화를 거절한다. */
    private void requireNoUpcomingLessons(Long instructorId) {
        if (lessonRepository.existsByInstructorIdAndStatusNotAndStartAtAfter(instructorId,
                LessonStatus.CANCELED, Instant.now())) {
            throw new ApiException(ErrorCode.INSTRUCTOR_HAS_UPCOMING_LESSONS);
        }
    }

    /** 관리자 여부를 확인한 뒤에 입력을 검증하기 위해, 컨트롤러가 아니라 서비스에서 검증한다. */
    private void validate(Object request) {
        requestValidator.validate(request);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private List<String> permissionsOf(Long memberId) {
        return permissionRepository.findByMemberIdInOrderByPermissionCode(List.of(memberId))
                .stream().map(MemberPermission::getPermissionCode).toList();
    }

    /** 최신 가입순(같으면 id 역순)으로 회원 목록을 조회한다. {@code page}는 1부터 시작한다. */
    @Transactional(readOnly = true)
    public MemberPageResponse getMembers(Long callerId, MemberRole role, MemberStatus status, String keyword,
            int page, int size) {
        requireMemberReader(callerId);

        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Member> result = memberRepository.findAll(filter(role, status, keyword), pageable);

        List<Long> ids = result.getContent().stream().map(Member::getId).toList();
        Map<Long, List<String>> permissions = ids.isEmpty() ? Map.of()
                : permissionRepository.findByMemberIdInOrderByPermissionCode(ids).stream()
                        .collect(Collectors.groupingBy(MemberPermission::getMemberId,
                                Collectors.mapping(MemberPermission::getPermissionCode, Collectors.toList())));

        List<MemberResponse> content = result.getContent().stream()
                .map(m -> MemberResponse.from(m, permissions.getOrDefault(m.getId(), List.of())))
                .toList();
        return new MemberPageResponse(content, PageInfo.of(result));
    }

    private Specification<Member> filter(MemberRole role, MemberStatus status, String keyword) {
        String pattern = likePattern(keyword);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 앞뒤 공백을 지우고 소문자로 바꾼 부분 일치 패턴을 만든다. 비어 있으면 null이다. LIKE 와일드카드는 이스케이프한다. */
    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private void requireAdmin(Long callerId) {
        adminGuard.requireAdmin(callerId);
    }

    /** ACTIVE 관리자이거나, MEMBER_READ 권한을 가진 ACTIVE 강사만 통과한다. */
    private void requireMemberReader(Long callerId) {
        Member caller = findCaller(callerId);
        boolean allowed = caller.isActive() && (caller.isRole(MemberRole.ADMIN)
                || (caller.isRole(MemberRole.INSTRUCTOR)
                        && permissionRepository.existsByMemberIdAndPermissionCode(callerId, MEMBER_READ)));
        if (!allowed) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
    }

    private Member findCaller(Long callerId) {
        return memberRepository.findById(callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
