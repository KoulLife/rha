package com.rha.reservation;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 권한을 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/reservations")
    public ReservationPageResponse getReservations(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long lessonId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Instant lessonFrom,
            @RequestParam(required = false) Instant lessonTo,
            @RequestParam(defaultValue = "DESC") Sort.Direction order,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reservationService.getReservations(me.id(),
                new ReservationService.Filter(memberId, lessonId, status, lessonFrom, lessonTo), order, page, size);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse createReservation(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody ReservationCreateRequest request) {
        return reservationService.createReservation(me.id(), request);
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse getReservation(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long reservationId) {
        return reservationService.getReservation(me.id(), reservationId);
    }

    @PostMapping("/reservations/{reservationId}/cancellations")
    public ReservationResponse cancelReservation(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long reservationId, @RequestBody(required = false) ReservationCancelRequest request) {
        return reservationService.cancelReservation(me.id(), reservationId, request);
    }

    @PutMapping("/reservations/{reservationId}/attendance")
    public ReservationResponse confirmAttendance(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long reservationId, @RequestBody AttendanceRequest request) {
        return reservationService.confirmAttendance(me.id(), reservationId, request);
    }

    @GetMapping("/lessons/{lessonId}/reservations")
    public ReservationPageResponse getLessonReservations(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long lessonId, @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reservationService.getLessonReservations(me.id(), lessonId, status, page, size);
    }

    @GetMapping("/members/me/reservations")
    public ReservationPageResponse getMyReservations(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Instant lessonFrom,
            @RequestParam(required = false) Instant lessonTo,
            @RequestParam(defaultValue = "DESC") Sort.Direction order,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reservationService.getMyReservations(me.id(), status, lessonFrom, lessonTo, order, page, size);
    }
}
