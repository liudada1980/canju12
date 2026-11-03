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

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import android.graphics.Typeface;
import android.graphics.Typeface;

public class AnalyzeActivity extends AppCompatActivity {

    private static final String TAG = "AnalyzeActivity";
    private static final String CLOUD_DB_URL = "https://www.chessdb.cn/chessdb.php";

    private ChessView chessView;
    private final PositionValidator positionValidator = new PositionValidator();
    private TextView tvAnalyzeResult;
    private TextView tvCloudDb;
    private LinearLayout llMoveList;
    private ScrollView svMoveList;
    private LinearLayout analysisArea;
    private Button btnBack, btnUndo, btnNext, btnReset, btnAnalyze;
    private ImageView ivBoard, ivTurnIndicator;
    private androidx.constraintlayout.widget.ConstraintLayout mainLayout;

    private String initialFEN = null;
    private boolean isBoardFlipped = false;

    private Handler handler = new Handler();
    private Handler poetryHandler = new Handler();
    private volatile int cloudRequestId = 0;
    private int analysisPrimaryTextColor = Color.BLACK;
    private int analysisSecondaryTextColor = 0xFF333333;
    private int analysisPanelColor = 0xFFE8E8E8;

    // ===== Pikafish 引擎相关 =====
    private PikafishEngine pikafishEngine;
    private boolean isAnalyzing = false;       // 当前是否正在分析（用户点击了"分析"且未点"停止"）
    private boolean isEngineReady = false;     // 引擎是否已启动就绪
    private boolean isEngineStarting = false;  // 防止快速点击重复启动引擎
    private volatile int searchId = 0;         // 搜索ID，每次发新局面递增，用于区分过期结果
    private Thread readerThread = null;        // 持久读取线程（只创建一次）
    private final Object engineLock = new Object();  // 引擎发送命令的锁
    private volatile String analysisBaseFen = null;  // 当前分析所基于的FEN，用于招法解析
    // ==============================

    // 诗词相关
    private TextView tvShiju;
    private List<String> poetryList;
    private int minuteCounter = 0;
    private volatile boolean activityDestroyed = false;
    private final long MINUTE_DELAY = 60000;

    private boolean isActivityAlive() {
        return !activityDestroyed && !isFinishing();
    }

    // 招法历史
    private List<MoveRecord> moveHistory = new ArrayList<>();
    private int currentMoveIndex = -1;  // 当前显示到哪一步
    private boolean navigatingHistory = false;  // 是否正在浏览历史

    static class MoveRecord {
        String uci;           // UCI走法
        String chinese;       // 中文记谱
        String fen;           // 走完后的FEN
        int moveNumber;       // 第几步
        boolean isRed;        // 是否红方走

        MoveRecord(String uci, String chinese, String fen, int moveNumber, boolean isRed) {
            this.uci = uci; this.chinese = chinese; this.fen = fen;
            this.moveNumber = moveNumber; this.isRed = isRed;
        }
    }

