package com.canju12.zhuzicanju;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PositionValidatorTest {

    private final PositionValidator validator = new PositionValidator();

    @Test
    public void validateFenFormat_acceptsStandardFen() {
        PositionValidator.Result result = validator.validateFenFormat(
                "5a3/4k4/4b4/9/4N4/r3CR3/3p5/2C4n1/4p4/3K4c w - - 0 1");

        assertTrue(result.valid);
    }

    @Test
    public void validateFenFormat_rejectsBadSideField() {
        PositionValidator.Result result = validator.validateFenFormat(
                "9/9/9/9/9/9/9/9/9/9 x - - 0 1");

        assertFalse(result.valid);
    }

    @Test
    public void validateFenFormat_rejectsNonStandardPieceAliases() {
        PositionValidator.Result result = validator.validateFenFormat(
                "4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1");
        assertTrue(result.valid);

        result = validator.validateFenFormat(
                "4k4/9/9/9/9/9/9/9/8E/4K4 w - - 0 1");
        assertFalse(result.valid);
    }

    @Test
    public void validateBoard_rejectsIllegalElephantPlacement() {
        int[][] board = new int[10][9];
        board[0][4] = -1;
        board[9][4] = 1;
        board[1][8] = -3;

        PositionValidator.Result result = validator.validate(board);

        assertFalse(result.valid);
    }

    @Test
    public void validateBoard_rejectsTwoPawnsOnSameFile() {
        int[][] board = standardBoard();
        board[7][0] = 7;
        board[6][0] = 7;

        PositionValidator.Result result = validator.validate(board);

        assertFalse(result.valid);
    }

    @Test
    public void validateBoard_allowsPawnsOnDifferentFiles() {
        int[][] board = standardBoard();

        PositionValidator.Result result = validator.validate(board);

        assertTrue(result.valid);
    }

    private int[][] standardBoard() {
        return new int[][]{
                {-5, -4, -3, -2, -1, -2, -3, -4, -5},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, -6, 0, 0, 0, 0, 0, -6, 0},
                {-7, 0, -7, 0, -7, 0, -7, 0, -7},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {7, 0, 7, 0, 7, 0, 7, 0, 7},
                {0, 6, 0, 0, 0, 0, 0, 6, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {5, 4, 3, 2, 1, 2, 3, 4, 5}
        };
    }
}
