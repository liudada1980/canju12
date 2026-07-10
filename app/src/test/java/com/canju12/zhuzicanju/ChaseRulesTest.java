package com.canju12.zhuzicanju;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 长捉/长将循环判定（ChineseRules.classifyCycle）的本地单元测试。
 *
 * 不依赖 Android 框架：ChineseRules 只用到 android.util.Log，已通过
 * testOptions.unitTests.isReturnDefaultValues=true 使其返回默认值而非抛 "not mocked"。
 *
 * 棋盘约定（与 ChessView 一致）：y=0 黑方底线、y=9 红方底线，河界在第4/5行之间；
 * 正值=红，负值=黑；1帅/2仕/3相/4马/5车/6炮/7兵卒；side: 1=红, -1=黑。
 *
 * 说明：本测试聚焦规则层 classifyCycle 的分支判定——即"哪方长捉/长将判负、
 * 双方长捉/长将判和、长将优先于长捉"。这是用户关注的核心（判负方向是否正确）。
 * 棋子级"捉"识别（未过河兵卒排除、旧威胁排除、有根子排除）位于 ChessView.isLastMoveChase，
 * 依赖 Android 资源加载，不在本地单测覆盖，改由编译 + 人工局面验证（见计划文档）。
 *
 * 位置键含 sideToMove，故"同棋盘 + 轮到同一方"才算同一局面。这里用一方在两点间
 * 反复横移、另一方在两点间反复横移，构造 k=2 循环，使位置键按周期重复，
 * 同一局面累计出现 4 次从而触发判定阈值。
 */
public class ChaseRulesTest {

    private static final int K = 1, N = 4, R = 5, P = 7;

    /**
     * 双将在各自九宫、且不在同列（避免双将照面）。
     * 红车在 (2,1)<->(2,3) 间横移，黑将在 (0,3)<->(0,4) 间横移。
     * 因红车横移、黑将横移，每个回合后棋子归位 → k=2 循环。
     */
    private static int[][] baseBoard(boolean redAtLeft, boolean blackKingAtLeft) {
        int[][] b = new int[10][9];
        b[0][3] = -K;           // 黑将（先固定放3，下面按 blackKingAtLeft 调整）
        b[9][4] = K;            // 红帅在4列，黑将在3列，不同列，不照面
        b[2][ redAtLeft ? 1 : 3 ] = R;   // 红车
        if (!blackKingAtLeft) {
            b[0][3] = 0;
            b[0][4] = -K;       // 黑将移到4列 —— 注意：红帅也在4列！会双将照面。
            // 改用黑将在 3 列与 2 列横移，避开红帅所在列。
            b[0][4] = 0;
            b[0][3] = -K;
            b[0][2] = -K;
            b[0][3] = 0;
        }
        return b;
    }

    private static int[][] clone(int[][] b) {
        int[][] c = new int[10][9];
        for (int y = 0; y < 10; y++) c[y] = b[y].clone();
        return c;
    }

    private static int[][] applyMove(int[][] b, int fy, int fx, int ty, int tx) {
        int[][] c = clone(b);
        c[ty][tx] = c[fy][fx];
        c[fy][fx] = 0;
        return c;
    }

    /**
     * 构造一个 k=4 循环：红车在 (2,1)<->(2,3) 横移，黑将在 (0,3)<->(0,4) 横移，
     * 每 4 步(红+黑+红+黑)后局面复原。走 13 步(7红+6黑，末手红)，使同一局面
     * (idx0/4/8/12) 累计出现 4 次 → 触发判定阈值 REPETITION_DECIDE_COUNT=4。
     *
     * 13 步序列(交替，红先行)：红黑红黑红黑红黑红黑红黑红
     *
     * @param redChase   红方每步 wasChase 入参
     * @param blackChase 黑方每步 wasChase 入参
     * @param redCheck   红方每步 wasCheck 入参
     * @param blackCheck 黑方每步 wasCheck 入参
     * @return 判定结果（可能为 null，若未达阈值）
     */
    private ChineseRules.ViolationResult runTwoPointCycle(
            boolean redChase, boolean blackChase,
            boolean redCheck, boolean blackCheck) {
        ChineseRules rules = new ChineseRules();
        // 初始棋盘：红帅(9,4)、黑将(0,3)、红车(2,1)
        int[][] board = new int[10][9];
        board[0][3] = -K;
        board[9][4] = K;
        board[2][1] = R;

        boolean redAtLeft = true;    // 红车当前在左(2,1)还是右(2,3)
        boolean blackAtLeft = true;  // 黑将当前在(0,3)还是(0,4)

        // 13 步：奇数下标为红，偶数下标(从0计) ... 用步号 0..12，步号为偶数=红，奇数=黑
        for (int step = 0; step < 13; step++) {
            boolean redStep = (step % 2 == 0);
            if (redStep) {
                int fromX = redAtLeft ? 1 : 3;
                int toX = redAtLeft ? 3 : 1;
                board = applyMove(board, 2, fromX, 2, toX);
                rules.recordMove(false, redCheck, redChase, 1, board, -1);
                redAtLeft = !redAtLeft;
            } else {
                int fromX = blackAtLeft ? 3 : 4;
                int toX = blackAtLeft ? 4 : 3;
                board = applyMove(board, 0, fromX, 0, toX);
                rules.recordMove(false, blackCheck, blackChase, -1, board, 1);
                blackAtLeft = !blackAtLeft;
            }
        }
        return rules.getViolationResult();
    }

