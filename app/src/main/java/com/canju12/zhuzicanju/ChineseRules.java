/*
 * 一二残局 - 中国象棋残局刷题软件
 * Copyright (C) 2026 刘霸天-长沙
 *
 * 本程序是自由软件：你可以再分发之和/或依照由自由软件基金会发布的
 * GNU 通用公共许可证修改之，无论是版本 3 许可证，还是（按你的决定）任何以后版都可以。
 *
 * 发布该程序是希望它能有用，但是并无保障;甚至连可销售和符合某个特定的目的都不保证。
 * 请参看 GNU 通用公共许可证，了解详情。
 *
 * 你应该随程序获得一份 GNU 通用公共许可证的复本。如果没有，请看 <https://www.gnu.org/licenses/>。
 */

package com.canju12.zhuzicanju;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Chinese Chess repetition & draw rules (中国象棋循环与和棋规则)
 *
 * Rules implemented:
 * 1. 60 rounds (120 half-moves) without capture = draw
 * 2. Only first 8 checks per side count toward valid moves
 * 3. No attacking pieces (R/N/C/P) on both sides = draw
 * 4. Perpetual check (长将): the side that repeatedly checks loses
 * 5. Perpetual chase (长捉): the side that repeatedly chases unrooted pieces loses
 * 6. Non-violating repetition (一将一闲/长兑/长献 etc.): draw
 *
 * Repetition detection:
 * - Same position (board + side-to-move) appearing 3 times → warning
 * - 4th occurrence → classify the cycle and decide:
 *   - 长将 → that side loses
 *   - 长捉 → that side loses
 *   - Otherwise → draw
 */
public class ChineseRules {

    private static final String TAG = "ChineseRules";

    // Piece type constants (matching ChessView encoding)
    private static final int KNIGHT = 4;
    private static final int ROOK   = 5;
    private static final int CANNON = 6;
    private static final int PAWN   = 7;

    private static final int MAX_ROUNDS = 60;
    private static final int MAX_VALID_MOVES = MAX_ROUNDS * 2;
    private static final int MAX_CHECKS_COUNTED = 8;

    // Repetition thresholds
    private static final int REPETITION_WARNING_COUNT = 3;  // 3rd occurrence → warning
    private static final int REPETITION_DECIDE_COUNT  = 4;  // 4th occurrence → decide (lose or draw)

    private int validMoves;
    private int redCheckCount;
    private int blackCheckCount;

    // Position history: each entry is "boardKey|sideToMove"
    private List<String> positionHistory;
    // Check history: parallel to positionHistory, records whether the move that LED to this position was a check
    // checkHistory[i] = true means the move that produced positionHistory[i] was a check (将军)
    private List<Boolean> checkHistory;
    // Chase history: parallel to positionHistory, records whether the move was a chase (捉无根子)
    // chaseHistory[i] = true means the move that produced positionHistory[i] was a chase
    private List<Boolean> chaseHistory;
    // Side history: which side made the move that produced this position
    // sideHistory[i] = 1 (red) or -1 (black)
    private List<Integer> sideHistory;

    // Repetition state
    private boolean repetitionWarningShown;
    private boolean repetitionToastShown;

    // Violation result: set when a repetition cycle is classified
    private ViolationResult violationResult;

    // Simple result holder for checkDraw
    public static class DrawResult {
        public final boolean isDraw;
        public final String reason;
        public DrawResult(boolean isDraw, String reason) {
            this.isDraw = isDraw;
            this.reason = reason;
        }
    }

    // Violation result: long check → loser side; long chase → loser side; or draw by repetition
    public static class ViolationResult {
        public static final int TYPE_LONG_CHECK_LOSS  = 1;  // 长将判负
        public static final int TYPE_REPETITION_DRAW   = 2;  // 重复局面判和
        public static final int TYPE_LONG_CHASE_LOSS   = 3;  // 长捉判负

        public final int type;
        public final int loserSide;  // meaningful for TYPE_LONG_CHECK_LOSS and TYPE_LONG_CHASE_LOSS
        public final String description;

        private ViolationResult(int type, int loserSide, String description) {
            this.type = type;
            this.loserSide = loserSide;
            this.description = description;
        }

