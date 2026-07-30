package com.ximofam.graduation_project.chess.listeners;

import com.ximofam.graduation_project.chess.services.RoomService;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.helpers.dtos.events.UserWentOfflineEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnUserWentOfflineListenerTest {

    @Mock
    private RoomService roomService;

    @InjectMocks
    private OnUserWentOfflineListener listener;

    @Test
    void onUserWentOffline_WhenInRoom_ShouldCallLeaveRoom() {
        var event = new UserWentOfflineEvent("42", Map.of("status", "IN_ROOM", "roomId", "room1"));

        listener.onUserWentOffline(event);

        verify(roomService).leaveRoom("room1", "42", com.ximofam.graduation_project.chess.enums.LeaveReason.DISCONNECT);
    }

    @Test
    void onUserWentOffline_WhenNotInRoom_ShouldDoNothing() {
        var event = new UserWentOfflineEvent("42", Map.of("status", "ONLINE"));

        listener.onUserWentOffline(event);

        verifyNoInteractions(roomService);
    }

    @Test
    void onUserWentOffline_WhenRoomIdMissing_ShouldDoNothing() {
        var event = new UserWentOfflineEvent("42", Map.of("status", "IN_ROOM"));

        listener.onUserWentOffline(event);

        verifyNoInteractions(roomService);
    }

    @Test
    void onUserWentOffline_WhenLeaveRoomThrows_ShouldSwallowAndLog() {
        var event = new UserWentOfflineEvent("42", Map.of("status", "IN_ROOM", "roomId", "room1"));
        doThrow(new NotFoundException("Room not found")).when(roomService).leaveRoom("room1", "42", com.ximofam.graduation_project.chess.enums.LeaveReason.DISCONNECT);

        // should not propagate
        listener.onUserWentOffline(event);

        verify(roomService).leaveRoom("room1", "42", com.ximofam.graduation_project.chess.enums.LeaveReason.DISCONNECT);
    }
}
