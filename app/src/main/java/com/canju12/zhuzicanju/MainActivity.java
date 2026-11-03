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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.ImageView;
import android.graphics.Bitmap;
import java.io.FileWriter;

public class MainActivity extends AppCompatActivity {

    // UI组件
    private TextView tvTitle;
    private TextView tvTitleDetail;
    private TextView tvTime;
    private TextView tvAccuracy;
    private ChessView chessView;

    // 按钮
    private Button btnTiku;
    private Button btnSelectLevel;
    private Button btnRegret;
    private Button btnCopy;
    private Button btnRepeat;
    private Button btnAnalyze;
    private Button btnNonono;
    private Button btnYesyes;
    private AlertDialog tikuDialog = null;
    private long lastTurnIndicatorClickTime = 0L;

    // 数据管理
    private TikuManager tikuManager;
    private TikuData currentTiku;
    private int currentQuestionIndex = 0;
    private int totalQuestions = 0;
    // 添加：当前题库的所有FEN列表
    private List<String> currentFenList = new ArrayList<>();

    // 计时相关
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;
    private int elapsedSeconds = 0;
    private TextView tvQQGroup;
    private Handler totalTimerHandler = new Handler();
    private Runnable totalTimerRunnable;
    private int totalElapsedSeconds = 0;

    // 诗词相关
    private TextView tvShiju;
    private List<String> poetryList;
    private Handler handler = new Handler();
    private Handler uiHandler = new Handler();
    private Handler questionSelectorHandler = new Handler();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private int minuteCounter = 0;
    private final long MINUTE_DELAY = 60000;
    private ViewGroup pageFlipContainer;
    private View pageFlipOverlay;
    private int pageFlipDirection = 0;
    private boolean pageFlipRunning = false;
    private int pendingPageFlipDirection = 0;
    private Bitmap pendingPageFlipSnapshot;
    private volatile boolean activityDestroyed = false;

    // 权限请求码
    private static final int REQUEST_PERMISSION_CODE = 100;
    // 计时暂停相关
    private boolean isTimerPaused = false;  // 计时器是否暂停
    private int pausedElapsedSeconds = 0;   // 暂停时已用时间
    private int pausedTotalElapsedSeconds = 0; // 暂停时总时间

    // 统计相关
    private int winCount = 0;        // 获胜进入下一关的题目数
    private int masteredCount = 0;   // 点"已会"的题目数
    private int notMasteredCount = 0; // 点"不会"的题目数
    private ImageView ivTurnIndicator;

    // ===== Pikafish AI 对手相关 =====
    private PikafishEngine pikafishEngine;
    private ChineseRules chineseRules = new ChineseRules();  // 直接初始化
    private boolean pikafishMode = true;      // AI对手模式开关
    private int humanSide = 1;                 // 人类走哪方 (1=红, -1=黑)
    private int pikafishSide = -1;            // Pikafish走哪方
    private boolean isPikafishThinking = false; // 正在AI计算中
    private boolean isDrawHandling = false;      // 正在处理和棋（防重复弹窗）
    private boolean questionRecorded = false;
    private boolean longCheckHandled = false;  // 长将判负是否已处理    // 当前题目是否已统计过（防重复统计）
    // 题目是否已结束过（无论重来/重下/悔棋，第一次结束结果为准，不再重复统计）
    private boolean questionFinished = false;
    // 每次换题递增，令旧题目的异步引擎回调失效
    private int aiQuestionGeneration = 0;
    private Handler pikafishHandler = new Handler();
    private Runnable pikafishWatchdog;
    // 局面合法性检查器
    private final PositionValidator positionValidator = new PositionValidator();
    // ================================

    private boolean isActivityAlive() {
        return !activityDestroyed && !isFinishing();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 检查并请求存储权限
        checkAndRequestPermissions();

        // ===== 首次运行显示说明书 =====
        if (isFirstRun()) {
            // 延迟显示，确保界面已加载
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showFirstRunDialog();
                }
            }, 500);
        }
        // =============================
        createNecessaryFolders();

        initViews();          // chessView 在这里初始化
        setupListeners();
        initData();
        setupChessView();
        // Pikafish 引擎不在这里启动，等人类走完第一步再触发

        // 横屏启动时程序化调整棋盘尺寸
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            chessView.post(new Runnable() {
                @Override
                public void run() {
                    adjustBoardSizeForLandscape();
                }
            });
        }

        startTimer();

        initPoetryLibrary();
        startPoetryTimer();
        showRandomPoetry();
        // ===== 初始化进度管理器（会自动加载进度） =====
        ProgressManager.getInstance(this);
        // ============================================

        tikuManager = new TikuManager(this);

        // 检查是否从错题本/收藏夹页跳转
        if (handleIncomingTikuIntentIfNeeded()) {
            return;
        }

