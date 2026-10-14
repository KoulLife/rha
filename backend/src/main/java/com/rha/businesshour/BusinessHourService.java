package com.rha.businesshour;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.validation.RequestValidator;
import com.rha.member.AdminGuard;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessHourService {

    /** 응답 순서. 월요일부터 일요일이다. */
    private static final List<String> DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private final BusinessHourRepository businessHourRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;

    public BusinessHourService(BusinessHourRepository businessHourRepository, AdminGuard adminGuard,
            RequestValidator requestValidator) {
        this.businessHourRepository = businessHourRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    @Transactional(readOnly = true)
    public BusinessHoursResponse getBusinessHours() {
        return toResponse(businessHourRepository.findAll());
    }

    /** 7개 요일 전체를 한 번에 교체한다. */
    @Transactional
    public BusinessHoursResponse updateBusinessHours(Long callerId, BusinessHoursRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);

        List<BusinessHourRequest> days = request.businessHours();
        Set<String> distinct = new HashSet<>();
        days.forEach(day -> distinct.add(day.dayOfWeek()));
        if (days.size() != DAYS.size() || distinct.size() != DAYS.size()) {
            throw new ApiException(ErrorCode.INVALID_BUSINESS_HOURS);
        }
        Map<String, LocalTime[]> times = days.stream().collect(Collectors.toMap(
                BusinessHourRequest::dayOfWeek, BusinessHourService::validTimes));

        List<BusinessHour> rows = businessHourRepository.findAll();
        for (BusinessHour row : rows) {
            BusinessHourRequest day = days.stream()
                    .filter(d -> d.dayOfWeek().equals(row.getDayOfWeek())).findFirst().orElseThrow();
            LocalTime[] range = times.get(row.getDayOfWeek());
            row.change(day.closed(), range[0], range[1]);
        }
        return toResponse(businessHourRepository.saveAll(rows));
    }

    /** 휴무이면 시각이 없어야 하고, 영업일이면 오픈 시각이 마감 시각보다 빨라야 한다. */
    private static LocalTime[] validTimes(BusinessHourRequest day) {
        boolean hasOpen = day.openTime() != null;
        boolean hasClose = day.closeTime() != null;
        if (day.closed()) {
            if (hasOpen || hasClose) {
                throw new ApiException(ErrorCode.INVALID_BUSINESS_HOURS);
            }
            return new LocalTime[] {null, null};
        }
        if (!hasOpen || !hasClose) {
            throw new ApiException(ErrorCode.INVALID_BUSINESS_HOURS);
        }
        LocalTime open = LocalTime.parse(day.openTime());
        LocalTime close = LocalTime.parse(day.closeTime());
        if (!open.isBefore(close)) {
            throw new ApiException(ErrorCode.INVALID_BUSINESS_HOURS);
        }
        return new LocalTime[] {open, close};
    }

    private BusinessHoursResponse toResponse(List<BusinessHour> rows) {
        Map<String, BusinessHour> byDay = rows.stream()
                .collect(Collectors.toMap(BusinessHour::getDayOfWeek, Function.identity()));
        List<BusinessHoursResponse.Item> items = DAYS.stream()
                .map(day -> BusinessHoursResponse.Item.from(byDay.get(day)))
                .toList();
        return new BusinessHoursResponse(items);
    }
}
