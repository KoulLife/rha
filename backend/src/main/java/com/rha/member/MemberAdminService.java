package com.rha.member;

import com.rha.auth.PasswordResetService;
import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
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

    public MemberAdminService(MemberRepository memberRepository,
            MemberPermissionRepository permissionRepository,
            PasswordResetService passwordResetService) {
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
        this.passwordResetService = passwordResetService;
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

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<String> permissions = permissionRepository.findByMemberIdInOrderByPermissionCode(List.of(memberId))
                .stream().map(MemberPermission::getPermissionCode).toList();
        return MemberResponse.from(member, permissions);
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

    /** 토큰의 역할이 아니라 DB의 현재 역할·상태로 확인한다. 역할이 바뀐 직후의 오래된 토큰을 막기 위함이다. */
    private void requireAdmin(Long callerId) {
        Member caller = findCaller(callerId);
        if (!caller.isRole(MemberRole.ADMIN) || !caller.isActive()) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
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
