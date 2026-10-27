package com.rha.activity;

import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 이력을 남긴다. 업무 변경과 같은 트랜잭션에서 호출되므로 변경이 롤백되면 이력도 남지 않는다.
 * 행위자가 null이면 시스템이 자동으로 한 처리로 본다. 요약에는 행위자 이름을 넣지 않고 행위자는 별도 필드로 남긴다.
 */
@Component
public class ActivityLogRecorder {

    private static final int SUMMARY_MAX = 500;

    private final ActivityLogRepository activityLogRepository;
    private final MemberRepository memberRepository;

    public ActivityLogRecorder(ActivityLogRepository activityLogRepository, MemberRepository memberRepository) {
        this.activityLogRepository = activityLogRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * @param detail 요약 한 줄. 무엇을 했는지를 명사형으로 끝맺고 행위자 이름은 넣지 않는다.
     *               예: {@code "수업 '필라테스' 취소"}, {@code "수업 '필라테스' 공개 상태를 PUBLISHED로 변경"}
     * @param changes null 항목은 무시한다(변경 없는 필드를 {@link ActivityChange#ifChanged}로 넘기기 위함).
     */
    @Transactional
    public void record(ActivityAction action, Long actorId, Long subjectMemberId, String targetType,
            Long targetId, String detail, ActivityChange... changes) {
        Member actor = actorId == null ? null : memberRepository.findById(actorId).orElse(null);
        save(action, actor, subjectMemberId, targetType, targetId, detail, changes);
    }

    /** 요청이 거절되어 바깥 트랜잭션이 롤백되더라도 남아야 하는 이력(로그인 실패)을 별도 트랜잭션으로 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(ActivityAction action, Long actorId, Long subjectMemberId, String targetType,
            Long targetId, String detail, ActivityChange... changes) {
        Member actor = actorId == null ? null : memberRepository.findById(actorId).orElse(null);
        save(action, actor, subjectMemberId, targetType, targetId, detail, changes);
    }

    private void save(ActivityAction action, Member actor, Long subjectMemberId, String targetType,
            Long targetId, String detail, ActivityChange[] changes) {
        String summary = detail;
        if (summary.length() > SUMMARY_MAX) {
            summary = summary.substring(0, SUMMARY_MAX - 1) + "…";
        }
        MemberRole actorRole = actor == null ? null : actor.getRole();
        List<ActivityChange> changeList = Arrays.stream(changes).filter(Objects::nonNull).toList();
        activityLogRepository.save(ActivityLog.of(action, actor == null ? null : actor.getId(), actorRole,
                subjectMemberId, targetType, targetId, summary, changeList, Instant.now()));
    }
}
