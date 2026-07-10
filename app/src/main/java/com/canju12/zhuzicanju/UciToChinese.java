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
 * 将 UCI 走法转换为中文象棋记谱
 */
public class UciToChinese {

    private static final String[] RED_NUMS = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] BLACK_NUMS = {"１", "２", "３", "４", "５", "６", "７", "８", "９"};

    // 红方棋子名称
    private static String redPieceName(int absType) {
        switch (absType) {
            case 1: return "帅";
            case 2: return "仕";
            case 3: return "相";
            case 4: return "马";
            case 5: return "车";
            case 6: return "炮";
            case 7: return "兵";
            default: return "?";
        }
    }

    // 黑方棋子名称
    private static String blackPieceName(int absType) {
        switch (absType) {
            case 1: return "将";
            case 2: return "士";
            case 3: return "象";
            case 4: return "马";
            case 5: return "车";
            case 6: return "炮";
            case 7: return "卒";
            default: return "?";
        }
    }

    /**
     * 将 UCI 走法转为中文记谱（指定棋子类型，适用于走棋后棋盘已更新的情况）
     * @param uciMove UCI走法如 "e2e6"
     * @param pieceValue 棋子值（正=红，负=黑），如 chessView.chessBoard[fromY][fromX] 移动前的值
     * @param board 当前棋盘（用于获取列号等）
     * @param side 走棋方 (1=红, -1=黑)
     */
    public static String convert(String uciMove, int pieceValue, int[][] board, int side) {
        if (uciMove == null || uciMove.length() < 4) return uciMove;
        int fromX = uciMove.charAt(0) - 'a';
        int fromY = 9 - (uciMove.charAt(1) - '0');
        int toX = uciMove.charAt(2) - 'a';
        int toY = 9 - (uciMove.charAt(3) - '0');
        if (fromX < 0 || fromX > 8 || fromY < 0 || fromY > 9 ||
                toX < 0 || toX > 8 || toY < 0 || toY > 9) return uciMove;
        if (pieceValue == 0) return uciMove;
        return convertWithPiece(uciMove, pieceValue, board, fromX, fromY, toX, toY, side);
    }

    /**
     * 将 UCI 走法转为中文记谱（从棋盘读取棋子类型）
     */
    public static String convert(String uciMove, int[][] board, int side) {
        if (uciMove == null || uciMove.length() < 4) return uciMove;

        int fromX = uciMove.charAt(0) - 'a';
        int fromY = 9 - (uciMove.charAt(1) - '0');
        int toX = uciMove.charAt(2) - 'a';
        int toY = 9 - (uciMove.charAt(3) - '0');

        if (fromX < 0 || fromX > 8 || fromY < 0 || fromY > 9 ||
                toX < 0 || toX > 8 || toY < 0 || toY > 9) {
            return uciMove;
        }

        int piece = board[fromY][fromX];
        if (piece == 0) return uciMove;

        return convertWithPiece(uciMove, piece, board, fromX, fromY, toX, toY, side);
    }

    private static String convertWithPiece(String uciMove, int piece, int[][] board,
                                            int fromX, int fromY, int toX, int toY, int side) {
        int absType = Math.abs(piece);
        String pieceName = (side == 1) ? redPieceName(absType) : blackPieceName(absType);

        // 列号：红方从右到左一~九，黑方从右到左1~9
        String fromCol;
        String toCol;
        if (side == 1) {
            fromCol = RED_NUMS[8 - fromX];  // col 0=最右=一
            toCol = RED_NUMS[8 - toX];
        } else {
            fromCol = BLACK_NUMS[fromX];     // col 0=最右=1
            toCol = BLACK_NUMS[toX];
        }

        // 方向
        String dir;
        int rowDiff = toY - fromY;
        if (rowDiff == 0) {
            dir = "平";
        } else if (side == 1) {
            dir = (rowDiff < 0) ? "进" : "退";
        } else {
            dir = (rowDiff > 0) ? "进" : "退";
        }

        // 目的地
        String dest;
        boolean isStraight = (absType == 5 || absType == 6 || absType == 1 || absType == 7);

        if (dir.equals("平")) {
            // 平移：目的地=列号
            dest = toCol;
        } else if (isStraight) {
            // 直线棋子进退：目的地=步数
            int steps = Math.abs(rowDiff);
            if (side == 1) {
                dest = RED_NUMS[steps - 1];
            } else {
                dest = BLACK_NUMS[steps - 1];
            }
        } else {
            // 非直线棋子（马相士）进退：目的地=列号
            dest = toCol;
        }

        return pieceName + fromCol + dir + dest;
    }
}
