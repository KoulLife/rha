package com.rha.member;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberPermissionRepository permissionRepository;

    public MemberService(MemberRepository memberRepository, MemberPermissionRepository permissionRepository) {
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMyProfile(Long memberId) {
        return toResponse(findAuthenticatedMember(memberId));
    }

    @Transactional
    public MemberResponse updateMyProfile(Long memberId, MemberUpdateRequest request) {
        if (!request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        Member member = findAuthenticatedMember(memberId);
        member.updateProfile(request.getName(), request.getPhoneNumber(), request.getBirthDate(),
                request.isAddressSpecified(), request.getAddress(), Instant.now());
        return toResponse(member);
    }

    private MemberResponse toResponse(Member member) {
        List<String> permissions = permissionRepository
                .findByMemberIdInOrderByPermissionCode(List.of(member.getId()))
                .stream().map(MemberPermission::getPermissionCode).toList();
        return MemberResponse.from(member, permissions);
    }

    /** 토큰은 유효하지만 회원이 존재하지 않으면 인증 정보가 유효하지 않은 것으로 본다. */
    private Member findAuthenticatedMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
