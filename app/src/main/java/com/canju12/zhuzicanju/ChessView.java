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
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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

    /** 返回当前棋盘的宽高比 (designW / designH)，用于横屏布局计算棋盘宽度 */
    public float getBoardAspect() {
        return activeScheme.aspect();
    }

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
    private int[] lastMoveFrom = null;
    private int[] lastMoveTo = null;
    private float highlightPhase = 0f;
    private long highlightLastFrameMs = 0L;
    private static final float HIGHLIGHT_ROTATIONS_PER_SECOND = 1f / 8f;
    private static final long HIGHLIGHT_FRAME_DELAY_MS = 40L;
    private final Handler highlightHandler = new Handler(Looper.getMainLooper());
    private boolean highlightAnimatorRunning = false;
    private final Rect highlightDirtyRect = new Rect();
    private final Runnable highlightAnimator = new Runnable() {
        @Override
        public void run() {
            if (!hasAnimatedHighlight()) {
                highlightAnimatorRunning = false;
                highlightLastFrameMs = 0L;
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (highlightLastFrameMs == 0L) {
                highlightLastFrameMs = now;
            }
            float elapsedSeconds = Math.min(0.1f,
                    (now - highlightLastFrameMs) / 1000f);
            highlightLastFrameMs = now;
            highlightPhase = (highlightPhase
                    + elapsedSeconds * HIGHLIGHT_ROTATIONS_PER_SECOND) % 1f;
            invalidateHighlightRegion();
            highlightHandler.postDelayed(this, HIGHLIGHT_FRAME_DELAY_MS);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (moveSoundEnabled) {
            preloadMoveSound();
        }
        updateHighlightAnimatorState();
    }

    @Override
    protected void onDetachedFromWindow() {
        highlightHandler.removeCallbacks(highlightAnimator);
        highlightAnimatorRunning = false;
        highlightLastFrameMs = 0L;
        releaseMoveSound();
        super.onDetachedFromWindow();
    }

    private boolean hasAnimatedHighlight() {
        return redHighlight != null || blackHighlight != null || lastMoveTo != null;
    }

    private void updateHighlightAnimatorState() {
        if (!hasAnimatedHighlight()) {
            highlightHandler.removeCallbacks(highlightAnimator);
            highlightAnimatorRunning = false;
            highlightLastFrameMs = 0L;
            return;
        }
        if (!isAttachedToWindow() || highlightAnimatorRunning) {
            return;
        }
        highlightAnimatorRunning = true;
        highlightLastFrameMs = SystemClock.uptimeMillis();
        highlightHandler.post(highlightAnimator);
    }

    private void invalidateHighlightRegion() {
        if (chessWidth <= 0 || getWidth() <= 0 || getHeight() <= 0) {
            postInvalidateOnAnimation();
            return;
        }

        highlightDirtyRect.setEmpty();
        unionHighlightDirty(highlightDirtyRect, redHighlight);
        unionHighlightDirty(highlightDirtyRect, blackHighlight);
        unionHighlightDirty(highlightDirtyRect, lastMoveFrom);
        unionHighlightDirty(highlightDirtyRect, lastMoveTo);

        if (highlightDirtyRect.isEmpty()) {
            postInvalidateOnAnimation();
            return;
        }

        highlightDirtyRect.left = Math.max(0, highlightDirtyRect.left);
        highlightDirtyRect.top = Math.max(0, highlightDirtyRect.top);
        highlightDirtyRect.right = Math.min(getWidth(), highlightDirtyRect.right);
        highlightDirtyRect.bottom = Math.min(getHeight(), highlightDirtyRect.bottom);
        postInvalidateOnAnimation(highlightDirtyRect.left, highlightDirtyRect.top,
                highlightDirtyRect.right, highlightDirtyRect.bottom);
    }

    private void unionHighlightDirty(Rect dirty, int[] pos) {
        if (pos == null || pos[0] < 0 || pos[1] < 0) return;
        int drawX = isBoardFlipped ? 8 - pos[1] : pos[1];
        int drawY = isBoardFlipped ? 9 - pos[0] : pos[0];
        int centerX = offsetX + drawX * chessWidth;
        int centerY = offsetY + drawY * chessWidth;
        int radius = Math.max(2, (int) Math.ceil(chessWidth * 0.68f));
        dirty.union(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);
    }
    // AI对战时人类所执的一方（1红/-1黑）；-2表示未限定（自由练习/分析模式，按当前走棋方限制）
    private int humanSide = -2;
    // =====================================================================

    private Paint paint = new Paint();           // 普通画笔
    private Paint paintMove = new Paint();       // move画笔
    private Paint paintFrom = new Paint();       // 选中棋子画圆画笔
    private Paint paintArrow = new Paint();      // 绘制箭头的画笔
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moveOriginRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moveOriginDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect boardSrcRect = new Rect();
    private final Rect boardDstRect = new Rect();
    private final Rect pieceDstRect = new Rect();
    private final RectF auraArcRect = new RectF();

    private SoundPool moveSoundPool;
    private int moveSoundId = 0;
    private boolean moveSoundLoaded = false;
    private boolean moveSoundLoadStarted = false;
    private boolean moveSoundEnabled = false;
    private boolean moveSoundUsingExternal = false;
    private boolean moveSoundFallbackStarted = false;
    private long lastMoveSoundPlayMs = 0L;

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
    private int auraRedColor = Color.rgb(198, 40, 40); // #c62828
    private int auraBlackColor = Color.rgb(10, 10, 10); // #0a0a0a
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
        lastMoveFrom = null;
        lastMoveTo = null;
        invalidate();
        updateHighlightAnimatorState();
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
            // ========== 红方棋子 ==========
            redRook = UiAssetCache.getBitmap(context, "rr.png", R.drawable.rr);
            redKnight = UiAssetCache.getBitmap(context, "rn.png", R.drawable.rn);
            redCannon = UiAssetCache.getBitmap(context, "rc.png", R.drawable.rc);
            redPawn = UiAssetCache.getBitmap(context, "rp.png", R.drawable.rp);
            redKing = UiAssetCache.getBitmap(context, "rk.png", R.drawable.rk);
            redAdvisor = UiAssetCache.getBitmap(context, "ra.png", R.drawable.ra);
            redElephant = UiAssetCache.getBitmap(context, "rb.png", R.drawable.rb);

            // ========== 黑方棋子 ==========
            blackRook = UiAssetCache.getBitmap(context, "br.png", R.drawable.br);
            blackKnight = UiAssetCache.getBitmap(context, "bn.png", R.drawable.bn);
            blackCannon = UiAssetCache.getBitmap(context, "bc.png", R.drawable.bc);
            blackPawn = UiAssetCache.getBitmap(context, "bp.png", R.drawable.bp);
            blackKing = UiAssetCache.getBitmap(context, "bk.png", R.drawable.bk);
            blackAdvisor = UiAssetCache.getBitmap(context, "ba.png", R.drawable.ba);
            blackElephant = UiAssetCache.getBitmap(context, "bb.png", R.drawable.bb);

            // ========== 棋盘背景 ==========
            boardBitmap = UiAssetCache.getBoard(context);
            roomBitmap = UiAssetCache.getRoom(context);
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
            boardSrcRect.set(0, 0, boardBitmap.getWidth(), boardBitmap.getHeight());
            boardDstRect.set(left, top, right, bottom);
            canvas.drawBitmap(boardBitmap, boardSrcRect, boardDstRect, null);
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

                    pieceDstRect.set(left, top, right, bottom);
                    try {
                        canvas.drawBitmap(bitmap, null, pieceDstRect, paint);
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
        drawMoveOrigin(canvas, lastMoveFrom);
        // 同一时刻只保留一个棋子光圈：选中棋子优先，否则显示最后落子位置。
        if (redHighlight != null) {
            drawParticleAuraAt(canvas, redHighlight, false);
        } else if (blackHighlight != null) {
            drawParticleAuraAt(canvas, blackHighlight, false);
        } else {
            drawParticleAuraAt(canvas, lastMoveTo, true);
        }
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

        drawParticleAura(canvas, centerX, centerY, pieceSize, false);
    }

    private void drawParticleAuraAt(Canvas canvas, int[] pos, boolean strong) {
        if (pos == null || pos[0] < 0 || pos[1] < 0) return;
        int drawX = isBoardFlipped ? 8 - pos[1] : pos[1];
        int drawY = isBoardFlipped ? 9 - pos[0] : pos[0];
        float centerX = offsetX + drawX * chessWidth;
        float centerY = offsetY + drawY * chessWidth;
        int piece = chessBoard[pos[0]][pos[1]];
        float pieceScale = 0.98f;
        float pieceSize = chessWidth * pieceScale;
        if (piece == 0) {
            pieceSize = chessWidth * 0.82f;
        }
        drawParticleAura(canvas, centerX, centerY, pieceSize, strong);
    }

    private void drawParticleAura(Canvas canvas, float centerX, float centerY,
                                  float pieceSize, boolean strong) {
        float pulse = 1f + 0.018f * (float) Math.sin(highlightPhase * Math.PI * 2);
        // Keep the effect just outside the piece edge so it does not spill into
        // neighboring intersections or get clipped at the board boundary.
        // The stroke's inner edge should meet the outside edge of the piece.
        float strokeReference = Math.max(1.6f, chessWidth * (strong ? 0.018f : 0.015f));
        float radius = Math.max(1f, pieceSize * 0.5f - strokeReference * 0.5f);

        float fullCircle = (float) (Math.PI * 2.0);
        float phaseAngle = highlightPhase * fullCircle;
        float halfArc = (float) Math.PI - 0.08f;
        float tailArc = (float) Math.PI * 0.82f;
        float baseStroke = strokeReference;
        float headStroke = Math.max(baseStroke * 2.6f, chessWidth * (strong ? 0.048f : 0.041f));

        // The two opposing heads divide the ring into a black and a white
        // half. Each head leaves a narrowing tail, producing the yin-yang
        // style rotating light instead of two isolated dots.
        float auraBaseStroke = baseStroke * 1.5f;
        float auraHeadStroke = headStroke * 1.5f;
        drawAuraTail(canvas, centerX, centerY, radius, phaseAngle,
                halfArc, tailArc, auraBlackColor, auraBaseStroke, auraHeadStroke,
                strong ? 255 : 225);
        drawAuraTail(canvas, centerX, centerY, radius, phaseAngle + (float) Math.PI,
                halfArc, tailArc, auraRedColor, auraBaseStroke,
                auraHeadStroke, strong ? 255 : 225);

    }

    private void drawAuraTail(Canvas canvas, float centerX, float centerY,
                              float radius, float headAngle, float halfArc,
                              float tailArc, int color, float baseStroke,
                              float headStroke, int alpha) {
        auraPaint.setStyle(Paint.Style.STROKE);
        auraPaint.setStrokeCap(Paint.Cap.ROUND);
        auraArcRect.set(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);

        int segments = 18;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            float start = (float) Math.toDegrees(headAngle - t1 * tailArc);
            float sweep = (float) Math.toDegrees((t1 - t0) * tailArc);
            float width = baseStroke + (headStroke - baseStroke) * (1f - t0) * (1f - t0);
            int segmentAlpha = Math.max(20, (int) (alpha * (0.35f + 0.65f * (1f - t0))));
            auraPaint.setStrokeWidth(width);
            auraPaint.setColor(Color.argb(segmentAlpha,
                    Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawArc(auraArcRect, start, sweep, false, auraPaint);
        }

        auraPaint.setStrokeWidth(headStroke);
        auraPaint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawArc(auraArcRect,
                (float) (Math.toDegrees(headAngle) - Math.toDegrees(halfArc * 0.12f)),
                (float) Math.toDegrees(halfArc * 0.24f), false, auraPaint);
    }

    private void drawMoveOrigin(Canvas canvas, int[] pos) {
        if (pos == null || pos[0] < 0 || pos[1] < 0) return;
        int drawX = isBoardFlipped ? 8 - pos[1] : pos[1];
        int drawY = isBoardFlipped ? 9 - pos[0] : pos[0];
        float centerX = offsetX + drawX * chessWidth;
        float centerY = offsetY + drawY * chessWidth;
        moveOriginRingPaint.setStyle(Paint.Style.STROKE);
        moveOriginRingPaint.setStrokeWidth(Math.max(1.5f, chessWidth * 0.012f));
        moveOriginRingPaint.setColor(Color.argb(220, 255, 255, 255));
        moveOriginRingPaint.setShadowLayer(chessWidth * 0.025f, 0f, 0f, Color.argb(150, 255, 255, 255));
        canvas.drawCircle(centerX, centerY, chessWidth * 0.115f, moveOriginRingPaint);
        moveOriginRingPaint.clearShadowLayer();
        moveOriginDotPaint.setStyle(Paint.Style.FILL);
        moveOriginDotPaint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, Math.max(2f, chessWidth * 0.045f), moveOriginDotPaint);
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
        updateHighlightAnimatorState();
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

                        chessBoard[toChess[0]][toChess[1]] = chessBoard[fromChess[0]][fromChess[1]];
                        chessBoard[fromChess[0]][fromChess[1]] = 0;

                        playMoveSound();  // 只在棋子实际落下后播放音效

                        recordMove(fromChess[0], fromChess[1], toChess[0], toChess[1], capturedPiece);

                        // 落子后保留起点和终点标记
                        lastMoveFrom = new int[]{fromChess[0], fromChess[1]};
                        lastMoveTo = new int[]{toChess[0], toChess[1]};
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
        // 选中新的棋子后，清除上一次落子的光圈，避免两个光圈同时出现。
        lastMoveTo = null;
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
        updateHighlightAnimatorState();
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

        // 悔棋后恢复撤销后剩余的最后一手光影；没有历史时清除光影。
        restoreLastMoveHighlight();

        // 刷新画面
        invalidate();
        updateHighlightAnimatorState();

        return true;
    }

    /**
     * 按指定走法恢复起点标记和终点光圈，供拆解页面浏览棋谱使用。
     */
    public void restoreMoveHighlight(int fromY, int fromX, int toY, int toX) {
        if (fromY < 0 || fromY > 9 || fromX < 0 || fromX > 8
                || toY < 0 || toY > 9 || toX < 0 || toX > 8) {
            clearSelection();
            return;
        }
        isSelect = false;
        selectChess[0] = -1;
        selectChess[1] = -1;
        fromChess[0] = -1;
        fromChess[1] = -1;
        toChess[0] = -1;
        toChess[1] = -1;
        redHighlight = null;
        blackHighlight = null;
        lastMoveFrom = new int[]{fromY, fromX};
        lastMoveTo = new int[]{toY, toX};

        int movedPiece = chessBoard[toY][toX];
        if (movedPiece > 0) {
            redHighlight = new int[]{toY, toX};
        } else if (movedPiece < 0) {
            blackHighlight = new int[]{toY, toX};
        }
        invalidate();
        updateHighlightAnimatorState();
    }

    /** 恢复当前内部走法历史中最后一手的光影。 */
    public void restoreLastMoveHighlight() {
        if (moveHistory.isEmpty()) {
            clearSelection();
            return;
        }
        int[] move = moveHistory.get(moveHistory.size() - 1);
        restoreMoveHighlight(move[0], move[1], move[2], move[3]);
    }

    /** 从 UCI 走法恢复指定步骤的光影。 */
    public void restoreMoveHighlightFromUci(String uci) {
        if (uci == null || uci.length() < 4) {
            clearSelection();
            return;
        }
        int fromX = uci.charAt(0) - 'a';
        int fromY = 9 - (uci.charAt(1) - '0');
        int toX = uci.charAt(2) - 'a';
        int toY = 9 - (uci.charAt(3) - '0');
        restoreMoveHighlight(fromY, fromX, toY, toX);
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
        lastMoveFrom = null;
        lastMoveTo = null;
        clearHistory();  // 清空历史
        invalidate();
        updateHighlightAnimatorState();
    }


    // 本地保存的当前题目初始FEN
    private String currentInitialFEN = null;  // 当前题目的初始FEN

    /**
     * 设置当前题目的初始FEN
     * @param fen 初始FEN字符串
     */
    public void setCurrentInitialFEN(String fen) {
        this.currentInitialFEN = normalizeFenString(fen);
        Log.d(TAG, "设置初始FEN: " + this.currentInitialFEN);
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
        lastMoveFrom = null;
        lastMoveTo = null;

        // 清空历史记录
        clearHistory();
        lastMoveWasCapture = false;
        lastMoveWasCheck = false;

        // 刷新画面
        invalidate();
        updateHighlightAnimatorState();
    }
    /**
     * 根据FEN字符串设置棋盘
     * @param fen 中国象棋FEN字符串
     */
    public boolean setBoardByFEN(String fen) {
        return setBoardByFEN(fen, false);
    }

    public boolean setBoardByFEN(String fen, boolean flipped) {
        if (fen == null || fen.isEmpty()) {
            Log.e(TAG, "FEN is empty");
            return false;
        }

        try {
            String normalizedFen = normalizeFenString(fen);
            int[][] strictBoard = parseStrictFENToBoard(normalizedFen);
            if (strictBoard == null) {
                Log.e(TAG, "Invalid FEN: " + fen);
                return false;
            }

            String[] parts = normalizedFen.split("\\s+");
            if (parts.length < 2 || (!"w".equals(parts[1]) && !"b".equals(parts[1]))) {
                Log.e(TAG, "Invalid FEN fields: " + fen);
                return false;
            }
            chessBoard = strictBoard;

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
            lastMoveFrom = null;
            lastMoveTo = null;

            clearHistory();
            setCurrentInitialFEN(normalizedFen);
            invalidate();
            updateHighlightAnimatorState();

            Log.d(TAG, "setBoardByFEN: " + normalizedFen + ", flipped=" + flipped);

        } catch (Exception e) {
            Log.e(TAG, "解析FEN失败: " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean setBoardByFENPreserveInitialFEN(String fen, boolean flipped) {
        String oldInitialFen = getCurrentInitialFEN();
        boolean loaded = setBoardByFEN(fen, flipped);
        setCurrentInitialFEN(oldInitialFen);
        return loaded;
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
     * 归一化外部传入的FEN/题库行：
     * 1. 去掉2#前缀；
     * 2. 只保留标准6段FEN，丢掉后面的标题文本。
     */
    private String normalizeFenString(String fen) {
        if (fen == null) return null;

        String trimmed = stripBom(fen).trim();
        if (trimmed.isEmpty()) return trimmed;

        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0) return trimmed;

        String first = tokens[0];
        if (first.startsWith("2#")) {
            first = first.substring(2);
        }

        StringBuilder sb = new StringBuilder(first);
        for (int i = 1; i < tokens.length && i < 6; i++) {
            sb.append(' ').append(tokens[i]);
        }
        return sb.toString();
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    /**
     * 获取当前棋盘视图为Bitmap（直接使用View的绘制）
     */
    public Bitmap captureChessView(int width, int height, String fen, boolean flipped) {
        int[][] oldBoard = chessBoard;
        int oldSide = side;
        boolean oldFlipped = isBoardFlipped;
        int oldChessWidth = this.chessWidth;
        int oldOffsetX = this.offsetX;
        int oldOffsetY = this.offsetY;
        try {
            // 保存当前状态
            // 设置新棋盘
            int[][] board = parseFENToBoard(fen);
            if (board == null) {
                return null;
            }
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
            return bitmap;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            chessBoard = oldBoard;
            side = oldSide;
            isBoardFlipped = oldFlipped;
            this.chessWidth = oldChessWidth;
            this.offsetX = oldOffsetX;
            this.offsetY = oldOffsetY;
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
    private int[][] parseStrictFENToBoard(String fen) {
        if (fen == null || fen.isEmpty()) return null;
        try {
            String[] parts = fen.trim().split("\\s+");
            if (parts.length < 2 || (!"w".equals(parts[1]) && !"b".equals(parts[1]))) {
                return null;
            }
            String[] rows = parts[0].split("/", -1);
            if (rows.length != 10) return null;

            int[][] board = new int[10][9];
            for (int y = 0; y < rows.length; y++) {
                int x = 0;
                String row = rows[y];
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (c >= '1' && c <= '9') {
                        x += c - '0';
                    } else {
                        int value = getChessValueFromFENChar(c);
                        if (value == 0 || x >= 9) return null;
                        board[y][x++] = value;
                    }
                    if (x > 9) return null;
                }
                if (x != 9) return null;
            }
            return board;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private int[][] parseFENToBoard(String fen) {
        return parseStrictFENToBoard(normalizeFenString(fen));
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
        return executeUCIMove(uciMove, 0);
    }

    /**
     * 执行引擎走法，并校验引擎只能走指定颜色。
     *
     * @param uciMove UCI 格式走法
     * @param expectedSide 期望的引擎方：1=红方，-1=黑方；0=仅按当前轮次校验
     * @return 是否成功执行
     */
    public boolean executeUCIMove(String uciMove, int expectedSide) {
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

            int movingPiece = chessBoard[fromY][fromX];
            int movingSide = movingPiece > 0 ? 1 : -1;

            // 引擎走法必须同时满足：轮到该方、起点是该方棋子。
            if (movingPiece == 0 || movingSide != side ||
                    (expectedSide != 0 && movingSide != expectedSide)) {
                Log.e(TAG, "UCI走法错误，无棋子可走: " + uciMove +
                        ", from=(" + fromY + "," + fromX + ")=" + movingPiece +
                        ", currentSide=" + side + ", expectedSide=" + expectedSide);
                return false;
            }

            // 执行走子
            int capturedPiece = chessBoard[toY][toX];
            lastMoveWasCapture = (capturedPiece != 0);

            chessBoard[toY][toX] = movingPiece;
            chessBoard[fromY][fromX] = 0;

            playMoveSound();  // 只在引擎棋子实际落下后播放音效

            // 记录历史
            recordMove(fromY, fromX, toY, toX, capturedPiece);

            // AI走子同样显示起点和终点光圈
            lastMoveFrom = new int[]{fromY, fromX};
            lastMoveTo = new int[]{toY, toX};
            setHighlight(movingSide, toY, toX);

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

    public void setMoveSoundEnabled(boolean enabled) {
        moveSoundEnabled = enabled;
        if (enabled) {
            preloadMoveSound();
        } else {
            releaseMoveSound();
        }
    }

    private void preloadMoveSound() {
        if (!moveSoundEnabled || moveSoundLoadStarted || context == null) return;
        moveSoundLoadStarted = true;
        moveSoundFallbackStarted = false;

        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            moveSoundPool = new SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(attributes)
                    .build();
            moveSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                if (sampleId != 0 && status == 0) {
                    moveSoundLoaded = true;
                } else {
                    Log.w(TAG, "move sound load failed, status=" + status);
                    if (moveSoundUsingExternal && !moveSoundFallbackStarted) {
                        moveSoundFallbackStarted = true;
                        reloadBuiltinMoveSound();
                    }
                }
            });

            File externalDir = Environment.getExternalStorageDirectory();
            File uiStyleDir = new File(new File(externalDir, "canju12"), "uistyle");
            File externalFile = new File(uiStyleDir, "move.mp3");
            if (externalFile.isFile()) {
                moveSoundId = moveSoundPool.load(externalFile.getAbsolutePath(), 1);
                moveSoundUsingExternal = moveSoundId != 0;
            } else {
                moveSoundId = moveSoundPool.load(context, R.raw.move, 1);
                moveSoundUsingExternal = false;
            }

            if (moveSoundId == 0) {
                releaseMoveSound();
            }
        } catch (Throwable t) {
            Log.w(TAG, "preloadMoveSound error: " + t.getMessage());
            releaseMoveSound();
        }
    }

    private void playMoveSound() {
        try {
            if (!moveSoundEnabled) return;
            preloadMoveSound();
            long now = SystemClock.uptimeMillis();
            if (now - lastMoveSoundPlayMs < 90L) return;
            if (moveSoundPool != null && moveSoundLoaded && moveSoundId != 0) {
                moveSoundPool.play(moveSoundId, 1f, 1f, 1, 0, 1f);
                lastMoveSoundPlayMs = now;
            }
        } catch (Throwable t) {
            Log.w(TAG, "playMoveSound error: " + t.getMessage());
        }
    }

    private void reloadBuiltinMoveSound() {
        releaseMoveSound();
        if (!moveSoundEnabled || context == null) return;
        moveSoundLoadStarted = true;
        moveSoundUsingExternal = false;
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            moveSoundPool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(attributes)
                    .build();
            moveSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                moveSoundLoaded = sampleId != 0 && status == 0;
                if (!moveSoundLoaded) {
                    Log.w(TAG, "builtin move sound load failed, status=" + status);
                }
            });
            moveSoundId = moveSoundPool.load(context, R.raw.move, 1);
            if (moveSoundId == 0) releaseMoveSound();
        } catch (Throwable t) {
            Log.w(TAG, "reloadBuiltinMoveSound error: " + t.getMessage());
            releaseMoveSound();
        }
    }

    private void releaseMoveSound() {
        if (moveSoundPool != null) {
            try {
                moveSoundPool.release();
            } catch (Throwable ignored) {
            }
        }
        moveSoundPool = null;
        moveSoundId = 0;
        moveSoundLoaded = false;
        moveSoundLoadStarted = false;
        moveSoundUsingExternal = false;
        moveSoundFallbackStarted = false;
        lastMoveSoundPlayMs = 0L;
    }
}
