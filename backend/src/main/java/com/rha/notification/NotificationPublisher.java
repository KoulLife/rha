package com.rha.notification;

import com.rha.absence.Absence;
import com.rha.lesson.Lesson;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import com.rha.member.MemberStatus;
import com.rha.reservation.Reservation;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 알림을 만든다. 원인이 되는 처리와 같은 트랜잭션에서 호출되므로 처리가 실패하면 알림도 만들어지지 않는다.
 * 날짜와 시각은 센터 현지 시각(Asia/Seoul)으로 표기한다.
 */
@Component
public class NotificationPublisher {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    public NotificationPublisher(NotificationRepository notificationRepository, MemberRepository memberRepository) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
    }

    /** 수업이 취소되어 예약이 자동 취소된 회원들에게 보낸다. */
    public void lessonCanceled(Lesson lesson, Collection<Long> memberIds) {
        String message = TIME.format(lesson.getStartAt()) + " " + lesson.getTitle()
                + " 수업이 취소되어 예약이 취소되고 이용권 횟수가 복구되었습니다.";
        for (Long memberId : memberIds) {
            send(memberId, "LESSON_CANCELED", "수업이 취소되었습니다", message, "LESSON", lesson.getId());
        }
    }

    /** 관리자가 예약을 취소했을 때 그 회원에게 보낸다. */
    public void reservationCanceledByAdmin(Reservation reservation, Lesson lesson) {
        String message = "관리자가 " + TIME.format(lesson.getStartAt()) + " " + lesson.getTitle()
                + " 수업 예약을 취소했습니다. 이용권 횟수가 복구되었습니다.";
        send(reservation.getMemberId(), "RESERVATION_CANCELED", "예약이 취소되었습니다", message, "RESERVATION",
                reservation.getId());
    }

    /** 회원이 본인 예약을 취소했을 때 활동 중인 모든 관리자에게 보낸다. 취소 내역은 수업 상세에서 확인하므로 수업으로 연결한다. */
    public void reservationCanceledByMember(Lesson lesson, String memberName) {
        String message = memberName + " 회원이 " + TIME.format(lesson.getStartAt()) + " " + lesson.getTitle()
                + " 수업 예약을 취소했습니다.";
        for (Member admin : activeAdmins()) {
            send(admin.getId(), "RESERVATION_CANCELED_BY_MEMBER", "회원이 예약을 취소했습니다", message, "LESSON",
                    lesson.getId());
        }
    }

    /** 부재 신청을 활동 중인 모든 관리자에게 보낸다. */
    public void absenceRequested(Absence absence, String instructorName) {
        String message = instructorName + " 강사가 " + period(absence) + " 부재를 신청했습니다.";
        for (Member admin : activeAdmins()) {
            send(admin.getId(), "ABSENCE_REQUESTED", "부재 신청이 접수되었습니다", message, "ABSENCE", absence.getId());
        }
    }

    private List<Member> activeAdmins() {
        return memberRepository.findByRoleAndStatus(MemberRole.ADMIN, MemberStatus.ACTIVE,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    public void absenceApproved(Absence absence) {
        send(absence.getInstructorId(), "ABSENCE_APPROVED", "부재가 승인되었습니다",
                period(absence) + " 부재 신청이 승인되었습니다.", "ABSENCE", absence.getId());
    }

    public void absenceRejected(Absence absence) {
        String reason = absence.getRejectReason();
        String message = period(absence) + " 부재 신청이 반려되었습니다."
                + (reason == null || reason.isBlank() ? "" : " 사유: " + reason);
        send(absence.getInstructorId(), "ABSENCE_REJECTED", "부재가 반려되었습니다", message, "ABSENCE",
                absence.getId());
    }

    private static String period(Absence absence) {
        return TIME.format(absence.getStartAt()) + " ~ " + TIME.format(absence.getEndAt());
    }

    private void send(Long recipientId, String type, String title, String message, String relatedType,
            Long relatedId) {
        notificationRepository.save(Notification.create(recipientId, type, title, message, relatedType, relatedId,
                Instant.now()));
    }
}
