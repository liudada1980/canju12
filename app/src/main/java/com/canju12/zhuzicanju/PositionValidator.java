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

/**
 * 中国象棋局面静态合法性检查器
 *
 * 检查项：
 * 1. 棋子数量：帅将各1个；各兵种不超过上限（车马炮各≤2，仕士相象各≤2，兵卒各≤5）
 * 2. 棋子位置：帅将在九宫格、仕士在九宫格、相象不过河、兵卒位置合法
 * 3. 将帅不能照面（飞将）
 *
 * 棋盘编码（与 ChessView 一致）：
 * 1=帅 -1=将, 2=仕 -2=士, 3=相 -3=象, 4=马 -4=马, 5=车 -5=车, 6=炮 -6=炮, 7=兵 -7=卒
 * 正数=红方(下方 row7-9九宫), 负数=黑方(上方 row0-2九宫)
 */
public class PositionValidator {

    // 各兵种数量上限
    private static final int MAX_ROOK    = 2;  // 车
    private static final int MAX_KNIGHT  = 2;  // 马
    private static final int MAX_CANNON  = 2;  // 炮
    private static final int MAX_ADVISOR = 2;  // 仕士
    private static final int MAX_ELEPHANT = 2; // 相象
    private static final int MAX_PAWN    = 5;  // 兵卒

    private int[][] board;

    // 校验结果
    public static class Result {
        public final boolean valid;
        public final String reason;
        public Result(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
    }

    /**
     * 校验棋盘局面的合法性
     * @param board int[10][9]，调用后不会被修改
     * @return 合法返回 Result(true, null)，否则返回非法原因
     */
    public Result validate(int[][] board) {
        if (board == null) {
            return new Result(false, "棋盘为空");
        }
        this.board = board;

        Result r;
        if ((r = checkPieceCounts()) != null) return r;
        if ((r = checkPiecePositions()) != null) return r;
        if ((r = checkKingsFacing()) != null) return r;

        return new Result(true, null);
    }

    /**
     * 检查棋子数量
     */
    private Result checkPieceCounts() {
        // 统计各兵种数量：索引1-7为红方，-1到-7为黑方
        // 用数组 [红方1..7] 和 [黑方1..7]
        int[] red = new int[8];   // red[1..7]
        int[] black = new int[8]; // black[1..7]
        int total = 0;

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int p = board[y][x];
                if (p == 0) continue;
                total++;
                int absP = Math.abs(p);
                if (absP < 1 || absP > 7) {
                    return new Result(false, "存在未知棋子类型: " + p);
                }
                if (p > 0) red[absP]++;
                else black[absP]++;
            }
        }

        if (total > 32) {
            return new Result(false, "棋子总数超过32个");
        }

        // 帅将必须各1个
        if (red[1] != 1) return new Result(false, "红帅数量不合法(" + red[1] + ")");
        if (black[1] != 1) return new Result(false, "黑将数量不合法(" + black[1] + ")");

        if (red[2] > MAX_ADVISOR)  return new Result(false, "红仕超过2个");
        if (black[2] > MAX_ADVISOR) return new Result(false, "黑士超过2个");
        if (red[3] > MAX_ELEPHANT) return new Result(false, "红相超过2个");
        if (black[3] > MAX_ELEPHANT) return new Result(false, "黑象超过2个");
        if (red[4] > MAX_KNIGHT)   return new Result(false, "红马超过2个");
        if (black[4] > MAX_KNIGHT)  return new Result(false, "黑马超过2个");
        if (red[5] > MAX_ROOK)     return new Result(false, "红车超过2个");
        if (black[5] > MAX_ROOK)    return new Result(false, "黑车超过2个");
        if (red[6] > MAX_CANNON)   return new Result(false, "红炮超过2个");
        if (black[6] > MAX_CANNON)  return new Result(false, "黑炮超过2个");
        if (red[7] > MAX_PAWN)     return new Result(false, "红兵超过5个");
        if (black[7] > MAX_PAWN)    return new Result(false, "黑卒超过5个");