        public static ViolationResult longCheckLoss(int loserSide) {
            return new ViolationResult(TYPE_LONG_CHECK_LOSS, loserSide,
                    loserSide == 1 ? "红方长将判负" : "黑方长将判负");
        }

        public static ViolationResult longChaseLoss(int loserSide) {
            return new ViolationResult(TYPE_LONG_CHASE_LOSS, loserSide,
                    loserSide == 1 ? "红方长捉判负" : "黑方长捉判负");
        }

        public static ViolationResult repetitionDraw() {
            return new ViolationResult(TYPE_REPETITION_DRAW, 0, "重复局面判和");
        }

        public static ViolationResult repetitionDraw(String reason) {
            return new ViolationResult(TYPE_REPETITION_DRAW, 0, reason);
        }
    }

    public ChineseRules() {
        positionHistory = new ArrayList<>();
        checkHistory = new ArrayList<>();
        chaseHistory = new ArrayList<>();
        sideHistory = new ArrayList<>();
        reset();
    }

    public void reset() {
        validMoves = 0;
        redCheckCount = 0;
        blackCheckCount = 0;
        positionHistory.clear();
        checkHistory.clear();
        chaseHistory.clear();
        sideHistory.clear();
        repetitionWarningShown = false;
        repetitionToastShown = false;
        violationResult = null;
        Log.d(TAG, "rules reset");
    }

    /**
     * Record a move and check for repetition cycles.
     *
     * @param wasCapture  whether this move captured a piece
     * @param wasCheck    whether this move was a check (将军)
     * @param wasChase    whether this move was a chase (捉无根子)
     * @param movingSide  which side made this move (1=red, -1=black)
     * @param chessBoard  current board state after the move
     * @param sideToMove  which side is to move next (1=red, -1=black)
     */
    public void recordMove(boolean wasCapture, boolean wasCheck, boolean wasChase,
                           int movingSide, int[][] chessBoard, int sideToMove) {
        // Update valid move counter
        updateValidMoves(wasCapture, wasCheck, movingSide);

        // On capture, clear repetition tracking (board fundamentally changed)
        if (wasCapture) {
            positionHistory.clear();
            checkHistory.clear();
            chaseHistory.clear();
            sideHistory.clear();
            repetitionWarningShown = false;
            repetitionToastShown = false;
            violationResult = null;
        }

        // Record position with side-to-move (critical: same board + different side = different position)
        String posKey = getPositionKey(chessBoard, sideToMove);
        positionHistory.add(posKey);
        checkHistory.add(wasCheck);
        chaseHistory.add(wasChase);
        sideHistory.add(movingSide);

        // Check for repetition cycles
        checkRepetitionCycle();
    }

    /**
     * Legacy overload without wasChase — treats all non-check moves as non-chase.
     */
    public void recordMove(boolean wasCapture, boolean wasCheck, int movingSide,
                           int[][] chessBoard, int sideToMove) {
        recordMove(wasCapture, wasCheck, false, movingSide, chessBoard, sideToMove);
    }

    /**
     * Legacy overload — derives sideToMove from movingSide.
     */
    public void recordMove(boolean wasCapture, boolean wasCheck, int movingSide, int[][] chessBoard) {
        recordMove(wasCapture, wasCheck, false, movingSide, chessBoard, -movingSide);
    }

    private void updateValidMoves(boolean wasCapture, boolean wasCheck, int movingSide) {
        if (wasCapture) {
            validMoves = 0;
            redCheckCount = 0;
            blackCheckCount = 0;
            Log.d(TAG, "capture -> reset counters");
            return;
        }

        if (wasCheck) {
            int totalChecks;
            if (movingSide == 1) {
                redCheckCount++;
                totalChecks = redCheckCount;
            } else {
                blackCheckCount++;
                totalChecks = blackCheckCount;
            }

            if (totalChecks <= MAX_CHECKS_COUNTED) {
                validMoves++;
                Log.d(TAG, "check(effective): side=" + (movingSide == 1 ? "R" : "B") +
                        " #" + totalChecks + " valid=" + validMoves);
            } else {
                Log.d(TAG, "check(invalid): side=" + (movingSide == 1 ? "R" : "B") +
                        " #" + totalChecks + " valid=" + validMoves);
            }
        } else {
            validMoves++;
            Log.d(TAG, "normal move: valid=" + validMoves +
                    " Rchk=" + redCheckCount + " Bchk=" + blackCheckCount);
        }
    }

