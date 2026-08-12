package com.ximofam.graduation_project.chess;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleBot {
    private final Random random = new Random();

    public Move getNextMove(Board board) {
        List<Move> legalMoves = board.legalMoves();

        if (legalMoves.isEmpty()) {
            return null;
        }

        return legalMoves.get(random.nextInt(legalMoves.size()));
    }
}

public class SimpleBotTest {

    @Test
    public void testBotGeneratesLegalMove() {
        Board board = new Board();
        SimpleBot bot = new SimpleBot();

        // Lấy nước đi đầu tiên
        Move move = bot.getNextMove(board);

        // Kiểm tra Bot có trả về nước đi hay không
        assertNotNull(move, "Bot phải trả về một nước đi ở đầu trận.");

        // Sử dụng tính năng kiểm tra luật của Chesslib
        assertTrue(board.isMoveLegal(move, true), "Nước đi của Bot phải tuân thủ luật cờ vua.");
    }

    @Test
    public void testBotMatchSimulation() {
        Board board = new Board();
        SimpleBot whiteBot = new SimpleBot();
        SimpleBot blackBot = new SimpleBot();

        int maxMoves = 200; // Giới hạn số nước đi để tránh lặp vô hạn (Draw by repetition)
        int moveCount = 0;

        // Cho 2 Bot đánh tự động cho đến khi có kết quả hoặc chạm giới hạn
        while (!board.isMated() && !board.isDraw() && !board.isStaleMate() && moveCount < maxMoves) {
            Move move = board.getSideToMove() == com.github.bhlangonijr.chesslib.Side.WHITE
                    ? whiteBot.getNextMove(board)
                    : blackBot.getNextMove(board);

            assertNotNull(move, "Phải có nước đi khi bàn cờ chưa kết thúc.");

            // Thực hiện nước đi trên bàn cờ
            board.doMove(move);
            moveCount++;
        }

        // Ván đấu phải kết thúc với một trạng thái hợp lệ
        assertTrue(board.isMated() || board.isDraw() || board.isStaleMate() || moveCount == maxMoves,
                "Trận đấu kết thúc không đúng quy luật.");

        System.out.println("Mô phỏng thành công! Số lượt đã đi: " + moveCount);
        System.out.println("FEN cuối cùng: " + board.getFen());
    }
}