    @Override
        protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyze);

        if (savedInstanceState != null) {
            initialFEN = savedInstanceState.getString("initialFEN");
            isBoardFlipped = savedInstanceState.getBoolean("isBoardFlipped", false);
            currentMoveIndex = savedInstanceState.getInt("currentMoveIndex", -1);
            moveHistory.clear();
            java.util.ArrayList<String> uciList = savedInstanceState.getStringArrayList("moveUciList");
            java.util.ArrayList<String> cnList = savedInstanceState.getStringArrayList("moveCnList");
            java.util.ArrayList<String> fenList = savedInstanceState.getStringArrayList("moveFenList");
            int[] moveNums = savedInstanceState.getIntArray("moveNums");
            boolean[] isReds = savedInstanceState.getBooleanArray("isReds");
            if (uciList != null && cnList != null && fenList != null
                    && moveNums != null && isReds != null
                    && uciList.size() == cnList.size()
                    && uciList.size() == fenList.size()
                    && uciList.size() == moveNums.length
                    && uciList.size() == isReds.length) {
                for (int i = 0; i < uciList.size(); i++) {
                    moveHistory.add(new MoveRecord(uciList.get(i), cnList.get(i),
                            fenList.get(i), moveNums[i], isReds[i]));
                }
            }
        } else {
            initialFEN = getIntent().getStringExtra("initialFEN");
            isBoardFlipped = getIntent().getBooleanExtra("flipped", false);
            if (initialFEN == null || initialFEN.isEmpty()) {
                initialFEN = getIntent().getStringExtra("fen");
            }
        }

        initViews();
        setupListeners();
        setupChessView();
        setupBackground();

        initPoetryLibrary();
        startPoetryTimer();
        showRandomPoetry();

        if (initialFEN != null && !initialFEN.isEmpty()) {
            PositionValidator.Result formatResult =
                    positionValidator.validateFenFormat(initialFEN);
            if (!formatResult.valid || !chessView.setBoardByFEN(initialFEN, isBoardFlipped)) {
                ToastUtil.makeText(this, "FEN格式错误，无法加载局面",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            PositionValidator.Result positionResult =
                    positionValidator.validate(chessView.chessBoard);
            if (!positionResult.valid) {
                ToastUtil.makeText(this, "局面不合法：" + positionResult.reason,
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            chessView.setCurrentInitialFEN(initialFEN);
        }
        if (currentMoveIndex >= 0 && currentMoveIndex < moveHistory.size()) {
            restoreHistoryPosition(currentMoveIndex);
        }
        updateTurnIndicator(chessView.getCurrentSide());

        // 横屏启动时程序化调整棋盘尺寸
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            chessView.post(new Runnable() {
                @Override
                public void run() {
                    adjustAnalyzeBoardForLandscape();
                }
            });
        }

        queryCloudDb(chessView.getFEN());
        updateMoveList();
    }

    private void initViews() {
        chessView = findViewById(R.id.chessView);
        tvAnalyzeResult = findViewById(R.id.tvAnalyzeResult);
        // 结果区域使用普通字重，标题的粗细由 HTML 标记单独控制
        tvAnalyzeResult.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        tvCloudDb = findViewById(R.id.tvCloudDb);
        // 云库招法和评分使用普通字重
        tvCloudDb.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        llMoveList = findViewById(R.id.moveListContent);
        svMoveList = findViewById(R.id.svMoveList);
        analysisArea = findViewById(R.id.analysisArea);
        btnBack = findViewById(R.id.btnBack);
        btnUndo = findViewById(R.id.btnUndo);
        btnNext = findViewById(R.id.btnNext);
        btnReset = findViewById(R.id.btnReset);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        ivBoard = findViewById(R.id.ivBoard);
        ivTurnIndicator = findViewById(R.id.ivTurnIndicator);
        tvShiju = findViewById(R.id.shiju);
        mainLayout = findViewById(R.id.mainLayout);

        refreshAnalyzeTheme(chessView != null ? chessView.getRoomBitmap() : null);
        tvAnalyzeResult.setText(toBoldCenterTitle(
                "<font color='" + colorToHex(analysisPrimaryTextColor) + "'><b>🐟🐠🐡皮卡鱼🦈🎏🐬🐳🐋</b></font><br/>" +
                "<br/>" +
                "🐟🐠🐡皮卡鱼为您加油！🦈🎏🐬🐳🐋", tvAnalyzeResult));
        setCloudDbText("云库分析");
    }

    private void updateTurnIndicator(int side) {
        UiThemeHelper.applyTurnIndicatorImage(this, ivTurnIndicator, side);
    }

    private void setupBackground() {
        try {
            Bitmap boardBitmap = chessView.getBoardBitmap();
            Bitmap roomBitmap = chessView.getRoomBitmap();
            if (roomBitmap != null) {
                mainLayout.setBackground(new BitmapDrawable(getResources(), roomBitmap));
            } else {
                mainLayout.setBackgroundResource(R.drawable.room);
            }
            if (boardBitmap != null) {
                ivBoard.setImageBitmap(boardBitmap);
            } else {
                ivBoard.setImageResource(R.drawable.board);
            }

            applyRoomButtonTheme(roomBitmap);
            refreshAnalyzeTheme(roomBitmap);
        } catch (Throwable e) {
            // 用 Throwable 兜底：内置 PNG 在某些硬件上解码/设置可能抛 OOM 等 Error
            e.printStackTrace();
            try {
                mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#E8D5A2"));
            } catch (Throwable ignored) { }
        }
    }

    private void applyRoomButtonTheme(Bitmap roomBitmap) {
        try {
            View content = findViewById(android.R.id.content);
            UiThemeHelper.applyRoomButtonTheme(this, content, roomBitmap);
        } catch (Throwable t) {
            Log.w(TAG, "????????: " + t.getMessage());
        }
    }

    private void refreshAnalyzeTheme(Bitmap roomBitmap) {
        UiThemeHelper.ThemePalette palette = UiThemeHelper.resolvePalette(this, roomBitmap);
        if (palette != null) {
            analysisPanelColor = palette.normalFillColor;
            analysisPrimaryTextColor = palette.textColor;
            analysisSecondaryTextColor = palette.textColor == Color.WHITE ? Color.WHITE : 0xFF333333;
        } else {
            analysisPanelColor = 0xFFE8E8E8;
            analysisPrimaryTextColor = Color.BLACK;
            analysisSecondaryTextColor = 0xFF333333;
        }
        applyAnalyzeAreaThemeColors();
    }

    private void applyAnalyzeAreaThemeColors() {
        if (analysisArea != null) {
            analysisArea.setBackgroundColor(analysisPanelColor);
        }
        if (tvCloudDb != null) {
            View parent = (View) tvCloudDb.getParent();
            if (parent != null) {
                parent.setBackgroundColor(analysisPanelColor);
            }
            tvCloudDb.setTextColor(analysisSecondaryTextColor);
        }
        if (tvAnalyzeResult != null) {
            View parent = (View) tvAnalyzeResult.getParent();
            if (parent != null) {
                parent.setBackgroundColor(analysisPanelColor);
            }
            tvAnalyzeResult.setTextColor(analysisSecondaryTextColor);
        }
        if (svMoveList != null) {
            svMoveList.setBackgroundColor(analysisPanelColor);
        }
        if (llMoveList != null) {
            llMoveList.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private String colorToHex(int color) {
        return String.format(java.util.Locale.US, "%06X", (0xFFFFFF & color));
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            finish();
        });

        btnUndo.setOnClickListener(v -> {
            if (currentMoveIndex >= 0 && currentMoveIndex < moveHistory.size()) {
                // 悔棋：回到上一步
                navigatingHistory = true;
                currentMoveIndex--;
                restoreHistoryPosition(currentMoveIndex);
                navigatingHistory = false;
                updateTurnIndicator(chessView.getCurrentSide());
                queryCloudDb(chessView.getFEN());
                updateMoveList();
                // 悔棋后重新分析当前局面
                restartAnalysisIfNeeded();
            } else {
                ToastUtil.makeText(this, "已回到最初局面", Toast.LENGTH_SHORT).show();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentMoveIndex + 1 < moveHistory.size()) {
                navigatingHistory = true;
                currentMoveIndex++;
                restoreHistoryPosition(currentMoveIndex);
                navigatingHistory = false;
                updateTurnIndicator(chessView.getCurrentSide());
                queryCloudDb(chessView.getFEN());
                updateMoveList();
                restartAnalysisWithFen(chessView.getFEN());
            } else {
                ToastUtil.makeText(this, "已经是最后一步", Toast.LENGTH_SHORT).show();
            }
        });

        btnReset.setOnClickListener(v -> {
            navigatingHistory = true;
            moveHistory.clear();
                currentMoveIndex = -1;
                if (initialFEN != null && !initialFEN.isEmpty()) {
                    chessView.setBoardByFEN(initialFEN, isBoardFlipped);
                    chessView.setCurrentInitialFEN(initialFEN);
                } else {
                    chessView.resetToInitial();
                }
                chessView.clearSelection();
            navigatingHistory = false;
            updateTurnIndicator(1);
            updateMoveList();
            tvAnalyzeResult.setText(toBoldCenterTitle(
                "<font color='" + colorToHex(analysisPrimaryTextColor) + "'><b>🐟🐠🐡皮卡鱼🦈🎏🐬🐳🐋</b></font><br/><br/>🐟🐠🐡皮卡鱼为您加油！🦈🎏🐬🐳🐋", tvAnalyzeResult));
            queryCloudDb(chessView.getFEN());
            ToastUtil.makeText(this, "已重置局面", Toast.LENGTH_SHORT).show();
            // 重来后重新分析当前局面
            restartAnalysisIfNeeded();
        });

        // ===== 分析/停止 按钮 =====
        btnAnalyze.setOnClickListener(v -> {
            if (isAnalyzing) {
                // 当前正在分析，点击停止
                stopPikafishAnalysis();
            } else {
                // 当前未分析，点击开始分析
                startPikafishAnalysis();
            }
        });
        // ==========================
    }

    private void setupChessView() {
        chessView.setOnTurnChangeListener(side -> updateTurnIndicator(side));
        chessView.setMoveSoundEnabled(true);

        chessView.setOnGameListener(new ChessView.OnGameListener() {
            @Override
            public void onMove(int fromY, int fromX, int toY, int toX, int chessType) {
                // 如果是浏览历史中，不记录
                if (navigatingHistory) return;

                // 如果当前不是最后一步，说明走了分支 → 删除后面的历史
                while (moveHistory.size() > currentMoveIndex + 1) {
                    moveHistory.remove(moveHistory.size() - 1);
                }

                // 记录走法
                int side = chessView.getCurrentSide(); // 已经切换过了，所以 -side 是刚走的一方
                boolean isRed = (side == -1);  // side 是下一方，刚走的是 -side
                String fen = chessView.getFEN();
                String uci = uciFromMove(fromY, fromX, toY, toX);
                String chinese = UciToChinese.convert(uci, chessType, chessView.chessBoard, -side);
                int moveNum = moveHistory.size() / 2 + 1;

                MoveRecord record = new MoveRecord(uci, chinese, fen, moveNum, isRed);
                moveHistory.add(record);
                currentMoveIndex = moveHistory.size() - 1;
                updateMoveList();
                scrollToLastMove();

                queryCloudDb(fen);
                // 棋子走动后重新分析当前局面
                restartAnalysisIfNeeded();
            }

            @Override
            public void onGameOver(int winner) {
                ToastUtil.makeText(AnalyzeActivity.this,
                        winner == 1 ? "红方胜利！" : "黑方胜利！", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== 招法列表 ====================

    private void updateMoveList() {
        llMoveList.removeAllViews();

        // 棋谱标题 - 同时也是初始局面入口
        TextView header = new TextView(this);
        header.setText("初始局面");
        header.setTextSize(12);
        header.setTextColor(analysisPrimaryTextColor);
        header.setTypeface(Typeface.DEFAULT_BOLD); // 加粗

        header.setGravity(android.view.Gravity.CENTER);
        header.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        header.setOnClickListener(v -> {
            navigatingHistory = true;
            currentMoveIndex = -1;
            if (initialFEN != null && !initialFEN.isEmpty()) {
                chessView.setBoardByFEN(initialFEN, isBoardFlipped);
            } else {
                chessView.resetToInitial();
            }
            chessView.clearSelection();
            navigatingHistory = false;
            updateTurnIndicator(chessView.getCurrentSide());
            updateMoveList();
            queryCloudDb(chessView.getFEN());
            // 点击初始局面后重新分析（直接用初始FEN，不带moves）
            restartAnalysisWithFen(initialFEN != null ? initialFEN : chessView.getFEN());
        });
        llMoveList.addView(header);


        for (int i = 0; i < moveHistory.size(); i++) {
            MoveRecord r = moveHistory.get(i);
            final int index = i;

            android.widget.TextView tv = new android.widget.TextView(this);
            String numStr = String.format("%2d", i + 1) + ". ";
            String displayText = numStr + r.chinese;
            // 步号用普通字体，招法用等宽字体（数字对齐）
            android.text.SpannableString ss = new android.text.SpannableString(displayText);
            ss.setSpan(new android.text.style.TypefaceSpan(
                    "sans-serif"),
                    0, numStr.length(), 0);
            ss.setSpan(new android.text.style.TypefaceSpan(
                    "monospace"),
                    numStr.length(), displayText.length(), 0);
            tv.setText(ss);
            tv.setTextColor(analysisPrimaryTextColor);
            tv.setTextSize(12);
            tv.setBackgroundColor(index == currentMoveIndex ? 0xFFCCE5FF : 0x00000000);
            tv.setOnClickListener(v -> {
                navigatingHistory = true;
                currentMoveIndex = index;
                restoreHistoryPosition(index);
                navigatingHistory = false;
                updateTurnIndicator(chessView.getCurrentSide());
                updateMoveList();

                queryCloudDb(r.fen);
                // 点击棋谱列表后重新分析（直接用该步走完后的FEN，不带moves）
                restartAnalysisWithFen(r.fen);
            });
        llMoveList.addView(tv);
        }
    }

    /** Restore both the board position and the move highlight for a history entry. */
    private boolean restoreHistoryPosition(int index) {
        if (index < 0 || index >= moveHistory.size()) {
            boolean loaded = initialFEN != null && !initialFEN.isEmpty()
                    && chessView.setBoardByFEN(initialFEN, isBoardFlipped);
            chessView.clearSelection();
            return loaded;
        }
        MoveRecord record = moveHistory.get(index);
        boolean loaded = chessView.setBoardByFENPreserveInitialFEN(
                record.fen, isBoardFlipped);
        if (loaded) {
            chessView.restoreMoveHighlightFromUci(record.uci);
        }
        return loaded;
    }

    private void scrollToLastMove() {
        // 使用外层 ScrollView 的 fullScroll 滚动到底部
        if (svMoveList != null) {
            svMoveList.post(() -> svMoveList.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String uciFromMove(int fromY, int fromX, int toY, int toX) {
        char f = (char) ('a' + fromX);
        char fr = (char) ('0' + (9 - fromY));
        char t = (char) ('a' + toX);
        char tr = (char) ('0' + (9 - toY));
        return "" + f + fr + t + tr;
    }

    // ==================== 云库查询 ====================

    private void queryCloudDb(final String fen) {
        // 捕获当前棋盘快照和行棋方，避免子线程读取时棋盘已被用户导航改变
        final int requestId = ++cloudRequestId;
        final int[][] boardSnapshot = copyBoard(chessView.chessBoard);
        final int sideSnapshot = chessView.getCurrentSide();
        new Thread(() -> {
            try {
                String encodedFen = java.net.URLEncoder.encode(fen, "UTF-8");
                StringBuilder display = new StringBuilder();
                display.append("云库分析\n");

                // 查所有着法 queryall
                String allUrl = CLOUD_DB_URL + "?action=queryall&board=" + encodedFen + "&egtbmetric=dtm&showall=1";
                String allResult = httpGet(allUrl);
                Log.d(TAG, "云库 all: " + (allResult != null ? allResult.substring(0, Math.min(200, allResult.length())) : "null"));

                if (allResult != null && !allResult.equals("unknown") && !allResult.equals("checkmate") && !allResult.equals("stalemate")) {
                    String[] moves = allResult.split("\\|");
                    int count = Math.min(moves.length, 7);
                    for (int i = 0; i < count; i++) {
                        String[] fields = moves[i].split(",");
                        String uci = null;
                        String mscore = null;
                        for (String f : fields) {
                            if (f.startsWith("move:")) uci = f.substring(5).trim();
                            else if (f.startsWith("score:")) mscore = f.substring(6).trim();
                        }
                        if (uci != null && uci.length() >= 4) {
                            String cn = UciToChinese.convert(uci, boardSnapshot, sideSnapshot);
                            String mv = padVisual(cn, 10);
                            String sc = mscore != null ? mscore : "";
                            display.append(mv).append(sc).append("\n");
                        }
                    }
                } else if ("checkmate".equals(allResult)) {
                    display.append("绝杀\n");
                } else if ("stalemate".equals(allResult)) {
                    display.append("困毙\n");
                } else {
                    display.append("未找到该局面\n");
                }

                // 去掉多余的深度查询段，深度已在上面每行显示
                final String text = display.toString().trim();
                runOnUiThread(() -> {
                    if (requestId != cloudRequestId
                            || chessView == null
                            || isFinishing()
                            || isDestroyed()
                            || !fen.equals(chessView.getFEN())) {
                        return;
                    }
                    // 用 SpannableString 让标题居中，招法和评分保持普通字重
                    setCloudDbText(text);
                });

            } catch (Exception e) {
                Log.w(TAG, "云库查询失败", e);
                runOnUiThread(() -> {
                    if (requestId != cloudRequestId
                            || chessView == null
                            || isFinishing()
                            || isDestroyed()
                            || !fen.equals(chessView.getFEN())) {
                        return;
                    }
                    setCloudDbText("云库分析\n查询失败");
                });
            }
        }).start();
    }

    private void setCloudDbText(String text) {
        if (tvCloudDb == null) return;

        android.text.SpannableString ss = new android.text.SpannableString(
                text == null ? "" : text);
        int titleEnd = ss.toString().indexOf('\n');
        if (titleEnd < 0) titleEnd = ss.length();
        if (titleEnd > 0) {
            ss.setSpan(new android.text.style.ForegroundColorSpan(analysisPrimaryTextColor),
                    0, titleEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, titleEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.AlignmentSpan.Standard(
                    android.text.Layout.Alignment.ALIGN_CENTER), 0, titleEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tvCloudDb.setTextColor(analysisSecondaryTextColor);
        tvCloudDb.setText(ss);
    }

    /** ??????????????????=2?????1??*/
    private String padVisual(String s, int target) {
        int w = 0;
        for (char c : s.toCharArray()) { w += (c > 127) ? 2 : 1; }
        StringBuilder sb = new StringBuilder(s);
        while (w < target) { sb.append(' '); w++; }
        return sb.toString();
    }

    /** HTTP GET 请求，返回第一行 */
    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = conn.getResponseCode();
            Log.d(TAG, "HTTP " + code + " for: " + urlStr.substring(0, Math.min(80, urlStr.length())));
            if (code != 200) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line = reader.readLine();
            reader.close();
            return line != null ? line.trim() : null;
        } catch (Exception e) {
            Log.w(TAG, "HTTP error: " + e.getMessage());
            return null;
        }
    }




    private int[][] copyBoard(int[][] src) {
        int[][] dst = new int[10][9];
        for (int y = 0; y < 10; y++) System.arraycopy(src[y], 0, dst[y], 0, 9);
        return dst;
    }
private void initPoetryLibrary() {
        String[] rawArray = getResources().getStringArray(R.array.quotes_array);
        poetryList = new ArrayList<>();
        for (String s : rawArray) {
            if (s != null && !s.isEmpty()) {
                poetryList.add(s);
            }
        }
        if (poetryList.isEmpty()) {
            tvShiju.setText("诗词库为空，请检查 strings.xml");
        }
    }

    private void startPoetryTimer() {
        poetryHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                minuteCounter++;
                if (minuteCounter % 10 == 0) {
                    displaySpecialMessage();
                } else {
                    showRandomPoetry();
                }
                poetryHandler.postDelayed(this, MINUTE_DELAY);
            }
        }, MINUTE_DELAY);
    }

    private void showRandomPoetry() {
        if (poetryList == null || poetryList.isEmpty()) return;
        List<String> normalList = new ArrayList<>(poetryList);
        if (normalList.contains("🔥🔥刘霸天为您加油🔥🔥")) {
            normalList.remove("🔥🔥刘霸天为您加油🔥🔥🔥");
        }
        if (normalList.isEmpty()) {
            setShijuText("暂无可用诗句");
            return;
        }
        Random random = new Random();
        int index = random.nextInt(normalList.size());
        String text = normalList.get(index);
        animateText(tvShiju, text);
    }

    private void displaySpecialMessage() {
        String text = "🔥🔥一二残局🔥🔥🔥";
        animateText(tvShiju, text);
    }

    /** 设置shiju文字，横屏时自动转为竖排（每字换行），正确处理emoji等unicode补充字符 */
    private void setShijuText(String text) {
        if (tvShiju == null) return;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < text.length()) {
                int cp = text.codePointAt(i);
                if (sb.length() > 0) sb.append("\n");
                sb.appendCodePoint(cp);
                i += Character.charCount(cp);
            }
            tvShiju.setText(sb.toString());
        } else {
            tvShiju.setText(text);
        }
    }

    private void animateText(TextView textView, String newText) {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(500);
        fadeOut.setFillAfter(true);
        textView.startAnimation(fadeOut);
        fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationStart(android.view.animation.Animation animation) {
            }

            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                // 横屏时shiju需要竖排显示
                if (textView == tvShiju) {
                    setShijuText(newText);
                } else {
                    textView.setText(newText);
                }
                AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                fadeIn.setDuration(500);
                fadeIn.setFillAfter(true);
                textView.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(android.view.animation.Animation animation) {
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("initialFEN", initialFEN);
        outState.putBoolean("isBoardFlipped", isBoardFlipped);
        outState.putInt("currentMoveIndex", currentMoveIndex);
        // 保存走法历史
        ArrayList<String> uciList = new ArrayList<>();
        ArrayList<String> cnList = new ArrayList<>();
        ArrayList<String> fenList = new ArrayList<>();
        int[] moveNums = new int[moveHistory.size()];
        boolean[] isReds = new boolean[moveHistory.size()];
        for (int i = 0; i < moveHistory.size(); i++) {
            MoveRecord r = moveHistory.get(i);
            uciList.add(r.uci);
            cnList.add(r.chinese);
            fenList.add(r.fen);
            moveNums[i] = r.moveNumber;
            isReds[i] = r.isRed;
        }
        outState.putStringArrayList("moveUciList", uciList);
        outState.putStringArrayList("moveCnList", cnList);
        outState.putStringArrayList("moveFenList", fenList);
        outState.putIntArray("moveNums", moveNums);
        outState.putBooleanArray("isReds", isReds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activityDestroyed) return;
        // 暂停时停止引擎搜索，避免后台全速运行消耗电量和CPU
        if (isAnalyzing && pikafishEngine != null && isEngineReady) {
            synchronized (engineLock) {
                try {
                    pikafishEngine.sendCommandOnly("stop");
                } catch (Exception ignored) {}
            }
            isAnalyzing = false;
            if (btnAnalyze != null) btnAnalyze.setText("分析");
        }
    }



    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        poetryHandler.removeCallbacksAndMessages(null);
        // ===== 关闭 Pikafish 引擎 =====
        isAnalyzing = false;
        isEngineReady = false;
        isEngineStarting = false;
        cloudRequestId++;
        searchId = 0;
        if (pikafishEngine != null) {
            pikafishEngine.quit();
            pikafishEngine = null;
        }
        // ==============================
    }

    // ==================== 皮卡鱼引擎分析 ====================

    /**
     * 构建发送给引擎的 position 命令
     * 使用 moveHistory 列表中的 UCI 走法来构建，
     * 而不是依赖 ChessView 的 getUCIHistory()（导航历史局面时会被清空）。
     *
     * 逻辑：
     * - 如果当前在初始局面（currentMoveIndex == -1），只发 fen
     * - 如果有走法历史，发 fen moves ...
     * - 如果导航到了某个历史局面，用该局面的 fen 作为起始，只发该 fen
     */
    private String buildPositionCommand() {
        if (currentMoveIndex < 0 || moveHistory.isEmpty()) {
            // 当前在初始局面，只发fen
            String baseFen = getBaseFen();
            return "position fen " + baseFen;
        } else {
            // 有走法历史，发 fen moves ...
            String baseFen = getBaseFen();
            StringBuilder sb = new StringBuilder("position fen ").append(baseFen).append(" moves");
            for (int i = 0; i <= currentMoveIndex; i++) {
                String uci = moveHistory.get(i).uci;
                if (uci != null && uci.length() >= 4) sb.append(" ").append(uci);
            }
            return sb.toString();
        }
    }

    /**
     * 根据指定FEN构建position命令（不带moves）
     * 用于点击棋谱导航到某个历史局面时，直接用走完那步棋后的FEN
     */
    private String buildPositionCommandFromFen(String fen) {
        return "position fen " + fen;
    }

    /**
     * 获取基础FEN（初始局面FEN）
     */
    private String getBaseFen() {
        String baseFen = initialFEN;
        if (baseFen == null || baseFen.isEmpty()) {
            baseFen = chessView.getCurrentInitialFEN();
        }
        if (baseFen == null || baseFen.isEmpty()) {
            baseFen = chessView.getFEN();
        }
        return baseFen;
    }

    /**
     * 开始皮卡鱼分析
     */
    private void startPikafishAnalysis() {
        // 如果引擎还没启动，先异步启动
        if (pikafishEngine == null) {
            pikafishEngine = new PikafishEngine();
        }

        if (isEngineStarting) {
            return;
        }

        if (!isEngineReady) {
            isEngineStarting = true;
            btnAnalyze.setText("启动中...");
            Log.d(TAG, "引擎未就绪，首次启动...");

            new Thread(() -> {
                try {
                    pikafishEngine.start(AnalyzeActivity.this);
                    Log.d(TAG, "★★★ Pikafish 引擎启动完成 ★★★");
                    isEngineReady = true;
                    runOnUiThread(() -> {
                        isEngineStarting = false;
                        if (isActivityAlive()) {
                            doPikafishAnalysis();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Pikafish 引擎启动失败", e);
                    runOnUiThread(() -> {
                        isEngineStarting = false;
                        isEngineReady = false;
                        if (!isActivityAlive()) return;
                        btnAnalyze.setText("分析");
                        String msg = e.getMessage();
                        String displayMsg = msg != null && msg.length() > 300 ? msg.substring(0, 300) : msg;
                        ToastUtil.makeText(AnalyzeActivity.this,
                                "AI启动失败:\n" + displayMsg,
                                Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
            return;
        }

        // 引擎已就绪，直接分析
        doPikafishAnalysis();
    }

    /**
     * 执行皮卡鱼分析（引擎已就绪）
     */
    private void doPikafishAnalysis() {
        if (!isActivityAlive() || pikafishEngine == null || !isEngineReady
                || chessView == null || tvAnalyzeResult == null || btnAnalyze == null) return;

        isAnalyzing = true;
        btnAnalyze.setText("停止");

        // 递增搜索ID
        int currentSearchId = ++searchId;

        // 构建position命令
        String positionCmd = buildPositionCommand();
        // analysisBaseFen 使用当前局面的FEN（走完所有moves之后的FEN），而非基础FEN
        // 这样招法解析和分数翻转都以当前实际走棋方为准
        analysisBaseFen = chessView.getFEN();
        Log.d(TAG, "分析命令: " + positionCmd + ", searchId=" + currentSearchId + ", baseFen=" + analysisBaseFen);

        // 清空分析结果显示
        tvAnalyzeResult.setText("");

        // 发送position + go infinite（两行命令）
        sendEngineCommands(positionCmd);

        // 启动持久读取线程（如果还没启动）
        startReaderThreadIfNeeded();
    }

    /**
     * 发送引擎命令（stop + position + go infinite）
     * 线程安全：所有发送命令的操作通过 engineLock 同步
     */
    private void sendEngineCommands(String positionCmd) {
        synchronized (engineLock) {
            try {
                // 先发送stop，确保之前的搜索停止
                pikafishEngine.sendCommandOnly("stop");
                // 发送局面信息
                pikafishEngine.sendCommandOnly(positionCmd);
                // 发送 go infinite
                pikafishEngine.sendCommandOnly("go infinite");
                Log.d(TAG, "已发送: " + positionCmd);
                Log.d(TAG, "已发送: go infinite");
            } catch (Exception e) {
                Log.e(TAG, "发送分析命令失败", e);
            }
        }
    }

    /**
     * 启动持久读取线程（如果还没启动）
     * 该线程持续读取引擎输出，通过 searchId 过滤过期结果
     * 整个Activity生命周期内只创建一次
     */
    private void startReaderThreadIfNeeded() {
        if (readerThread != null && readerThread.isAlive()) return;

        readerThread = new Thread(() -> {
            Log.d(TAG, "持久读取线程启动");
            int activeSearchId = searchId;

            try {
                while (true) {
                    if (activityDestroyed) break;
                    // 检测搜索ID变化（新搜索开始）
                    if (searchId != activeSearchId) {
                        activeSearchId = searchId;
                        Log.d(TAG, "读取线程: 切换到新搜索 searchId=" + activeSearchId);
                    }

                    // 如果当前没有在分析，等待
                    if (!isAnalyzing) {
                        Thread.sleep(100);
                        continue;
                    }

                    String line = pikafishEngine.readLineFromEngine();
                    if (line == null) {
                        Log.w(TAG, "引擎输出流关闭，读取线程退出");
                        break;
                    }

                    // 再次检测搜索ID变化
                    if (searchId != activeSearchId) {
                        activeSearchId = searchId;
                        continue;  // 丢弃旧行，重新开始
                    }

                    // bestmove 表示当前搜索结束
                    if (line.startsWith("bestmove")) {
                        Log.d(TAG, "读取线程: bestmove searchId=" + activeSearchId + " line=" + line);
                        continue;
                    }

                    // 处理info行：只要有depth和pv就更新显示，只保留最后一层
                    if (line.startsWith("info") && line.contains(" depth ") && line.contains(" pv ")) {
                        // 验证searchId没变
                        if (searchId == activeSearchId && isAnalyzing) {
                            final String reportInfo = line;
                            final int cbSearchId = activeSearchId;
                            runOnUiThread(() -> {
                                if (cbSearchId == searchId && isAnalyzing) {
                                    updateAnalysisDisplay(reportInfo);
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "读取线程异常退出", e);
                runOnUiThread(() -> {
                    if (!isActivityAlive()) return;
                    isAnalyzing = false;
                    btnAnalyze.setText("分析");
                });
            }
            Log.d(TAG, "持久读取线程退出");
        });
        readerThread.start();
    }

    /**
     * 根据引擎返回的info行更新分析结果显示
     * 格式：
     *   标题行：皮卡鱼分析（居中，绿色加粗）
     *   第一行：分数：XXX  深度：XXX层  NPS：XXXX k（以下方视角为准，>=0蓝色，<0红色）
     *   第二行：中文招法（|score|>20000返回全部，否则最多20招）
     */
    private void updateAnalysisDisplay(String infoLine) {
        if (infoLine == null || infoLine.isEmpty()) return;

        try {
            String[] tokens = infoLine.split(" ");
            int score = 0;           // 分数（厘兵值）
            int depth = 0;           // 深度
            long nps = 0;            // NPS
            boolean isMate = false;  // 是否是杀棋分数
            int mateDistance = 0;    // 杀棋步数
            List<String> pvMoves = new ArrayList<>();  // PV招法列表

            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].equals("depth") && i + 1 < tokens.length) {
                    try { depth = Integer.parseInt(tokens[i + 1]); } catch (Exception e) {}
                } else if (tokens[i].equals("score") && i + 2 < tokens.length) {
                    if (tokens[i + 1].equals("cp")) {
                        try { score = Integer.parseInt(tokens[i + 2]); } catch (Exception e) {}
                    } else if (tokens[i + 1].equals("mate")) {
                        isMate = true;
                        try { mateDistance = Integer.parseInt(tokens[i + 2]); } catch (Exception e) {}
                        // 杀棋分数转换为厘兵值：正杀=+30000-步数，负杀=-30000+步数
                        score = mateDistance > 0 ? (30000 - mateDistance) : (-30000 - mateDistance);
                    }
                } else if (tokens[i].equals("nps") && i + 1 < tokens.length) {
                    try { nps = Long.parseLong(tokens[i + 1]); } catch (Exception e) {}
                } else if (tokens[i].equals("pv")) {
                    // pv之后的所有token都是招法
                    for (int j = i + 1; j < tokens.length; j++) {
                        if (tokens[j].length() >= 4) {
                            pvMoves.add(tokens[j]);
                        }
                    }
                    break;
                }
            }

            // ===== 分数以下方视角为准 =====
            // 皮卡鱼返回的分数是当前走棋方视角（FEN中w/b字段）
            // 需要转换为下方视角：
            //   - 红方在下（未翻转）：下方=红方，红方走时无需翻转，黑方走时翻转
            //   - 黑方在下（翻转）：下方=黑方，黑方走时无需翻转，红方走时翻转
            boolean currentSideIsBlack = false;  // 当前走棋方是否为黑方
            if (analysisBaseFen != null) {
                String[] fenParts = analysisBaseFen.split(" ");
                if (fenParts.length > 1 && fenParts[1].equals("b")) {
                    currentSideIsBlack = true;
                }
            }
            // needFlip: 当前走棋方与下方不是同一方时需要翻转
            boolean needFlip;
            if (isBoardFlipped) {
                // 黑方在下：黑方走时不翻转，红方走时翻转
                needFlip = !currentSideIsBlack;
            } else {
                // 红方在下：红方走时不翻转，黑方走时翻转
                needFlip = currentSideIsBlack;
            }
            int displayScore = needFlip ? -score : score;
            int displayMateDistance = needFlip ? -mateDistance : mateDistance;
            boolean displayIsMate = isMate;

            // 构建第一行：分数、层数、NPS（以下方视角显示），左对齐，半角数字
            StringBuilder line1 = new StringBuilder();
            if (displayIsMate) {
                if (displayMateDistance > 0) {
                    line1.append("绝杀（").append(displayMateDistance).append("步杀）");
                } else {
                    line1.append("被绝杀（").append(Math.abs(displayMateDistance)).append("步杀）");
                }
            } else {
                line1.append("分数：").append(displayScore).append("分");
            }
            line1.append("  ").append(depth).append("层");
            line1.append("  ").append(nps / 1000).append(" k");

            // 构建第二行：中文招法
            // 以发送给引擎的FEN为基础解析招法
            int[][] boardSnapshot;
            int sideSnapshot;
            if (analysisBaseFen != null) {
                // 从FEN解析棋盘和走棋方
                boardSnapshot = boardFromFen(analysisBaseFen);
                String[] fenParts = analysisBaseFen.split(" ");
                sideSnapshot = (fenParts.length > 1 && fenParts[1].equals("b")) ? -1 : 1;
            } else {
                // 降级：从当前棋盘读取
                boardSnapshot = copyBoard(chessView.chessBoard);
                sideSnapshot = chessView.getCurrentSide();
            }

            // 限制招法数量（以下方视角的分数绝对值判断）
            int absDisplayScore = Math.abs(displayScore);
            int maxMoves = absDisplayScore > 20000 ? pvMoves.size() : Math.min(pvMoves.size(), 20);

            StringBuilder line2 = new StringBuilder();
            // 模拟走法：每走一步需要更新棋盘快照和走棋方
            int[][] simBoard = copyBoard(boardSnapshot);
            int simSide = sideSnapshot;

            for (int i = 0; i < maxMoves; i++) {
                String uci = pvMoves.get(i);
                // 从模拟棋盘获取棋子类型
                int fromX = uci.charAt(0) - 'a';
                int fromY = 9 - (uci.charAt(1) - '0');
                int toX = uci.charAt(2) - 'a';
                int toY = 9 - (uci.charAt(3) - '0');

                if (fromX < 0 || fromX > 8 || fromY < 0 || fromY > 9 ||
                        toX < 0 || toX > 8 || toY < 0 || toY > 9) {
                    line2.append(uci).append(" ");
                    continue;
                }

                int piece = simBoard[fromY][fromX];
                String chinese = UciToChinese.convert(uci, piece, simBoard, simSide);
                line2.append(chinese).append(" ");

                // 更新模拟棋盘
                simBoard[toY][toX] = simBoard[fromY][fromX];
                simBoard[fromY][fromX] = 0;
                simSide = -simSide;
            }

            // 使用HTML着色：标题行绿色居中加粗，分数行左对齐：>=0蓝色，<0红色，招法行左对齐全角数字
            String color;
            if (displayIsMate) {
                color = displayMateDistance > 0 ? "#0000FF" : "#FF0000";
            } else {
                color = (displayScore >= 0) ? "#0000FF" : "#FF0000";
            }
            String html = "<div style='text-align:center'><font color='#" + colorToHex(analysisPrimaryTextColor) + "'><b>🐟🐠🐡皮卡鱼🦈🎏🐬🐳🐋</b></font></div>" +
                    "<font color='" + color + "'><b>" + line1.toString() + "</b></font><br/>" +
                    "<font color='#" + colorToHex(analysisSecondaryTextColor) + "'>" + toFullWidth(line2.toString().trim()) + "</font>";

            // 标题和评分行单独加粗，招法列表保持普通字重
            tvAnalyzeResult.setText(toBoldCenterTitle(html, tvAnalyzeResult));

        } catch (Exception e) {
            Log.e(TAG, "解析分析结果失败", e);
        }
    }

    /**
     * 从FEN字符串解析棋盘数组
     */
    private int[][] boardFromFen(String fen) {
        int[][] board = new int[10][9];
        if (fen == null || fen.isEmpty()) return board;
        try {
            String boardPart = fen.split(" ")[0];
            String[] rows = boardPart.split("/");
            for (int y = 0; y < 10 && y < rows.length; y++) {
                String row = rows[y];
                int x = 0;
                for (int i = 0; i < row.length() && x < 9; i++) {
                    char c = row.charAt(i);
                    if (Character.isDigit(c)) {
                        x += Character.getNumericValue(c);
                    } else {
                        int val = fenCharToValue(c);
                        if (val != 0) board[y][x] = val;
                        x++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析FEN棋盘失败: " + fen, e);
        }
        return board;
    }

    /**
     * FEN字符转棋子值（与ChessView.getChessValueFromFENChar一致）
     */
    private int fenCharToValue(char c) {
        switch (c) {
            case 'K': return 1;   // 红帅
            case 'A': return 2;   // 红仕
            case 'B': return 3;   // 红相
            case 'N': return 4;   // 红马
            case 'R': return 5;   // 红车
            case 'C': return 6;   // 红炮
            case 'P': return 7;   // 红兵
            case 'k': return -1;  // 黑将
            case 'a': return -2;  // 黑士
            case 'b': return -3;  // 黑象
            case 'n': return -4;  // 黑马
            case 'r': return -5;  // 黑车
            case 'c': return -6;  // 黑炮
            case 'p': return -7;  // 黑卒
            default: return 0;
        }
    }

    /**
     * 停止皮卡鱼分析（用户点击"停止"按钮时调用）
     * 已显示的结果不清除
     */
    private void stopPikafishAnalysis() {
        isAnalyzing = false;
        btnAnalyze.setText("分析");

        // 递增searchId
        searchId++;

        if (pikafishEngine != null && isEngineReady) {
            synchronized (engineLock) {
                try {
                    pikafishEngine.sendCommandOnly("stop");
                    Log.d(TAG, "用户停止，已发送 stop 命令");
                } catch (Exception e) {
                    Log.e(TAG, "发送 stop 失败", e);
                }
            }
        }
    }

    /**
     * 如果当前正在分析，则重新分析当前局面
     * （棋子走动、悔棋、重来时调用）
     * 先发送FEN给引擎，再清除之前返回的结果，然后接收新结果
     */
    private void restartAnalysisIfNeeded() {
        if (!isAnalyzing) return;

        // 递增搜索ID
        int currentSearchId = ++searchId;

        // 构建position命令
        String positionCmd = buildPositionCommand();
        // analysisBaseFen 使用当前局面的FEN（走完所有moves之后的FEN）
        analysisBaseFen = chessView.getFEN();
        Log.d(TAG, "重新分析: " + positionCmd + ", searchId=" + currentSearchId + ", baseFen=" + analysisBaseFen);

        // 先发送新局面给引擎
        sendEngineCommands(positionCmd);

        // 然后清空显示结果（引擎已在分析新局面，清空旧结果不会闪屏）
        tvAnalyzeResult.setText("");
    }

    /**
     * 如果当前正在分析，用指定FEN重新分析
     * （点击棋谱列表或初始局面时调用，直接用走完那步后的FEN，不带moves）
     * 先发送FEN给引擎，再清除之前返回的结果，然后接收新结果
     */
    private void restartAnalysisWithFen(String fen) {
        if (!isAnalyzing) return;

        // 递增搜索ID
        int currentSearchId = ++searchId;

        // 记录分析的FEN（就是传入的fen，走完那步棋后的FEN）
        analysisBaseFen = fen;

        // 直接用FEN构建position命令
        String positionCmd = buildPositionCommandFromFen(fen);
        Log.d(TAG, "FEN重新分析: " + positionCmd + ", searchId=" + currentSearchId + ", baseFen=" + analysisBaseFen);

        // 先发送新局面给引擎
        sendEngineCommands(positionCmd);

        // 然后清空显示结果
        tvAnalyzeResult.setText("");
    }

    // ==================== 皮卡鱼引擎分析结束 ====================

    /**
     * 将 HTML 解析为 Spanned，再对第一行标题居中。
     * 只负责将第一行标题居中，字体粗细由 HTML <b> 标签控制。
     */
    private android.text.Spanned toBoldCenterTitle(String html, TextView tv) {
        android.text.Spanned parsed = android.text.Html.fromHtml(html);
        android.text.SpannableString ss = new android.text.SpannableString(parsed);
        String text = ss.toString();
        int firstNl = text.indexOf('\n');
        if (firstNl > 0) {
            // 第一行标题居中
            ss.setSpan(new android.text.style.AlignmentSpan.Standard(
                    android.text.Layout.Alignment.ALIGN_CENTER), 0, firstNl,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    /** 半角数字/字母转全角（用于招法行） */
    private String toFullWidth(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append((char) (c - '0' + '０'));
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c - 'A' + 'Ａ'));
            } else if (c >= 'a' && c <= 'z') {
                sb.append((char) (c - 'a' + 'ａ'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ========== 横屏旋转支持 ==========

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 保存棋盘状态
        String currentFen = chessView != null ? chessView.getFEN() : null;
        boolean isFlipped = chessView != null && chessView.isBoardFlipped();
        int currentSide = chessView != null ? chessView.getCurrentSide() : 1;

        // 重新加载布局
        setContentView(R.layout.activity_analyze);

        // 重新绑定视图引用
        initViews();
        setupListeners();

        // 恢复棋盘状态
        if (chessView != null) {
            if (currentFen != null) {
                chessView.setBoardByFENPreserveInitialFEN(currentFen, isFlipped);
                if (currentMoveIndex >= 0 && currentMoveIndex < moveHistory.size()) {
                    chessView.restoreMoveHighlightFromUci(moveHistory.get(currentMoveIndex).uci);
                } else {
                    chessView.clearSelection();
                }
            }
            if (initialFEN != null && !initialFEN.isEmpty()) {
                chessView.setCurrentInitialFEN(initialFEN);
            }
        }

        setupBackground();

        // 重新设置棋盘监听
        setupChessView();

        // 恢复UI显示
        updateTurnIndicator(currentSide);
        updateMoveList();

        // 恢复诗词显示（tvShiju已在initViews中重新绑定）
        if (tvShiju != null && poetryList != null && !poetryList.isEmpty()) {
            showRandomPoetry();
        }

        // 横屏时程序化调整棋盘尺寸
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            adjustAnalyzeBoardForLandscape();
        }

        // 如果正在分析，恢复分析状态
        if (isAnalyzing) {
            restartAnalysisIfNeeded();
        }

        Log.d(TAG, "配置变更: " + (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏"));
    }

    /**
     * 横屏时程序化调整棋盘尺寸：
     * - 棋盘高度 = 屏幕高度
     * - 棋盘宽度 = 高度 × 棋盘宽高比
     * - 棋盘中心在屏幕宽度的37%位置
     */
    private void adjustAnalyzeBoardForLandscape() {
        View boardFrame = findViewById(R.id.boardFrame);
        if (boardFrame == null || chessView == null) return;

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        // 从ChessView获取棋盘宽高比
        float boardAspect = chessView.getBoardAspect();

        int boardHeight = screenHeight;
        int boardWidth = (int) (boardHeight * boardAspect);

        // 棋盘中心在屏幕37%位置
        int boardCenterX = (int) (screenWidth * 0.37);
        int boardLeft = boardCenterX - boardWidth / 2;
        if (boardLeft < 0) boardLeft = 0;

        // 使用ConstraintLayout.LayoutParams设置尺寸和位置
        // 只约束start+top+bottom，不约束end，让宽度由params.width决定
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) boardFrame.getLayoutParams();
        params.width = boardWidth;
        params.height = boardHeight;
        params.leftMargin = boardLeft;
        params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        boardFrame.setLayoutParams(params);

        Log.d(TAG, "横屏棋盘尺寸: " + boardWidth + "x" + boardHeight + ", left=" + boardLeft);
    }
}