    /**
     * Detect repetition cycles in position history.
     *
     * A cycle of length k means: positionHistory[n] == positionHistory[n-k] == positionHistory[n-2k]
     * i.e., the same position has appeared 3 times at interval k.
     *
     * When detected, we classify the cycle:
     * - 长将 → that side loses
     * - 长捉 → that side loses
     * - Otherwise → draw by repetition
     */
    private void checkRepetitionCycle() {
        int size = positionHistory.size();
        if (size < 7) return;  // Need at least 2*3+1 = 7 entries for a cycle of length 3

        String currentPos = positionHistory.get(size - 1);

        // Try cycle lengths from 2 to 6 (typical Chinese chess cycles are 2-6 half-moves)
        for (int k = 2; k <= 6; k++) {
            if (size < 2 * k + 1) continue;

            String p1 = positionHistory.get(size - 1 - k);
            String p2 = positionHistory.get(size - 1 - 2 * k);

            if (currentPos.equals(p1) && p1.equals(p2)) {
                // Found a repeating pattern at interval k
                // Count how many times this position appears consecutively at interval k
                int count = 0;
                for (int i = size - 1; i >= 0; i -= k) {
                    if (positionHistory.get(i).equals(currentPos)) {
                        count++;
                    } else {
                        break;
                    }
                }

                Log.d(TAG, "repetition cycle detected: length=" + k + " count=" + count);

                if (count >= REPETITION_DECIDE_COUNT) {
                    // 4th occurrence → classify the cycle
                    if (violationResult == null) {
                        violationResult = classifyCycle(k);
                        Log.d(TAG, "cycle classified: " + violationResult.description);
                    }
                } else if (count >= REPETITION_WARNING_COUNT && !repetitionWarningShown) {
                    // 3rd occurrence of same position → warning
                    repetitionWarningShown = true;
                    repetitionToastShown = false;
                    Log.d(TAG, "cycle x" + count + " -> warning");
                }
                return;
            }
        }
    }

    /**
     * Classify a detected repetition cycle of length k.
     *
     * Examine the moves in one full cycle (the most recent k moves).
     * For each side, check:
     * 1. Do they check on every move? → 长将 → that side loses
     * 2. Do they chase (捉无根子) on every move (and not all checks)? → 长捉 → that side loses
     * 3. Otherwise → draw (一将一闲/长兑/长献 etc.)
     *
     * @param k cycle length in half-moves
     * @return ViolationResult indicating the outcome
     */
    private ViolationResult classifyCycle(int k) {
        int size = positionHistory.size();

        // Examine the most recent cycle: moves from (size - k) to (size - 1)
        boolean redAlwaysChecks = true;
        boolean blackAlwaysChecks = true;
        boolean redAlwaysChases = true;
        boolean blackAlwaysChases = true;
        boolean redHasMove = false;
        boolean blackHasMove = false;

        for (int i = size - k; i < size; i++) {
            if (i < 0) continue;
            int side = sideHistory.get(i);
            boolean wasCheck = checkHistory.get(i);
            boolean wasChase = chaseHistory.get(i);

            if (side == 1) {
                redHasMove = true;
                if (!wasCheck) redAlwaysChecks = false;
                // 走棋方的某步既不将军也不捉 → 该方不是长捉
                if (!wasCheck && !wasChase) redAlwaysChases = false;
            } else {
                blackHasMove = true;
                if (!wasCheck) blackAlwaysChecks = false;
                if (!wasCheck && !wasChase) blackAlwaysChases = false;
            }
        }

        // 先判长将（长将比长捉更严重）。但若双方都长将，按规则判和而非任判一方。
        boolean redLongCheck = redHasMove && redAlwaysChecks;
        boolean blackLongCheck = blackHasMove && blackAlwaysChecks;
        if (redLongCheck && blackLongCheck) {
            return ViolationResult.repetitionDraw("双方长将判和");
        }
        if (redLongCheck) {
            return ViolationResult.longCheckLoss(1);
        }
        if (blackLongCheck) {
            return ViolationResult.longCheckLoss(-1);
        }

        // 长捉判定：走棋方在循环中每步要么将军要么捉无根子，且不全是将军
        // （到这里双方都非纯长将，说明各自至少有一步是非将步）
        boolean redLongChase = redHasMove && redAlwaysChases && !redLongCheck;
        boolean blackLongChase = blackHasMove && blackAlwaysChases && !blackLongCheck;
        if (redLongChase && blackLongChase) {
            return ViolationResult.repetitionDraw("双方长捉判和");
        }
        if (redLongChase) {
            return ViolationResult.longChaseLoss(1);
        }
        if (blackLongChase) {
            return ViolationResult.longChaseLoss(-1);
        }

        // 其他情况：一将一闲、长兑、长献等 → 判和
        return ViolationResult.repetitionDraw();
    }

