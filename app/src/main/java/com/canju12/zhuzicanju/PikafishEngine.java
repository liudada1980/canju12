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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Pikafish UCI 引擎通信封装
 * 负责启动 pikafish 进程，通过 UCI 协议获取最佳走法
 */
public class PikafishEngine {

    private static final String TAG = "PikafishEngine";
    private static final String ENGINE_ASSET_PATH = "Engine/pikafish-armv8";
    private static final String NNUE_ASSET_PATH = "Engine/pikafish.nnue";
    private static final String NNUE_FILE_NAME = "pikafish.nnue";
    private static final String ENGINE_BINARY_NAME = "pikafish-armv8";
    private static final int DEFAULT_DEPTH = 18;
    private static final long DEFAULT_MOVE_TIME_MS = 500;
    private static final long UCI_TIMEOUT_MS = 15000; // UCI 握手超时（15秒）

    private Process process;
    private BufferedReader reader;
    private OutputStreamWriter writer;
    private boolean isReady = false;
    private boolean isQuitting = false;
    private String lastError = "";
    private final Object analysisLock = new Object();
    private volatile boolean analysisActive = false;
    private volatile Thread analysisThread = null; // 当前分析线程引用
    private volatile int currentSearchId = 0;      // 搜索ID，每次新搜索递增，用于区分过期回调

    public interface PikafishCallback {
        void onBestMove(String move);
        void onError(String error);
    }

    /**
     * 分析回调：返回搜索信息和最佳走法
     */
    public interface AnalysisCallback {
        void onResult(String infoLine, String bestMove);
        void onError(String error);
    }

    /**
     * 启动引擎：解压二进制 → 启动进程 → UCI握手
     */
    public void start(Context context) throws Exception {
        // 1. 先提取 NNUE 评估文件（引擎搜索时需要）
        File nnueFile = extractNNUEFile(context);

        // 2. 尝试从 nativeLibraryDir 执行（通过 jniLibs 部署）
        File binaryInLib = tryNativeLibDir(context);
        if (binaryInLib != null) {
            Log.d(TAG, "在 nativeLibraryDir 找到二进制: " + binaryInLib.getAbsolutePath());
            startProcess(binaryInLib, nnueFile);
            return;
        }

        // 3. 从 assets 解压并执行
        File binaryFile = extractBinary(context);
        startProcess(binaryFile, nnueFile);
    }