    @Test
    public void redLongChase_blackIdle_shouldRedLose() {
        // 红方每步捉(无根子)，黑方闲步 → 红长捉判负
        ChineseRules.ViolationResult vr = runTwoPointCycle(true, false, false, false);
        assertNotNull("红长捉应产生结果", vr);
        assertEquals("应为长捉判负",
                ChineseRules.ViolationResult.TYPE_LONG_CHASE_LOSS, vr.type);
        assertEquals("负方为红", 1, vr.loserSide);
        assertEquals("描述", "红方长捉判负", vr.description);
    }

    @Test
    public void blackLongChase_redIdle_shouldBlackLose() {
        // 黑方每步捉，红方闲 → 黑长捉判负（验证方向：哪方长捉哪方负）
        ChineseRules.ViolationResult vr = runTwoPointCycle(false, true, false, false);
        assertNotNull("黑长捉应产生结果", vr);
        assertEquals("应为长捉判负",
                ChineseRules.ViolationResult.TYPE_LONG_CHASE_LOSS, vr.type);
        assertEquals("负方为黑", -1, vr.loserSide);
        assertEquals("描述", "黑方长捉判负", vr.description);
    }

    @Test
    public void bothSidesLongChase_shouldDraw() {
        // 双方都长捉 → 判和（修复点2：原实现会误判红负）
        ChineseRules.ViolationResult vr = runTwoPointCycle(true, true, false, false);
        assertNotNull("双方长捉应产生结果", vr);
        assertEquals("双方长捉应判和",
                ChineseRules.ViolationResult.TYPE_REPETITION_DRAW, vr.type);
        assertEquals("负方应为0(和)", 0, vr.loserSide);
        assertEquals("描述", "双方长捉判和", vr.description);
    }

    @Test
    public void redLongCheck_blackIdle_shouldRedLoseByCheck() {
        // 红方每步将军，黑方闲 → 红长将判负
        ChineseRules.ViolationResult vr = runTwoPointCycle(false, false, true, false);
        assertNotNull("红长将应产生结果", vr);
        assertEquals("应为长将判负",
                ChineseRules.ViolationResult.TYPE_LONG_CHECK_LOSS, vr.type);
        assertEquals("负方为红", 1, vr.loserSide);
        assertEquals("描述", "红方长将判负", vr.description);
    }

    @Test
    public void bothSidesLongCheck_shouldDraw() {
        // 双方都长将 → 判和（修复点2：原实现会误判红负）
        ChineseRules.ViolationResult vr = runTwoPointCycle(false, false, true, true);
        assertNotNull("双方长将应产生结果", vr);
        assertEquals("双方长将应判和",
                ChineseRules.ViolationResult.TYPE_REPETITION_DRAW, vr.type);
        assertEquals("描述", "双方长将判和", vr.description);
    }

    @Test
    public void longCheckOverridesLongChase_whenBothFromRed() {
        // 红方每步既将又捉，黑方闲 → 长将优先，判红负（长将）
        ChineseRules.ViolationResult vr = runTwoPointCycle(true, false, true, false);
        assertNotNull(vr);
        assertEquals("长将优先于长捉",
                ChineseRules.ViolationResult.TYPE_LONG_CHECK_LOSS, vr.type);
        assertEquals("负方为红", 1, vr.loserSide);
    }

    @Test
    public void bothIdle_shouldDraw() {
        // 双方都闲步（既不将也不捉）→ 重复局面判和
        ChineseRules.ViolationResult vr = runTwoPointCycle(false, false, false, false);
        assertNotNull("闲步循环应产生结果", vr);
        assertEquals("闲步循环应判和",
                ChineseRules.ViolationResult.TYPE_REPETITION_DRAW, vr.type);
    }
}
