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
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;

public class ProgressManager {

    private static final String TAG = "ProgressManager";
    private static ProgressManager instance;
    private Context context;
    private Set<String> completedQuestions;
    private long totalPlayTime;
    private Set<String> completedTikus;
    private boolean unlockNotified;
    private String lastTiku;      // 上次停留的题库名
    private int lastIndex;        // 上次停留的题号

    private static final long UNLOCK_TIME_SECONDS = 86400;
    // 内置题库名（与 assets/NeizhiTiku 下文件名去掉 .txt 一致；
    // markTikuCompleted 存的也是这个名，这里必须与实际题库名完全一致才能匹配）
    private static final String[] BUILT_IN_TIKUS = {"连将杀一至四步杀", "连将杀五至七步杀",
            "竹子涨棋", "象棋杀着大全"};

    private ProgressManager(Context context) {
        this.context = context.getApplicationContext();
        this.completedQuestions = new HashSet<>();
        this.completedTikus = new HashSet<>();
        loadProgress();
    }

    public static synchronized ProgressManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProgressManager(context);
        } else {
            instance.loadProgress();
        }
        return instance;
    }

    public void reload() { loadProgress(); }

    private File getProgressFile() {
        File ext = Environment.getExternalStorageDirectory();
        return new File(new File(new File(ext, "canju12"), "tiku/progressManager"), "progress.txt");
    }

    private String getKey(String tikuName, int index) {
        return tikuName + "_" + index;
    }

    public void loadProgress() {
        completedQuestions.clear();
        completedTikus.clear();
        totalPlayTime = 0;
        unlockNotified = false;
        lastTiku = null;
        lastIndex = -1;

        File file = getProgressFile();
        if (!file.exists()) {
            try {
                File parent = file.getParentFile();
                if (!parent.exists()) parent.mkdirs();
            } catch (Exception ignored) {}
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line, section = "";
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[questions]")) { section = "q"; continue; }
                if (line.startsWith("[tikus]")) { section = "t"; continue; }
                if (line.startsWith("[meta]")) { section = "m"; continue; }

                switch (section) {
                    case "q": completedQuestions.add(line); break;
                    case "t": completedTikus.add(line); break;
                    case "m":
                        if (line.startsWith("time=")) totalPlayTime = Long.parseLong(line.substring(5));
                        else if (line.startsWith("notified=")) unlockNotified = Boolean.parseBoolean(line.substring(9));
                        else if (line.startsWith("lastTiku=")) lastTiku = line.substring(9);
                        else if (line.startsWith("lastIndex=")) lastIndex = Integer.parseInt(line.substring(10));
                        break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "加载进度失败", e);
        }
        Log.d(TAG, "加载进度: " + completedQuestions.size() + " 题, " +
                completedTikus.size() + " 题库完成, 时间=" + totalPlayTime + "s");
    }

    public void saveProgress() {
        try {
            File file = getProgressFile();
            File parent = file.getParentFile();
            if (!parent.exists()) parent.mkdirs();

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                writer.write("# 一二残局进度文件\n\n");
                writer.write("[questions]\n");
                for (String q : completedQuestions) writer.write(q + "\n");
                writer.write("\n[tikus]\n");
                for (String t : completedTikus) writer.write(t + "\n");
                writer.write("\n[meta]\n");
                writer.write("time=" + totalPlayTime + "\n");
                writer.write("notified=" + unlockNotified + "\n");
                if (lastTiku != null) writer.write("lastTiku=" + lastTiku + "\n");
                if (lastIndex >= 0) writer.write("lastIndex=" + lastIndex + "\n");
            }
        } catch (Exception e) {
            Log.w(TAG, "保存进度失败", e);
        }
    }

    public void markCompleted(String tikuName, int index) {
        completedQuestions.add(getKey(tikuName, index));
        saveProgress();
    }

    public void markTikuCompleted(String tikuName) {
        completedTikus.add(tikuName);
        saveProgress();
    }

    public boolean isCompleted(String tikuName, int index) {
        return completedQuestions.contains(getKey(tikuName, index));
    }

    public int getCompletedCount(String tikuName) {
        int count = 0;
        for (String key : completedQuestions) {
            if (key.startsWith(tikuName + "_")) count++;
        }
        return count;
    }

    public boolean isTikuFullyCompleted(String tikuName, int totalCount) {
        return getCompletedCount(tikuName) >= totalCount;
    }

    public void resetTikuProgress(String tikuName) {
        Set<String> toRemove = new HashSet<>();
        for (String key : completedQuestions) {
            if (key.startsWith(tikuName + "_")) toRemove.add(key);
        }
        completedQuestions.removeAll(toRemove);
        completedTikus.remove(tikuName);
        saveProgress();
    }

    public void addPlayTime(long seconds) {
        totalPlayTime += seconds;
        saveProgress();
    }

    /** 检查是否需要显示解锁弹窗 */
    public boolean needsUnlockNotification() {
        return isUnlocked() && !unlockNotified;
    }

    public long getTotalPlayTime() { return totalPlayTime; }

    public boolean isUnlocked() {
        // 条件一：累计使用时长 ≥ 24 小时（86400 秒）
        if (totalPlayTime >= UNLOCK_TIME_SECONDS) return true;
        // 条件二：完成内置题库中的任何一个
        for (String tiku : BUILT_IN_TIKUS) {
            if (completedTikus.contains(tiku)) return true;
        }
        return false;
    }

    public boolean isUnlockNotified() { return unlockNotified; }
    public void setUnlockNotified(boolean v) { unlockNotified = v; saveProgress(); }

    public String getLastTiku() { return lastTiku; }
    public int getLastIndex() { return lastIndex; }

    /**
     * 是否存在之前的载入记录（progress.txt 中有 lastTiku/lastIndex）
     * 用于判断是否为首次运行
     */
    public boolean hasLastPosition() {
        return lastTiku != null && lastIndex >= 0;
    }

    /**
     * 获取指定题库中已完成序号最大的题目索引
     * @param tikuName 题库名称
     * @return 最大已完成索引，如果没有已完成题目则返回 -1
     */
    public int getMaxCompletedIndex(String tikuName) {
        int maxIndex = -1;
        String prefix = tikuName + "_";
        for (String key : completedQuestions) {
            if (key.startsWith(prefix)) {
                try {
                    int idx = Integer.parseInt(key.substring(prefix.length()));
                    if (idx > maxIndex) maxIndex = idx;
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxIndex;
    }

    public void setLastPosition(String tikuName, int index) {
        lastTiku = tikuName;
        lastIndex = index;
        saveProgress();
    }
}
