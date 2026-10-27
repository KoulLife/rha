package com.rha.settlement;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.common.persistence.RowLock;
import com.rha.common.validation.RequestValidator;
import com.rha.lesson.Lesson;
import com.rha.lessontype.LessonType;
import com.rha.lessontype.LessonTypeRepository;
import com.rha.member.AdminGuard;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import com.rha.payrate.PayRate;
import com.rha.payrate.PayRateRepository;
import com.rha.reservation.ReservationRepository;
import com.rha.reservation.ReservationStatus;
import com.rha.room.Room;
import com.rha.room.RoomRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.ActivityTime;

/**
 * 강사 정산(관리자 전용). 계산하면 기간 안의 수업이 정산에 편입되고 그때의 단가가 기록되며,
 * 폐기하면 수업이 풀린다. 지급은 시스템 밖에서 이뤄지고 지급했다는 사실만 기록한다.
 * 계산·폐기·상태 전이는 활동 이력에 남긴다.
 */
@Service
public class SettlementService {

    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Seoul");

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository itemRepository;
    private final PayRateRepository payRateRepository;
    private final ReservationRepository reservationRepository;
    private final LessonTypeRepository lessonTypeRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final com.rha.lesson.LessonRepository lessonRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;
    private final ActivityLogRecorder activityLogRecorder;
    private final RowLock rowLock;

