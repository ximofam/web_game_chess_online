package com.ximofam.graduation_project.chess.enums;

public enum GameResult {
    WHITE_WIN,
    BLACK_WIN,
    DRAW;

    public static GameResult fromWinner(PlayerRole winner) {
        return winner == PlayerRole.WHITE ? WHITE_WIN : BLACK_WIN;
    }

    public static GameResult fromLoser(PlayerRole loser) {
        return loser == PlayerRole.WHITE ? BLACK_WIN : WHITE_WIN;
    }
}
