package com.rha.room;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.lesson.LessonRepository;
import com.rha.lesson.LessonStatus;
import com.rha.member.AdminGuard;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.Josa;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final LessonRepository lessonRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;
    private final ActivityLogRecorder activityLogRecorder;

    public RoomService(RoomRepository roomRepository, LessonRepository lessonRepository, AdminGuard adminGuard,
            RequestValidator requestValidator, ActivityLogRecorder activityLogRecorder) {
        this.roomRepository = roomRepository;
        this.lessonRepository = lessonRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
        this.activityLogRecorder = activityLogRecorder;
    }

    /** 이름 오름차순(같으면 id 오름차순). 삭제된 룸은 제외한다. {@code page}는 1부터 시작한다. */
    @Transactional(readOnly = true)
    public RoomPageResponse getRooms(int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        Page<Room> result = roomRepository.findByDeletedAtIsNull(pageable);
        List<RoomResponse> content = result.getContent().stream().map(RoomResponse::from).toList();
        return new RoomPageResponse(content, PageInfo.of(result));
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(Long roomId) {
        return RoomResponse.from(findActive(roomId));
    }

    @Transactional
    public RoomResponse createRoom(Long callerId, RoomCreateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);

        String name = request.name().strip();
        if (roomRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw new ApiException(ErrorCode.ROOM_NAME_DUPLICATED);
        }
        Room room = saveUnique(Room.create(name, request.capacity(), Instant.now()));
        activityLogRecorder.record(ActivityAction.ROOM_CREATED, callerId, null, "ROOM", room.getId(),
                Josa.eulReul("룸 '" + room.getName() + "'") + " 등록했다.");
        return RoomResponse.from(room);
    }

    @Transactional
    public RoomResponse updateRoom(Long callerId, Long roomId, RoomUpdateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        if (!request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        Room room = findActive(roomId);

        String name = request.name() == null ? null : request.name().strip();
        if (name != null && roomRepository.existsByNameAndIdNotAndDeletedAtIsNull(name, roomId)) {
            throw new ApiException(ErrorCode.ROOM_NAME_DUPLICATED);
        }
        String beforeName = room.getName();
        int beforeCapacity = room.getCapacity();
        room.update(name, request.capacity(), Instant.now());
        Room saved = saveUnique(room);
        activityLogRecorder.record(ActivityAction.ROOM_UPDATED, callerId, null, "ROOM", saved.getId(),
                Josa.eulReul("룸 '" + saved.getName() + "'") + " 수정했다.",
                ActivityChange.ifChanged("name", beforeName, saved.getName()),
                ActivityChange.ifChanged("capacity", beforeCapacity, saved.getCapacity()));
        return RoomResponse.from(saved);
    }

    /** 룸을 삭제 처리한다. 시작 전이고 취소되지 않은 수업이 있으면 거절한다. */
    @Transactional
    public void deleteRoom(Long callerId, Long roomId) {
        adminGuard.requireAdmin(callerId);
        Room room = roomRepository.findActiveForUpdate(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Instant now = Instant.now();
        if (lessonRepository.existsByRoomIdAndStatusNotAndStartAtAfter(roomId, LessonStatus.CANCELED, now)) {
            throw new ApiException(ErrorCode.ROOM_IN_USE);
        }
        room.delete(now);
        activityLogRecorder.record(ActivityAction.ROOM_DELETED, callerId, null, "ROOM", room.getId(),
                Josa.eulReul("룸 '" + room.getName() + "'") + " 삭제했다.");
    }

    private Room findActive(Long roomId) {
        return roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** 같은 이름이 동시에 등록·수정된 경우 유일 인덱스 위반을 409로 바꾼다. */
    private Room saveUnique(Room room) {
        try {
            return roomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.ROOM_NAME_DUPLICATED);
        }
    }
}
