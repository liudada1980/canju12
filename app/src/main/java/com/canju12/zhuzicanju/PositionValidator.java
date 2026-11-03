package com.canju12.zhuzicanju;

/**
 * Chinese chess static legality checker.
 *
 * Board encoding matches ChessView:
 *  1=-red king, 2=-red advisor, 3=-red elephant, 4=-red knight,
 *  5=-red rook, 6=-red cannon, 7=-red pawn
 * Negative values are black pieces.
 */
public class PositionValidator {

    private static final int MAX_ROOK = 2;
    private static final int MAX_KNIGHT = 2;
    private static final int MAX_CANNON = 2;
    private static final int MAX_ADVISOR = 2;
    private static final int MAX_ELEPHANT = 2;
    private static final int MAX_PAWN = 5;

    private int[][] board;

    public static class Result {
        public final boolean valid;
        public final String reason;

        public Result(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
    }

    public Result validate(int[][] board) {
        if (board == null || board.length != 10) {
            return new Result(false, "棋盘为空");
        }
        for (int[] row : board) {
            if (row == null || row.length != 9) {
                return new Result(false, "棋盘尺寸不正确");
            }
        }
        this.board = board;

        Result r;
        if ((r = checkPieceCounts()) != null) return r;
        if ((r = checkPiecePositions()) != null) return r;
        if ((r = checkKingsFacing()) != null) return r;

        return new Result(true, null);
    }

    /**
     * Validate the raw FEN text before parsing.
     */
    public Result validateFenFormat(String fen) {
        if (fen == null) {
            return new Result(false, "FEN为空");
        }

        String normalized = stripBom(fen).trim();
        if (normalized.isEmpty()) {
            return new Result(false, "FEN为空");
        }
        if (normalized.length() > 4096) {
            return new Result(false, "FEN过长");
        }

        String[] tokens = normalized.split("\\s+");
        if (tokens.length < 2) {
            return new Result(false, "FEN格式不完整");
        }

        String boardPart = tokens[0];
        if (boardPart.startsWith("2#")) {
            boardPart = boardPart.substring(2);
        }
        if (boardPart.isEmpty()) {
            return new Result(false, "FEN棋盘字段为空");
        }

        if (countChar(boardPart, '/') != 9) {
            return new Result(false, "FEN棋盘字段必须包含9个斜杠");
        }

        String side = tokens[1];
        if (!"w".equals(side) && !"b".equals(side)) {
            return new Result(false, "FEN第二字段必须是w或b");
        }

        String[] rows = boardPart.split("/", -1);
        if (rows.length != 10) {
            return new Result(false, "FEN棋盘字段必须是10行");
        }

        for (int i = 0; i < rows.length; i++) {
            String row = rows[i];
            if (row == null || row.isEmpty()) {
                return new Result(false, "FEN第" + (i + 1) + "行为空");
            }

            int cells = 0;
            for (int j = 0; j < row.length(); j++) {
                char c = row.charAt(j);
                if (c >= '1' && c <= '9') {
                    cells += c - '0';
                } else if (isFenPieceChar(c)) {
                    cells++;
                } else {
                    return new Result(false, "FEN包含非法字符: " + c);
                }

                if (cells > 9) {
                    return new Result(false, "FEN第" + (i + 1) + "行超过9个位置");
                }
            }

            if (cells != 9) {
                return new Result(false, "FEN第" + (i + 1) + "行位置数不是9");
            }
        }

        return new Result(true, null);
    }

    private Result checkPieceCounts() {
        int[] red = new int[8];
        int[] black = new int[8];
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

        if (red[1] != 1) return new Result(false, "红帅数量不合法(" + red[1] + ")");
        if (black[1] != 1) return new Result(false, "黑将数量不合法(" + black[1] + ")");

        if (red[2] > MAX_ADVISOR) return new Result(false, "红仕超过2个");
        if (black[2] > MAX_ADVISOR) return new Result(false, "黑士超过2个");
        if (red[3] > MAX_ELEPHANT) return new Result(false, "红相超过2个");
        if (black[3] > MAX_ELEPHANT) return new Result(false, "黑象超过2个");
        if (red[4] > MAX_KNIGHT) return new Result(false, "红马超过2个");
        if (black[4] > MAX_KNIGHT) return new Result(false, "黑马超过2个");
        if (red[5] > MAX_ROOK) return new Result(false, "红车超过2个");
        if (black[5] > MAX_ROOK) return new Result(false, "黑车超过2个");
        if (red[6] > MAX_CANNON) return new Result(false, "红炮超过2个");
        if (black[6] > MAX_CANNON) return new Result(false, "黑炮超过2个");
        if (red[7] > MAX_PAWN) return new Result(false, "红兵超过5个");
        if (black[7] > MAX_PAWN) return new Result(false, "黑卒超过5个");

        return null;
    }

