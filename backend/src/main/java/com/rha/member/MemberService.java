package com.rha.member;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.Josa;
import com.rha.activity.Masking;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberPermissionRepository permissionRepository;
    private final ActivityLogRecorder activityLogRecorder;

    public MemberService(MemberRepository memberRepository, MemberPermissionRepository permissionRepository,
            ActivityLogRecorder activityLogRecorder) {
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
        this.activityLogRecorder = activityLogRecorder;
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
        String beforeName = member.getName();
        String beforePhone = member.getPhoneNumber();
        Object beforeBirthDate = member.getBirthDate();
        String beforeAddress = member.getAddress();
        member.updateProfile(request.getName(), request.getPhoneNumber(), request.getBirthDate(),
                request.isAddressSpecified(), request.getAddress(), Instant.now());
        activityLogRecorder.record(ActivityAction.MEMBER_PROFILE_UPDATED, memberId, memberId, "MEMBER", memberId,
                "내 정보를 수정했다.",
                ActivityChange.ifChanged("name", beforeName, member.getName()),
                ActivityChange.ifChanged("phoneNumber", beforePhone, member.getPhoneNumber(), Masking::phone),
                ActivityChange.ifChanged("birthDate", beforeBirthDate, (Object) member.getBirthDate(),
                        Masking::birthDate),
                ActivityChange.ifChanged("address", beforeAddress, member.getAddress(), Masking::address));
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