    public SettlementService(SettlementRepository settlementRepository, SettlementItemRepository itemRepository,
            PayRateRepository payRateRepository, ReservationRepository reservationRepository,
            LessonTypeRepository lessonTypeRepository, RoomRepository roomRepository,
            MemberRepository memberRepository, com.rha.lesson.LessonRepository lessonRepository,
            AdminGuard adminGuard, RequestValidator requestValidator, ActivityLogRecorder activityLogRecorder, RowLock rowLock) {
        this.activityLogRecorder = activityLogRecorder;
        this.rowLock = rowLock;
        this.settlementRepository = settlementRepository;
        this.itemRepository = itemRepository;
        this.payRateRepository = payRateRepository;
        this.reservationRepository = reservationRepository;
        this.lessonTypeRepository = lessonTypeRepository;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.lessonRepository = lessonRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    // ===== 조회 =====

    /** 생성 최신순(같으면 id 내림차순). {@code page}는 1부터 시작한다. */
    @Transactional(readOnly = true)
    public SettlementPageResponse getSettlements(Long callerId, Long instructorId, SettlementStatus status,
            LocalDate periodFrom, LocalDate periodTo, int page, int size) {
        adminGuard.requireAdmin(callerId);
        if (periodFrom != null && periodTo != null && periodTo.isBefore(periodFrom)) {
            throw new ApiException(ErrorCode.INVALID_INPUT,
                    List.of(new ApiException.Detail("periodTo", "periodFrom보다 빠를 수 없습니다.")));
        }
        Specification<Settlement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (instructorId != null) {
                predicates.add(cb.equal(root.get("instructorId"), instructorId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (periodFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("periodEnd"), periodFrom));
            }
            if (periodTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("periodStart"), periodTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Settlement> result = settlementRepository.findAll(spec, pageable);
        return new SettlementPageResponse(toSummaries(result.getContent()), PageInfo.of(result));
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(Long callerId, Long settlementId) {
        adminGuard.requireAdmin(callerId);
        return toDetail(find(settlementId));
    }

    // ===== 계산 =====

    /**
     * 확인 순서: 관리자(403) → 형식(400) → 기간(400) → 강사(404, 400) → 기간 겹침 → 출결 미기록
     * → 단가 미설정(409).
     */
    @Transactional
    public SettlementResponse createSettlement(Long callerId, SettlementCreateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        LocalDate start = parseDate(request.periodStart());
        LocalDate end = parseDate(request.periodEnd());
        LocalDate today = LocalDate.now(CENTER_ZONE);
        if (start.isAfter(end) || !end.isBefore(today)) {
            throw new ApiException(ErrorCode.INVALID_PERIOD);
        }
        // 강사를 잠가 같은 강사의 정산 계산이 서로의 기간 겹침 확인을 지나치지 못하게 한다.
        Member instructor = memberRepository.findById(request.instructorId()).map(rowLock::lock)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!instructor.isRole(MemberRole.INSTRUCTOR)) {
            throw new ApiException(ErrorCode.INVALID_INSTRUCTOR);
        }
        if (settlementRepository.existsOverlapping(instructor.getId(), start, end)) {
            throw new ApiException(ErrorCode.SETTLEMENT_PERIOD_OVERLAP);
        }

        Instant now = Instant.now();
        // 수업을 잠가 계산하는 동안 출결이 정정되지 않게 한다. 항목은 시작 시각 순으로 담는다.
        List<Lesson> lessons = lessonRepository.findLessonsToSettleForUpdate(instructor.getId(),
                start.atStartOfDay(CENTER_ZONE).toInstant(), end.plusDays(1).atStartOfDay(CENTER_ZONE).toInstant(),
                now).stream()
                .sorted(java.util.Comparator.comparing(Lesson::getStartAt).thenComparing(Lesson::getId))
                .toList();
        Map<Long, Map<ReservationStatus, Integer>> counts = countReservations(lessons);

        List<ApiException.Detail> unrecorded = lessons.stream()
                .filter(lesson -> counts.get(lesson.getId()).get(ReservationStatus.RESERVED) > 0)
                .map(lesson -> new ApiException.Detail(String.valueOf(lesson.getId()),
                        ErrorCode.UNRECORDED_ATTENDANCE_EXISTS.name()))
                .toList();
        if (!unrecorded.isEmpty()) {
            throw new ApiException(ErrorCode.UNRECORDED_ATTENDANCE_EXISTS, unrecorded);
        }

        Map<Long, Long> rates = payRateRepository.findByInstructorIdOrderByLessonTypeIdAsc(instructor.getId())
                .stream().collect(Collectors.toMap(PayRate::getLessonTypeId, PayRate::getUnitPrice));
        Set<Long> missing = new TreeSet<>();
        for (Lesson lesson : lessons) {
            if (isConducted(counts.get(lesson.getId())) && !rates.containsKey(lesson.getLessonTypeId())) {
                missing.add(lesson.getLessonTypeId());
            }
        }
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.PAY_RATE_NOT_SET, missing.stream()
                    .map(id -> new ApiException.Detail(String.valueOf(id), ErrorCode.PAY_RATE_NOT_SET.name()))
                    .toList());
        }

        long total = 0;
        int settledCount = 0;
        int excludedCount = 0;
        List<Object[]> plan = new ArrayList<>();
        for (Lesson lesson : lessons) {
            Map<ReservationStatus, Integer> count = counts.get(lesson.getId());
            if (isConducted(count)) {
                long price = rates.get(lesson.getLessonTypeId());
                total += price;
                settledCount++;
                plan.add(new Object[] {lesson.getId(), count, price});
            } else {
                excludedCount++;
                plan.add(new Object[] {lesson.getId(), count, null});
            }
        }

        Settlement settlement = settlementRepository.save(Settlement.calculated(instructor.getId(), start, end,
                total, settledCount, excludedCount, now));
        List<SettlementItem> items = new ArrayList<>();
        for (Object[] entry : plan) {
            @SuppressWarnings("unchecked")
            Map<ReservationStatus, Integer> count = (Map<ReservationStatus, Integer>) entry[1];
            items.add(entry[2] == null ? SettlementItem.excluded(settlement.getId(), (Long) entry[0])
                    : SettlementItem.settled(settlement.getId(), (Long) entry[0],
                            count.get(ReservationStatus.ATTENDED), count.get(ReservationStatus.NO_SHOW),
                            (Long) entry[2]));
        }
        itemRepository.saveAll(items);
        activityLogRecorder.record(ActivityAction.SETTLEMENT_CREATED, callerId, instructor.getId(), "SETTLEMENT",
                settlement.getId(), "강사 '" + instructor.getName() + "'의 정산(" + start + " ~ " + end
                        + ") 계산 (정산 수업 " + settledCount + "건, 제외 " + excludedCount + "건, 합계 "
                        + String.format("%,d원", total) + ")");
        return toDetail(settlement);
    }

    /** 취소되지 않은 예약이 한 건이라도 있으면 수업이 진행된 것으로 본다. 전원 노쇼여도 정산 대상이다. */
    private static boolean isConducted(Map<ReservationStatus, Integer> count) {
        return count.get(ReservationStatus.RESERVED) + count.get(ReservationStatus.ATTENDED)
                + count.get(ReservationStatus.NO_SHOW) > 0;
    }

    private Map<Long, Map<ReservationStatus, Integer>> countReservations(List<Lesson> lessons) {
        Map<Long, Map<ReservationStatus, Integer>> counts = new HashMap<>();
        for (Lesson lesson : lessons) {
            Map<ReservationStatus, Integer> zero = new EnumMap<>(ReservationStatus.class);
            for (ReservationStatus value : ReservationStatus.values()) {
                zero.put(value, 0);
            }
            counts.put(lesson.getId(), zero);
        }
        if (!lessons.isEmpty()) {
            for (Object[] row : reservationRepository.countsByLessonIdsAndStatus(
                    lessons.stream().map(Lesson::getId).toList())) {
                counts.get((Long) row[0]).put((ReservationStatus) row[1], ((Number) row[2]).intValue());
            }
        }
        return counts;
    }

    // ===== 상태 전이·폐기 =====

    /** 계산됨 → 검토 요청됨. */
    @Transactional
    public SettlementResponse requestReview(Long callerId, Long settlementId) {
        adminGuard.requireAdmin(callerId);
        Settlement settlement = lock(settlementId);
        requireStatus(settlement, SettlementStatus.CALCULATED);
        settlement.requestReview(Instant.now());
        recordStatusChange(ActivityAction.SETTLEMENT_REVIEW_REQUESTED, callerId, settlement,
                SettlementStatus.CALCULATED, "검토 요청");
        return toSummaries(List.of(settlement)).get(0);
    }

    /** 검토 요청됨 → 확정됨. 확정되면 금액을 바꿀 수 없다. */
    @Transactional
    public SettlementResponse confirm(Long callerId, Long settlementId) {
        adminGuard.requireAdmin(callerId);
        Settlement settlement = lock(settlementId);
        requireStatus(settlement, SettlementStatus.REVIEW_REQUESTED);
        settlement.confirm(Instant.now());
        recordStatusChange(ActivityAction.SETTLEMENT_CONFIRMED, callerId, settlement,
                SettlementStatus.REVIEW_REQUESTED, "확정");
        return toSummaries(List.of(settlement)).get(0);
    }

    /** 확정됨 → 지급 기록됨. 확인 순서: 관리자(403) → 형식(400) → 정산(404) → 상태(409). */
    @Transactional
    public SettlementResponse recordPayment(Long callerId, Long settlementId, SettlementPaymentRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        LocalDate paidAt = parseDate(request.paidAt());
        Settlement settlement = lock(settlementId);
        requireStatus(settlement, SettlementStatus.CONFIRMED);
        settlement.recordPayment(paidAt, request.memo(), Instant.now());
        activityLogRecorder.record(ActivityAction.SETTLEMENT_PAID, callerId, settlement.getInstructorId(),
                "SETTLEMENT", settlement.getId(), settlementLabel(settlement) + " 지급 기록 (지급일 " + paidAt
                        + ")",
                ActivityChange.of("status", SettlementStatus.CONFIRMED, SettlementStatus.PAID),
                ActivityChange.of("paidAt", null, paidAt));
        return toSummaries(List.of(settlement)).get(0);
    }

    /** 계산됨, 검토 요청됨 상태에서만 폐기할 수 있다. 포함됐던 수업은 정산에서 풀린다. */
    @Transactional
    public void discard(Long callerId, Long settlementId) {
        adminGuard.requireAdmin(callerId);
        Settlement settlement = lock(settlementId);
        if (settlement.getStatus() != SettlementStatus.CALCULATED
                && settlement.getStatus() != SettlementStatus.REVIEW_REQUESTED) {
            throw new ApiException(ErrorCode.INVALID_SETTLEMENT_STATUS);
        }
        activityLogRecorder.record(ActivityAction.SETTLEMENT_DISCARDED, callerId, settlement.getInstructorId(),
                "SETTLEMENT", settlementId, settlementLabel(settlement) + " 폐기 (포함된 수업은 다시 정산할 수 있음)",
                ActivityChange.of("status", settlement.getStatus(), null));
        itemRepository.deleteAllOf(settlementId);
        settlementRepository.deleteById(settlementId);
    }

    // ===== 공통 =====

    private Settlement find(Long settlementId) {
        return settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** 상태를 바꾸거나 폐기할 정산을 잠가 읽어, 동시에 들어온 다른 처리와 겹치지 않게 한다. 먼저 폐기되었으면 404이다. */
    private Settlement lock(Long settlementId) {
        return settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private void recordStatusChange(ActivityAction action, Long callerId, Settlement settlement,
            SettlementStatus before, String verb) {
        activityLogRecorder.record(action, callerId, settlement.getInstructorId(), "SETTLEMENT", settlement.getId(),
                settlementLabel(settlement) + " " + verb,
                ActivityChange.of("status", before, settlement.getStatus()));
    }

    /** "강사 '이름'의 정산(시작 ~ 끝)" */
    private String settlementLabel(Settlement settlement) {
        String name = memberRepository.findById(settlement.getInstructorId()).map(Member::getName).orElse("");
        return "강사 '" + name + "'의 정산(" + settlement.getPeriodStart() + " ~ " + settlement.getPeriodEnd() + ")";
    }

    private static void requireStatus(Settlement settlement, SettlementStatus expected) {
        if (settlement.getStatus() != expected) {
            throw new ApiException(ErrorCode.INVALID_SETTLEMENT_STATUS);
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    private List<SettlementResponse> toSummaries(List<Settlement> settlements) {
        Set<Long> instructorIds = settlements.stream().map(Settlement::getInstructorId).collect(Collectors.toSet());
        Map<Long, Member> instructors = memberRepository.findAllById(instructorIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        return settlements.stream().map(settlement -> {
            Member instructor = instructors.get(settlement.getInstructorId());
            SettlementResponse.Payment payment = settlement.getStatus() == SettlementStatus.PAID
                    ? new SettlementResponse.Payment(settlement.getPaidAt(), settlement.getPaymentMemo(),
                            settlement.getPaymentRecordedAt())
                    : null;
            return new SettlementResponse(settlement.getId(),
                    new SettlementResponse.IdName(instructor.getId(), instructor.getName()),
                    settlement.getPeriodStart(), settlement.getPeriodEnd(), settlement.getStatus(),
                    settlement.getTotalAmount(), settlement.getSettledLessonCount(),
                    settlement.getExcludedLessonCount(), settlement.getCreatedAt(),
                    settlement.getReviewRequestedAt(), settlement.getConfirmedAt(), payment, null);
        }).toList();
    }

    /** 수업별 내역(수업 시작 시각 오름차순)을 포함한 정산. */
    private SettlementResponse toDetail(Settlement settlement) {
        SettlementResponse summary = toSummaries(List.of(settlement)).get(0);
        List<SettlementItem> items = itemRepository.findBySettlementId(settlement.getId());
        Map<Long, Lesson> lessons = lessonRepository
                .findAllById(items.stream().map(SettlementItem::getLessonId).toList()).stream()
                .collect(Collectors.toMap(Lesson::getId, Function.identity()));
        Set<Long> typeIds = new HashSet<>();
        Set<Long> roomIds = new HashSet<>();
        Set<Long> memberIds = new HashSet<>();
        lessons.values().forEach(lesson -> {
            typeIds.add(lesson.getLessonTypeId());
            roomIds.add(lesson.getRoomId());
            memberIds.add(lesson.getInstructorId());
        });
        Map<Long, LessonType> types = lessonTypeRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(LessonType::getId, Function.identity()));
        Map<Long, Room> rooms = roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        List<SettlementResponse.Item> content = items.stream()
                .sorted(Comparator.comparing((SettlementItem i) -> lessons.get(i.getLessonId()).getStartAt())
                        .thenComparing(SettlementItem::getLessonId))
                .map(item -> {
                    Lesson lesson = lessons.get(item.getLessonId());
                    LessonType type = types.get(lesson.getLessonTypeId());
                    Member instructor = members.get(lesson.getInstructorId());
                    Room room = rooms.get(lesson.getRoomId());
                    return new SettlementResponse.Item(new SettlementResponse.LessonSummary(lesson.getId(),
                            lesson.getTitle(),
                            new SettlementResponse.LessonType(type.getId(), type.getCode(), type.getName()),
                            new SettlementResponse.IdName(instructor.getId(), instructor.getName()),
                            new SettlementResponse.IdName(room.getId(), room.getName()), lesson.getStartAt(),
                            lesson.getEndAt(), lesson.getStatus()),
                            item.getAttendedCount(), item.getNoShowCount(), item.getUnitPrice(), item.getAmount(),
                            item.isSettled(), item.getExcludedReason());
                }).toList();
        return new SettlementResponse(summary.id(), summary.instructor(), summary.periodStart(),
                summary.periodEnd(), summary.status(), summary.totalAmount(), summary.settledLessonCount(),
                summary.excludedLessonCount(), summary.createdAt(), summary.reviewRequestedAt(),
                summary.confirmedAt(), summary.payment(), content);
    }
}
