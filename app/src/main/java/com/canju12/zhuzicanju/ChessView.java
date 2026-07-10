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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChessView extends View {

    private Context context;
    private String TAG = "ChessView";

    private int chessWidth = 0; // 棋盘格子大小（横竖线间距）
    private int offsetX = 0;    // X方向偏移量（第一条竖线的横坐标）
    private int offsetY = 0;    // Y方向偏移量（第一条横线的纵坐标）
    // ===== 棋盘尺寸方案 =====
    // 方案A：横向678×纵向750，格子68，横边67，纵边69（内置 board.png 2034×2250，3×基准）
    // 方案B：纵向1000×横向900（10:9），格子100，边缘50/50
    // 启动时按 board.png 实际宽高比选择方案；读不到 board.png 时默认方案B。
    private enum BoardScheme {
        A(678f, 750f, 68f, 67f, 69f),
        B(900f, 1000f, 100f, 50f, 50f);
        final float designW;   // 横向总宽
        final float designH;   // 纵向总高
        final float cell;      // 格子大小
        final float marginX;   // 横向边缘
        final float marginY;   // 纵向边缘
        BoardScheme(float w, float h, float c, float mx, float my) {
            this.designW = w; this.designH = h; this.cell = c;
            this.marginX = mx; this.marginY = my;
        }
        /** 设计基准宽高比（designW/designH） */
        float aspect() { return designW / designH; }
    }
    private BoardScheme activeScheme = BoardScheme.A;

    private int side = 1; // 1为红方，-1为黑方
    private boolean mode = true; // true人机，false人人，默认人机

    private boolean isSelect = false; // 已经选中则为true
    private int[] selectChess = new int[]{-1, -1}; // 选中的棋子位置
    private int[] fromChess = new int[]{-1, -1};   // 起点
    private int[] toChess = new int[]{-1, -1};     // 终点

    // ===== 选中高亮持续显示：记录各方已选中的棋子，直到对手选中棋子才清除 =====
    // 红方选中棋子时清除黑方高亮，黑方选中棋子时清除红方高亮。
    // 值为 [fromY, fromX]，null 表示该方当前没有高亮棋子。
    private int[] redHighlight = null;   // 红方高亮棋子
    private int[] blackHighlight = null; // 黑方高亮棋子
    // AI对战时人类所执的一方（1红/-1黑）；-2表示未限定（自由练习/分析模式，按当前走棋方限制）
    private int humanSide = -2;
    // =====================================================================

    private Paint paint = new Paint();           // 普通画笔
    private Paint paintMove = new Paint();       // move画笔
    private Paint paintFrom = new Paint();       // 选中棋子画圆画笔
    private Paint paintArrow = new Paint();      // 绘制箭头的画笔

    // 棋子图片资源
    private Bitmap redRook;      // 红车 rr
    private Bitmap redKnight;    // 红马 rn
    private Bitmap redCannon;    // 红炮 rc
    private Bitmap redPawn;      // 红兵 rp
    private Bitmap redKing;      // 红帅 rk
    private Bitmap redAdvisor;   // 红仕 ra
    private Bitmap redElephant;  // 红相 rb

    private Bitmap blackRook;    // 黑车 br
    private Bitmap blackKnight;  // 黑马 bn
    private Bitmap blackCannon;  // 黑炮 bc
    private Bitmap blackPawn;    // 黑卒 bp
    private Bitmap blackKing;    // 黑将 bk
    private Bitmap blackAdvisor; // 黑士 ba
    private Bitmap blackElephant; // 黑象 bb
    private boolean isBoardFlipped = false;

    // ===== Pikafish 对战相关字段 =====
    private boolean lastMoveWasCapture = false;  // 上一手是否吃子
    private boolean lastMoveWasCheck = false;    // 上一手是否将军
    private boolean touchEnabled = true;          // 是否响应触摸，AI思考时禁用
    // ==================================

    // 本地保存的棋盘数据，复用图片资源一起
    private Bitmap boardBitmap;   // 棋盘背景
    private Bitmap roomBitmap;    // 房间背景
    // 走棋记录
    private List<int[]> moveHistory = new ArrayList<>();  // 记录走历史步法 [fromY, fromX, toY, toX]
    private List<Integer> capturedHistory = new ArrayList<>(); // 记录被吃的棋子，使用Integer而不是int[]
    public int[][] chessBoard = {
            {-5, -4, -3, -2, -1, -2, -3, -4, -5},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, -6, 0, 0, 0, 0, 0, -6, 0},
            {-7, 0, -7, 0, -7, 0, -7, 0, -7},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {7, 0, 7, 0, 7, 0, 7, 0, 7},
            {0, 6, 0, 0, 0, 0, 0, 6, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {5, 4, 3, 2, 1, 2, 3, 4, 5},
    };

    private OnGameListener gameListener;

    public interface OnGameListener {
        void onMove(int fromY, int fromX, int toY, int toX, int chessType);
        void onGameOver(int winner);
    }
    // ========== 走棋方监听接口 ==========
    public interface OnTurnChangeListener {
        void onTurnChange(int side);  // side: 1=红方，-1=黑方
    }
    // =================================

    private OnTurnChangeListener turnChangeListener;

    public void setOnTurnChangeListener(OnTurnChangeListener listener) {
        this.turnChangeListener = listener;
    }

    /**
     * 设置人类所执的一方（仅AI对战模式使用）。
     * @param side 1=红方, -1=黑方, -2=未限定（自由练习/分析模式，按当前走棋方限制）
     */
    public void setHumanSide(int side) {
        this.humanSide = side;
    }

    /**
     * 清除所有选中高亮（落子、切换走棋方、AI走棋等场景调用）。
     */
    public void clearSelection() {
        isSelect = false;
        selectChess[0] = -1;
        selectChess[1] = -1;
        redHighlight = null;
        blackHighlight = null;
        invalidate();
    }

    public void setOnGameListener(OnGameListener listener) {
        this.gameListener = listener;
    }

    public ChessView(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public ChessView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    private void init() {
        // 加载棋子图片
        loadChessImages();

        // 设置透明背景，让棋盘背景图透出来
        setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // 初始化画笔
        paintMove.setColor(android.graphics.Color.parseColor("#4CAF50"));
        paintMove.setAlpha(100);
        paintFrom.setColor(android.graphics.Color.parseColor("#FF5722"));
        paintFrom.setStyle(android.graphics.Paint.Style.STROKE);
        paintFrom.setStrokeWidth(5);
        paintArrow.setColor(android.graphics.Color.RED);
        paintArrow.setStrokeWidth(4);
    }

    /**
     * 从外部存储加载图片，供用户自定义替换。
     */
    private Bitmap loadImageFromExternal(String fileName) {
        try {
            File externalDir = Environment.getExternalStorageDirectory();  // /storage/emulated/0/
            File canju12Dir = new File(externalDir, "canju12");            // /storage/emulated/0/canju12/
            File uiStyleDir = new File(canju12Dir, "uistyle");             // /storage/emulated/0/canju12/uistyle/

            File imageFile = new File(uiStyleDir, fileName);
            if (imageFile.exists()) {
                // 与内置解码一致地关闭密度缩放：外部文件不带密度信息，
                // decodeFile 默认按原像素返回，这里显式配置以防个别 ROM 行为不一致。
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inScaled = false;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), opts);
                if (bitmap != null) {
                    Log.d(TAG, "成功从外部加载图片: " + fileName);
                    return bitmap;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "加载外部图片失败: " + fileName + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * 加载棋子相关图片，优先从外部存储加载，否则使用drawable。
     * 全程不向上抛异常：某些硬件上内置 PNG（如 board.png）可能解码失败，
     * 但即使读不出棋盘/棋子，程序也不应退出——绘制路径已对 null 做了保护，
     * 这里最多让对应 Bitmap 为 null，绝不崩溃。
     */
    public void loadChessImages() {
        try {
            Bitmap external;

            // ========== 红方棋子 ==========
            external = loadImageFromExternal("rr.png");
            redRook = (external != null) ? external : decodeResourceSafely(R.drawable.rr);

            external = loadImageFromExternal("rn.png");
            redKnight = (external != null) ? external : decodeResourceSafely(R.drawable.rn);

            external = loadImageFromExternal("rc.png");
            redCannon = (external != null) ? external : decodeResourceSafely(R.drawable.rc);

            external = loadImageFromExternal("rp.png");
            redPawn = (external != null) ? external : decodeResourceSafely(R.drawable.rp);

            external = loadImageFromExternal("rk.png");
            redKing = (external != null) ? external : decodeResourceSafely(R.drawable.rk);

            external = loadImageFromExternal("ra.png");
            redAdvisor = (external != null) ? external : decodeResourceSafely(R.drawable.ra);

            external = loadImageFromExternal("rb.png");
            redElephant = (external != null) ? external : decodeResourceSafely(R.drawable.rb);

            // ========== 黑方棋子 ==========
            external = loadImageFromExternal("br.png");
            blackRook = (external != null) ? external : decodeResourceSafely(R.drawable.br);

            external = loadImageFromExternal("bn.png");
            blackKnight = (external != null) ? external : decodeResourceSafely(R.drawable.bn);

            external = loadImageFromExternal("bc.png");
            blackCannon = (external != null) ? external : decodeResourceSafely(R.drawable.bc);

            external = loadImageFromExternal("bp.png");
            blackPawn = (external != null) ? external : decodeResourceSafely(R.drawable.bp);

            external = loadImageFromExternal("bk.png");
            blackKing = (external != null) ? external : decodeResourceSafely(R.drawable.bk);

            external = loadImageFromExternal("ba.png");
            blackAdvisor = (external != null) ? external : decodeResourceSafely(R.drawable.ba);

            external = loadImageFromExternal("bb.png");
            blackElephant = (external != null) ? external : decodeResourceSafely(R.drawable.bb);

            // ========== 棋盘背景 ==========
            external = loadImageFromExternal("board.png");
            boardBitmap = (external != null) ? external : decodeResourceSafely(R.drawable.board);

            external = loadImageFromExternal("room.png");
            roomBitmap = (external != null) ? external : decodeResourceSafely(R.drawable.room);
        } catch (Throwable t) {
            // 兜底：任何意外都不应让构造/init 崩溃导致程序退出
            Log.e(TAG, "loadChessImages 整体异常: " + t.getMessage());
        }
        // 按实际加载到的 board.png 选择尺寸方案；读不到棋盘时 detectScheme() 返回方案B（10:9）
        activeScheme = detectScheme();
        Log.d(TAG, "棋盘尺寸方案: " + activeScheme.name());
        Log.d(TAG, "所有图片加载完成");
    }

    /**
     * 按 board.png 实际宽高比选择棋盘尺寸方案。
     * - boardBitmap 为 null（内置/外置都读不到）→ 方案B（10:9），作为“读不出棋盘”的兜底。
     * - 否则取与两方案 designW/designH 宽高比最接近者：
     *   方案A 678/750≈0.904，方案B 900/1000=0.900，分界约 0.902。
     *   二者比例接近，要求 board.png 按对应方案的比例制作。
     */
    private BoardScheme detectScheme() {
        if (boardBitmap == null) return BoardScheme.B;
        int bw = boardBitmap.getWidth();
        int bh = boardBitmap.getHeight();
        if (bw <= 0 || bh <= 0) return BoardScheme.B;
        float imgAspect = (float) bw / (float) bh;
        float dA = Math.abs(imgAspect - BoardScheme.A.aspect());
        float dB = Math.abs(imgAspect - BoardScheme.B.aspect());
        return (dA < dB) ? BoardScheme.A : BoardScheme.B;
    }

    /**
     * 安全地解码内置 drawable 资源。
     *
     * 为什么不能直接用 BitmapFactory.decodeResource(res)：
     *   放在 res/drawable/ 的 PNG 会被系统当作 mdpi(160) 基准，decodeResource 默认
     *   inScaled=true，在高密度设备(xxhdpi=480)上会把图"解码并放大 3 倍"再返回。
     *   某些厂商 ROM 的 PNG 解码器在这条"密度缩放解码"路径上会直接返回 null——
     *   这正是"有些硬件读得出、有些读不出"的根因（差异点是屏幕密度）。
     *
     * 修复策略（两条独立路径，任一成功即返回）：
     *   1) BitmapFactory + inScaled=false：关闭密度缩放，按 PNG 原始像素 1:1 解码，
     *      走最兼容的原始解码路径；绘制时再由 canvas.drawBitmap 缩放到棋盘尺寸。
     *   2) 框架 ContextCompat.getDrawable 兜底：与系统加载 drawable 同一通道，
     *      兼容性最好；成功后从 BitmapDrawable 取出 Bitmap。
     * 任一抛异常或返回 null 都不向上传播，最终返回 null 由调用方按 null 保护绘制。
     */
    private Bitmap decodeResourceSafely(int resId) {
        // ---- 路径1：BitmapFactory，关闭密度缩放 ----
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;                  // 不做密度缩放，按原始像素解码
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bm = BitmapFactory.decodeResource(getResources(), resId, opts);
            if (bm != null) return bm;
        } catch (Throwable t) {
            Log.e(TAG, "BitmapFactory 解码失败 resId=" + resId + ": " + t.getMessage());
        }
        // ---- 路径2：框架 Drawable 管线兜底 ----
        try {
            android.graphics.drawable.Drawable d =
                    androidx.core.content.ContextCompat.getDrawable(getContext(), resId);
            if (d instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap bm = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                if (bm != null) {
                    Log.d(TAG, "内置图片经 Drawable 兜底加载成功 resId=" + resId);
                    return bm;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "getDrawable 兜底失败 resId=" + resId + ": " + t.getMessage());
        }
        return null;
    }
    /**
     * 获取棋盘背景。
     */
    public Bitmap getBoardBitmap() {
        return boardBitmap;
    }

    /**
     * 获取房间背景。
     */
    public Bitmap getRoomBitmap() {
        return roomBitmap;
    }
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        LayoutHolder holder = new LayoutHolder();
        computeBoardLayout(w, h, holder);
        this.chessWidth = holder.chessWidth;
        this.offsetX = holder.offsetX;
        this.offsetY = holder.offsetY;
    }

    /**
     * 按当前棋盘尺寸方案（activeScheme）的设计基准计算棋盘布局。
     * 视图尺寸 w×h 按比例缩放：取宽高分别能容纳的最大尺寸的较小缩放比，
     * 保证棋盘既不超出视图，又保持该方案的原始比例。
     *
     * 计算结果写入 holder 的 chessWidth / offsetX / offsetY：
     * - offsetX = 第一条竖线的横坐标（左边缘）
     * - offsetY = 第一条横线的纵坐标（上边缘）
     * - chessWidth = 横竖线间距（格子大小）
     * 棋子中心绘制在 offsetX + col*chessWidth, offsetY + row*chessWidth 交叉点。
     */
    private void computeBoardLayout(int w, int h, LayoutHolder holder) {
        if (w <= 0 || h <= 0) return;
        // 按当前方案设计基准计算：方案A 678×750/68/67/69，方案B 900×1000/100/50/50
        BoardScheme s = activeScheme;
        float scaleByW = w / s.designW;
        float scaleByH = h / s.designH;
        float scale = Math.min(scaleByW, scaleByH);

        int cell = Math.max(1, Math.round(s.cell * scale));
        int marginX = Math.round(s.marginX * scale);
        int marginY = Math.round(s.marginY * scale);

        // 棋盘外接尺寸：marginX*2 + cell*8 = designW，marginY*2 + cell*9 = designH
        int boardW = marginX * 2 + cell * 8;
        int boardH = marginY * 2 + cell * 9;

        // 居中
        holder.chessWidth = cell;
        holder.offsetX = (w - boardW) / 2 + marginX;
        holder.offsetY = (h - boardH) / 2 + marginY;
    }

    /** 用于 captureChessView 局部布局计算 */
    private static class LayoutHolder {
        int chessWidth;
        int offsetX;
        int offsetY;
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (chessWidth == 0) return;

        try {
            // 先绘制棋盘背景图（board.png），确保在棋子下方
            drawBoardBackground(canvas);

            // 绘制棋子
            drawChessPieces(canvas);

            // 绘制选中高亮（红方/黑方各自独立，持续显示到对手选中棋子）
            drawHighlights(canvas);
        } catch (Throwable t) {
            // 绘制过程意外（含某颗 bitmap 异常）不应让程序退出
            Log.e(TAG, "onDraw 异常: " + t.getMessage());
        }
    }

    /**
     * 绘制棋盘背景图（board.png），铺满棋盘区域。
     * 这样无论外置uistyle里有没有Board.png，棋盘都能正确显示在棋子下方，
     * 不会被room.png遮挡。
     * boardBitmap 为 null 时（某些硬件读不到内置 PNG）直接跳过，留空背景，不报错。
     */
    private void drawBoardBackground(Canvas canvas) {
        if (boardBitmap == null) return;
        try {
            // 棋盘背景图按当前方案设计基准铺满外接矩形：
            // 左边缘 = offsetX - marginX，上边缘 = offsetY - marginY
            float scale = chessWidth / activeScheme.cell;
            int mx = Math.round(activeScheme.marginX * scale);
            int my = Math.round(activeScheme.marginY * scale);
            int left = offsetX - mx;
            int top = offsetY - my;
            int right = offsetX + chessWidth * 8 + mx;
            int bottom = offsetY + chessWidth * 9 + my;
            Rect srcRect = new Rect(0, 0, boardBitmap.getWidth(), boardBitmap.getHeight());
            Rect dstRect = new Rect(left, top, right, bottom);
            canvas.drawBitmap(boardBitmap, srcRect, dstRect, null);
        } catch (Throwable t) {
            Log.e(TAG, "drawBoardBackground 异常: " + t.getMessage());
        }
    }

    /**
     * 绘制棋子，支持翻转。
     * 单颗 bitmap 若异常只跳过该子，不影响整盘绘制。
     */
    private void drawChessPieces(Canvas canvas) {
        float pieceScale = 0.98f;
        int pieceSize = (int) (chessWidth * pieceScale);

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int chess = chessBoard[y][x];
                if (chess == 0) continue;

                Bitmap bitmap = getChessBitmap(chess);
                if (bitmap != null) {
                    // ===== 棋盘映射：若是翻转则映射到显示位置 =====
                    int drawX, drawY;
                    if (isBoardFlipped) {
                        drawX = 8 - x;  // 左右镜像
                        drawY = 9 - y;  // 上下镜像
                    } else {
                        drawX = x;
                        drawY = y;
                    }
                    // ==============================================

                    int centerX = offsetX + drawX * chessWidth;
                    int centerY = offsetY + drawY * chessWidth;

                    int left = centerX - pieceSize / 2;
                    int top = centerY - pieceSize / 2;
                    int right = centerX + pieceSize / 2;
                    int bottom = centerY + pieceSize / 2;

                    Rect rect = new Rect(left, top, right, bottom);
                    try {
                        canvas.drawBitmap(bitmap, null, rect, paint);
                    } catch (Throwable t) {
                        Log.e(TAG, "drawChessPieces 单子绘制异常: " + t.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 绘制红方/黑方当前高亮的棋子。
     * 红方选中棋子时清除黑方高亮，黑方选中棋子时清除红方高亮。
     */
    private void drawHighlights(Canvas canvas) {
        drawHighlight(canvas, redHighlight);
        drawHighlight(canvas, blackHighlight);
    }

    /**
     * 在指定棋子位置绘制高亮（填充+描边圆圈）。
     */
    private void drawHighlight(Canvas canvas, int[] pos) {
        if (pos == null || pos[0] < 0 || pos[1] < 0) return;
        float pieceScale = 0.98f;
        int pieceSize = (int) (chessWidth * pieceScale);

        // ===== 棋盘映射：若是翻转则映射到显示位置 =====
        int drawX, drawY;
        if (isBoardFlipped) {
            drawX = 8 - pos[1];
            drawY = 9 - pos[0];
        } else {
            drawX = pos[1];
            drawY = pos[0];
        }
        // =================================================

        int centerX = offsetX + drawX * chessWidth;
        int centerY = offsetY + drawY * chessWidth;

        float circleRadius = pieceSize / 2f;

        float newRadius = circleRadius * 0.98f;

        Paint fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor("#3300BFFF")); // 半透蓝
        canvas.drawCircle(centerX, centerY, newRadius, fillPaint);

        paintFrom.setStyle(Paint.Style.STROKE);
        paintFrom.setStrokeWidth(Math.max(3, chessWidth / 12));
        paintFrom.setColor(Color.parseColor("#FF00BFFF")); // 亮蓝
        paintFrom.setAntiAlias(true);

        // 棋盘四周已有边距，边缘棋子的高亮圆可完整画出，无需裁剪
        canvas.drawCircle(centerX, centerY, newRadius, paintFrom);
    }

    /**
     * 翻转棋盘，用于换色 + 左右镜像。
     */
    public void flipBoard() {
        int[][] newBoard = new int[10][9];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int newX = 8 - x;
                int newY = 9 - y;
                newBoard[newY][newX] = -chessBoard[y][x];
            }
        }
        chessBoard = newBoard;
        side = -side;
        isBoardFlipped = !isBoardFlipped;
        invalidate();
    }

    /**
     * 设置棋盘是否翻转
     */
    public void setBoardFlipped(boolean flipped) {
        this.isBoardFlipped = flipped;
    }

    /**
     * 根据棋子数值，获取对应的图片
     */
    private Bitmap getChessBitmap(int chess) {
        switch (chess) {
            case 1: return redKing;
            case 2: return redAdvisor;
            case 3: return redElephant;
            case 4: return redKnight;
            case 5: return redRook;
            case 6: return redCannon;
            case 7: return redPawn;
            case -1: return blackKing;
            case -2: return blackAdvisor;
            case -3: return blackElephant;
            case -4: return blackKnight;
            case -5: return blackRook;
            case -6: return blackCannon;
            case -7: return blackPawn;
            default: return null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mode) return true;
        if (!touchEnabled) return true;  // AI思考时禁止触摸

        // 计算触摸的格子位置，屏幕坐标。
        // 棋子绘制在交叉点（offsetX + col*chessWidth），故触摸区应以交叉点为中心：
        // 偏移半个格子后做整数除法，每颗棋子左右各有半格容差，点选更灵敏、不易误触邻格。
        int half = chessWidth / 2;
        int screenX = (int) ((event.getX() - offsetX + half) / chessWidth);
        int screenY = (int) ((event.getY() - offsetY + half) / chessWidth);

        // 边界检查：落在棋盘线网之外（含四周边距区）则忽略
        if (screenX < 0 || screenX > 8 || screenY < 0 || screenY > 9) return true;

        // ===== 若是翻转，把屏幕坐标映射回数据坐标 =====
        int dataX, dataY;
        if (isBoardFlipped) {
            dataX = 8 - screenX;
            dataY = 9 - screenY;
        } else {
            dataX = screenX;
            dataY = screenY;
        }
        // =============================================

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int touchedPiece = chessBoard[dataY][dataX];

            // ===== 选子限制 =====
            // 当前走棋方 side：只能选中己方棋子（chessBoard * side > 0）。
            // AI对战时人类只能选人类方棋子：当轮到人类走棋（side == humanSide）时，
            // side 即人类方，故 chessBoard * side > 0 已天然排除AI方棋子。
            // 此条件同时满足分析页面"红方行棋时不能选黑方、黑方行棋时不能选红方"。
            if (!isSelect) {
                // 尚未选中：尝试选中己方棋子
                if (touchedPiece * side > 0) {
                    selectPiece(dataY, dataX);
                }
            } else {
                // 已选中：尝试移动到目标
                if (touchedPiece * side > 0) {
                    // 点到己方另一棋子 → 改选该棋子（保持高亮为己方，清对方高亮）
                    selectPiece(dataY, dataX);
                } else {
                    // 点到空格或敌方棋子 → 尝试移动
                    toChess[0] = dataY;
                    toChess[1] = dataX;

                    if (canMove(fromChess[0], fromChess[1], toChess[0], toChess[1])) {
                        int capturedPiece = chessBoard[toChess[0]][toChess[1]];
                        lastMoveWasCapture = (capturedPiece != 0);  // 记录是否吃子

                        playMoveSound();  // 播放音效

                        chessBoard[toChess[0]][toChess[1]] = chessBoard[fromChess[0]][fromChess[1]];
                        chessBoard[fromChess[0]][fromChess[1]] = 0;

                        recordMove(fromChess[0], fromChess[1], toChess[0], toChess[1], capturedPiece);

                        // 落子后，本方高亮跟随到落子位置，保持到对手选中棋子
                        setHighlight(side, toChess[0], toChess[1]);

                        side = -side;

                        // 检查落子后是否将军
                        lastMoveWasCheck = isInCheck(side);

                        if (turnChangeListener != null) {
                            turnChangeListener.onTurnChange(side);
                        }

                        if (gameListener != null) {
                            gameListener.onMove(fromChess[0], fromChess[1], toChess[0], toChess[1],
                                    chessBoard[toChess[0]][toChess[1]]);
                        }

                        checkGameOver();

                        // 取消"选中待走"状态，但保留本方高亮
                        isSelect = false;
                        selectChess[0] = -1;
                        selectChess[1] = -1;
                        invalidate();
                    } else {
                        // 走法非法：保持选中状态，让用户继续尝试其他目标
                        // （不取消 isSelect，避免用户感觉"选中后走不了棋"）
                    }
                }
            }
        }
        return true;
    }

    /**
     * 选中一枚棋子：设置选中态、更新本方高亮、清除对方高亮。
     * 红方选中 → 清黑方高亮；黑方选中 → 清红方高亮。
     */
    private void selectPiece(int dataY, int dataX) {
        isSelect = true;
        selectChess[0] = dataY;
        selectChess[1] = dataX;
        fromChess[0] = dataY;
        fromChess[1] = dataX;
        setHighlight(side, dataY, dataX);
    }

    /**
     * 设置指定方的高亮棋子，并清除对方高亮。
     * @param moverSide 走棋方（1红/-1黑）
     */
    private void setHighlight(int moverSide, int y, int x) {
        if (moverSide == 1) {
            redHighlight = new int[]{y, x};
            blackHighlight = null;  // 红方选中，清除黑方高亮
        } else if (moverSide == -1) {
            blackHighlight = new int[]{y, x};
            redHighlight = null;    // 黑方选中，清除红方高亮
        }
        invalidate();
    }


    /**
     * 获取棋盘翻转状态
     */
    public boolean isBoardFlipped() {
        return isBoardFlipped;
    }

    /**
     * 判断能否移动（不检查将死）
     */
    public boolean canMove(int fromY, int fromX, int toY, int toX) {
        // 1、边界检查
        if (toY < 0 || toY > 9 || toX < 0 || toX > 8) return false;

        // 2、走到原地
        if (fromX == toX && fromY == toY) return false;

        // 3、不能吃自己的棋子
        if (chessBoard[fromY][fromX] * chessBoard[toY][toX] > 0) return false;

        // 4、检查棋子走法（原canMove逻辑）
        switch (Math.abs(chessBoard[fromY][fromX])) {
            case 4: // 马
                if (Math.abs(fromY - toY) == 2 && Math.abs(fromX - toX) == 1) {
                    int centerY = (fromY + toY) / 2;
                    if (chessBoard[centerY][fromX] == 0) {
                        // 走法合法，但还需要检查将死
                        if (isMoveSafe(fromY, fromX, toY, toX)) {
                            return true;
                        }
                    }
                } else if (Math.abs(fromY - toY) == 1 && Math.abs(fromX - toX) == 2) {
                    int centerX = (fromX + toX) / 2;
                    if (chessBoard[fromY][centerX] == 0) {
                        if (isMoveSafe(fromY, fromX, toY, toX)) {
                            return true;
                        }
                    }
                }
                break;
            case 5: // 车
                if (fromX == toX) {
                    int minY = Math.min(fromY, toY);
                    int maxY = Math.max(fromY, toY);
                    boolean blocked = false;
                    for (int i = minY + 1; i < maxY; i++) {
                        if (chessBoard[i][fromX] != 0) {
                            blocked = true;
                            break;
                        }
                    }
                    if (!blocked && isMoveSafe(fromY, fromX, toY, toX)) {
                        return true;
                    }
                } else if (fromY == toY) {
                    int minX = Math.min(fromX, toX);
                    int maxX = Math.max(fromX, toX);
                    boolean blocked = false;
                    for (int i = minX + 1; i < maxX; i++) {
                        if (chessBoard[fromY][i] != 0) {
                            blocked = true;
                            break;
                        }
                    }
                    if (!blocked && isMoveSafe(fromY, fromX, toY, toX)) {
                        return true;
                    }
                }
                break;
            case 6: // 炮
                int count = 0;
                if (fromX == toX) {
                    int minY = Math.min(fromY, toY);
                    int maxY = Math.max(fromY, toY);
                    for (int i = minY + 1; i < maxY; i++) {
                        if (chessBoard[i][fromX] != 0) count++;
                    }
                } else if (fromY == toY) {
                    int minX = Math.min(fromX, toX);
                    int maxX = Math.max(fromX, toX);
                    for (int i = minX + 1; i < maxX; i++) {
                        if (chessBoard[fromY][i] != 0) count++;
                    }
                }
                if ((fromX == toX) || (fromY == toY)) {
                    if (count == 0 && chessBoard[toY][toX] == 0) {
                        if (isMoveSafe(fromY, fromX, toY, toX)) return true;
                    }
                    if (count == 1 && chessBoard[fromY][fromX] * chessBoard[toY][toX] < 0) {
                        if (isMoveSafe(fromY, fromX, toY, toX)) return true;
                    }
                }
                break;
            default:
                // 判断帅和将、士、象、兵的走法
                int chessValue = chessBoard[fromY][fromX];
                boolean canMoveResult = false;

                if (chessValue == 1 || chessValue == -1) { // 帅将
                    if (Math.abs(fromX - toX) + Math.abs(fromY - toY) == 1) {
                        if (chessValue == 1 && toX >= 3 && toX <= 5 && toY >= 7 && toY <= 9) {
                            canMoveResult = true;
                        }
                        if (chessValue == -1 && toX >= 3 && toX <= 5 && toY >= 0 && toY <= 2) {
                            canMoveResult = true;
                        }
                    }
                } else if (Math.abs(chessValue) == 2) { // 仕士
                    if (Math.abs(fromX - toX) == 1 && Math.abs(fromY - toY) == 1) {
                        if (chessValue == 2 && toX >= 3 && toX <= 5 && toY >= 7 && toY <= 9) {
                            canMoveResult = true;
                        }
                        if (chessValue == -2 && toX >= 3 && toX <= 5 && toY >= 0 && toY <= 2) {
                            canMoveResult = true;
                        }
                    }
                } else if (Math.abs(chessValue) == 3) { // 相象
                    if (Math.abs(fromX - toX) == 2 && Math.abs(fromY - toY) == 2) {
                        int centerX = (fromX + toX) / 2;
                        int centerY = (fromY + toY) / 2;
                        if (chessBoard[centerY][centerX] == 0) {
                            if (chessValue == 3 && toY >= 5) canMoveResult = true;
                            if (chessValue == -3 && toY <= 4) canMoveResult = true;
                        }
                    }
                } else if (Math.abs(chessValue) == 7) { // 兵卒
                    if (chessValue == 7) { // 红兵
                        if (toY > fromY) return false;
                        boolean isCrossed = (fromY <= 4);
                        if (isCrossed) {
                            if ((fromX == toX && fromY - toY == 1) || (fromY == toY && Math.abs(fromX - toX) == 1)) {
                                canMoveResult = true;
                            }
                        } else {
                            if (fromX == toX && fromY - toY == 1) canMoveResult = true;
                        }
                    } else { // 黑卒
                        if (toY < fromY) return false;
                        boolean isCrossed = (fromY >= 5);
                        if (isCrossed) {
                            if ((fromX == toX && toY - fromY == 1) || (fromY == toY && Math.abs(fromX - toX) == 1)) {
                                canMoveResult = true;
                            }
                        } else {
                            if (fromX == toX && toY - fromY == 1) canMoveResult = true;
                        }
                    }
                }

                // 如果走法合法也不将死
                if (canMoveResult && isMoveSafe(fromY, fromX, toY, toX)) {
                    return true;
                }
                break;
        }

        return false;
    }

    /**
     * 记录走法数据
     */
    private void recordMove(int fromY, int fromX, int toY, int toX, int capturedPiece) {
        moveHistory.add(new int[]{fromY, fromX, toY, toX});
        capturedHistory.add(capturedPiece);  // Integer会自动装箱
    }
    /**
     * 悔棋：撤销一步
     * @return 是否撤销成功
     */
    public boolean undoMove() {
        if (moveHistory.isEmpty()) {
            return false;  // 没有历史记录
        }

        // 取出最后一手走法
        int[] lastMove = moveHistory.get(moveHistory.size() - 1);
        int capturedPiece = capturedHistory.get(capturedHistory.size() - 1);

        int fromY = lastMove[0];
        int fromX = lastMove[1];
        int toY = lastMove[2];
        int toX = lastMove[3];

        // 恢复棋子位置
        chessBoard[fromY][fromX] = chessBoard[toY][toX];
        chessBoard[toY][toX] = capturedPiece;

        // 切换回原走棋方
        side = -side;

        // 清除选中状态与高亮
        isSelect = false;
        selectChess[0] = -1;
        selectChess[1] = -1;
        redHighlight = null;
        blackHighlight = null;

        // 移除历史记录
        moveHistory.remove(moveHistory.size() - 1);
        capturedHistory.remove(capturedHistory.size() - 1);

        // 刷新画面
        invalidate();

        return true;
    }
// ========== 双将面对面判断辅助函数 ==========

    /**
     * 检查双将是否照面（双将在同一列且中间无棋子）
     * @return true表示双将照面，false表示没有
     */
    private boolean isKingsFacing() {
        int redKingY = -1, redKingX = -1;
        int blackKingY = -1, blackKingX = -1;

        // 找到红帅和黑将位置
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (chessBoard[y][x] == 1) {
                    redKingY = y;
                    redKingX = x;
                } else if (chessBoard[y][x] == -1) {
                    blackKingY = y;
                    blackKingX = x;
                }
            }
        }

        // 如果找不到帅或将，说明游戏已结束，这里返回false
        if (redKingY == -1 || blackKingY == -1) {
            return false;
        }

        // 如果在同一列
        if (redKingX == blackKingX) {
            int minY = Math.min(redKingY, blackKingY);
            int maxY = Math.max(redKingY, blackKingY);
            for (int y = minY + 1; y < maxY; y++) {
                if (chessBoard[y][redKingX] != 0) {
                    return false; // 中间有棋子
                }
            }
            return true; // 中间无棋子，双将照面
        }
        return false;
    }

    /**
     * 检查某方是否被将死或困毙
     * @param targetSide 要检查的一方（1=红方，-1=黑方）
     * @return true表示被将死或困毙
     */
    private boolean isCheckmateOrStalemate(int targetSide) {
        // 1. 检查目标方帅/将是否存在
        boolean hasKing = false;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (chessBoard[y][x] == targetSide) {
                    hasKing = true;
                    break;
                }
            }
            if (hasKing) break;
        }
        if (!hasKing) return true; // 帅将被吃

        // 2. 检查目标方是否有任何合法走法
        for (int fromY = 0; fromY < 10; fromY++) {
            for (int fromX = 0; fromX < 9; fromX++) {
                // 只考虑目标方棋子
                if (chessBoard[fromY][fromX] * targetSide > 0) {
                    // 尝试所有可能的目标位置
                    for (int toY = 0; toY < 10; toY++) {
                        for (int toX = 0; toX < 9; toX++) {
                            // 模拟走法，检查是否合法
                            if (canMove(fromY, fromX, toY, toX)) {
                                // 模拟走法
                                int capturedPiece = chessBoard[toY][toX];
                                int movingPiece = chessBoard[fromY][fromX];
                                chessBoard[toY][toX] = movingPiece;
                                chessBoard[fromY][fromX] = 0;

                                // 检查走法后是否合法（不能将帅照面）
                                boolean isLegal = !isKingsFacing();

                                // 恢复模拟
                                chessBoard[fromY][fromX] = movingPiece;
                                chessBoard[toY][toX] = capturedPiece;

                                if (isLegal) {
                                    return false; // 有合法走法
                                }
                            }
                        }
                    }
                }
            }
        }

        return true; // 没有合法走法，被将死或困毙
    }

    /**
     * 检查游戏是否结束
     * 每当调用此方法，检查当前局面走棋一方是否被将死/困毙
     *
     * 检查逻辑：
     * 走完一步后，检查下一步走棋一方是否已经无法解将或解困
     * 如果是，则下一步走棋一方判负
     *
     * 例如：红方走完步 → 检查黑方是否被将死/困毙 → 如果是则红方胜
     */
    private void checkGameOver() {
        // side 表示下一步走棋一方
        // 检查 side 是否被将死或困毙
        if (isCheckmateOrStalemate(side)) {
            // side 被将死或困毙，则上一手走棋一方（-side）获胜
            if (gameListener != null) {
                gameListener.onGameOver(-side);
            }
            return;
        }

        // 检查双将照面（违规走法）
        // 双将照面时，下一步走棋一方（side）违例，判负
        if (isKingsFacing()) {
            if (gameListener != null) {
                // 双将照面，side 违例，-side 获胜
                gameListener.onGameOver(-side);
            }
        }
    }
    /**
     * 模拟走一步后，检查走子后是否全部安全（不被将军）
     * @param fromY 起点
     * @param fromX 起点
     * @param toY 终点
     * @param toX 终点
     * @return true表示安全，false表示会被将军（将死）
     */
    private boolean isMoveSafe(int fromY, int fromX, int toY, int toX) {
        // 记录被吃的棋子
        int capturedPiece = chessBoard[toY][toX];
        int movingPiece = chessBoard[fromY][fromX];

        // 模拟走法
        chessBoard[toY][toX] = movingPiece;
        chessBoard[fromY][fromX] = 0;

        // 走子后检查帅/将是否会被吃
        // 找出走子方帅/将位置
        int side = (movingPiece > 0) ? 1 : -1; // 1=红方，-1=黑方
        int kingValue = (side == 1) ? 1 : -1;
        int kingY = -1, kingX = -1;

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (chessBoard[y][x] == kingValue) {
                    kingY = y;
                    kingX = x;
                    break;
                }
            }
            if (kingY != -1) break;
        }

        // 如果找不到将帅，说明已经被吃，不安全
        if (kingY == -1) {
            // 恢复模拟
            chessBoard[fromY][fromX] = movingPiece;
            chessBoard[toY][toX] = capturedPiece;
            return false;
        }

        // 检查对方棋子是否能吃到本方帅/将
        int enemySide = -side;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (chessBoard[y][x] * enemySide > 0) {
                    // 如果对方棋子能走到帅/将位置，说明不安全
                    if (canMoveDirect(y, x, kingY, kingX)) {
                        // 恢复模拟
                        chessBoard[fromY][fromX] = movingPiece;
                        chessBoard[toY][toX] = capturedPiece;
                        return false;
                    }
                }
            }
        }

        // 检查双将照面（违规走法）
        if (isKingsFacing()) {
            // 恢复模拟
            chessBoard[fromY][fromX] = movingPiece;
            chessBoard[toY][toX] = capturedPiece;
            return false;
        }

        // 恢复模拟
        chessBoard[fromY][fromX] = movingPiece;
        chessBoard[toY][toX] = capturedPiece;

        return true;
    }

    /**
     * 直接判断能否移动（不检查将死）
     * 用于 isMoveSafe 中检查对方棋子是否能吃帅/将
     */
    private boolean canMoveDirect(int fromY, int fromX, int toY, int toX) {
        // 1、边界检查
        if (toY < 0 || toY > 9 || toX < 0 || toX > 8) return false;

        // 2、走到原地
        if (fromX == toX && fromY == toY) return false;

        // 3、目标位置不计自己棋子，检查攻击时不需要，为了安全起见
        // 这里不检查，因为要检查能否到对方位置

        // 4、检查棋子走法（不限制的canMove逻辑）
        switch (Math.abs(chessBoard[fromY][fromX])) {
            case 4: // 马
                if (Math.abs(fromY - toY) == 2 && Math.abs(fromX - toX) == 1) {
                    int centerY = (fromY + toY) / 2;
                    if (chessBoard[centerY][fromX] == 0) return true;
                } else if (Math.abs(fromY - toY) == 1 && Math.abs(fromX - toX) == 2) {
                    int centerX = (fromX + toX) / 2;
                    if (chessBoard[fromY][centerX] == 0) return true;
                }
                break;
            case 5: // 车
                if (fromX == toX) {
                    int minY = Math.min(fromY, toY);
                    int maxY = Math.max(fromY, toY);
                    for (int i = minY + 1; i < maxY; i++) {
                        if (chessBoard[i][fromX] != 0) return false;
                    }
                    return true;
                } else if (fromY == toY) {
                    int minX = Math.min(fromX, toX);
                    int maxX = Math.max(fromX, toX);
                    for (int i = minX + 1; i < maxX; i++) {
                        if (chessBoard[fromY][i] != 0) return false;
                    }
                    return true;
                }
                break;
            case 6: // 炮
                int count = 0;
                if (fromX == toX) {
                    int minY = Math.min(fromY, toY);
                    int maxY = Math.max(fromY, toY);
                    for (int i = minY + 1; i < maxY; i++) {
                        if (chessBoard[i][fromX] != 0) count++;
                    }
                } else if (fromY == toY) {
                    int minX = Math.min(fromX, toX);
                    int maxX = Math.max(fromX, toX);
                    for (int i = minX + 1; i < maxX; i++) {
                        if (chessBoard[fromY][i] != 0) count++;
                    }
                }
                if ((fromX == toX) || (fromY == toY)) {
                    if (count == 0 && chessBoard[toY][toX] == 0) return true;
                    if (count == 1 && chessBoard[fromY][fromX] * chessBoard[toY][toX] < 0) return true;
                }
                break;
            default:
                // 帅将、士仕、象相走法（不限原逻辑）
                int chessValue = chessBoard[fromY][fromX];
                if (chessValue == 1 || chessValue == -1) { // 帅将
                    if (Math.abs(fromX - toX) + Math.abs(fromY - toY) == 1) {
                        if (chessValue == 1 && toX >= 3 && toX <= 5 && toY >= 7 && toY <= 9) return true;
                        if (chessValue == -1 && toX >= 3 && toX <= 5 && toY >= 0 && toY <= 2) return true;
                    }
                } else if (Math.abs(chessValue) == 2) { // 仕士
                    if (Math.abs(fromX - toX) == 1 && Math.abs(fromY - toY) == 1) {
                        if (chessValue == 2 && toX >= 3 && toX <= 5 && toY >= 7 && toY <= 9) return true;
                        if (chessValue == -2 && toX >= 3 && toX <= 5 && toY >= 0 && toY <= 2) return true;
                    }
                } else if (Math.abs(chessValue) == 3) { // 相象
                    if (Math.abs(fromX - toX) == 2 && Math.abs(fromY - toY) == 2) {
                        int centerX = (fromX + toX) / 2;
                        int centerY = (fromY + toY) / 2;
                        if (chessBoard[centerY][centerX] == 0) {
                            if (chessValue == 3 && toY >= 5) return true;
                            if (chessValue == -3 && toY <= 4) return true;
                        }
                    }
                } else if (Math.abs(chessValue) == 7) { // 兵卒
                    if (chessValue == 7) { // 红兵
                        if (toY > fromY) return false;
                        boolean isCrossed = (fromY <= 4);
                        if (isCrossed) {
                            if ((fromX == toX && fromY - toY == 1) || (fromY == toY && Math.abs(fromX - toX) == 1)) return true;
                        } else {
                            if (fromX == toX && fromY - toY == 1) return true;
                        }
                    } else { // 黑卒
                        if (toY < fromY) return false;
                        boolean isCrossed = (fromY >= 5);
                        if (isCrossed) {
                            if ((fromX == toX && toY - fromY == 1) || (fromY == toY && Math.abs(fromX - toX) == 1)) return true;
                        } else {
                            if (fromX == toX && toY - fromY == 1) return true;
                        }
                    }
                }
                break;
        }
        return false;
    }

    /**
     * 获取当前局面的中国象棋FEN字符串。
     * @return FEN字符串
     */
    public String getFEN() {
        // 不改变棋盘，直接使用 chessBoard 和 side
        // 不需要考虑是否翻转状态
        StringBuilder fen = new StringBuilder();

        for (int y = 0; y < 10; y++) {
            int emptyCount = 0;
            for (int x = 0; x < 9; x++) {
                int chess = chessBoard[y][x];
                if (chess == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(getFENChar(chess));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (y < 9) {
                fen.append("/");
            }
        }

        fen.append(" ");
        fen.append(side == 1 ? "w" : "b");
        fen.append(" - - 0 1");

        return fen.toString();
    }

    /**
     * 计算走棋步数（从初始局面开始，每走一步增加一个走棋回合）
     */
    private int getMoveCount() {
        // 初始棋子数为32，每吃一个棋子减少一个
        int currentPieceCount = 0;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (chessBoard[y][x] != 0) {
                    currentPieceCount++;
                }
            }
        }
        // 已吃棋子 = 32 - 当前棋子数
        int capturedCount = 32 - currentPieceCount;

        // 简单估算：每吃一个棋子增加2个走棋，加上当前走棋方一个
        // 更精确需要记录走棋，这里简化处理
        return Math.max(1, capturedCount + 1);
    }

    /**
     * 将棋子数值转换为中国象棋FEN字符串
     * 红方大写，黑方小写。
     */
    private char getFENChar(int chess) {
        switch (chess) {
            // 红方（大写）
            case 1: return 'K';  // 帅
            case 2: return 'A';  // 仕
            case 3: return 'B';  // 相
            case 4: return 'N';  // 马
            case 5: return 'R';  // 车
            case 6: return 'C';  // 炮
            case 7: return 'P';  // 兵
            // 黑方（小写）
            case -1: return 'k';  // 将
            case -2: return 'a';  // 士
            case -3: return 'b';  // 象
            case -4: return 'n';  // 马
            case -5: return 'r';  // 车
            case -6: return 'c';  // 炮
            case -7: return 'p';  // 卒
            default: return ' ';
        }
    }
    /**
     * 清空历史记录（重置棋盘时调用）
     */
    public void clearHistory() {
        moveHistory.clear();
        capturedHistory.clear();
    }
    /**
     * 重置棋盘
     */
    public void resetBoard() {
        chessBoard = new int[][]{
                {-5, -4, -3, -2, -1, -2, -3, -4, -5},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, -6, 0, 0, 0, 0, 0, -6, 0},
                {-7, 0, -7, 0, -7, 0, -7, 0, -7},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {7, 0, 7, 0, 7, 0, 7, 0, 7},
                {0, 6, 0, 0, 0, 0, 0, 6, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {5, 4, 3, 2, 1, 2, 3, 4, 5},
        };
        side = 1;
        isSelect = false;
        redHighlight = null;
        blackHighlight = null;
        clearHistory();  // 清空历史
        invalidate();
    }


    // 本地保存的当前题目初始FEN
    private String currentInitialFEN = null;  // 当前题目的初始FEN

    /**
     * 设置当前题目的初始FEN
     * @param fen 初始FEN字符串
     */
    public void setCurrentInitialFEN(String fen) {
        this.currentInitialFEN = fen;
        Log.d(TAG, "设置初始FEN: " + fen);
    }

    /**
     * 重置到当前题目的初始局面
     */
    public void resetToCurrentInitial() {
        if (currentInitialFEN != null && !currentInitialFEN.isEmpty()) {
            Log.d(TAG, "重置到初始FEN: " + currentInitialFEN);
            setBoardByFEN(currentInitialFEN);
        } else {
            Log.d(TAG, "没有保存初始FEN，使用默认初始局面");
            resetToInitial();
        }
    }
    /**
     * 获取当前题目的初始FEN
     */
    public String getCurrentInitialFEN() {
        return currentInitialFEN;
    }
    /**
     * 重置到初始局面
     */
    public void resetToInitial() {
        // 重置棋盘数据
        chessBoard = new int[][]{
                {-5, -4, -3, -2, -1, -2, -3, -4, -5},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, -6, 0, 0, 0, 0, 0, -6, 0},
                {-7, 0, -7, 0, -7, 0, -7, 0, -7},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {7, 0, 7, 0, 7, 0, 7, 0, 7},
                {0, 6, 0, 0, 0, 0, 0, 6, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {5, 4, 3, 2, 1, 2, 3, 4, 5},
        };

        // 默认走棋方红方先走
        side = 1;

        // 清除选中状态与高亮
        isSelect = false;
        selectChess[0] = -1;
        selectChess[1] = -1;
        fromChess[0] = -1;
        fromChess[1] = -1;
        toChess[0] = -1;
        toChess[1] = -1;
        redHighlight = null;
        blackHighlight = null;

        // 清空历史记录
        clearHistory();
        lastMoveWasCapture = false;
        lastMoveWasCheck = false;

        // 刷新画面
        invalidate();
    }
    /**
     * 根据FEN字符串设置棋盘
     * @param fen 中国象棋FEN字符串
     */
    public void setBoardByFEN(String fen) {
        setBoardByFEN(fen, false);
    }

    public void setBoardByFEN(String fen, boolean flipped) {
        if (fen == null || fen.isEmpty()) {
            resetBoard();
            return;
        }

        try {
            // 先清空棋盘
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    chessBoard[y][x] = 0;
                }
            }

            // 解析FEN
            String[] parts = fen.split(" ");
            String boardPart = parts[0];

            String[] rows = boardPart.split("/");
            for (int y = 0; y < 10 && y < rows.length; y++) {
                String row = rows[y];
                int x = 0;
                for (int i = 0; i < row.length() && x < 9; i++) {
                    char c = row.charAt(i);
                    if (Character.isDigit(c)) {
                        int emptyCount = Character.getNumericValue(c);
                        x += emptyCount;
                    } else {
                        int chessValue = getChessValueFromFENChar(c);
                        if (chessValue != 0) {
                            chessBoard[y][x] = chessValue;
                        }
                        x++;
                    }
                }
            }

            // 解析走棋方
            if (parts.length > 1) {
                String sideStr = parts[1];
                if (sideStr.equals("b")) {
                    side = -1;
                } else {
                    side = 1;
                }
            }

            // ===== 设置翻转状态 =====
            setBoardFlipped(flipped);
            // =========================

            isSelect = false;
            selectChess[0] = -1;
            selectChess[1] = -1;
            fromChess[0] = -1;
            fromChess[1] = -1;
            toChess[0] = -1;
            toChess[1] = -1;
            redHighlight = null;
            blackHighlight = null;

            clearHistory();
            setCurrentInitialFEN(fen);
            invalidate();

            Log.d(TAG, "setBoardByFEN: " + fen + ", flipped=" + flipped);

        } catch (Exception e) {
            Log.e(TAG, "解析FEN失败: " + e.getMessage());
            resetBoard();
        }
    }


    /**
     * 获取当前走棋方
     * @return 1=红方，-1=黑方
     */
    public int getCurrentSide() {
        return side;
    }
    /**
     * 从FEN字符串获取棋子值
     * @param c FEN字符
     * @return 棋子值
     */
    private int getChessValueFromFENChar(char c) {
        switch (c) {
            // 红方（大写）
            case 'K': return 1;   // 帅
            case 'A': return 2;   // 仕
            case 'B': return 3;   // 相
            case 'N': return 4;   // 马
            case 'R': return 5;   // 车
            case 'C': return 6;   // 炮
            case 'P': return 7;   // 兵
            // 黑方（小写）
            case 'k': return -1;  // 将
            case 'a': return -2;  // 士
            case 'b': return -3;  // 象
            case 'n': return -4;  // 马
            case 'r': return -5;  // 车
            case 'c': return -6;  // 炮
            case 'p': return -7;  // 卒
            default: return 0;
        }
    }

    /**
     * 获取当前棋盘视图为Bitmap（直接使用View的绘制）
     */
    public Bitmap captureChessView(int width, int height, String fen, boolean flipped) {
        try {
            // 保存当前状态
            int[][] oldBoard = chessBoard;
            int oldSide = side;
            boolean oldFlipped = isBoardFlipped;
            int oldChessWidth = this.chessWidth;
            int oldOffsetX = this.offsetX;
            int oldOffsetY = this.offsetY;

            // 设置新棋盘
            int[][] board = parseFENToBoard(fen);
            chessBoard = board;
            side = 1;
            isBoardFlipped = flipped;

            // 按当前方案设计基准计算缩略图布局，与主界面一致
            LayoutHolder holder = new LayoutHolder();
            computeBoardLayout(width, height, holder);
            int chessW = holder.chessWidth;
            int offX = holder.offsetX;
            int offY = holder.offsetY;

            // 设置尺寸
            this.chessWidth = chessW;
            this.offsetX = offX;
            this.offsetY = offY;

            // 创建Bitmap并绘制
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // ===== 绘制棋盘背景 =====
            // 1. 先绘制背景色
            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.TRANSPARENT);  // ← 这里改成透明
            canvas.drawRect(0, 0, width, height, bgPaint);

            // 2. 绘制棋盘背景图（按当前方案设计基准外接矩形）
            if (boardBitmap != null) {
                float scale = chessW / activeScheme.cell;
                int mx = Math.round(activeScheme.marginX * scale);
                int my = Math.round(activeScheme.marginY * scale);
                int left = offX - mx;
                int top = offY - my;
                int right = offX + chessW * 8 + mx;
                int bottom = offY + chessW * 9 + my;
                Rect srcRect = new Rect(0, 0, boardBitmap.getWidth(), boardBitmap.getHeight());
                Rect dstRect = new Rect(left, top, right, bottom);
                canvas.drawBitmap(boardBitmap, srcRect, dstRect, null);
            }

            // 绘制棋子
            drawChessPiecesForThumbnail(canvas, board, chessW, offX, offY, flipped);

            // 恢复状态
            chessBoard = oldBoard;
            side = oldSide;
            isBoardFlipped = oldFlipped;
            this.chessWidth = oldChessWidth;
            this.offsetX = oldOffsetX;
            this.offsetY = oldOffsetY;

            return bitmap;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 为缩略图绘制棋子
     */
    private void drawChessPiecesForThumbnail(Canvas canvas, int[][] board, int chessW, int offsetX, int offsetY, boolean flipped) {
        float pieceScale = 0.85f;
        int pieceSize = (int) (chessW * pieceScale);
        if (pieceSize < 1) pieceSize = 1;

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int chess = board[y][x];
                if (chess == 0) continue;

                // getChessBitmap 会返回已经加载的棋子图片
                // 加载逻辑已经在 loadChessImages 中实现
                // 如果从 uistyle 加载，没有则使用 drawable
                Bitmap bitmap = getChessBitmap(chess);
                if (bitmap != null) {
                    int drawX = flipped ? 8 - x : x;
                    int drawY = flipped ? 9 - y : y;

                    int centerX = offsetX + drawX * chessW;
                    int centerY = offsetY + drawY * chessW;
                    int left = centerX - pieceSize / 2;
                    int top = centerY - pieceSize / 2;

                    Rect rect = new Rect(left, top, left + pieceSize, top + pieceSize);
                    canvas.drawBitmap(bitmap, null, rect, new Paint());
                }
            }
        }
    }
    /**
     * 从FEN解析棋盘
     */
    private int[][] parseFENToBoard(String fen) {
        int[][] board = new int[10][9];
        try {
            String[] parts = fen.split(" ");
            String boardPart = parts[0];
            String[] rows = boardPart.split("/");
            for (int y = 0; y < 10 && y < rows.length; y++) {
                String row = rows[y];
                int x = 0;
                for (int i = 0; i < row.length() && x < 9; i++) {
                    char c = row.charAt(i);
                    if (Character.isDigit(c)) {
                        x += Character.getNumericValue(c);
                    } else {
                        board[y][x] = getChessValueFromFENChar(c);
                        x++;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return board;
    }

    // ==================== Pikafish 相关方法 ====================

    /**
     * 上一手是否吃子
     */
    public boolean isLastMoveCapture() {
        return lastMoveWasCapture;
    }

    /**
     * 上一手是否将军
     */
    public boolean isLastMoveCheck() {
        return lastMoveWasCheck;
    }

    /**
     * 上一手是否构成"捉"（威胁对方无根子）。
     * 捉的定义：本步走子后，走棋方对某个对方"无根子"新建立了吃子威胁。
     *  —— 将帅不算（攻击将帅是将军）；
     *  —— 未过河的兵卒不算"子"（被威胁也不构成捉，长捉未过河兵卒不违例）；
     *  —— 必须是本步"新建立"的威胁，移动前就已存在的旧威胁（持续威胁）不算捉；
     *  —— 等价交换（兑）天然被排除：能被吃回的目标是有根子，本方法只针对无根子，
     *    无根子被吃后无法吃回，故对无根子的任何新威胁都是真捉。
     */
    public boolean isLastMoveChase() {
        if (moveHistory.isEmpty()) return false;
        if (lastMoveWasCapture) return false;  // 吃子着法会清空循环历史，不参与捉的判定
        int[] lastMove = moveHistory.get(moveHistory.size() - 1);
        int fromY = lastMove[0], fromX = lastMove[1];
        int toY = lastMove[2], toX = lastMove[3];
        int movingPiece = chessBoard[toY][toX];
        if (movingPiece == 0) return false;
        int movingSide = (movingPiece > 0) ? 1 : -1;
        int enemySide = -movingSide;

        // 遍历对方所有棋子，检查是否有"无根子"被走棋方新建立威胁
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int target = chessBoard[y][x];
                if (target * enemySide <= 0) continue;  // 不是对方棋子
                int absTarget = Math.abs(target);
                if (absTarget == 1) continue;  // 将帅被攻击是将军，不算捉
                // 未过河的兵卒不算"子"，被威胁也不构成捉
                if (isPawnNotCrossed(y, target)) continue;

                // 检查该子是否被走棋方攻击
                if (!isPieceAttackedBy(y, x, movingSide)) continue;
                // 检查该子是否无根（有根子的威胁属兑/保护，不算捉）
                if (isPieceRooted(y, x)) continue;
                // 必须是本步"新建立"的威胁；移动前就存在的旧威胁不算捉
                if (wasAlreadyThreatenedBefore(y, x, movingSide, fromY, fromX)) continue;

                return true;
            }
        }
        return false;
    }

    /**
     * 未过河兵卒判定：未过河的兵/卒本身不算"子"（长捉未过河兵卒不违例）。
     * 棋盘 y=0 为黑方底线、y=9 为红方底线，河界在第4、5行之间。
     * 红兵(值7)未过河 = y <= 4（仍在红方半场）；黑卒(值-7)未过河 = y >= 5（仍在黑方半场）。
     */
    private boolean isPawnNotCrossed(int pieceY, int piece) {
        if (Math.abs(piece) != 7) return false;  // 非兵卒
        if (piece > 0) {  // 红兵
            return pieceY <= 4;
        } else {          // 黑卒
            return pieceY >= 5;
        }
    }

    /**
     * 判断 (targetY, targetX) 上的对方子是否在"本步之前"就已被 attackerSide 威胁
     * （即该威胁并非本步新建立的，属于持续存在的旧威胁，不算捉）。
     * 做法：把本步走动的子从落点撤回到起点，还原"本步前"的棋盘，看那时该子是否已被攻击，
     * 然后恢复棋盘到本步后状态。
     */
    private boolean wasAlreadyThreatenedBefore(int targetY, int targetX,
                                               int attackerSide, int fromY, int fromX) {
        int movingPiece = chessBoard[targetY][targetX];  // 本步走动的子
        if (movingPiece == 0) return false;
        // 还原本步前：走动的子放回起点
        chessBoard[targetY][targetX] = 0;
        chessBoard[fromY][fromX] = movingPiece;
        boolean already = isPieceAttackedBy(targetY, targetX, attackerSide);
        // 恢复棋盘到本步后状态
        chessBoard[fromY][fromX] = 0;
        chessBoard[targetY][targetX] = movingPiece;
        return already;
    }

    /**
     * 检查 (targetY, targetX) 上的棋子是否被 attackerSide 方的任何棋子攻击。
     * 注意：本方法直接基于当前棋盘读取走法，炮的攻击/吃子路径判断会复用
     * canMoveDirect（含"恰好隔一子可吃"的炮规），因此炮"隔子打"会被正确识别为攻击。
     */
    public boolean isPieceAttackedBy(int targetY, int targetX, int attackerSide) {
        int target = chessBoard[targetY][targetX];
        if (target == 0) return false;

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int piece = chessBoard[y][x];
                if (piece * attackerSide <= 0) continue;  // 不是攻击方棋子
                if (canMoveDirect(y, x, targetY, targetX)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查 (pieceY, pieceX) 上的棋子是否有根（被己方棋子保护）
     * 有根 = 如果该子被吃，己方有棋子可以立即吃回
     */
    public boolean isPieceRooted(int pieceY, int pieceX) {
        int piece = chessBoard[pieceY][pieceX];
        if (piece == 0) return false;
        int absPiece = Math.abs(piece);
        if (absPiece == 1) return false;  // 将帅不算有根（将帅不能被保护）

        int ownSide = (piece > 0) ? 1 : -1;

        // 模拟移除该子，检查己方其他棋子是否能攻击到该位置
        chessBoard[pieceY][pieceX] = 0;
        boolean rooted = false;

        for (int y = 0; y < 10 && !rooted; y++) {
            for (int x = 0; x < 9 && !rooted; x++) {
                int other = chessBoard[y][x];
                if (other * ownSide <= 0) continue;  // 不是己方棋子
                if (y == pieceY && x == pieceX) continue;  // 跳过自己（已移除）
                if (canMoveDirect(y, x, pieceY, pieceX)) {
                    rooted = true;
                }
            }
        }

        // 恢复棋盘
        chessBoard[pieceY][pieceX] = piece;
        return rooted;
    }

    /**
     * 设置/获取触摸启用（AI思考时禁用）
     */
    public void setTouchEnabled(boolean enabled) {
        this.touchEnabled = enabled;
        if (!enabled) {
            // 进入AI思考时，清除人类方"选中待走"状态（保留高亮由AI走棋后处理）
            isSelect = false;
            selectChess[0] = -1;
            selectChess[1] = -1;
            invalidate();
        }
    }

    /**
     * 判断指定方是否被将军
     * @param targetSide 1=红方, -1=黑方
     */
    public boolean isInCheck(int targetSide) {
        int kingValue = (targetSide == 1) ? 1 : -1;
        int enemySide = -targetSide;

        // 找到将帅位置
        int kingY = -1, kingX = -1;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (chessBoard[y][x] == kingValue) {
                    kingY = y;
                    kingX = x;
                    break;
                }
            }
            if (kingY != -1) break;
        }
        if (kingY == -1) return false;

        // 检查对方棋子是否能吃到本方帅/将
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (chessBoard[y][x] * enemySide > 0) {
                    if (canMoveDirect(y, x, kingY, kingX)) {
                        return true;
                    }
                }
            }
        }

        // 检查双将照面
        if (isKingsFacing()) return true;

        return false;
    }

    /**
     * 执行一步棋（UCI 格式走法，由 Pikafish 引擎返回）
     * UCI 格式: "e2e4" 即 文件字母(a-i) + 数字(0-9) × 2
     * 内部存储: chessBoard[row][col], row 0=上 col 0=左
     * UCI转换: col = file - 'a', row = 9 - rank
     *
     * @param uciMove 比如 "e2e3"
     * @return 是否成功执行
     */
    public boolean executeUCIMove(String uciMove) {
        if (uciMove == null || uciMove.length() < 4) return false;

        try {
            // 解析 UCI 走法
            int fromX = uciMove.charAt(0) - 'a';
            int fromY = 9 - (uciMove.charAt(1) - '0');
            int toX = uciMove.charAt(2) - 'a';
            int toY = 9 - (uciMove.charAt(3) - '0');

            // 边界检查
            if (fromX < 0 || fromX > 8 || fromY < 0 || fromY > 9 ||
                    toX < 0 || toX > 8 || toY < 0 || toY > 9) {
                Log.e(TAG, "UCI走法越界错误: " + uciMove);
                return false;
            }

            // 检查是否有当前走棋方的棋子
            if (chessBoard[fromY][fromX] * side <= 0) {
                Log.e(TAG, "UCI走法错误，无棋子可走: " + uciMove +
                        ", from=(" + fromY + "," + fromX + ")=" + chessBoard[fromY][fromX] +
                        ", side=" + side);
                return false;
            }

            // 执行走子
            int capturedPiece = chessBoard[toY][toX];
            lastMoveWasCapture = (capturedPiece != 0);

            playMoveSound();  // AI走子音效

            chessBoard[toY][toX] = chessBoard[fromY][fromX];
            chessBoard[fromY][fromX] = 0;

            // 记录历史
            recordMove(fromY, fromX, toY, toX, capturedPiece);

            // AI选中并落子：设置AI方高亮到落子位置，并清除人类方高亮
            setHighlight(side, toY, toX);

            // 切换走棋方
            side = -side;

            // 检查是否将军
            lastMoveWasCheck = isInCheck(side);

            // 回调
            if (turnChangeListener != null) {
                turnChangeListener.onTurnChange(side);
            }
            if (gameListener != null) {
                gameListener.onMove(fromY, fromX, toY, toX, chessBoard[toY][toX]);
            }

            // 检查是否将杀/困毙
            checkGameOver();

            // 刷新画面
            invalidate();

            Log.d(TAG, "UCI走法执行成功: " + uciMove +
                    ", captured=" + lastMoveWasCapture +
                    ", check=" + lastMoveWasCheck);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "执行UCI走法异常: " + uciMove, e);
            return false;
        }
    }

    /**
     * 获取当前局面 FEN（在末尾标注翻转状态，供外部判断是否翻转）
     * @return FEN 字符串
     */
    public String getFENWithFlipFlag() {
        String fen = getFEN();
        if (isBoardFlipped) {
            return "2#" + fen;
        }
        return fen;
    }

    /**
     * 获取走棋历史走法（UCI格式）数组
     */
    public String[] getUCIHistory() {
        String[] uci = new String[moveHistory.size()];
        for (int i = 0; i < moveHistory.size(); i++) {
            int[] m = moveHistory.get(i);
            uci[i] = "" + (char)('a' + m[1]) + (9 - m[0]) + (char)('a' + m[3]) + (9 - m[2]);
        }
        return uci;
    }
    private void playMoveSound() {
        try {
            File externalDir = Environment.getExternalStorageDirectory();
            File uiStyleDir = new File(new File(externalDir, "canju12"), "uistyle");
            File externalFile = new File(uiStyleDir, "move.mp3");

            MediaPlayer mp;
            if (externalFile.exists()) {
                mp = new MediaPlayer();
                mp.setDataSource(externalFile.getAbsolutePath());
                mp.prepare();
            } else {
                mp = MediaPlayer.create(context, R.raw.move);
            }
            mp.setOnCompletionListener(mp0 -> {
                mp0.release();
            });
            mp.start();
        } catch (Exception e) {
            Log.w(TAG, "playMoveSound error: " + e.getMessage());
        }
    }
}
