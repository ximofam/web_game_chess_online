package com.ximofam.graduation_project.common.utils;

import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;

public class LuaErrorHandler {

    private LuaErrorHandler() {
        // Utility class
    }

    public static void handle(int errorCode) {
        handle(errorCode, null);
    }

    public static void handle(int errorCode, String role) {
        switch (errorCode) { // NOSONAR java:S131
            case -1 -> throw new NotFoundException("Room not found.");
            case -2 -> throw new BadRequestException("Room is not accepting requests at this time.");
            case -3 -> throw new BadRequestException("You are already seated in this room.");
            case -4 -> throw new BadRequestException("The " + (role != null ? role : "requested") + " seat is already taken.");
            case -5 -> throw new ForbiddenException("Spectators are not allowed in this room.");
            case -6 -> throw new BadRequestException("Invalid role.");
            case -7 -> throw new ForbiddenException("This room is private.");
            case -8 -> throw new BadRequestException("You must be online to perform this action.");
            case -9 -> throw new BadRequestException("You are already in a room.");
            case -10 -> throw new ForbiddenException("You are not in this room.");
            case -12 -> throw new ForbiddenException("You are not a player in this room.");
            case -13 -> throw new BadRequestException("Room is not in countdown state.");
            case -14 -> throw new BadRequestException("Game start time has not been reached yet.");
            case -15 -> throw new BadRequestException("It is not your turn.");
            case -16 -> throw new NotFoundException("Game not found.");
            case -17 -> throw new BadRequestException("Time out.");
            case -18 -> throw new BadRequestException("Cannot perform this action while the game is in progress.");
            case -19 -> throw new BadRequestException("You are already in this seat.");
        }
    }
}