    /**
     * 尝试从 nativeLibraryDir 查找二进制
     * 如果通过 jniLibs/arm64-v8a/libpikafish.so 部署，系统会解压到这里
     */
    private File tryNativeLibDir(Context context) {
        try {
            String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
            File libFile = new File(nativeLibDir, "libpikafish.so");
            if (libFile.exists() && libFile.length() > 0) {
                Log.d(TAG, "nativeLibraryDir: " + nativeLibDir);
                Log.d(TAG, "libpikafish.so 存在，大小: " + libFile.length());
                return libFile;
            }
            Log.d(TAG, "nativeLibraryDir 未找到 libpikafish.so");
        } catch (Exception e) {
            Log.w(TAG, "查找 nativeLibraryDir 失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 启动进程并进行 UCI 握手
     * @param binaryFile 引擎二进制
     * @param nnueFile NNUE 评估文件（可能为 null）
     */
    private void startProcess(File binaryFile, File nnueFile) throws Exception {
        Log.d(TAG, "二进制路径: " + binaryFile.getAbsolutePath());
        Log.d(TAG, "二进制大小: " + binaryFile.length() + " bytes");

        // 确保可执行
        if (!binaryFile.canExecute()) {
            binaryFile.setExecutable(true);
            Log.d(TAG, "已设置可执行权限");
        }

        // 尝试多种方式启动进程
        Exception lastException = null;

        // 方法A：直接执行
        try {
            Log.d(TAG, "尝试直接执行: " + binaryFile.getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(binaryFile.getAbsolutePath());
            pb.directory(binaryFile.getParentFile());
            process = pb.start();

            // 快速检测进程是否立即崩溃
            Thread.sleep(300);
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                String stderr = readStderr();
                Log.w(TAG, "直接执行后进程退出，exitCode=" + exitCode +
                        ", stderr=" + stderr);
                throw new Exception("进程退出(exitCode=" + exitCode + "): " +
                        (stderr.isEmpty() ? "(无错误输出)" : stderr.trim()));
            }
            Log.d(TAG, "直接执行成功");
        } catch (Exception e) {
            Log.w(TAG, "直接执行失败: " + e.getMessage());
            lastException = e;

            // 方法B：通过 linker 执行（Android 10+ 需要）
            try {
                Log.d(TAG, "尝试通过 linker 执行");
                // 检测设备架构
                String[] abis = Build.SUPPORTED_ABIS;
                boolean is64bit = false;
                if (abis != null) {
                    for (String abi : abis) {
                        if (abi.contains("64")) {
                            is64bit = true;
                            break;
                        }
                    }
                }
                String linker = is64bit ? "/system/bin/linker64" : "/system/bin/linker";
                Log.d(TAG, "使用 linker: " + linker + " (64bit=" + is64bit + ")");

                ProcessBuilder pb = new ProcessBuilder(
                        linker, binaryFile.getAbsolutePath());
                pb.directory(binaryFile.getParentFile());
                process = pb.start();

                Thread.sleep(300);
                if (!process.isAlive()) {
                    int exitCode = process.exitValue();
                    String stderr = readStderr();
                    Log.w(TAG, "linker 后进程退出，exitCode=" + exitCode +
                            ", stderr=" + stderr);
                    throw new Exception("linker执行后进程退出(" + exitCode + "):" +
                            (stderr.isEmpty() ? "" : stderr.trim()));
                }
                Log.d(TAG, "linker 执行成功");
                lastException = null;
            } catch (Exception e2) {
                Log.w(TAG, "linker 也失败: " + e2.getMessage());

                // 方法C：通过 shell 执行
                try {
                    Log.d(TAG, "尝试通过 shell 执行");
                    ProcessBuilder pb = new ProcessBuilder(
                            "/system/bin/sh", "-c",
                            "\"" + binaryFile.getAbsolutePath() + "\"");
                    pb.directory(binaryFile.getParentFile());
                    process = pb.start();

                    Thread.sleep(300);
                    if (!process.isAlive()) {
                        int exitCode = process.exitValue();
                        String stderr = readStderr();
                        Log.w(TAG, "shell 后进程退出，exitCode=" + exitCode +
                                ", stderr=" + stderr);
                        throw new Exception("shell执行后进程退出(" + exitCode + "):" +
                                (stderr.isEmpty() ? "" : stderr.trim()));
                    }
                    Log.d(TAG, "shell 执行成功");
                    lastException = null;
                } catch (Exception e3) {
                    throw new Exception("无法执行引擎二进制:\n" +
                            "方法1(直接): " + e.getMessage() + "\n" +
                            "方法2(linker): " + e2.getMessage() + "\n" +
                            "方法3(shell): " + e3.getMessage());
                }
            }
        }

        // 4. 设置输入输出流
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        writer = new OutputStreamWriter(process.getOutputStream(), "UTF-8");

        // 5. UCI 握手
        Log.d(TAG, "开始 UCI 握手...");

        sendCommand("uci");
        String uciok = waitForResponse("uciok", UCI_TIMEOUT_MS);
        Log.d(TAG, "收到 uciok: " + uciok);

        sendCommand("isready");
        String readyok = waitForResponse("readyok", UCI_TIMEOUT_MS);
        Log.d(TAG, "收到 readyok: " + readyok);

        // 设置 NNUE 评估文件路径（引擎搜索时必需）
        if (nnueFile != null && nnueFile.exists()) {
            String nnuePath = nnueFile.getAbsolutePath();
            sendCommand("setoption name EvalFile value " + nnuePath);
            Log.d(TAG, "已设置 NNUE 路径: " + nnuePath);
            // 设置后需要重新 isready
            sendCommand("isready");
            waitForResponse("readyok", UCI_TIMEOUT_MS);
        } else {
            Log.w(TAG, "NNUE 文件不存在，引擎搜索可能崩溃");
        }

        // 设置引擎参数
        sendCommand("setoption name Threads value 2");
        sendCommand("setoption name Hash value 256");
        sendCommand("setoption name Repetition Rule value ChineseRule");
        sendCommand("setoption name Sixty Move Rule value true");
        sendCommand("isready");
        waitForResponse("readyok", 5000);
        Log.d(TAG, "已设置 Threads=2, Hash=256MB, RepetitionRule=ChineseRule");

        isReady = true;
        Log.d(TAG, "★★★ Pikafish 引擎启动成功 ★★★");
    }

    /**
     * 从 assets/Engine/ 解压 pikafish 二进制
     */
    private File extractBinary(Context context) throws Exception {
        File filesDir = context.getFilesDir();
        File binaryFile = new File(filesDir, ENGINE_BINARY_NAME);

        // 检查 assets 文件是否存在（不用 openFd，避免压缩格式问题）
        boolean assetExists;
        try {
            InputStream testIs = context.getAssets().open(ENGINE_ASSET_PATH);
            assetExists = true;
            testIs.close();
            Log.d(TAG, "Assets 文件存在: " + ENGINE_ASSET_PATH);
        } catch (Exception e) {
            throw new Exception("assets/Engine/pikafish-armv8 未找到!\n" +
                    "请确认文件位于:\n" +
                    "app/src/main/assets/Engine/pikafish-armv8\n" +
                    "注意: 不要用 .so 格式!", e);
        }

        // 如果已存在，跳过解压
        if (binaryFile.exists() && binaryFile.length() > 0) {
            Log.d(TAG, "pikafish 已存在，跳过解压 (大小: " + binaryFile.length() + " bytes)");
            return binaryFile;
        }

        // 已存在但大小为0，删除重新解压
        if (binaryFile.exists()) {
            binaryFile.delete();
        }

        // 从 assets 读取并写入
        Log.d(TAG, "开始解压二进制...");
        try (InputStream is = context.getAssets().open(ENGINE_ASSET_PATH);
             FileOutputStream os = new FileOutputStream(binaryFile)) {

            byte[] buffer = new byte[65536];
            int totalRead = 0;
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }

            Log.d(TAG, "解压完成: " + totalRead + " bytes 写入");
        }

        if (!binaryFile.setExecutable(true)) {
            Log.w(TAG, "setExecutable 返回 false");
        }

        Log.d(TAG, "二进制就绪: " + binaryFile.getAbsolutePath() +
                " (" + binaryFile.length() + " bytes)");
        return binaryFile;
    }

    /**
     * 从 assets/Engine/ 提取 pikafish.nnue 评估文件到内部存储
     * NNUE 文件是神经网络权重，引擎搜索时必须加载
     */
    private File extractNNUEFile(Context context) throws Exception {
        File filesDir = context.getFilesDir();
        File nnueFile = new File(filesDir, NNUE_FILE_NAME);

        // 检查 assets 中是否存在
        boolean assetExists;
        try {
            InputStream testIs = context.getAssets().open(NNUE_ASSET_PATH);
            assetExists = true;
            testIs.close();
            Log.d(TAG, "NNUE assets 文件存在: " + NNUE_ASSET_PATH);
        } catch (Exception e) {
            Log.w(TAG, "NNUE 文件不存在于 assets，引擎可能无法搜索: " + NNUE_ASSET_PATH);
            return null;  // 没有 NNUE 文件也可以运行（会降级）
        }

        // 已存在则跳过
        if (nnueFile.exists() && nnueFile.length() > 0) {
            Log.d(TAG, "NNUE 已存在，跳过解压 (大小: " + nnueFile.length() + " bytes)");
            return nnueFile;
        }

        // 删除旧文件
        if (nnueFile.exists()) {
            nnueFile.delete();
        }

        // 解压
        Log.d(TAG, "开始解压 NNUE 文件...");
        try (InputStream is = context.getAssets().open(NNUE_ASSET_PATH);
             FileOutputStream os = new FileOutputStream(nnueFile)) {

            byte[] buffer = new byte[65536];
            int totalRead = 0;
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            Log.d(TAG, "NNUE 解压完成: " + totalRead + " bytes");
        }

        return nnueFile;
    }

    /**
     * 异步获取最佳走法（在主线程回调）
     */
    public void getBestMoveAsync(final String fen,
                                  final int depth,
                                  final long maxTimeMs,
                                  final PikafishCallback callback) {
        if (!isReady || isQuitting) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("引擎未就绪"));
            }
            return;
        }