    /**
     * Check for draw or violation results.
     *
     * Callers should check:
     * - result.isDraw → game is a draw
     * - isLongCheckViolation() → long check loss
     * - isLongChaseViolation() → long chase loss
     */
    public DrawResult checkDraw(int[][] chessBoard) {
        // 60-round rule
        if (validMoves >= MAX_VALID_MOVES) {
            return new DrawResult(true, "60回合无吃子判和");
        }

        // Repetition draw (non-violating repetition)
        if (violationResult != null && violationResult.type == ViolationResult.TYPE_REPETITION_DRAW) {
            return new DrawResult(true, violationResult.description);
        }

        // No attacking pieces
        if (hasNoAttackingPieces(chessBoard)) {
            return new DrawResult(true, "无攻击子力判和");
        }

        return new DrawResult(false, null);
    }

    /**
     * Whether a long-check (perpetual check) violation has been detected.
     */
    public boolean isLongCheckViolation() {
        return violationResult != null && violationResult.type == ViolationResult.TYPE_LONG_CHECK_LOSS;
    }

    /**
     * Whether a long-chase (perpetual chase) violation has been detected.
     */
    public boolean isLongChaseViolation() {
        return violationResult != null && violationResult.type == ViolationResult.TYPE_LONG_CHASE_LOSS;
    }

    /**
     * Whether any violation (long check or long chase) has been detected.
     */
    public boolean isViolation() {
        return violationResult != null && violationResult.type != ViolationResult.TYPE_REPETITION_DRAW;
    }

    /**
     * Get which side loses due to violation (long check or long chase).
     * @return 1 = red loses, -1 = black loses, 0 = no violation
     */
    public int getViolationSide() {
        if (violationResult == null || violationResult.type == ViolationResult.TYPE_REPETITION_DRAW) {
            return 0;
        }
        return violationResult.loserSide;
    }

    /**
     * Get which side loses due to long check.
     * @return 1 = red loses, -1 = black loses
     */
    public int getLongCheckViolationSide() {
        if (violationResult == null || violationResult.type != ViolationResult.TYPE_LONG_CHECK_LOSS) {
            return 0;
        }
        return violationResult.loserSide;
    }

    /**
     * Get the full violation result (for UI display of description).
     */
    public ViolationResult getViolationResult() {
        return violationResult;
    }

    public boolean isRepetitionWarningActive() {
        return repetitionWarningShown && !repetitionToastShown && violationResult == null;
    }

    public void markRepetitionToastShown() {
        repetitionToastShown = true;
    }

    public boolean isRepetitionToastShown() {
        return repetitionToastShown;
    }

    private boolean hasNoAttackingPieces(int[][] board) {
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int piece = board[y][x];
                int absVal = Math.abs(piece);
                if (absVal == KNIGHT || absVal == ROOK || absVal == CANNON || absVal == PAWN) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Generate a position key that includes the side to move.
     * Same board position with different side-to-move is a DIFFERENT position
     * per Chinese chess rules.
     */
    private String getPositionKey(int[][] board, int sideToMove) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                sb.append(board[y][x]).append(',');
            }
        }
        sb.append('S').append(sideToMove);
        return sb.toString();
    }

    // Accessors
    public int getValidMoves() { return validMoves; }
    public int getRedCheckCount() { return redCheckCount; }
    public int getBlackCheckCount() { return blackCheckCount; }
    public int getMovesUntilDraw() {
        return Math.max(0, MAX_VALID_MOVES - validMoves);
    }
}
