package com.canju12.zhuzicanju;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LongCheckRulesTest {

    private static final int KING = 1;
    private static final int ROOK = 5;

    private static int[][] move(int[][] source, int fromX, int toX) {
        int[][] board = new int[10][9];
        for (int y = 0; y < 10; y++) {
            board[y] = source[y].clone();
        }
        board[2][toX] = board[2][fromX];
        board[2][fromX] = 0;
        return board;
    }

    private static ChineseRules.ViolationResult runCycle(boolean redChecks, boolean blackChecks) {
        ChineseRules rules = new ChineseRules();
        int[][] board = new int[10][9];
        board[9][4] = KING;
        board[0][3] = -KING;
        board[2][1] = ROOK;

        boolean rookLeft = true;
        for (int step = 0; step < 13; step++) {
            int fromX = rookLeft ? 1 : 3;
            int toX = rookLeft ? 3 : 1;
            board = move(board, fromX, toX);
            rules.recordMove(false, redChecks, 1, board, -1);
            rookLeft = !rookLeft;

            if (step == 12) break;

            rules.recordMove(false, blackChecks, -1, board, 1);
        }
        return rules.getViolationResult();
    }

    @Test
    public void redLongCheckShouldLose() {
        ChineseRules.ViolationResult result = runCycle(true, false);
        assertNotNull(result);
        assertEquals(ChineseRules.ViolationResult.TYPE_LONG_CHECK_LOSS, result.type);
        assertEquals(1, result.loserSide);
    }

    @Test
    public void blackLongCheckShouldLose() {
        ChineseRules.ViolationResult result = runCycle(false, true);
        assertNotNull(result);
        assertEquals(ChineseRules.ViolationResult.TYPE_LONG_CHECK_LOSS, result.type);
        assertEquals(-1, result.loserSide);
    }

    @Test
    public void bothLongCheckShouldDraw() {
        ChineseRules.ViolationResult result = runCycle(true, true);
        assertNotNull(result);
        assertEquals(ChineseRules.ViolationResult.TYPE_REPETITION_DRAW, result.type);
        assertEquals(0, result.loserSide);
    }

    @Test
    public void nonCheckingCycleShouldDraw() {
        ChineseRules.ViolationResult result = runCycle(false, false);
        assertNotNull(result);
        assertEquals(ChineseRules.ViolationResult.TYPE_REPETITION_DRAW, result.type);
    }
}