        final long startTime = System.currentTimeMillis();

        Thread t = new Thread(() -> {
            synchronized (analysisLock) {
                analysisActive = true;
                analysisLock.notifyAll();
            }
            try {
                // 发送局面
                sendCommand("position fen " + fen);

                // 发送搜索命令
                String goCmd = "go depth " + depth + " movetime " + maxTimeMs;
                Log.d(TAG, "搜索命令: " + goCmd);
                sendCommand(goCmd);

                long safetyTimeout = maxTimeMs + 5000; // 安全超时
                boolean sentStop = false;

                String line;
                while (analysisActive && (line = readLine()) != null) {
                    long elapsed = System.currentTimeMillis() - startTime;

                    // 只记录 info 的摘要，减少日志量
                    if (line.startsWith("bestmove")) {
                        String[] parts = line.split(" ");
                        final String move = (parts.length >= 2) ? parts[1] : null;
                        Log.d(TAG, "bestmove: " + move + " (耗时 " + elapsed + "ms)");

                        if (callback != null) {
                            new Handler(Looper.getMainLooper()).post(() ->
                                    callback.onBestMove(move));
                        }
                        return;
                    }

                    // 安全超时：发送 stop
                    if (!sentStop && elapsed > safetyTimeout) {
                        Log.w(TAG, "搜索超时 (" + elapsed + "ms)，发送 stop");
                        sendCommand("stop");
                        sentStop = true;
                    }
                }

                // 流关闭
                Log.e(TAG, "引擎输出流意外关闭");
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onError("引擎输出流关闭"));
                }
            } catch (Exception e) {
                Log.e(TAG, "获取走法失败", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onError(e.getMessage()));
                }
            } finally {
                synchronized (analysisLock) {
                    analysisActive = false;
                    analysisLock.notifyAll();
                }
            }
        });
        analysisThread = t;
        t.start();
    }

    /**
     * 简化调用：使用默认深度和时间
     */
    public void getBestMoveAsync(String fen, PikafishCallback callback) {
        getBestMoveAsync(fen, DEFAULT_DEPTH, DEFAULT_MOVE_TIME_MS, callback);
    }

    /**
     * 异步分析局面，逐层返回结果
     * 每完成一层 >= 14 层就回调一次，最多保留最近3层
     * FEN变更时无需停止引擎，直接发新 position + go 即可，引擎会自动中断旧搜索
     * 只有用户点击"停止"时才调用 stopAnalysis()
     * @param fen       当前局面 FEN
     * @param maxDepth  最大搜索深度：0 = 无限深度（go infinite），>0 = 限定深度（go depth N）
     * @param callback  回调
     */
    public void analyzePositionAsync(final String fen, final int maxDepth, final AnalysisCallback callback) {
        if (!isReady || isQuitting) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("引擎未就绪"));
            }
            return;
        }

        final int searchId = ++currentSearchId;

        // 先 stop 当前搜索（如果正在运行），让引擎尽快输出 bestmove 以便接收新命令
        // 不等待线程退出，因为读取线程是持久的
        try {
            sendCommand("stop");
        } catch (Exception e) {
            Log.w(TAG, "发送 stop 失败: " + e.getMessage());
        }

        // 确保持久读取线程在运行
        synchronized (analysisLock) {
            if (analysisThread == null || !analysisThread.isAlive()) {
                startReaderThread(callback, searchId);
            } else {
                // 读取线程已在运行，只通知它有新搜索
                analysisLock.notifyAll();
            }
        }

        // 在新线程中发送 position + go（sendCommand 可能阻塞）
        new Thread(() -> {
            try {
                sendCommand("position fen " + fen);
                if (maxDepth > 0) {
                    sendCommand("go depth " + maxDepth);
                    Log.d(TAG, "限定深度分析: go depth " + maxDepth + ", searchId=" + searchId);
                } else {
                    sendCommand("go infinite");
                    Log.d(TAG, "无限深度分析: go infinite, searchId=" + searchId);
                }
            } catch (Exception e) {
                Log.e(TAG, "发送分析命令失败", e);
            }
        }).start();
    }

    /**
     * 持久读取线程：持续从引擎读取输出，根据 searchId 分发回调
     * 引擎收到 stop+新position+go 后会中断旧搜索并输出 bestmove，
     * 旧 bestmove 的 searchId 已过期，回调中会被忽略
     */
    private void startReaderThread(final AnalysisCallback callback, final int initialSearchId) {
        Thread t = new Thread(() -> {
            Log.d(TAG, "持久读取线程启动");
            int lastReportedDepth = 0;
            String lastInfo = "";
            int activeSearchId = initialSearchId;

            try {
                while (analysisActive) {
                    String line = readLine();
                    if (line == null) {
                        Log.w(TAG, "引擎输出流关闭，读取线程退出");
                        break;
                    }

                    // 检查是否有新的搜索请求（searchId 变化）
                    int latestSearchId = currentSearchId;
                    if (latestSearchId != activeSearchId) {
                        activeSearchId = latestSearchId;
                        lastReportedDepth = 0;
                        lastInfo = "";
                        Log.d(TAG, "切换到新搜索: searchId=" + activeSearchId);
                    }

                    if (line.startsWith("bestmove")) {
                        String[] parts = line.split(" ");
                        String bestMove = (parts.length >= 2) ? parts[1] : null;
                        final String finalInfo = lastInfo;
                        final String finalMove = bestMove;
                        final int cbSearchId = activeSearchId;
                        if (callback != null) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                // 只回调当前搜索的结果
                                if (cbSearchId == currentSearchId) {
                                    callback.onResult(finalInfo, finalMove);
                                }
                            });
                        }
                        // bestmove 后重置深度追踪
                        lastReportedDepth = 0;
                        lastInfo = "";
                        continue;
                    }

                    if (line.startsWith("info") && line.contains(" depth ") && line.contains(" pv ")) {
                        lastInfo = line;
                        int depth = 0;
                        String[] tokens = line.split(" ");
                        for (int i = 0; i < tokens.length - 1; i++) {
                            if (tokens[i].equals("depth")) {
                                try { depth = Integer.parseInt(tokens[i + 1]); } catch (Exception e) {}
                                break;
                            }
                        }
                        if (depth >= 14 && depth > lastReportedDepth) {
                            lastReportedDepth = depth;
                            final String reportInfo = line;
                            final int cbSearchId = activeSearchId;
                            if (callback != null) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (cbSearchId == currentSearchId) {
                                        callback.onResult(reportInfo, null);
                                    }
                                });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "读取线程异常退出", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onError(e.getMessage()));
                }
            }
            Log.d(TAG, "持久读取线程退出");
        });
        analysisActive = true;
        analysisThread = t;
        t.start();
    }

    /**
     * 停止分析（仅用户点击"停止"按钮时调用）
     */
    public void stopAnalysis() {
        analysisActive = false;
        try {
            sendCommand("stop");
        } catch (Exception e) {
            Log.e(TAG, "发送 stop 失败", e);
        }
        synchronized (analysisLock) {
            analysisLock.notifyAll();
        }
    }

    /**
     * 关闭引擎
     */
    public void quit() {
        isQuitting = true;
        try {
            sendCommand("quit");
        } catch (Exception ignored) {}

        try {
            if (process != null) {
                Thread.sleep(200);
                process.destroy();
            }
        } catch (Exception ignored) {}

        isReady = false;
        Log.d(TAG, "Pikafish 引擎已关闭");
    }

    public boolean isReady() {
        return isReady;
    }

    public String getLastError() {
        return lastError;
    }

    /**
     * 仅发送命令到引擎（不读取响应），供外部调用者自行管理读取
     */
    public void sendCommandOnly(String cmd) throws Exception {
        sendCommand(cmd);
    }

    /**
     * 从引擎读取一行输出，供外部调用者自行管理读取
     * @return 一行文本，流关闭时返回null
     */
    public String readLineFromEngine() throws Exception {
        return readLine();
    }

    // ==================== 内部方法 ====================

    /**
     * 读取进程 stderr（进程已退出时读取）
     */
    private String readStderr() {
        try {
            if (process == null) return "";
            BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = errReader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "(读取stderr失败: " + e.getMessage() + ")";
        }
    }

    private void sendCommand(String cmd) throws Exception {
        if (writer == null) throw new Exception("writer 为空");
        writer.write(cmd + "\n");
        writer.flush();
        Log.v(TAG, ">>> " + cmd);
    }

    private String readLine() throws Exception {
        if (reader == null) return null;
        return reader.readLine();
    }

    /**
     * 等待引擎返回包含特定关键字的行
     */
    private String waitForResponse(String expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String line;
        while ((line = readLine()) != null) {
            Log.d(TAG, "<<< " + line);
            if (line.contains(expected)) {
                return line;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new Exception("等待 '" + expected + "' 超时 (" + timeoutMs + "ms)");
            }
        }
        throw new Exception("引擎输出流提前关闭，未收到 '" + expected + "'");
    }
}