        return null;
    }

    /**
     * 检查棋子位置合法性
     * 红方九宫：row7-9, col3-5；黑方九宫：row0-2, col3-5
     * 河界：row4-row5之间，红方区域 row5-9，黑方区域 row0-4
     */
    private Result checkPiecePositions() {
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int p = board[y][x];
                if (p == 0) continue;
                int absP = Math.abs(p);
                boolean isRed = p > 0;

                switch (absP) {
                    case 1: // 帅将
                        if (!inPalace(y, x, isRed)) {
                            return new Result(false, (isRed ? "红帅" : "黑将") + "不在九宫格");
                        }
                        break;
                    case 2: // 仕士
                        if (!inPalace(y, x, isRed)) {
                            return new Result(false, (isRed ? "红仕" : "黑士") + "不在九宫格");
                        }
                        // 仕士只能走斜线，固定在九宫的5个点：(7或2,3)(7或2,5)(8或1,4)(9或0,3)(9或0,5)
                        if (!isAdvisorPoint(y, x, isRed)) {
                            return new Result(false, (isRed ? "红仕" : "黑士") + "位置非法");
                        }
                        break;
                    case 3: // 相象
                        // 相象不能过河
                        if (isRed && y < 5) {
                            return new Result(false, "红相过河");
                        }
                        if (!isRed && y > 4) {
                            return new Result(false, "黑象过河");
                        }
                        // 相象只能走田字，固定在7个点
                        if (!isElephantPoint(y, x, isRed)) {
                            return new Result(false, (isRed ? "红相" : "黑象") + "位置非法");
                        }
                        break;
                    case 7: // 兵卒
                        Result pr = checkPawnPosition(y, x, isRed);
                        if (pr != null) return pr;
                        break;
                    default:
                        // 车马炮位置不受限制
                        break;
                }
            }
        }
        return null;
    }

    /**
     * 是否在九宫格内
     */
    private boolean inPalace(int y, int x, boolean isRed) {
        if (x < 3 || x > 5) return false;
        if (isRed) {
            return y >= 7 && y <= 9;
        } else {
            return y >= 0 && y <= 2;
        }
    }

    /**
     * 仕士合法落点（九宫5个点）
     * 红仕: (7,3)(7,5)(8,4)(9,3)(9,5)
     * 黑士: (0,3)(0,5)(1,4)(2,3)(2,5)
     */
    private boolean isAdvisorPoint(int y, int x, boolean isRed) {
        if (isRed) {
            return (y == 7 && (x == 3 || x == 5))
                || (y == 8 && x == 4)
                || (y == 9 && (x == 3 || x == 5));
        } else {
            return (y == 0 && (x == 3 || x == 5))
                || (y == 1 && x == 4)
                || (y == 2 && (x == 3 || x == 5));
        }
    }

    /**
     * 相象合法落点（本方半区7个点）
     * 红相: (5,2)(5,6)(7,0)(7,4)(7,8)(9,2)(9,6)
     * 黑象: (0,2)(0,6)(2,0)(2,4)(2,8)(4,2)(4,6)
     */
    private boolean isElephantPoint(int y, int x, boolean isRed) {
        if (isRed) {
            return (y == 5 && (x == 2 || x == 6))
                || (y == 7 && (x == 0 || x == 4 || x == 8))
                || (y == 9 && (x == 2 || x == 6));
        } else {
            return (y == 0 && (x == 2 || x == 6))
                || (y == 2 && (x == 0 || x == 4 || x == 8))
                || (y == 4 && (x == 2 || x == 6));
        }
    }

    /**
     * 兵卒位置检查
     *
     * 兵卒未过河前只能直走（不能横走），因此列号固定为初始偶数列 {0,2,4,6,8}。
     * 河界在 row4 / row5 之间：
     * - 红兵初始在 row6，向上走；未过河(y>=5)时只在 row5/row6 的偶数列（共10个点）；
     *   过河(y<=4)后可任意列。
     * - 黑卒初始在 row3，向下走；未过河(y<=4)时只在 row3/row4 的偶数列（共10个点）；
     *   过河(y>=5)后可任意列。
     *
     * 红兵不会出现在 row7/8/9（不能后退），黑卒不会出现在 row0/1/2。
     * 兵卒数量(各≤5)由 checkPieceCounts 检查。
     */
    private Result checkPawnPosition(int y, int x, boolean isRed) {
        boolean evenCol = (x == 0 || x == 2 || x == 4 || x == 6 || x == 8);

        if (isRed) {
            if (y >= 5) {
                // 未过河：只能在 row5 或 row6 的偶数列
                if (y != 5 && y != 6) {
                    return new Result(false, "红兵未过河却在非法位置(row" + y + ")");
                }
                if (!evenCol) {
                    return new Result(false, "红兵未过河不能横走(col" + x + ")");
                }
            }
            // 过河后(y<=4)位置不限
        } else {
            if (y <= 4) {
                // 未过河：只能在 row3 或 row4 的偶数列
                if (y != 3 && y != 4) {
                    return new Result(false, "黑卒未过河却在非法位置(row" + y + ")");
                }
                if (!evenCol) {
                    return new Result(false, "黑卒未过河不能横走(col" + x + ")");
                }
            }
            // 过河后(y>=5)位置不限
        }
        return null;
    }

    /**
     * 检查将帅是否照面（飞将）
     * 两个将在同一列且中间无棋子 → 非法
     */
    private Result checkKingsFacing() {
        int redKingY = -1, redKingX = -1;
        int blackKingY = -1, blackKingX = -1;

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (board[y][x] == 1) { redKingY = y; redKingX = x; }
                else if (board[y][x] == -1) { blackKingY = y; blackKingX = x; }
            }
        }

        if (redKingY == -1 || blackKingY == -1) return null;  // 数量检查已处理

        // 同列检查
        if (redKingX == blackKingX) {
            int col = redKingX;
            int minY = Math.min(redKingY, blackKingY);
            int maxY = Math.max(redKingY, blackKingY);
            boolean blocked = false;
            for (int y = minY + 1; y < maxY; y++) {
                if (board[y][col] != 0) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                return new Result(false, "将帅照面(飞将)");
            }
        }
        return null;
    }
}