// ===== 尝试恢复之前保存的题目 =====
        boolean restored = restoreCurrentQuestion();

        if (!restored) {
            // 没有可恢复的存档（首次运行）→ 默认加载“象棋杀着大全”第1题；
            // 若该题库不存在则退回到题库列表第1个
            TikuData defaultTiku = tikuManager.getTikuByName("象棋杀着大全");
            if (defaultTiku == null && !tikuManager.getTikuList().isEmpty()) {
                defaultTiku = tikuManager.getTikuList().get(0);
            }
            if (defaultTiku != null) {
                currentTiku = defaultTiku;
                tikuManager.setCurrentTiku(currentTiku);
                totalQuestions = currentTiku.getTotalCount();
                currentQuestionIndex = 0;
                loadCurrentQuestion();
            } else {
                ToastUtil.makeText(this, "未找到题库文件，请检查assets目录", Toast.LENGTH_LONG).show();
            }
        }
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Log.d("MainActivity", "onNewIntent 被调用");
        handleIncomingTikuIntentIfNeeded();
    }

    /**
     * 处理从错题本传来的数据
     */
    private boolean handleIncomingTikuIntentIfNeeded() {
        Intent currentIntent = getIntent();
        if (currentIntent == null) return false;

        if (currentIntent.hasExtra("load_tiku_name")) {
            String tikuName = currentIntent.getStringExtra("load_tiku_name");
            int tikuIndex = currentIntent.getIntExtra("load_tiku_index", -1);
            String requestedRawFen = currentIntent.getStringExtra("load_tiku_raw_fen");
            currentIntent.removeExtra("load_tiku_name");
            currentIntent.removeExtra("load_tiku_index");
            currentIntent.removeExtra("load_tiku_raw_fen");
            if (TextUtils.isEmpty(tikuName)) {
                ToastUtil.makeText(this, "加载题库失败: 参数无效", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (tikuManager != null) {
                tikuManager.reloadTiku();
            }
            TikuData targetTiku = tikuManager == null ? null : tikuManager.getTikuByName(tikuName);
            if (targetTiku == null) {
                ToastUtil.makeText(this, "加载题库失败: 未找到 " + tikuName, Toast.LENGTH_SHORT).show();
                return true;
            }
            if (tikuIndex > targetTiku.getTotalCount()
                    || (tikuIndex <= 0 && TextUtils.isEmpty(requestedRawFen))) {
                ToastUtil.makeText(this, "加载题库失败: 序号超出范围", Toast.LENGTH_SHORT).show();
                return true;
            }

            int targetPosition = tikuIndex - 1;
            if (!TextUtils.isEmpty(requestedRawFen)) {
                // 收藏夹缩略图传入原始行，优先按内容定位，避免解析后索引错位。
                if (targetPosition < 0
                        || targetPosition >= targetTiku.getTotalCount()
                        || !requestedRawFen.equals(targetTiku.getRawFEN(targetPosition))) {
                    targetPosition = -1;
                    for (int i = 0; i < targetTiku.getTotalCount(); i++) {
                        if (requestedRawFen.equals(targetTiku.getRawFEN(i))) {
                            targetPosition = i;
                            break;
                        }
                    }
                }
            }
            if (targetPosition < 0 || targetPosition >= targetTiku.getTotalCount()) {
                ToastUtil.makeText(this, "加载题目失败: 未找到对应局面", Toast.LENGTH_SHORT).show();
                return true;
            }
            loadSelectedQuestion(targetPosition, targetTiku);
            return true;
        }

        if (currentIntent.hasExtra("load_wrong")) {
            int wrongIndex = currentIntent.getIntExtra("wrong_index", -1);
            Log.d("MainActivity", "handleIncomingTikuIntentIfNeeded - wrongIndex: " + wrongIndex);
            handleWrongBookIntent(wrongIndex);
            currentIntent.removeExtra("load_wrong");
            currentIntent.removeExtra("wrong_index");
            return true;
        }

        if (currentIntent.hasExtra("load_favorite")) {
            int favoriteIndex = currentIntent.getIntExtra("favorite_index", -1);
            Log.d("MainActivity", "handleIncomingTikuIntentIfNeeded - favoriteIndex: "
                    + favoriteIndex);
            handleFavoriteBookIntent(favoriteIndex);
            currentIntent.removeExtra("load_favorite");
            currentIntent.removeExtra("favorite_index");
            return true;
        }
        return false;
    }

    /**
     * 从错题本加载对应序号的题目
     * @param index 错题序号（从1开始）
     */
    private void handleWrongBookIntent(int index) {
        Log.d("MainActivity", "========== handleWrongBookIntent 开始 ==========");
        Log.d("MainActivity", "传入序号: " + index);

        if (index <= 0) {
            ToastUtil.makeText(this, "加载错题失败: 序号无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 重新加载所有题库
        tikuManager.reloadTiku();

        // 打印所有题库名称
        Log.d("MainActivity", "所有题库列表:");
        for (TikuData tiku : tikuManager.getTikuList()) {
            Log.d("MainActivity", "  - " + tiku.getName() + " (" + tiku.getTotalCount() + " 题)");
        }

        // 查找错题本题库
        TikuData wrongBookTiku = null;
        for (TikuData tiku : tikuManager.getTikuList()) {
            if (tiku.getName().equals("错题本")) {
                wrongBookTiku = tiku;
                break;
            }
        }

        if (wrongBookTiku == null) {
            ToastUtil.makeText(this, "错题本不存在", Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "错题本不存在！");
            return;
        }

        Log.d("MainActivity", "找到错题本，共 " + wrongBookTiku.getTotalCount() + " 题");

        // 获取错题本的所有题目
        List<String> wrongList = wrongBookTiku.getFenList();

        // 检查序号是否有效
        int position = index - 1;
        if (position < 0 || position >= wrongList.size()) {
            ToastUtil.makeText(this, "错题序号无效，共 " + wrongList.size() + " 题", Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "序号无效: position=" + position + ", size=" + wrongList.size());
            return;
        }

        // 获取对应序号的FEN
        String fen = wrongList.get(position);
        if (fen == null || fen.isEmpty()) {
            ToastUtil.makeText(this, "加载错题失败", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("MainActivity", "加载FEN: " + fen);

        // 加载题目
        boolean flipped = wrongBookTiku.getFlipped(position);  // 从TikuData获取翻转状态
        String cleanFen = fen;  // currentFenList已去掉2#前缀，直接使用

        PositionValidator.Result fenFormatResult = positionValidator.validateFenFormat(cleanFen);
        if (!fenFormatResult.valid) {
            ToastUtil.makeText(this, "加载错题失败：FEN格式不合法，" + fenFormatResult.reason, Toast.LENGTH_LONG).show();
            Log.w("MainActivity", "错题本FEN格式不合法: " + fenFormatResult.reason + " FEN=" + cleanFen);
            return;
        }

        if (!chessView.setBoardByFEN(cleanFen, flipped)) {
            ToastUtil.makeText(this, "加载残局失败：FEN解析失败", Toast.LENGTH_LONG).show();
            return;
        }

        PositionValidator.Result vr = positionValidator.validate(chessView.chessBoard);
        if (!vr.valid) {
            ToastUtil.makeText(this, "加载错题失败：局面不合法，" + vr.reason, Toast.LENGTH_LONG).show();
            Log.w("MainActivity", "错题本局面不合法: " + vr.reason + " FEN=" + cleanFen);
            return;
        }

        // 设置当前题库为错题本
        currentTiku = wrongBookTiku;
        currentFenList = wrongList;
        currentQuestionIndex = position;
        totalQuestions = wrongList.size();
        tikuManager.setCurrentTiku(wrongBookTiku);

        updateTitle();
        updateAccuracy();
        elapsedSeconds = 0;

        // ===== 更新行棋指示器（红方先走） =====
        updateTurnIndicator(chessView.getCurrentSide());
        // =====================================
        // ===== 设置AI对手方 =====
        setupPikafishForQuestion();
        // =========================
        // ===== 保存当前题目 =====
        saveCurrentQuestionToPreferences();
        // ========================

        ToastUtil.makeText(this, "已加载错题本第 " + index + " 题", Toast.LENGTH_LONG).show();
    }

    /**
     * 从收藏夹缩略图加载对应题目。
     * 与错题本使用独立入口，按收藏夹文件中的序号加载，避免走通用题库分支。
     *
     * @param index 收藏夹题目序号（从1开始）
     */
    private void handleFavoriteBookIntent(int index) {
        Log.d("MainActivity", "========== handleFavoriteBookIntent 开始 ==========");
        Log.d("MainActivity", "传入序号: " + index);

        if (index <= 0) {
            ToastUtil.makeText(this, "加载收藏夹失败: 序号无效", Toast.LENGTH_SHORT).show();
            return;
        }

        tikuManager.reloadTiku();

        TikuData favoriteBookTiku = null;
        for (TikuData tiku : tikuManager.getTikuList()) {
            if ("收藏夹".equals(tiku.getName())) {
                favoriteBookTiku = tiku;
                break;
            }
        }

        if (favoriteBookTiku == null) {
            ToastUtil.makeText(this, "收藏夹不存在", Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "收藏夹不存在！");
            return;
        }

        List<String> favoriteList = favoriteBookTiku.getFenList();
        int position = index - 1;
        if (position < 0 || position >= favoriteList.size()) {
            ToastUtil.makeText(this, "收藏夹序号无效，共 " + favoriteList.size() + " 题",
                    Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "收藏夹序号无效: position=" + position
                    + ", size=" + favoriteList.size());
            return;
        }

        String fen = favoriteList.get(position);
        if (fen == null || fen.isEmpty()) {
            ToastUtil.makeText(this, "加载收藏夹失败", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("MainActivity", "加载收藏夹FEN: " + fen);

        boolean flipped = favoriteBookTiku.getFlipped(position);
        PositionValidator.Result fenFormatResult = positionValidator.validateFenFormat(fen);
        if (!fenFormatResult.valid) {
            ToastUtil.makeText(this,
                    "加载收藏夹失败：FEN格式不合法，" + fenFormatResult.reason,
                    Toast.LENGTH_LONG).show();
            Log.w("MainActivity", "收藏夹FEN格式不合法: " + fenFormatResult.reason
                    + " FEN=" + fen);
            return;
        }

        if (!chessView.setBoardByFEN(fen, flipped)) {
            ToastUtil.makeText(this, "加载收藏夹失败：FEN解析失败", Toast.LENGTH_LONG).show();
            return;
        }

        PositionValidator.Result vr = positionValidator.validate(chessView.chessBoard);
        if (!vr.valid) {
            ToastUtil.makeText(this,
                    "加载收藏夹失败：局面不合法，" + vr.reason,
                    Toast.LENGTH_LONG).show();
            Log.w("MainActivity", "收藏夹局面不合法: " + vr.reason + " FEN=" + fen);
            return;
        }

        currentTiku = favoriteBookTiku;
        currentFenList = favoriteList;
        currentQuestionIndex = position;
        totalQuestions = favoriteList.size();
        tikuManager.setCurrentTiku(favoriteBookTiku);

        updateTitle();
        updateAccuracy();
        elapsedSeconds = 0;
        updateTurnIndicator(chessView.getCurrentSide());
        setupPikafishForQuestion();
        saveCurrentQuestionToPreferences();

        ToastUtil.makeText(this, "已加载收藏夹第 " + index + " 题", Toast.LENGTH_LONG).show();
    }

    // ========== 权限相关方法 ==========

    /**
     * 检查并请求存储权限
     */
    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            if (!Environment.isExternalStorageManager()) {
                requestManageStoragePermission();
            }
        } else {
            // Android 10 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_CODE);
            }
        }
    }

    /**
     * 请求 MANAGE_EXTERNAL_STORAGE 权限 (Android 11+)
     */
    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Uri uri = Uri.parse("package:" + getPackageName());
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
                startActivityForResult(intent, REQUEST_PERMISSION_CODE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_PERMISSION_CODE);
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ToastUtil.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show();
                createNecessaryFolders();
                if (tikuManager != null) {
                    tikuManager.reloadTiku();
                }
                // 权限授予后重新加载进度
                ProgressManager pm = ProgressManager.getInstance(this);
                pm.reload();
                if (!restoreCurrentQuestion()) {
                    // 没有可恢复的存档（首次运行）→ 默认加载"象棋杀着大全"第1题；
                    // 若该题库不存在则退回到题库列表第1个
                    TikuData defaultTiku = tikuManager.getTikuByName("象棋杀着大全");
                    if (defaultTiku == null && !tikuManager.getTikuList().isEmpty()) {
                        defaultTiku = tikuManager.getTikuList().get(0);
                    }
                    if (defaultTiku != null) {
                        currentTiku = defaultTiku;
                        tikuManager.setCurrentTiku(currentTiku);
                        totalQuestions = currentTiku.getTotalCount();
                        currentQuestionIndex = 0;
                        loadCurrentQuestion();
                    }
                }
            } else {
                ToastUtil.makeText(this, "需要存储权限才能管理题库文件", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    ToastUtil.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show();
                    createNecessaryFolders();
                    if (tikuManager != null) {
                        tikuManager.reloadTiku();
                    }
                    // 权限授予后重新加载进度
                    ProgressManager pm = ProgressManager.getInstance(this);
                    pm.reload();
                    if (!restoreCurrentQuestion()) {
                        // 没有可恢复的存档（首次运行）→ 默认加载"象棋杀着大全"第1题；
                        // 若该题库不存在则退回到题库列表第1个
                        TikuData defaultTiku = tikuManager.getTikuByName("象棋杀着大全");
                        if (defaultTiku == null && !tikuManager.getTikuList().isEmpty()) {
                            defaultTiku = tikuManager.getTikuList().get(0);
                        }
                        if (defaultTiku != null) {
                            currentTiku = defaultTiku;
                            tikuManager.setCurrentTiku(currentTiku);
                            totalQuestions = currentTiku.getTotalCount();
                            currentQuestionIndex = 0;
                            loadCurrentQuestion();
                        }
                    }
                } else {
                    ToastUtil.makeText(this, "需要存储权限才能管理题库文件", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * 检查是否有存储权限
     */
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * 创建必要的文件夹（使用外部存储根目录）
     * 结构：/storage/emulated/0/canju12/uistyle/
     *       /storage/emulated/0/canju12/tiku/
     */
    private void createNecessaryFolders() {
        try {
            // 检查权限
            if (!hasStoragePermission()) {
                Log.d("MainActivity", "没有存储权限，跳过创建文件夹");
                return;
            }

            // 获取外部存储根目录
            File externalDir = Environment.getExternalStorageDirectory();
            File canju12Dir = new File(externalDir, "canju12");

            // 创建 canju12 主文件夹
            if (!canju12Dir.exists()) {
                canju12Dir.mkdirs();
            }

            // 创建 uistyle 文件夹
            File uiStyleDir = new File(canju12Dir, "uistyle");
            if (!uiStyleDir.exists()) {
                uiStyleDir.mkdirs();
            }

            // 创建 tiku 文件夹
            File tikuDir = new File(canju12Dir, "tiku");
            if (!tikuDir.exists()) {
                tikuDir.mkdirs();
            }

            // ========== 在 tiku 文件夹下创建 错题本.txt 文件 ==========
            File wrongBookFile = new File(tikuDir, "错题本.txt");
            if (!wrongBookFile.exists()) {
                try {
                    if (wrongBookFile.createNewFile()) {
                        Log.d("MainActivity", "错题本.txt文件创建成功: " + wrongBookFile.getAbsolutePath());
                        ToastUtil.makeText(this, "错题本.txt文件已创建", Toast.LENGTH_SHORT).show();

                        // 写入初始内容
                        java.io.FileWriter writer = new java.io.FileWriter(wrongBookFile);
                        writer.write("# 错题本\n");
                        writer.write("# 格式：FEN | 标记时间\n");
                        writer.write("# 每行一个错题\n");
                        writer.close();
                        Log.d("MainActivity", "错题本.txt初始内容写入成功");

                    } else {
                        Log.e("MainActivity", "错题本.txt文件创建失败");
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "创建错题本.txt文件异常: " + e.getMessage());
                }
            } else {
                Log.d("MainActivity", "错题本.txt文件已存在: " + wrongBookFile.getAbsolutePath());
            }

            // ========== 在 tiku 文件夹下创建 收藏夹.txt 文件 ==========
            File favoriteBookFile = new File(tikuDir, "收藏夹.txt");
            if (!favoriteBookFile.exists()) {
                try {
                    if (favoriteBookFile.createNewFile()) {
                        Log.d("MainActivity", "收藏夹.txt文件创建成功: " + favoriteBookFile.getAbsolutePath());
                        java.io.FileWriter writer = new java.io.FileWriter(favoriteBookFile);
                        writer.write("# 收藏夹\n");
                        writer.write("# 格式：FEN | 标记时间\n");
                        writer.write("# 每行一个收藏夹\n");
                        writer.close();
                    } else {
                        Log.e("MainActivity", "收藏夹.txt文件创建失败");
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "创建收藏夹.txt文件异常: " + e.getMessage());
                }
            } else {
                Log.d("MainActivity", "收藏夹.txt文件已存在: " + favoriteBookFile.getAbsolutePath());
            }

            // 打印最终结果
            Log.d("MainActivity", "========== 文件夹创建完成 ==========");
            Log.d("MainActivity", "canju12: " + canju12Dir.getAbsolutePath() + " 存在=" + canju12Dir.exists());
            Log.d("MainActivity", "uistyle: " + uiStyleDir.getAbsolutePath() + " 存在=" + uiStyleDir.exists());
            Log.d("MainActivity", "tiku: " + tikuDir.getAbsolutePath() + " 存在=" + tikuDir.exists());
            Log.d("MainActivity", "错题本.txt: " + wrongBookFile.getAbsolutePath() + " 存在=" + wrongBookFile.exists());
            Log.d("MainActivity", "收藏夹.txt: " + favoriteBookFile.getAbsolutePath() + " 存在=" + favoriteBookFile.exists());

        } catch (Exception e) {
            Log.e("MainActivity", "创建文件夹失败: " + e.getMessage());
            ToastUtil.makeText(this, "创建文件夹失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    // ========== 诗词相关方法 ==========
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
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                minuteCounter++;
                if (minuteCounter % 10 == 0) {
                    displaySpecialMessage();
                } else {
                    showRandomPoetry();
                }
                handler.postDelayed(this, MINUTE_DELAY);
            }
        }, MINUTE_DELAY);
    }

    private void showRandomPoetry() {
        if (tvShiju == null || poetryList == null || poetryList.isEmpty()) return;
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
        if (tvShiju == null) return;
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
        if (textView == null) return;
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

    // ========== 初始化方法 ==========
    private void initViews() {
        tvTitle = findViewById(R.id.tv_title);
        tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        tvTitle.setMaxLines(1);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitleDetail = findViewById(R.id.tv_title_detail);
        if (tvTitleDetail != null) {
            tvTitleDetail.setMaxLines(1);
            tvTitleDetail.setEllipsize(TextUtils.TruncateAt.END);
        }
        tvTime = findViewById(R.id.tv_time);
        tvTime.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvTime.setIncludeFontPadding(false);
        tvTime.setSingleLine(true);
        tvTime.setMaxLines(1);
        tvTime.setEllipsize(TextUtils.TruncateAt.END);
        tvTime.setGravity(android.view.Gravity.CENTER);
        tvTime.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvAccuracy = findViewById(R.id.tv_accuracy);
        tvAccuracy.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvAccuracy.setIncludeFontPadding(false);
        tvAccuracy.setSingleLine(true);
        tvAccuracy.setMaxLines(1);
        tvAccuracy.setEllipsize(TextUtils.TruncateAt.END);
        tvAccuracy.setGravity(android.view.Gravity.CENTER);
        tvAccuracy.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        chessView = findViewById(R.id.chessView);
        tvShiju = findViewById(R.id.shiju);

        btnTiku = findViewById(R.id.btnTiku);
        btnSelectLevel = findViewById(R.id.btnSelectLevel);
        btnRegret = findViewById(R.id.btnRegret);
        btnCopy = findViewById(R.id.btnCopy);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnNonono = findViewById(R.id.btnNonono);
        btnYesyes = findViewById(R.id.btnYesyes);
        tvQQGroup = findViewById(R.id.tvQQGroup);
        // 动态设置版本文字，从BuildConfig读取版本名
        tvQQGroup.setText("一二残局 V" + BuildConfig.VERSION_NAME + "    ");

        ivTurnIndicator = findViewById(R.id.ivTurnIndicator);  // 确保这行存在

        // 延迟设置背景
        chessView.post(new Runnable() {
            @Override
            public void run() {
                setupBackgrounds();
            }
        });
    }
    /**
     * 设置棋盘和房间背景
     */
    private void setupBackgrounds() {
        View mainLayout = null;
        ImageView ivBoard = null;

        try {
            // 获取 ChessView 中加载的位图
            Bitmap boardBitmap = chessView.getBoardBitmap();
            Bitmap roomBitmap = chessView.getRoomBitmap();

            // 设置房间背景
            mainLayout = findViewById(R.id.mainLayout);
            if (roomBitmap != null) {
                android.graphics.drawable.BitmapDrawable roomDrawable =
                        new android.graphics.drawable.BitmapDrawable(getResources(), roomBitmap);
                mainLayout.setBackground(roomDrawable);
                Log.d("MainActivity", "房间背景设置成功（外部）");
            } else {
                mainLayout.setBackgroundResource(R.drawable.room);
                Log.d("MainActivity", "房间背景使用默认");
            }

            // 设置棋盘背景
            ivBoard = findViewById(R.id.ivBoard);
            if (boardBitmap != null) {
                ivBoard.setImageBitmap(boardBitmap);
                Log.d("MainActivity", "棋盘背景设置成功（外部）");
            } else {
                ivBoard.setImageResource(R.drawable.board);
                Log.d("MainActivity", "棋盘背景使用默认");
            }

            applyRoomButtonTheme(roomBitmap);

        } catch (Throwable e) {
            // 用 Throwable 而非 Exception：内置 PNG 在某些硬件上解码可能抛 OOM 等 Error
            Log.e("MainActivity", "设置背景失败: " + e.getMessage());
            // 异常时使用纯色背景兜底（不再回头去用可能正是它导致崩溃的 drawable）
            try {
                if (mainLayout == null) {
                    mainLayout = findViewById(R.id.mainLayout);
                }
                if (mainLayout != null) {
                    mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#E8D5A2"));
                }
            } catch (Throwable ignored) { }
            // ivBoard 留空即可，ChessView 自身会按 null 保护绘制
        }
    }

    private void applyRoomButtonTheme(Bitmap roomBitmap) {
        try {
            View content = findViewById(android.R.id.content);
            UiThemeHelper.applyRoomButtonTheme(this, content, roomBitmap);
        } catch (Throwable t) {
            Log.w("MainActivity", "按钮主题应用失败: " + t.getMessage());
        }
    }

    private void setupListeners() {
        btnTiku.setOnClickListener(v -> showTikuSelectorDialog());
        btnSelectLevel.setOnClickListener(v -> showLevelDialog());
        btnRegret.setOnClickListener(v -> regretMove());
        // 收藏夹按钮：只保存当前题目，不统计正确率，也不自动进入下一题
        btnCopy.setOnClickListener(v -> addCurrentQuestionToFavoriteBook());
        btnCopy.setOnLongClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, FavoriteBookActivity.class);
            startActivity(intent);
            return true;
        });
        btnRepeat.setOnClickListener(v -> {
            // 从当前题库重新加载本题
            loadCurrentQuestion();
            questionRecorded = true;
        });
        // 长按重来 → 载入上一题；若是第一题则提示
        btnRepeat.setOnLongClickListener(v -> {
            if (pageFlipRunning) {
                return true;
            }
            if (currentTiku == null) {
                ToastUtil.makeText(this, "未选择题库", Toast.LENGTH_SHORT).show();
                return true;
            }
            currentFenList = currentTiku.getFenList();
            if (currentFenList == null || currentFenList.isEmpty()) {
                ToastUtil.makeText(this, "题库为空", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (currentQuestionIndex <= 0) {
                ToastUtil.makeText(this, "已是题库中第一题！", Toast.LENGTH_SHORT).show();
                return true;
            }
            preparePageFlip(-1);
            loadValidQuestion(currentQuestionIndex - 1, 0);
            ToastUtil.makeText(this, "已载入上一局！", Toast.LENGTH_SHORT).show();
            return true;
        });
        btnAnalyze.setOnClickListener(v -> analyzePosition());
        btnNonono.setOnClickListener(v -> markAsNotMastered());
        btnYesyes.setOnClickListener(v -> markAsMastered());
        ivTurnIndicator.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastTurnIndicatorClickTime < 350L) {
                lastTurnIndicatorClickTime = 0L;
                copyFEN();
            } else {
                lastTurnIndicatorClickTime = now;
            }
        });
        // 长按版本号打开使用说明
        tvQQGroup.setOnLongClickListener(v -> {
            showQQGroupDialog();
            return true;
        });
        // 长按行棋指示器进入错题本
        ivTurnIndicator.setOnLongClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WrongBookActivity.class);
            startActivity(intent);
            return true;
        });
        // ====================================
    }
    /**
     * 显示致谢和使用说明对话框（加载 assets/readme.html）
     */
    private void showQQGroupDialog() {
        try {
            showHtmlAssetDialog("readme.html", "知道了", true);

        } catch (Exception e) {
            Log.e("MainActivity", "显示说明对话框失败: " + e.getMessage());
            // 降级为Toast
            ToastUtil.makeText(this, "QQ群：635808985 · 1003608168 · 94846686", Toast.LENGTH_LONG).show();
        }
    }

    private void setupChessView() {
        // 设置走棋方变化监听
        chessView.setOnTurnChangeListener(new ChessView.OnTurnChangeListener() {
            @Override
            public void onTurnChange(int side) {
                updateTurnIndicator(side);
            }
        });
        chessView.setMoveSoundEnabled(true);

        chessView.setOnGameListener(new ChessView.OnGameListener() {
            @Override
            public void onMove(int fromY, int fromX, int toY, int toX, int chessType) {
                String fen = chessView.getFEN();
                Log.d("ChessFEN", "当前局面FEN: " + fen);

                // ===== Pikafish 对战模式 =====
                if (pikafishMode && !isPikafishThinking) {
                    // 记录到和棋规则（传棋盘、走棋方、是否捉用于循环判定）
                    chineseRules.recordMove(
                            chessView.isLastMoveCapture(),
                            chessView.isLastMoveCheck(),
                            -chessView.getCurrentSide(),
                            chessView.chessBoard,
                            chessView.getCurrentSide());

                    // 检查长将判负
                    if (chineseRules.isViolation() && !longCheckHandled && !questionFinished) {
                        longCheckHandled = true;
                        ChineseRules.ViolationResult vr = chineseRules.getViolationResult();
                        int loserSide = chineseRules.getViolationSide();
                        if (vr != null && loserSide != 0) {
                            finishAiQuestion(-loserSide, vr.description);
                        }
                        return;
                    }

                    // 检查是否和棋（含重复局面判和）
                    ChineseRules.DrawResult result = chineseRules.checkDraw(chessView.chessBoard);
                    if (result.isDraw) {
                        drawDetected();
                        return;
                    }

                    // 重复局面警告（循环正在形成，尚未达到判定阈值）
                    if (chineseRules.isRepetitionWarningActive()
                            && !chineseRules.isRepetitionToastShown()
                            && !longCheckHandled) {
                        chineseRules.markRepetitionToastShown();
                        ToastUtil.makeText(MainActivity.this,
                                "出现重复局面，请变招",
                                Toast.LENGTH_SHORT).show();
                    }

                    // 如果轮到 Pikafish 走棋，触发AI
                    if (chessView.getCurrentSide() == pikafishSide
                            && !isPikafishThinking
                            && !questionFinished) {
                        triggerPikafish();
                    }
                }
                // =================================
            }
            @Override
            public void onGameOver(int winner) {
                if (!isActivityAlive()) return;
                if (pikafishMode) {
                    // AI对战模式
                    finishAiQuestion(winner, null);
                } else {
                    // 普通练习模式（原有逻辑）
                    if (winner == 1) {
                        if (!questionFinished && !questionRecorded) {
                            winCount++; questionRecorded = true;
                        }
                        questionFinished = true;
                        updateAccuracy();
                        markQuestionCompleted();
                        ToastUtil.makeText(MainActivity.this, "🎉 红方胜利！进入下一题", Toast.LENGTH_SHORT).show();
                        uiHandler.postDelayed(() -> nextQuestion(), 800);
                    } else {
                        ToastUtil.makeText(MainActivity.this, "黑方胜利！", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    /**
     * 更新行棋指示器图标
     * @param side 当前走棋方（1=红方，-1=黑方）
     */
    private void updateTurnIndicator(int side) {
        UiThemeHelper.applyTurnIndicatorImage(this, ivTurnIndicator, side);
    }
    private void initData() {
        updateTitle();
        updateAccuracy();
        showCurrentDate();

        // 初始状态红方先走
        updateTurnIndicator(chessView.getCurrentSide());
    }

    /**
     * 显示当前日期（带加油语）
     */
    private void showCurrentDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.US);
        String currentDate = sdf.format(new java.util.Date());
        tvAccuracy.setText("🔥" + currentDate + "🔥");
    }


    // ========== UI更新方法 ==========
    /**
     * 更新标题显示（两行：题库名称 + 题目序号）
     */
    private void updateTitle() {
        if (currentTiku != null && currentFenList != null && currentQuestionIndex < currentFenList.size()) {
            String questionTitle = currentTiku.getQuestionTitle(currentQuestionIndex);
            String secondLine = "第 " + (currentQuestionIndex + 1) + "/" + currentFenList.size() + " 题";
            if (questionTitle != null && !questionTitle.isEmpty()) {
                secondLine += " " + questionTitle;
            }
            tvTitle.setText(currentTiku.getName());
            if (tvTitleDetail != null) {
                tvTitleDetail.setText(secondLine);
            }
        } else if (currentTiku != null) {
            tvTitle.setText(currentTiku.getName());
            if (tvTitleDetail != null) {
                tvTitleDetail.setText("已完成");
            }
        } else {
            tvTitle.setText("请选择题库");
            if (tvTitleDetail != null) {
                tvTitleDetail.setText("");
            }
        }
    }

    /**
     * 更新正确率/进度显示
     * 公式：(获胜数 + 已会数) / (获胜数 + 已会数 + 不会数) * 100%
     */
    private void updateAccuracy() {
        // 计算总题数（做题总数 = 获胜 + 已会 + 不会）
        int total = winCount + masteredCount + notMasteredCount;

        if (total == 0) {
            // 还没开始做题，显示当前日期和加油语
            showCurrentDate();
            return;
        }

        // 计算已完成题数（获胜 + 已会）
        int doneCount = winCount + masteredCount;

        // 计算百分比
        int percentage = (doneCount * 100) / total;

        // 显示进度和正确率
        String accuracyText = String.format(java.util.Locale.US, "进度/正确率：%03d/%03d (%d%%)",
                doneCount, total, percentage);
        tvAccuracy.setText(accuracyText);
    }



    // ========== 计时器方法 ==========
    private void startTimer() {
        startQuestionTimer();
        startTotalTimer();
    }
    /**
     * 重置统计数据
     */
    private void resetStatistics() {
        winCount = 0;
        masteredCount = 0;
        notMasteredCount = 0;
        updateAccuracy();
    }
    private void startQuestionTimer() {
        // 如果计时器已暂停，不重新启动
        if (isTimerPaused) return;

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                elapsedSeconds++;
                updateTimeDisplay();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void startTotalTimer() {
        // 如果计时器已暂停，不重新启动
        if (isTimerPaused) return;

        totalTimerRunnable = new Runnable() {
            @Override
            public void run() {
                totalElapsedSeconds++;
                updateTimeDisplay();
                // 每60秒保存一次累计时间
                if (totalElapsedSeconds % 60 == 0) {
                    ProgressManager pm = ProgressManager.getInstance(MainActivity.this);
                    pm.addPlayTime(60);
                    if (pm.needsUnlockNotification()) {
                        pm.setUnlockNotified(true);
                        showUnlockDialog();
                    }
                }
                totalTimerHandler.postDelayed(this, 1000);
            }
        };
        totalTimerHandler.postDelayed(totalTimerRunnable, 1000);
    }

    /**
     * 暂停所有计时器
     */
    private void pauseTimers() {
        if (isTimerPaused) return;

        isTimerPaused = true;

        // 移除所有计时器的回调
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (totalTimerHandler != null && totalTimerRunnable != null) {
            totalTimerHandler.removeCallbacks(totalTimerRunnable);
        }

        // 保存当前时间
        pausedElapsedSeconds = elapsedSeconds;
        pausedTotalElapsedSeconds = totalElapsedSeconds;

        Log.d("MainActivity", "计时器已暂停");
    }

    /**
     * 恢复所有计时器
     */
    private void resumeTimers() {
        if (!isTimerPaused) return;

        isTimerPaused = false;

        // 恢复计时器
        startQuestionTimer();
        startTotalTimer();

        Log.d("MainActivity", "计时器已恢复");
    }
    @Override
    protected void onPause() {
        super.onPause();
        // 应用进入后台，暂停计时器并保存累计时间
        pauseTimers();
        if (totalElapsedSeconds > 0) {
            ProgressManager pm = ProgressManager.getInstance(this);
            pm.addPlayTime(totalElapsedSeconds);
            if (pm.needsUnlockNotification()) {
                pm.setUnlockNotified(true);
                showUnlockDialog();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 恢复计时器
        resumeTimers();

        // ===== 每次回到前台时重新加载进度（确保进度最新） =====
        ProgressManager.getInstance(this).reload();
        // 刷新进度显示
        updateAccuracy();
        // 如果选关页面已打开，刷新适配器
        // 注意：这里需要刷新选关页面的适配器
        // ====================================================
    }
    private void updateTimeDisplay() {
        int elapsedMinutes = elapsedSeconds / 60;
        int elapsedSecs = elapsedSeconds % 60;
        int totalMinutes = totalElapsedSeconds / 60;
        int totalSecs = totalElapsedSeconds % 60;

        String timeText = String.format(java.util.Locale.US, "本题用时：%02d:%02d | 总用时：%02d:%02d",
                elapsedMinutes, elapsedSecs, totalMinutes, totalSecs);
        tvTime.setText(timeText);
    }


    private void loadCurrentQuestion() {
        if (chessView == null) {
            Log.e("MainActivity", "chessView 为 null");
            return;
        }

        if (currentTiku == null) {
            ToastUtil.makeText(this, "请选择题库", Toast.LENGTH_SHORT).show();
            return;
        }

        // ===== 加载当前题库的进度（由 ProgressManager 管理） =====
        // =====================================================

        currentFenList = currentTiku.getFenList();
        if (currentFenList == null || currentFenList.isEmpty()) {
            ToastUtil.makeText(this, "题库为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentQuestionIndex >= currentFenList.size()) {
            ToastUtil.makeText(this, "🎉 已完成所有题目！", Toast.LENGTH_LONG).show();
            return;
        }

        // 从当前题号开始尝试加载，跳过不合法局面（最多跳过10题防死循环）
        loadValidQuestion(currentQuestionIndex, 0);
    }

    /**
     * 从指定题号开始加载，若局面不合法则提示并自动跳到下一题。
     * @param index     要加载的题号
     * @param skipCount 已连续跳过的题数（防死循环，上限10）
     */
    private void loadValidQuestion(int index, int skipCount) {
        if (!isActivityAlive() || chessView == null || currentTiku == null) {
            return;
        }
        if (currentFenList == null || index >= currentFenList.size()) {
            // 已到题库末尾：不重置索引，直接重载当前最后一题，方便用户点"重来"恢复棋局
            if (currentTiku != null && currentFenList != null && !currentFenList.isEmpty()) {
                ToastUtil.makeText(this, "🎉 已完成所有题目！", Toast.LENGTH_LONG).show();
                loadCurrentQuestion();
            } else {
                ToastUtil.makeText(this, "🎉 已完成所有题目！", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (index < 0) {
            index = 0;
        }
        if (skipCount > 10) {
            ToastUtil.makeText(this, "连续多题局面不合法，已停止", Toast.LENGTH_LONG).show();
            return;
        }

        String fen = currentFenList.get(index);
        boolean isFlipped = currentTiku.getFlipped(index);

        if (fen == null) {
            // FEN 为空，视为不合法，跳过
            currentQuestionIndex = index + 1;
            loadValidQuestion(currentQuestionIndex, skipCount + 1);
            return;
        }

        PositionValidator.Result fenFormatResult = positionValidator.validateFenFormat(fen);
        if (!fenFormatResult.valid) {
            Log.w("MainActivity", "FEN格式不合法[题" + (index + 1) + "]: " + fenFormatResult.reason + " FEN=" + fen);
            ToastUtil.makeText(this,
                    "第" + (index + 1) + "题FEN格式不合法：" + fenFormatResult.reason + "，即将跳到下一题",
                    Toast.LENGTH_LONG).show();
            currentQuestionIndex = index + 1;
            final int nextIdx = currentQuestionIndex;
            final int nextSkip = skipCount + 1;
            uiHandler.postDelayed(() -> loadValidQuestion(nextIdx, nextSkip), 1200);
            return;
        }

        // 先把 FEN 载入棋盘以便校验
        String initialFEN = currentTiku.getInitialFEN(index);
        if (initialFEN != null && !initialFEN.isEmpty()) {
            chessView.setCurrentInitialFEN(initialFEN);
        } else {
            chessView.setCurrentInitialFEN(fen);
        }
        if (!chessView.setBoardByFEN(fen, isFlipped)) {
            Log.w("MainActivity", "FEN解析失败，跳过题目: " + fen);
            currentQuestionIndex = index + 1;
            final int nextIdx = currentQuestionIndex;
            final int nextSkip = skipCount + 1;
            uiHandler.postDelayed(() -> loadValidQuestion(nextIdx, nextSkip), 1200);
            return;
        }

        // ===== 局面合法性检查 =====
        PositionValidator.Result vr = positionValidator.validate(chessView.chessBoard);
        if (!vr.valid) {
            Log.w("MainActivity", "局面不合法[题" + (index + 1) + "]: " + vr.reason + " FEN=" + fen);
            ToastUtil.makeText(this,
                    "第" + (index + 1) + "题局面不合法：" + vr.reason + "，即将跳到下一题",
                    Toast.LENGTH_LONG).show();
            currentQuestionIndex = index + 1;
            // 延迟跳题，让 toast 有时间显示
            final int nextIdx = currentQuestionIndex;
            final int nextSkip = skipCount + 1;
            uiHandler.postDelayed(() -> loadValidQuestion(nextIdx, nextSkip), 1200);
            return;
        }
        // ===========================

        // 合法 → 正式加载
        currentQuestionIndex = index;
        currentTiku.goToIndex(index);
        playPendingPageFlip();
        totalQuestions = currentFenList.size();
        updateTitle();
        updateAccuracy();
        elapsedSeconds = 0;

        updateTurnIndicator(chessView.getCurrentSide());

        // ===== 设置AI对手方 =====
        setupPikafishForQuestion();
        // =========================
        questionRecorded = false;

        // ===== 保存当前题目 =====
        saveCurrentQuestionToPreferences();
        // ========================
    }



    /**
     * 显示题库选择对话框
     */
    private void showTikuSelectorDialog() {
        showTikuSelectorDialog(false);
    }

    private void showTikuSelectorDialog(boolean forceReload) {
        try {
            // 使用外部存储根目录下的 canju12 文件夹
            File externalDir = Environment.getExternalStorageDirectory();
            File canju12Dir = new File(externalDir, "canju12");
            if (!canju12Dir.exists()) {
                canju12Dir.mkdirs();
            }

            File tikuDir = new File(canju12Dir, "tiku");
            if (!tikuDir.exists()) {
                tikuDir.mkdirs();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📚 选择题库");

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_tiku_selector, null);
            builder.setView(dialogView);

            ListView listView = dialogView.findViewById(R.id.listViewTiku);
            TextView tvTotalInfo = dialogView.findViewById(R.id.tvTotalInfo);
            if (listView == null || tvTotalInfo == null || tikuManager == null) {
                Log.e("MainActivity", "题库选择器控件或管理器为空");
                ToastUtil.makeText(this, "题库列表加载失败", Toast.LENGTH_SHORT).show();
                return;
            }

            List<TikuData> tikuList = new ArrayList<>(tikuManager.getTikuList());

            // 过滤已隐藏的题库
            android.content.SharedPreferences prefs = getSharedPreferences("chess_data", MODE_PRIVATE);
            String hiddenStr = prefs.getString("hidden_tikus", "");
            if (!hiddenStr.isEmpty()) {
                String[] hiddenArr = hiddenStr.split("\\|");
                java.util.Set<String> hiddenSet = new java.util.HashSet<>();
                for (String h : hiddenArr) hiddenSet.add(h);
                // "象棋杀着大全"、"错题本" 和 "收藏夹" 始终显示
                hiddenSet.remove("错题本");
                hiddenSet.remove("收藏夹");
                // 象棋杀着大全如果在隐藏列表中则不显示
                tikuList.removeIf(t -> hiddenSet.contains(t.getName()));
            }

            // 隐藏题库解锁检查
            ProgressManager pm = ProgressManager.getInstance(MainActivity.this);
            boolean unlocked = pm.isUnlocked();
            if (!unlocked) {
                tikuList.removeIf(t -> t.isFromYincang());
            } else if (!pm.isUnlockNotified()) {
                pm.setUnlockNotified(true);
                showUnlockDialog();
            }

            // 排序：错题本第一，收藏夹第二，象棋杀着大全第三，其余按字母升序
            java.util.Collections.sort(tikuList, (a, b) -> {
                String na = a.getName();
                String nb = b.getName();
                if (na.equals("错题本")) return -1;
                if (nb.equals("错题本")) return 1;
                if (na.equals("收藏夹")) return -1;
                if (nb.equals("收藏夹")) return 1;
                if (na.equals("象棋杀着大全")) return -1;
                if (nb.equals("象棋杀着大全")) return 1;
                return na.compareTo(nb);
            });

            tvTotalInfo.setText("共 " + tikuList.size() + " 个题库");

            TikuSelectorAdapter adapter = new TikuSelectorAdapter(this, tikuList);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    try {
                    Object selectedItem = parent.getItemAtPosition(position);
                    if (!(selectedItem instanceof TikuData)) {
                        Log.w("MainActivity", "题库点击对象已失效: " + position);
                        return;
                    }
                    TikuData selectedTiku = (TikuData) selectedItem;
                    if (selectedTiku.getTotalCount() <= 0) {
                        ToastUtil.makeText(MainActivity.this,
                                "该题库没有可加载的有效题目", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // ===== 计算选关面板的高亮索引（在修改currentTiku之前判断） =====
                    int highlightIndex;
                    if (currentTiku != null && currentTiku.getName().equals(selectedTiku.getName())) {
                        // 是当前题库 → 高亮当前题目
                        highlightIndex = currentQuestionIndex;
                    } else {
                        // 不是当前题库 → 查找progress中已完成序号最大的题目
                        int maxCompleted = pm.getMaxCompletedIndex(selectedTiku.getName());
                        if (maxCompleted >= 0) {
                            // 有完成记录 → 高亮最大已完成序号的下一题（即当前应做的题）
                            // 如果下一题越界则高亮最大已完成序号本身
                            highlightIndex = (maxCompleted + 1 < selectedTiku.getTotalCount())
                                    ? maxCompleted + 1 : maxCompleted;
                        } else {
                            // 没有完成记录 → 高亮第1题
                            highlightIndex = 0;
                        }
                    }
                    // =====================================

                    currentTiku = selectedTiku;
                    tikuManager.setCurrentTiku(selectedTiku);
                    totalQuestions = selectedTiku.getTotalCount();

                    // ===== 保存当前选中的题库到SharedPreferences =====
                    saveCurrentTikuToPreferences(selectedTiku.getName());
                    // ================================================

                    // 显示选关页面
                    if (tikuDialog != null && tikuDialog.isShowing()) {
                        tikuDialog.dismiss();
                    }
                    tikuDialog = null;
                    showQuestionSelectorDialog(selectedTiku, highlightIndex);
                    } catch (Throwable t) {
                        Log.e("MainActivity", "打开题库失败", t);
                        ToastUtil.makeText(MainActivity.this,
                                "打开题库失败，请检查题库文件格式", Toast.LENGTH_LONG).show();
                    }
                }
            });

            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                Object selectedItem = parent.getItemAtPosition(position);
                if (!(selectedItem instanceof TikuData)) {
                    Log.w("MainActivity", "题库长按对象已失效: " + position);
                    return true;
                }
                TikuData tiku = (TikuData) selectedItem;
                String name = tiku.getName();
                // 检查外置文件
                File extFile = new File(new File(externalDir, "canju12/tiku"), tiku.getFileName());

                // "错题本" 和 "收藏夹" 不可删除也不可隐藏
                if (name.equals("错题本") || name.equals("收藏夹")) {
                    ToastUtil.makeText(MainActivity.this, name + "不可删除也不可隐藏", Toast.LENGTH_SHORT).show();
                    return true;
                }

                // "象棋杀着大全" 做完才可隐藏，否则 toast
                if (name.equals("象棋杀着大全")) {
                    if (!ProgressManager.getInstance(MainActivity.this).isTikuFullyCompleted(name, tiku.getTotalCount())) {
                        ToastUtil.makeText(MainActivity.this, "不可隐藏不可删除", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    // 已完成 → 弹窗确认隐藏
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("隐藏题库")
                            .setMessage("确定隐藏内置题库「" + name + "」吗？")
                            .setPositiveButton("隐藏", (di, wi) -> {
                                android.content.SharedPreferences sp2 = getSharedPreferences("chess_data", MODE_PRIVATE);
                                String h2 = sp2.getString("hidden_tikus", "");
                                h2 = h2.isEmpty() ? name : h2 + "|" + name;
                                sp2.edit().putString("hidden_tikus", h2).apply();
                                refreshExternalTikuInBackground(null);
                                if (tikuDialog != null && tikuDialog.isShowing()) tikuDialog.dismiss();
                                showTikuSelectorDialog(false);
                                ToastUtil.makeText(MainActivity.this, "已隐藏", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                }

                if (extFile.exists()) {
                    // 外置文件 → 删除确认
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("删除题库")
                            .setMessage("确定删除外置题库「" + name + "」吗？")
                            .setPositiveButton("删除", (di, wi) -> {
                                extFile.delete();
                                refreshExternalTikuInBackground(null);
                                if (tikuDialog != null && tikuDialog.isShowing()) tikuDialog.dismiss();
                                showTikuSelectorDialog(false);
                                ToastUtil.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                } else {
                    // assets 内置 → 隐藏确认
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("隐藏题库")
                            .setMessage("确定隐藏内置题库「" + name + "」吗？可在刷新后重新显示。")
                            .setPositiveButton("隐藏", (di, wi) -> {
                                // 记录到隐藏列表
                                android.content.SharedPreferences sp = getSharedPreferences("chess_data", MODE_PRIVATE);
                                String hidden = sp.getString("hidden_tikus", "");
                                if (!hidden.contains(name)) {
                                    hidden = hidden.isEmpty() ? name : hidden + "|" + name;
                                    sp.edit().putString("hidden_tikus", hidden).apply();
                                }
                                refreshExternalTikuInBackground(null);
                                if (tikuDialog != null && tikuDialog.isShowing()) tikuDialog.dismiss();
                                showTikuSelectorDialog(false);
                                ToastUtil.makeText(MainActivity.this, "已隐藏", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }
                return true;
            });

            // 刷新按钮（左边）— 清除隐藏记录并重新加载
            builder.setNegativeButton("刷新", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getSharedPreferences("chess_data", MODE_PRIVATE).edit().remove("hidden_tikus").apply();
                    refreshExternalTikuInBackground(null);
                    ToastUtil.makeText(MainActivity.this, "已刷新", Toast.LENGTH_SHORT).show();
                    showTikuSelectorDialog(false);
                }
            });

            // 关闭按钮（右边）
            builder.setPositiveButton("关闭", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            tikuDialog = builder.create();
            tikuDialog.show();

            final AlertDialog shownDialog = tikuDialog;
            final TikuSelectorAdapter shownAdapter = adapter;
            refreshExternalTikuInBackground(() -> {
                if (isFinishing() || shownDialog != tikuDialog || !shownDialog.isShowing()) {
                    return;
                }
                List<TikuData> refreshedList = getVisibleTikuList();
                shownAdapter.setTikuList(refreshedList);
                tvTotalInfo.setText("共 " + refreshedList.size() + " 个题库");
            });

        } catch (Exception e) {
            Log.e("MainActivity", "显示题库选择失败: " + e.getMessage());
            ToastUtil.makeText(this, "加载题库失败", Toast.LENGTH_SHORT).show();
        }
    }

    private List<TikuData> getVisibleTikuList() {
        List<TikuData> result = new ArrayList<>(tikuManager.getTikuList());

        android.content.SharedPreferences prefs =
                getSharedPreferences("chess_data", MODE_PRIVATE);
        String hiddenStr = prefs.getString("hidden_tikus", "");
        if (!hiddenStr.isEmpty()) {
            java.util.Set<String> hiddenSet = new java.util.HashSet<>();
            for (String hidden : hiddenStr.split("\\|")) {
                hiddenSet.add(hidden);
            }
            hiddenSet.remove("错题本");
            hiddenSet.remove("收藏夹");
            result.removeIf(t -> hiddenSet.contains(t.getName()));
        }

        ProgressManager pm = ProgressManager.getInstance(this);
        if (!pm.isUnlocked()) {
            result.removeIf(TikuData::isFromYincang);
        } else if (!pm.isUnlockNotified()) {
            pm.setUnlockNotified(true);
            showUnlockDialog();
        }

        Collections.sort(result, (a, b) -> {
            String nameA = a.getName();
            String nameB = b.getName();
            if (nameA.equals("错题本")) return -1;
            if (nameB.equals("错题本")) return 1;
            if (nameA.equals("收藏夹")) return -1;
            if (nameB.equals("收藏夹")) return 1;
            if (nameA.equals("象棋杀着大全")) return -1;
            if (nameB.equals("象棋杀着大全")) return 1;
            return nameA.compareTo(nameB);
        });
        return result;
    }

    private void refreshExternalTikuInBackground(Runnable onComplete) {
        ioExecutor.execute(() -> {
            tikuManager.refreshExternalTiku();
            if (onComplete != null) {
                uiHandler.post(onComplete);
            }
        });
    }
    /**
     * 加载选中的题目
     */

    private void loadSelectedQuestion(int position, TikuData tiku) {
        try {
        if (!isActivityAlive() || chessView == null || tiku == null
                || position < 0 || position >= tiku.getTotalCount()) {
            Log.w("MainActivity", "选题参数无效: position=" + position);
            ToastUtil.makeText(this, "题目不存在或已失效", Toast.LENGTH_SHORT).show();
            return;
        }

        String fen = tiku.getFENByIndex(position);
        if (fen != null && !fen.isEmpty()) {
            boolean isFlipped = tiku.getFlipped(position);

            PositionValidator.Result fenFormatResult = positionValidator.validateFenFormat(fen);
            if (!fenFormatResult.valid) {
                ToastUtil.makeText(MainActivity.this,
                        "第 " + (position + 1) + " 题FEN格式不合法：" + fenFormatResult.reason,
                        Toast.LENGTH_LONG).show();
                Log.w("MainActivity", "手动选题FEN格式不合法[题" + (position + 1) + "]: " + fenFormatResult.reason + " FEN=" + fen);
                return;
            }

            String initialFEN = tiku.getInitialFEN(position);
            if (initialFEN != null && !initialFEN.isEmpty()) {
                chessView.setCurrentInitialFEN(initialFEN);
            } else {
                chessView.setCurrentInitialFEN(fen);
            }

            if (!chessView.setBoardByFEN(fen, isFlipped)) {
                ToastUtil.makeText(MainActivity.this,
                        "加载题目失败：FEN解析失败", Toast.LENGTH_LONG).show();
                return;
            }

            PositionValidator.Result vr = positionValidator.validate(chessView.chessBoard);
            if (!vr.valid) {
                ToastUtil.makeText(MainActivity.this,
                        "第 " + (position + 1) + " 题局面不合法：" + vr.reason,
                        Toast.LENGTH_LONG).show();
                Log.w("MainActivity", "手动选题局面不合法[题" + (position + 1) + "]: " + vr.reason + " FEN=" + fen);
                return;
            }

            currentQuestionIndex = position;
            tiku.goToIndex(position);
            currentFenList = tiku.getFenList();

            updateTitle();
            updateAccuracy();
            elapsedSeconds = 0;

            // ===== 更新行棋指示器（红方先走） =====
            updateTurnIndicator(chessView.getCurrentSide());
            // =====================================
            // ===== 设置AI对手方（含2#检测+皮卡鱼先行） =====
            setupPikafishForQuestion();
            // =====================================
            // ===== 保存当前题目 =====
            saveCurrentQuestionToPreferences();
            // ========================

            ToastUtil.makeText(MainActivity.this,
                    "加载第 " + (position + 1) + " 题", Toast.LENGTH_SHORT).show();
        } else {
            ToastUtil.makeText(this, "该题目的 FEN 为空", Toast.LENGTH_LONG).show();
        }
        } catch (Throwable t) {
            Log.e("MainActivity", "加载选中题目失败", t);
            ToastUtil.makeText(this, "加载题目失败，请检查 FEN 内容", Toast.LENGTH_LONG).show();
        }
    }


    /**
     * 切换到下一题（手动管理索引）
     */
    private void nextQuestion() {
        if (pageFlipRunning) {
            return;
        }
        if (currentTiku == null) {
            ToastUtil.makeText(this, "未选择题库", Toast.LENGTH_SHORT).show();
            return;
        }

        currentFenList = currentTiku.getFenList();
        if (currentFenList == null || currentFenList.isEmpty()) {
            ToastUtil.makeText(this, "题库为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentQuestionIndex + 1 < currentFenList.size()) {
            preparePageFlip(1);
        }

        // 跳到下一题，并通过 loadValidQuestion 自动跳过不合法局面
        loadValidQuestion(currentQuestionIndex + 1, 0);
    }

    /**
     * 准备一次伪翻书动画。
     * direction=1 表示下一题：右上角向左翻；direction=-1 表示上一题：右下角向右翻。
     */
    private void preparePageFlip(int direction) {
        if (pageFlipRunning) return;

        Bitmap snapshot = capturePageSnapshot();
        if (pendingPageFlipSnapshot != null && !pendingPageFlipSnapshot.isRecycled()) {
            pendingPageFlipSnapshot.recycle();
        }
        pendingPageFlipSnapshot = snapshot;
        pendingPageFlipDirection = direction;
    }

    private Bitmap capturePageSnapshot() {
        View boardOnly = findViewById(R.id.boardFrame);
        if (boardOnly != null && boardOnly.getWidth() > 0 && boardOnly.getHeight() > 0) {
            return captureViewSnapshot(boardOnly);
        }

        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            View boardFrame = findViewById(R.id.boardFrame);
            return captureViewSnapshot(boardFrame);
        }

        View host = findViewById(R.id.mainLayout);
        View titleDetail = findViewById(R.id.tv_title_detail);
        View boardFrame = findViewById(R.id.boardFrame);
        if (host == null || titleDetail == null || boardFrame == null
                || host.getWidth() <= 0 || host.getHeight() <= 0) {
            return null;
        }
        Rect region = getPageFlipRegion(host);
        if (region == null || region.width() <= 0 || region.height() <= 0) {
            return null;
        }

        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    region.width(), region.height(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawViewAtRegionPosition(canvas, titleDetail, region, host);
            drawViewAtRegionPosition(canvas, boardFrame, region, host);
            return bitmap;
        } catch (Throwable t) {
            Log.w("MainActivity", "缈婚〉鎴浘澶辫触: " + t.getMessage());
            return null;
        }
    }

    private Bitmap captureViewSnapshot(View page) {
        if (page == null || page.getWidth() <= 0 || page.getHeight() <= 0) {
            return null;
        }

        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            page.draw(canvas);
            return bitmap;
        } catch (Throwable t) {
            Log.w("MainActivity", "翻页截图失败: " + t.getMessage());
            return null;
        }
    }

    private void playPendingPageFlip() {
        if (pageFlipRunning || pendingPageFlipDirection == 0) return;

        final Bitmap snapshot = pendingPageFlipSnapshot;
        final int direction = pendingPageFlipDirection;
        pendingPageFlipSnapshot = null;
        pendingPageFlipDirection = 0;

        if (snapshot == null || snapshot.isRecycled()) {
            return;
        }

        View boardFrame = findViewById(R.id.boardFrame);
        if (!(boardFrame instanceof ViewGroup)) {
            snapshot.recycle();
            return;
        }

        final ViewGroup container = (ViewGroup) boardFrame;

        final SoftPageFlipView overlay = new SoftPageFlipView(this, snapshot, direction);
        overlay.setClickable(true);

        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        container.addView(overlay, params);

        pageFlipContainer = container;
        pageFlipOverlay = overlay;
        pageFlipDirection = direction;
        pageFlipRunning = true;

        overlay.post(() -> {
            overlay.animate()
                    .setUpdateListener(animation ->
                            overlay.setProgress(animation.getAnimatedFraction()))
                    .setDuration(460)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            finishPageFlip(snapshot, overlay, container);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            finishPageFlip(snapshot, overlay, container);
                        }
                    })
                    .start();
        });
    }

    private View getPageFlipHost() {
        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            return findViewById(R.id.boardFrame);
        }
        return findViewById(R.id.mainLayout);
    }

    private Rect getPageFlipRegion(View host) {
        if (host == null) return null;

        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            return new Rect(0, 0, host.getWidth(), host.getHeight());
        }

        View titleDetail = findViewById(R.id.tv_title_detail);
        View boardFrame = findViewById(R.id.boardFrame);
        if (titleDetail == null || boardFrame == null) return null;

        int[] hostLocation = new int[2];
        int[] titleLocation = new int[2];
        int[] boardLocation = new int[2];
        host.getLocationInWindow(hostLocation);
        titleDetail.getLocationInWindow(titleLocation);
        boardFrame.getLocationInWindow(boardLocation);

        int left = Math.min(titleLocation[0], boardLocation[0]) - hostLocation[0];
        int top = titleLocation[1] - hostLocation[1];
        int right = Math.max(
                titleLocation[0] + titleDetail.getWidth(),
                boardLocation[0] + boardFrame.getWidth()) - hostLocation[0];
        int bottom = boardLocation[1] + boardFrame.getHeight() - hostLocation[1];
        return new Rect(left, top, right, bottom);
    }

    private void drawViewAtRegionPosition(
            Canvas canvas, View view, Rect region, View host) {
        int[] hostLocation = new int[2];
        int[] viewLocation = new int[2];
        host.getLocationInWindow(hostLocation);
        view.getLocationInWindow(viewLocation);
        canvas.save();
        canvas.translate(
                viewLocation[0] - hostLocation[0] - region.left,
                viewLocation[1] - hostLocation[1] - region.top);
        view.draw(canvas);
        canvas.restore();
    }

    private void finishPageFlip(Bitmap snapshot, SoftPageFlipView overlay,
                                ViewGroup container) {
        if (overlay.getParent() == container) {
            container.removeView(overlay);
        }
        overlay.releaseBitmap();
        if (snapshot != null && !snapshot.isRecycled()) {
            snapshot.recycle();
        }
        pageFlipOverlay = null;
        pageFlipContainer = null;
        pageFlipDirection = 0;
        pageFlipRunning = false;
    }




    // ========== 按钮功能方法 ==========

    /**
     * 选关功能：直接显示当前题库的题目列表
     */
    private void showLevelDialog() {
        if (currentTiku == null) {
            ToastUtil.makeText(this, "请先选择题库", Toast.LENGTH_SHORT).show();
            showTikuSelectorDialog();
            return;
        }
        showQuestionSelectorDialog(currentTiku);
    }


    private void showQuestionSelectorDialog(TikuData tiku) {
        showQuestionSelectorDialog(tiku, -1);
    }

    /**
     * 显示选关对话框
     * @param tiku 题库
     * @param forceHighlightIndex 强制高亮的题目索引，-1表示自动计算
     */
    private void showQuestionSelectorDialog(TikuData tiku, int forceHighlightIndex) {
        try {
            // ===== 重新加载进度 =====
            ProgressManager pm = ProgressManager.getInstance(this);
            pm.reload();
            // ========================
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_question_selector, null);
            builder.setView(dialogView);

            TextView tvTikuTitle = dialogView.findViewById(R.id.tvTikuTitle);
            TextView tvQuestionCount = dialogView.findViewById(R.id.tvQuestionCount);
            GridView gridView = dialogView.findViewById(R.id.gridViewQuestions);

            tvTikuTitle.setText("📖 " + tiku.getName());
            tvQuestionCount.setText("共 " + tiku.getTotalCount() + " 题");

            // ===== 计算高亮索引 =====
            int safeIndex;
            if (forceHighlightIndex >= 0) {
                // 外部指定了高亮索引（从题库选择器传入）
                safeIndex = forceHighlightIndex;
            } else {
                // 选关按钮：始终高亮当前题目
                safeIndex = currentQuestionIndex;
            }
            // 边界保护
            int total = tiku.getTotalCount();
            if (total > 0) {
                if (safeIndex < 0 || safeIndex >= total) {
                    safeIndex = Math.max(0, total - 1);
                }
            } else {
                safeIndex = 0;
            }
            // ========================

            QuestionGridAdapter adapter = new QuestionGridAdapter(
                    this, tiku, safeIndex);
            gridView.setAdapter(adapter);

            // ===== 重置按钮（左边 - NegativeButton） - 必须在 create() 之前设置 =====
            builder.setNegativeButton("🔄 重置进度", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    showResetConfirmDialog(tiku);
                }
            });

            // ===== 关闭按钮（右边 - PositiveButton） - 必须在 create() 之前设置 =====
            builder.setPositiveButton("关闭", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    dialogInterface.dismiss();
                    if (tikuDialog != null && tikuDialog.isShowing()) {
                        tikuDialog.dismiss();
                        tikuDialog = null;
                    }
                }
            });

            // ===== 先创建 dialog =====
            final AlertDialog dialog = builder.create();
            // ========================

            final int[] clickCount = {0};
            final int[] selectedPosition = {0};

            gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    adapter.setCurrentIndex(position);
                    selectedPosition[0] = position;

                    clickCount[0]++;

                    questionSelectorHandler.removeCallbacksAndMessages(null);
                    questionSelectorHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (clickCount[0] == 1) {
                                // 单击：加载题目
                                dialog.dismiss();
                                if (tikuDialog != null && tikuDialog.isShowing()) {
                                    tikuDialog.dismiss();
                                    tikuDialog = null;
                                }
                                loadSelectedQuestion(selectedPosition[0], tiku);
                            }
                            // 双击：不做操作（仅选中高亮）
                            clickCount[0] = 0;
                        }
                    }, 500);
                }
            });

            // ===== 最后显示 dialog =====
            dialog.show();

            // ===== 自动滚动到当前题目，使其可见 =====
            final int targetIndex = safeIndex;
            gridView.post(new Runnable() {
                @Override
                public void run() {
                    if (targetIndex >= 0 && targetIndex < gridView.getCount()) {
                        int firstVisible = gridView.getFirstVisiblePosition();
                        int lastVisible = gridView.getLastVisiblePosition();
                        if (targetIndex < firstVisible || targetIndex > lastVisible) {
                            gridView.smoothScrollToPosition(targetIndex);
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e("MainActivity", "显示题目列表失败: " + e.getMessage());
        }
    }


    /**
     * 显示重置进度确认对话框
     */
    private void showResetConfirmDialog(TikuData tiku) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ 重置进度")
                .setMessage("确定要重置 \"" + tiku.getName() + "\" 的进度吗？\n\n所有已完成题目将被标记为未完成，此操作不可撤销！")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("重置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // ===== 使用 ProgressManager 重置指定题库的进度 =====
                        ProgressManager.getInstance(MainActivity.this).resetTikuProgress(tiku.getName());

                        // 如果重置的是当前题库，更新界面
                        if (currentTiku != null && currentTiku.getName().equals(tiku.getName())) {
                            currentQuestionIndex = 0;
                            updateAccuracy();
                            loadCurrentQuestion();
                            ToastUtil.makeText(MainActivity.this, "进度已重置", Toast.LENGTH_SHORT).show();
                        }

                        dialog.dismiss();
                        // 刷新选关页面
                        showQuestionSelectorDialog(tiku);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }


    /**
     * 加载选中的题目
     */


    private void copyFEN() {
        if (chessView == null) {
            ToastUtil.makeText(this, "棋盘未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        // ===== 获取当前棋盘的FEN（不含2#前缀） =====
        String fen = chessView.getFEN();
        // ===========================================

        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("FEN", fen);
        clipboard.setPrimaryClip(clip);
        ToastUtil.makeText(this, "FEN已复制到剪贴板", Toast.LENGTH_SHORT).show();
        Log.d("MainActivity", "FEN: " + fen);
    }

    private void restartCurrentQuestion() {
        if (chessView == null) {
            ToastUtil.makeText(this, "棋盘未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        if (chessView.getCurrentInitialFEN() == null || chessView.getCurrentInitialFEN().isEmpty()) {
            if (currentTiku != null) {
                String initialFEN = currentTiku.getInitialFEN(currentQuestionIndex);
                if (initialFEN != null && !initialFEN.isEmpty()) {
                    chessView.setCurrentInitialFEN(initialFEN);
                }
            }
        }

        chessView.resetToCurrentInitial();
        elapsedSeconds = 0;
        ToastUtil.makeText(this, "已回到当前题目的初始局面", Toast.LENGTH_SHORT).show();
    }
    /**
     * 拆解分析功能 - 跳转到分析界面
     */
    private void analyzePosition() {
        if (chessView == null) {
            ToastUtil.makeText(this, "棋盘未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        // 从当前题库获取初始FEN和翻转状态
        String initialFEN = null;
        boolean flipped = false;
        if (currentTiku != null && currentQuestionIndex >= 0) {
            initialFEN = currentTiku.getFENByIndex(currentQuestionIndex);
            flipped = currentTiku.getFlipped(currentQuestionIndex);
        }
        if (initialFEN == null || initialFEN.isEmpty()) {
            initialFEN = chessView.getCurrentInitialFEN();
        }

        // 跳转到分析界面
        Intent intent = new Intent(this, AnalyzeActivity.class);
        intent.putExtra("initialFEN", initialFEN);
        intent.putExtra("flipped", flipped);
        startActivity(intent);
    }
    /**
     * 悔棋功能
     */
    private void regretMove() {
        if (chessView == null) {
            ToastUtil.makeText(this, "棋盘未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean success = chessView.undoMove();
        if (success) {
            // 对弈中的悔棋回退一整回合：AI一步 + 棋手一步。
            boolean secondSuccess = chessView.undoMove();
            if (!secondSuccess) {
                chessView.restoreLastMoveHighlight();
            }
            ToastUtil.makeText(this, "悔棋成功", Toast.LENGTH_SHORT).show();
            String fen = chessView.getFEN();
            Log.d("MainActivity", "悔棋后FEN: " + fen);
        } else {
            ToastUtil.makeText(this, "已回到最初局面", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 标记为不会 - 记录含2#前缀的原始FEN到错题本
     */
    private void markAsNotMastered() {
        if (currentTiku != null) {
            if (!questionRecorded) { notMasteredCount++; questionRecorded = true; }
            updateAccuracy();

            // ===== 获取原始FEN（含2#前缀） =====
            String rawFen = currentTiku.getRawFEN(currentQuestionIndex);
            if (rawFen == null || rawFen.isEmpty()) {
                // 如果没有原始FEN，使用当前FEN
                rawFen = chessView.getFEN();
            }
            addToWrongBook(rawFen);
            // ===================================

            ToastUtil.makeText(this, "不会 - 已记录到错题本！", Toast.LENGTH_SHORT).show();
            nextQuestion();
        } else {
            ToastUtil.makeText(this, "未选择题库", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 将FEN记录到错题本
     * @param fen 要记录的FEN（含2#前缀）
     */
    private void addToWrongBook(String fen) {
        try {
            if (fen == null || fen.isEmpty()) {
                Log.e("MainActivity", "获取FEN失败");
                return;
            }

            File externalDir = Environment.getExternalStorageDirectory();
            File canju12Dir = new File(externalDir, "canju12");
            File tikuDir = new File(canju12Dir, "tiku");
            File wrongBookFile = new File(tikuDir, "错题本.txt");

            if (!wrongBookFile.exists()) {
                wrongBookFile.createNewFile();
                FileWriter headerWriter = new FileWriter(wrongBookFile);
                headerWriter.write("# 错题本\n");
                headerWriter.write("# 格式：每行一个FEN\n");
                headerWriter.close();
            }

            FileWriter writer = new FileWriter(wrongBookFile, true);
            writer.write(fen + "\n");
            writer.close();

            Log.d("MainActivity", "已记录到错题本: " + fen);

        } catch (Exception e) {
            Log.e("MainActivity", "写入错题本失败: " + e.getMessage());
        }
    }

    private void finishAiQuestion(int winner, String reason) {
        if (questionFinished) return;

        questionFinished = true;
        String prefix = (reason == null || reason.isEmpty()) ? "" : reason + "，";
        if (winner == humanSide) {
            if (!questionRecorded) {
                winCount++;
                questionRecorded = true;
            }
            updateAccuracy();
            markQuestionCompleted();
            ToastUtil.makeText(this, prefix + "你赢了！进入下一题", Toast.LENGTH_SHORT).show();
            uiHandler.postDelayed(() -> nextQuestion(), 800);
        } else {
            if (!questionRecorded) {
                notMasteredCount++;
                questionRecorded = true;
            }
            updateAccuracy();
            addToWrongBookIfNotExists();
            showRetryDialog(prefix + "你输了！是否重来？");
        }
    }

    /**
     * 将当前题目保存到收藏夹，不改变答题统计和题目索引。
     */
    private void addCurrentQuestionToFavoriteBook() {
        try {
            if (chessView == null) {
                ToastUtil.makeText(this, "棋盘未初始化", Toast.LENGTH_SHORT).show();
                return;
            }

            String rawFen = null;
            if (currentTiku != null) {
                rawFen = currentTiku.getRawFEN(currentQuestionIndex);
            }
            if (rawFen == null || rawFen.trim().isEmpty()) {
                rawFen = chessView.getFEN();
            }
            if (rawFen == null || rawFen.trim().isEmpty()) {
                ToastUtil.makeText(this, "获取当前局面失败", Toast.LENGTH_SHORT).show();
                return;
            }

            WrongBookManager favoriteManager =
                    new WrongBookManager("收藏夹", "收藏夹.txt");
            favoriteManager.load();
            int oldCount = favoriteManager.getCount();
            favoriteManager.add(rawFen.trim());

            if (favoriteManager.getCount() == oldCount) {
                ToastUtil.makeText(this, "收藏夹中已存在", Toast.LENGTH_SHORT).show();
            } else {
                ToastUtil.makeText(this, "已加入收藏夹", Toast.LENGTH_SHORT).show();
        }
    } catch (Throwable t) {
            Log.e("MainActivity", "保存收藏夹失败", t);
            ToastUtil.makeText(this, "收藏夹保存失败，请检查存储权限", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 标记为已会
     */
    private void markAsMastered() {
        if (currentTiku != null) {
            if (!questionRecorded) { masteredCount++; questionRecorded = true; }
            // ===== 使用统一标记方法 =====
            markQuestionCompleted();
            // ===========================
            updateAccuracy();
            ToastUtil.makeText(this, "标记为：已会", Toast.LENGTH_SHORT).show();
            nextQuestion();
        } else {
            ToastUtil.makeText(this, "未选择题库", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * 保存当前题库和题目到SharedPreferences
     */
    private void saveCurrentQuestionToPreferences() {
        if (currentTiku == null) return;

        try {
            android.content.SharedPreferences prefs = getSharedPreferences("chess_data", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();

            // ===== 保存题库名称 =====
            editor.putString("saved_tiku_name", currentTiku.getName());

            // ===== 保存题目索引 =====
            editor.putInt("saved_question_index", currentQuestionIndex);

            // ===== 保存当前局面FEN（可选，用于恢复局面） =====
            if (chessView != null) {
                String fen = chessView.getFEN();
                editor.putString("saved_current_fen", fen);
            }

            // ===== 保存是否翻转 =====
            if (chessView != null) {
                editor.putBoolean("saved_is_flipped", chessView.isBoardFlipped());
            }

            // ===== 保存初始FEN =====
            String initialFEN = chessView.getCurrentInitialFEN();
            if (initialFEN != null && !initialFEN.isEmpty()) {
                editor.putString("saved_initial_fen", initialFEN);
            }

            editor.apply();
            Log.d("MainActivity", "保存题目: " + currentTiku.getName() + ", 第" + (currentQuestionIndex + 1) + "题");

        } catch (Exception e) {
            Log.e("MainActivity", "保存题目失败: " + e.getMessage());
        }
    }

    /**
     * 保存当前选中的题库名称（选择题库时调用）
     */
    private void saveCurrentTikuToPreferences(String tikuName) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("chess_data", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            editor.putString("saved_tiku_name", tikuName);
            editor.putInt("saved_question_index", 0);
            editor.apply();
            Log.d("MainActivity", "保存题库: " + tikuName);
        } catch (Exception e) {
            Log.e("MainActivity", "保存题库失败: " + e.getMessage());
        }
    }

    /**
     * 从SharedPreferences恢复之前保存的题目
     * @return true表示恢复成功，false表示没有保存的题目或恢复失败
     */
    private boolean restoreCurrentQuestion() {
        try {
            // 从 ProgressManager 读取上次停留位置
            ProgressManager pm = ProgressManager.getInstance(this);

            // 首次运行判断：progress.txt 中没有 lastTiku/lastIndex 记录
            if (!pm.hasLastPosition()) {
                Log.d("MainActivity", "首次运行，没有之前的载入记录");
                return false;
            }

            String tikuName = pm.getLastTiku();
            int savedIndex = pm.getLastIndex();

            Log.d("MainActivity", "恢复: 题库=" + tikuName + ", 索引=" + savedIndex);

            // 查找对应的题库
            TikuData restoredTiku = null;
            for (TikuData tiku : tikuManager.getTikuList()) {
                if (tiku.getName().equals(tikuName)) {
                    restoredTiku = tiku;
                    break;
                }
            }

            if (restoredTiku == null) {
                Log.d("MainActivity", "找不到保存的题库: " + tikuName);
                // 清除无效的保存数据
                clearSavedData();
                return false;
            }

            // 检查索引是否有效
            if (savedIndex >= restoredTiku.getTotalCount() || savedIndex < 0) {
                savedIndex = 0;
            }

            // 恢复题目
            // ===== 加载题目（翻转状态从 TikuData 获取） =====
            boolean isFlipped = restoredTiku.getFlipped(savedIndex);

            // 加载题目
            String fen = restoredTiku.getFENByIndex(savedIndex);
            if (fen != null) {
                PositionValidator.Result fenFormatResult = positionValidator.validateFenFormat(fen);
                if (!fenFormatResult.valid) {
                    Log.w("MainActivity", "恢复题目FEN格式不合法: " + fenFormatResult.reason + " FEN=" + fen);
                    clearSavedData();
                    return false;
                }

                if (!chessView.setBoardByFEN(fen, isFlipped)) {
                    Log.w("MainActivity", "恢复题目时 FEN 解析失败: " + fen);
                    clearSavedData();
                    return false;
                }

                PositionValidator.Result vr = positionValidator.validate(chessView.chessBoard);
                if (!vr.valid) {
                    Log.w("MainActivity", "恢复题目局面不合法: " + vr.reason + " FEN=" + fen);
                    clearSavedData();
                    return false;
                }

                currentTiku = restoredTiku;
                currentFenList = restoredTiku.getFenList();
                currentQuestionIndex = savedIndex;
                totalQuestions = restoredTiku.getTotalCount();
                tikuManager.setCurrentTiku(restoredTiku);

                // ===== 设置AI对手方 =====
                setupPikafishForQuestion();
                // =========================
            }

            updateTitle();
            updateAccuracy();
            elapsedSeconds = 0;

            // 更新行棋指示器
            updateTurnIndicator(chessView.getCurrentSide());

            Log.d("MainActivity", "恢复题目成功: " + tikuName + ", 第" + (savedIndex + 1) + "题");
            return true;

        } catch (Exception e) {
            Log.e("MainActivity", "恢复题目失败: " + e.getMessage());
            clearSavedData();
            return false;
        }
    }

    /**
     * 清除保存的数据
     */
    private void clearSavedData() {
        android.content.SharedPreferences prefs = getSharedPreferences("chess_data", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.remove("saved_tiku_name");
        editor.remove("saved_question_index");
        editor.remove("saved_current_fen");
        editor.remove("saved_is_flipped");
        editor.remove("saved_initial_fen");
        editor.apply();
        Log.d("MainActivity", "已清除保存的数据");
    }

    /**
     * 加载新题目后设置人类/AI的走棋方
     * 规则：红在下→人类走红，AI走黑；红在上(翻转)→人类走黑，AI走红
     */
    private void setupPikafishForQuestion() {
        if (!pikafishMode) return;

        aiQuestionGeneration++;
        if (pikafishWatchdog != null) {
            pikafishHandler.removeCallbacks(pikafishWatchdog);
        }

        boolean flipped = chessView.isBoardFlipped();
        humanSide = flipped ? -1 : 1;   // 人类走下方棋子
        pikafishSide = -humanSide;
        // 告知 ChessView 人类所执的一方，落子后保留人类方高亮、AI走棋时清人类方高亮
        chessView.setHumanSide(humanSide);

        chineseRules.reset();
        isPikafishThinking = false;
        isDrawHandling = false;
        questionRecorded = false;
        longCheckHandled = false;
        questionFinished = false;  // 新题加载，重置结束标记
        // 保存上次停留位置
        if (currentTiku != null) {
            ProgressManager.getInstance(MainActivity.this).setLastPosition(
                    currentTiku.getName(), currentQuestionIndex);
        }
        chessView.setTouchEnabled(true);

        Log.d("MainActivity", "AI对战: 人类=" + (humanSide == 1 ? "红" : "黑") +
                ", Pikafish=" + (pikafishSide == 1 ? "红" : "黑") +
                ", 先行=" + (chessView.getCurrentSide() == 1 ? "红" : "黑"));

        // 如果轮到 Pikafish 先走，延迟触发AI（引擎会在首次使用时自动启动）
        if (chessView.getCurrentSide() == pikafishSide) {
            Log.d("MainActivity", "轮到 Pikafish 先走，将自动启动引擎");
            pikafishHandler.postDelayed(() -> triggerPikafish(), 500);
        }
    }

    /**
     * 触发 Pikafish 计算并走棋
     * 引擎会在首次使用时自动启动（懒加载）
     */
    private void triggerPikafish() {
        if (isPikafishThinking || questionFinished
                || chessView == null
                || chessView.getCurrentSide() != pikafishSide) {
            return;
        }

        final int searchGeneration = aiQuestionGeneration;

        // 如果引擎还没启动，先异步启动
        if (pikafishEngine == null) {
            pikafishEngine = new PikafishEngine();
        }

        if (!pikafishEngine.isReady()) {
            isPikafishThinking = true;
            chessView.setTouchEnabled(false);
            Log.d("MainActivity", "引擎未就绪，首次启动...");

            new Thread(() -> {
                try {
                    pikafishEngine.start(MainActivity.this);
                    Log.d("MainActivity", "★★★ Pikafish 引擎启动完成 ★★★");

                    // 引擎就绪后，重置 isPikafishThinking 再走棋
                    runOnUiThread(() -> {
                        if (!isActivityAlive() || chessView == null
                                || searchGeneration != aiQuestionGeneration
                                || chessView.getCurrentSide() != pikafishSide
                                || questionFinished) {
                            isPikafishThinking = false;
                            if (chessView != null) chessView.setTouchEnabled(true);
                            return;
                        }
                        isPikafishThinking = false;
                        doPikafishSearch();
                    });
                } catch (Exception e) {
                    Log.e("MainActivity", "Pikafish 引擎启动失败", e);
                    runOnUiThread(() -> {
                        if (!isActivityAlive()) return;
                        isPikafishThinking = false;
                        if (chessView != null) chessView.setTouchEnabled(true);
                        updateAccuracy();
                        String msg = e.getMessage();
                        if (msg == null || msg.isEmpty()) {
                            msg = e.getClass().getSimpleName();
                        }
                        String displayMsg = msg.length() > 300 ? msg.substring(0, 300) : msg;
                        ToastUtil.makeText(MainActivity.this,
                                "AI启动失败:\n" + displayMsg,
                                Toast.LENGTH_LONG).show();
                        Log.e("MainActivity", "Pikafish 引擎启动失败，完整错误:", e);
                    });
                    // 输出设备诊断信息
                    Log.e("MainActivity", "设备ABI: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS));
                    Log.e("MainActivity", "SDK: " + Build.VERSION.SDK_INT);
                    Log.e("MainActivity", "二进制路径: " + MainActivity.this.getFilesDir() + "/pikafish-armv8");
                    File binFile = new File(MainActivity.this.getFilesDir(), "pikafish-armv8");
                    Log.e("MainActivity", "文件存在: " + binFile.exists() + ", 大小: " + binFile.length() + ", 可执行: " + binFile.canExecute());
                }
            }).start();
            return;
        }

        // 引擎已就绪，直接走棋
        doPikafishSearch();
    }

    /**
     * Pikafish 引擎搜索最佳走法
     * （引擎已确保就绪后才调用此方法）
     */
    private void startPikafishWatchdog() {
        if (pikafishWatchdog != null) pikafishHandler.removeCallbacks(pikafishWatchdog);
        pikafishWatchdog = () -> {
            if (!pikafishMode) return;
            if (chessView != null && chessView.getCurrentSide() == pikafishSide
                    && !isPikafishThinking && !isDrawHandling) {
                Log.d("MainActivity", "watchdog trigger Pikafish");
                triggerPikafish();
            }
            if (pikafishMode) {
                pikafishHandler.postDelayed(pikafishWatchdog, 2000);
            }
        };
        pikafishHandler.postDelayed(pikafishWatchdog, 2000);
    }

    private void stopPikafishWatchdog() {
        if (pikafishWatchdog != null) {
            pikafishHandler.removeCallbacks(pikafishWatchdog);
        }
    }

    private void doPikafishSearch() {
        if (isPikafishThinking || pikafishEngine == null || !pikafishEngine.isReady()
                || questionFinished || chessView == null
                || chessView.getCurrentSide() != pikafishSide) {
            return;
        }

        isPikafishThinking = true;
        chessView.setTouchEnabled(false);
        final int searchGeneration = aiQuestionGeneration;

        // ===== 构建完整局面信息 =====
        String baseFen = chessView.getCurrentInitialFEN() != null
                ? chessView.getCurrentInitialFEN() : chessView.getFEN();
        String[] history = chessView.getUCIHistory();
        String fen;
        if (history != null && history.length > 0) {
            StringBuilder sb = new StringBuilder(baseFen).append(" moves");
            for (String m : history) {
                if (m != null && m.length() >= 4) sb.append(" ").append(m);
            }
            fen = sb.toString();
            Log.d("MainActivity", "引擎调用: initFen=" + baseFen + ", moves=" + java.util.Arrays.toString(history));
        } else {
            fen = baseFen;
            Log.d("MainActivity", "引擎调用: fen=" + baseFen + " (无历史走法)");
        }
        final String finalFen = fen;
        // ============================

        final long searchStart = System.currentTimeMillis();

        pikafishEngine.getBestMoveAsync(finalFen, 22, 1500, new PikafishEngine.PikafishCallback() {
            @Override
            public void onBestMove(String move) {
                if (!isActivityAlive() || chessView == null
                        || searchGeneration != aiQuestionGeneration) {
                    Log.d("MainActivity", "忽略旧题目的引擎回调: " + move);
                    return;
                }

                if (move == null || move.equals("(none)")) {
                    isPikafishThinking = false;
                    chessView.setTouchEnabled(true);
                    Log.d("MainActivity", "Pikafish 没有可走的棋");
                    return;
                }

                Log.d("MainActivity", "Pikafish 走法: " + move + " (完整FEN: " + finalFen + ")");

                // 计算耗时，确保总时间约1秒后走棋
                long elapsed = System.currentTimeMillis() - searchStart;
                final long delay = Math.max(200, 1500 - elapsed);
                pikafishHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isActivityAlive() || chessView == null
                                || searchGeneration != aiQuestionGeneration
                                || questionFinished
                                || chessView.getCurrentSide() != pikafishSide) {
                            isPikafishThinking = false;
                            if (chessView != null) chessView.setTouchEnabled(true);
                            Log.d("MainActivity", "忽略旧题目或非AI回合的引擎走法: " + move);
                            return;
                        }

                        // 触摸保持禁用到 AI 落子完成，避免人在延迟期间抢先走棋。
                        isPikafishThinking = false;
                        boolean success = chessView.executeUCIMove(move, pikafishSide);
                        chessView.setTouchEnabled(true);
                        updateAccuracy();
                        if (success) {
                            // recordMove 已在 onMove 回调中执行，这里只需检查是否和棋
                            ChineseRules.DrawResult result = chineseRules.checkDraw(chessView.chessBoard);
                            if (result.isDraw) {
                                drawDetected();
                            }
                        } else {
                            Log.e("MainActivity", "Pikafish 走法执行失败: " + move);
                        }
                    }
                }, delay);
            }

            @Override
            public void onError(String error) {
                if (!isActivityAlive()) return;
                isPikafishThinking = false;
                if (chessView != null) chessView.setTouchEnabled(true);
                updateAccuracy();
                ToastUtil.makeText(MainActivity.this,
                        "皮卡鱼报错：" + error, Toast.LENGTH_LONG).show();
                Log.e("MainActivity", "Pikafish 错误: " + error);
            }
        });
    }

    /**
     * 检测到和棋时的处理
     * 显示 Toast "双方可和棋"，执行「已会」功能（标记完成→下一题）
     */
    private void drawDetected() {
        if (!isActivityAlive() || chessView == null) return;
        if (isDrawHandling) return;  // 防重复
        isDrawHandling = true;

        isPikafishThinking = false;
        chessView.setTouchEnabled(true);

        uiHandler.postDelayed(() -> {
            if (!isActivityAlive()) return;
            if (currentTiku != null) {
                if (humanSide == 1) {
                    // 人类执红 → 和棋算没过关 → 加入错题本，显示重试对话框
                    if (!questionFinished && !questionRecorded) {
                        notMasteredCount++; questionRecorded = true;
                    }
                    questionFinished = true;
                    updateAccuracy();
                    addToWrongBookIfNotExists();
                    showRetryDialog("双方可和棋（你执红，未过关），是否重来？");
                } else {
                    // 人类执黑 → 和棋算过关（执黑和棋或获胜都算过关）
                    if (!questionFinished && !questionRecorded) {
                        masteredCount++; questionRecorded = true;
                    }
                    questionFinished = true;
                    markQuestionCompleted();
                    updateAccuracy();
                    ToastUtil.makeText(MainActivity.this,
                            "双方可和棋（你执黑，算过关）", Toast.LENGTH_SHORT).show();
                    nextQuestion();
                }
            }
        }, 1500);
    }

    /**
     * 将当前题目加入错题本（只检查与最后一行是否连续重复）
     * 2#前缀参与完整字符串比较，"2#xxx"和"xxx"视为不同数据
     */
    private void addToWrongBookIfNotExists() {
        if (currentTiku == null) return;

        // 获取原始FEN（含2#前缀）
        String rawFen = currentTiku.getRawFEN(currentQuestionIndex);
        if (rawFen == null || rawFen.isEmpty()) {
            rawFen = chessView.getFEN();
        }

        // 只检查错题本最后一行是否相同（防止连续重复）
        String lastLine = readLastLineOfWrongBook();
        if (rawFen.equals(lastLine)) {
            Log.d("MainActivity", "错题本最后一行已相同，跳过！");
            ToastUtil.makeText(MainActivity.this, "错题本中已存在！", Toast.LENGTH_SHORT).show();
            return;
        }

        addToWrongBook(rawFen);
        Log.d("MainActivity", "已加入错题本: " + rawFen);
        ToastUtil.makeText(MainActivity.this, "已记录到错题本！", Toast.LENGTH_SHORT).show();
    }

    /**
     * 读取错题本文件的最后一行（非空非注释行）
     */
    private String readLastLineOfWrongBook() {
        try {
            File externalDir = Environment.getExternalStorageDirectory();
            File file = new File(new File(externalDir, "canju12/tiku"), "错题本.txt");
            if (!file.exists()) return "";

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line, lastLine = "";
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lastLine = line;
                }
            }
            reader.close();
            return lastLine;
        } catch (Exception e) {
            Log.e("MainActivity", "读取错题本最后一行失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 显示重试对话框
     * 选「Yes」→ 从当前题库重新加载题目
     * 选「No」→ 停留在当前局面
     */
    private void showRetryDialog(String message) {
        if (!isActivityAlive() || chessView == null) return;
        isPikafishThinking = false;
        chessView.setTouchEnabled(true);

        new AlertDialog.Builder(this)
                .setTitle("再接再厉！")
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> {
                    dialog.dismiss();
                    loadCurrentQuestion();
                    // 重来不重置统计（保留第一次结果）
                    questionRecorded = true;
                })
                .setNegativeButton("No", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    /** 显示隐藏题库解锁弹窗 */
    private void showUnlockDialog() {
        TextView msg = new TextView(this);
        msg.setText("🔥🔥一二残局🔥🔥🔥\n☁️☁️云库为您加油！☁️☁️\n🐟🐠🐡皮卡鱼为您加油！🦈🎏🐬🐳🐋");
        msg.setTextSize(16);
        msg.setGravity(android.view.Gravity.CENTER);
        msg.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(this)
                .setTitle("🎉 恭喜解锁隐藏题库！")
                .setView(msg)
                .setPositiveButton("开始挑战", (d, w) -> d.dismiss())
                .setCancelable(false)
                .show();
    }


    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        uiHandler.removeCallbacksAndMessages(null);
        questionSelectorHandler.removeCallbacksAndMessages(null);
        pikafishHandler.removeCallbacksAndMessages(null);

        if (pendingPageFlipSnapshot != null && !pendingPageFlipSnapshot.isRecycled()) {
            pendingPageFlipSnapshot.recycle();
        }
        pendingPageFlipSnapshot = null;
        if (pageFlipOverlay != null && pageFlipOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) pageFlipOverlay.getParent()).removeView(pageFlipOverlay);
        }
        if (pageFlipOverlay instanceof SoftPageFlipView) {
            ((SoftPageFlipView) pageFlipOverlay).animate().cancel();
            ((SoftPageFlipView) pageFlipOverlay).disposeBitmap();
        }
        pageFlipOverlay = null;
        pageFlipContainer = null;
        pageFlipDirection = 0;
        pendingPageFlipDirection = 0;
        pageFlipRunning = false;

        // ===== 关闭 Pikafish 引擎 =====
        if (pikafishEngine != null) {
            pikafishEngine.quit();
        }
        // ================================
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (totalTimerHandler != null && totalTimerRunnable != null) {
            totalTimerHandler.removeCallbacks(totalTimerRunnable);
        }
        ioExecutor.shutdownNow();

        // ===== 退出时保存当前题目 =====
        saveCurrentQuestionToPreferences();
        // =============================
    }
    /**
     * 检查是否是首次运行
     */
    private boolean isFirstRun() {
        android.content.SharedPreferences prefs = getSharedPreferences("chess_data", MODE_PRIVATE);
        boolean isFirst = prefs.getBoolean("is_first_run", true);
        if (isFirst) {
            // 标记已运行过
            android.content.SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("is_first_run", false);
            editor.apply();
        }
        return isFirst;
    }

    /**
     * 显示首次运行说明书
     */
    private void showFirstRunDialog() {
        try {
            AlertDialog dialog = showHtmlAssetDialog(
                    "dialog_first_run.html", "开始练习 🚀", false);

            // 首次运行说明关闭后，检查隐藏题库解锁
            dialog.setOnDismissListener(d -> {
                ProgressManager pm = ProgressManager.getInstance(MainActivity.this);
                if (pm.isUnlocked() && !pm.isUnlockNotified()) {
                    pm.setUnlockNotified(true);
                    showUnlockDialog();
                }
            });

        } catch (Exception e) {
            Log.e("MainActivity", "显示说明书失败: " + e.getMessage());
        }
    }

    private AlertDialog showHtmlAssetDialog(String assetName, String positiveText) {
        return showHtmlAssetDialog(assetName, positiveText, false);
    }

    private AlertDialog showHtmlAssetDialog(
            String assetName, String positiveText, boolean fullScreen) {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        int maxHeight = fullScreen
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxHeight));

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this, R.style.Theme_YourApp_Fullscreen);
        builder.setView(webView);
        builder.setPositiveButton(positiveText, (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(!fullScreen);

        webView.setWebViewClient(new android.webkit.WebViewClient() {
            private boolean shown = false;

            @Override
            public void onPageFinished(WebView view, String url) {
                if (shown || isFinishing() || isDestroyed()) {
                    return;
                }
                shown = true;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    dialog.show();

                    android.view.Window window = dialog.getWindow();
                    if (window != null) {
                        android.view.WindowManager.LayoutParams params = window.getAttributes();
                        params.width = fullScreen
                                ? ViewGroup.LayoutParams.MATCH_PARENT
                                : Math.round(getResources().getDisplayMetrics().widthPixels * 0.90f);
                        params.height = fullScreen
                                ? ViewGroup.LayoutParams.MATCH_PARENT
                                : ViewGroup.LayoutParams.WRAP_CONTENT;
                        window.setAttributes(params);
                    }

                    Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (positiveButton != null) {
                        positiveButton.setTextColor(
                                getResources().getColor(android.R.color.holo_green_dark));
                        positiveButton.setTextSize(18);
                    }
                });
            }
        });

        webView.loadUrl("file:///android_asset/" + assetName);
        return dialog;
    }
    /**
     * 标记题目为已完成（统一方法）
     */
    private void markQuestionCompleted() {
        if (currentTiku == null) return;

        // 使用 ProgressManager 标记完成
        ProgressManager pm = ProgressManager.getInstance(this);
        pm.markCompleted(currentTiku.getName(), currentQuestionIndex);

        // 检查该题库是否已全部完成
        int done = pm.getCompletedCount(currentTiku.getName());
        if (done >= currentTiku.getTotalCount()) {
            pm.markTikuCompleted(currentTiku.getName());
            if (pm.needsUnlockNotification()) {
                pm.setUnlockNotified(true);
                showUnlockDialog();
            }
        }

        // 更新UI
        updateAccuracy();
        Log.d("MainActivity", "标记完成: " + currentTiku.getName() + " 第 " + (currentQuestionIndex + 1) + " 题");
    }

    // ========== 横屏旋转支持 ==========

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 保存棋盘状态（Activity不会被销毁，成员变量自动保留，但视图引用会失效）
        String currentFen = chessView != null ? chessView.getFEN() : null;
        boolean isFlipped = chessView != null && chessView.isBoardFlipped();
        String initialFEN = chessView != null ? chessView.getCurrentInitialFEN() : null;
        int currentSide = chessView != null ? chessView.getCurrentSide() : 1;

        // 暂停计时器
        pauseTimers();

        // 重新加载布局（系统不会自动切换layout-land，需要手动setContentView）
        setContentView(R.layout.activity_main);

        // 重新绑定视图引用
        initViews();
        setupListeners();

        // 恢复棋盘状态
        if (chessView != null) {
            if (currentFen != null) {
                chessView.setBoardByFENPreserveInitialFEN(currentFen, isFlipped);
            }
            if (initialFEN != null && !initialFEN.isEmpty()) {
                chessView.setCurrentInitialFEN(initialFEN);
            }
        }

        // 重新设置棋盘监听
        setupChessView();

        // 恢复UI显示
        updateTitle();
        updateAccuracy();
        updateTimeDisplay();
        updateTurnIndicator(currentSide);

        // 恢复诗词显示（tvShiju已在initViews中重新绑定）
        if (tvShiju != null && poetryList != null && !poetryList.isEmpty()) {
            // 重新显示随机诗词，避免旋转后诗句消失
            showRandomPoetry();
        }

        // 横屏时程序化调整棋盘尺寸
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            adjustBoardSizeForLandscape();
        }

        // 恢复计时器
        resumeTimers();

        // 如果AI正在思考，恢复触摸禁用状态
        if (isPikafishThinking && chessView != null) {
            chessView.setTouchEnabled(false);
        }

        Log.d("MainActivity", "配置变更: " + (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏"));
    }

    /**
     * 横屏时程序化调整棋盘尺寸：
     * - 棋盘高度 = 屏幕高度
     * - 棋盘宽度 = 高度 × 棋盘宽高比（9:10 或 678:750，由ChessView的activeScheme决定）
     * - 棋盘中心在屏幕宽度的37%位置
     */
    private void adjustBoardSizeForLandscape() {
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

        Log.d("MainActivity", "横屏棋盘尺寸: " + boardWidth + "x" + boardHeight + ", left=" + boardLeft);
    }

}