    private Result checkPiecePositions() {
        int[] redPawnsByFile = new int[9];
        int[] blackPawnsByFile = new int[9];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int p = board[y][x];
                if (p == 0) continue;
                int absP = Math.abs(p);
                boolean isRed = p > 0;

                switch (absP) {
                    case 1:
                        if (!inPalace(y, x, isRed)) {
                            return new Result(false, (isRed ? "红帅" : "黑将") + "不在九宫内");
                        }
                        break;
                    case 2:
                        if (!inPalace(y, x, isRed)) {
                            return new Result(false, (isRed ? "红仕" : "黑士") + "不在九宫内");
                        }
                        if (!isAdvisorPoint(y, x, isRed)) {
                            return new Result(false, (isRed ? "红仕" : "黑士") + "位置非法");
                        }
                        break;
                    case 3:
                        if (isRed && y < 5) {
                            return new Result(false, "红相过河");
                        }
                        if (!isRed && y > 4) {
                            return new Result(false, "黑象过河");
                        }
                        if (!isElephantPoint(y, x, isRed)) {
                            return new Result(false, (isRed ? "红相" : "黑象") + "位置非法");
                        }
                        break;
                    case 7:
                        Result pr = checkPawnPosition(y, x, isRed);
                        if (pr != null) return pr;
                        if (isRed && y >= 5 && y <= 6) redPawnsByFile[x]++;
                        if (!isRed && y >= 3 && y <= 4) blackPawnsByFile[x]++;
                        break;
                    default:
                        break;
                }
            }
        }
        for (int x = 0; x < 9; x += 2) {
            if (redPawnsByFile[x] > 1) {
                return new Result(false, "红兵第" + (x + 1) + "路的第6、7行不能同时有两个红兵");
            }
            if (blackPawnsByFile[x] > 1) {
                return new Result(false, "黑卒第" + (x + 1) + "路的第4、5行不能同时有两个黑卒");
            }
        }
        return null;
    }

    private boolean inPalace(int y, int x, boolean isRed) {
        if (x < 3 || x > 5) return false;
        return isRed ? (y >= 7 && y <= 9) : (y >= 0 && y <= 2);
    }

    private boolean isAdvisorPoint(int y, int x, boolean isRed) {
        if (isRed) {
            return (y == 7 && (x == 3 || x == 5))
                    || (y == 8 && x == 4)
                    || (y == 9 && (x == 3 || x == 5));
        }
        return (y == 0 && (x == 3 || x == 5))
                || (y == 1 && x == 4)
                || (y == 2 && (x == 3 || x == 5));
    }

    private boolean isElephantPoint(int y, int x, boolean isRed) {
        if (isRed) {
            return (y == 5 && (x == 2 || x == 6))
                    || (y == 7 && (x == 0 || x == 4 || x == 8))
                    || (y == 9 && (x == 2 || x == 6));
        }
        return (y == 0 && (x == 2 || x == 6))
                || (y == 2 && (x == 0 || x == 4 || x == 8))
                || (y == 4 && (x == 2 || x == 6));
    }

    private Result checkPawnPosition(int y, int x, boolean isRed) {
        boolean evenCol = (x == 0 || x == 2 || x == 4 || x == 6 || x == 8);

        if (isRed) {
            if (y >= 5) {
                if (y != 5 && y != 6) {
                    return new Result(false, "红兵未过河却在非法位置(row" + y + ")");
                }
                if (!evenCol) {
                    return new Result(false, "红兵未过河不能横走(col" + x + ")");
                }
            }
        } else {
            if (y <= 4) {
                if (y != 3 && y != 4) {
                    return new Result(false, "黑卒未过河却在非法位置(row" + y + ")");
                }
                if (!evenCol) {
                    return new Result(false, "黑卒未过河不能横走(col" + x + ")");
                }
            }
        }
        return null;
    }

    private Result checkKingsFacing() {
        int redKingY = -1, redKingX = -1;
        int blackKingY = -1, blackKingX = -1;

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (board[y][x] == 1) {
                    redKingY = y;
                    redKingX = x;
                } else if (board[y][x] == -1) {
                    blackKingY = y;
                    blackKingX = x;
                }
            }
        }

        if (redKingY == -1 || blackKingY == -1) {
            return null;
        }

        if (redKingX == blackKingX) {
            int col = redKingX;
            int minY = Math.min(redKingY, blackKingY);
            int maxY = Math.max(redKingY, blackKingY);
            for (int y = minY + 1; y < maxY; y++) {
                if (board[y][col] != 0) {
                    return null;
                }
            }
            return new Result(false, "将帅照面(飞将)");
        }

        return null;
    }

    private int countChar(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) count++;
        }
        return count;
    }

    private boolean isFenPieceChar(char c) {
        switch (c) {
            case 'r':
            case 'n':
            case 'b':
            case 'a':
            case 'k':
            case 'c':
            case 'p':
            case 'R':
            case 'N':
            case 'B':
            case 'A':
            case 'K':
            case 'C':
            case 'P':
                return true;
            default:
                return false;
        }
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }
}
