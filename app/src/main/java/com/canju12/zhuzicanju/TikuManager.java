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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TikuManager {

    private static final String TAG = "TikuManager";
    private Context context;
    private List<TikuData> tikuList = new ArrayList<>();
    private TikuData currentTiku = null;

    public TikuManager(Context context) {
        this.context = context;
        // 鍒濆鍖栫洰褰曪紙鍒涘缓蹇呰鐨勬枃浠跺す锛?
        initTikuDirectory();
        // 鍔犺浇鎵€鏈夐搴擄紙鍏?assets锛屽啀澶栭儴瀛樺偍锛?
        loadAllTiku();
    }

    /**
     * 鍒濆鍖栭搴撶洰褰曪紙澶栭儴瀛樺偍锛?
     */
    private void initTikuDirectory() {
        try {
            // 澶栭儴瀛樺偍鏍圭洰褰曚笅鐨?canju12/tiku
            File externalDir = Environment.getExternalStorageDirectory();
            File canju12Dir = new File(externalDir, "canju12");
            if (!canju12Dir.exists()) {
                canju12Dir.mkdirs();
            }

            File tikuDir = new File(canju12Dir, "tiku");
            if (!tikuDir.exists()) {
                tikuDir.mkdirs();
                Log.d(TAG, "鍒涘缓澶栭儴棰樺簱鐩綍: " + tikuDir.getAbsolutePath());
            }

        } catch (Exception e) {
            Log.e(TAG, "鍒濆鍖栭搴撶洰褰曞け璐? " + e.getMessage());
        }
    }

    /**
     * 鍔犺浇鎵€鏈夐搴擄紙鍏?assets 鍐呯疆锛屽啀 assets 闅愯棌锛屽啀澶栭儴瀛樺偍锛?
     */
    public void loadAllTiku() {
        tikuList.clear();

        // 1. 浠?assets/NeizhiTiku 鍔犺浇棰樺簱
        loadTikuFromAssets("NeizhiTiku");

        // 2. 浠?assets/YincangTiku 鍔犺浇棰樺簱锛堟槸鍚﹀睍绀虹敱澶栭儴鎺у埗锛?
        loadTikuFromAssets("YincangTiku");

        // 3. 浠庡閮ㄥ瓨鍌ㄥ姞杞介搴?
        loadTikuFromExternal();

        Log.d(TAG, "共加载 " + tikuList.size() + " 个题库");
    }

    /**
     * 浠?assets 鐨勬寚瀹氭枃浠跺す鍔犺浇棰樺簱
     */
    private void loadTikuFromAssets(String folder) {
        try {
            String[] files = context.getAssets().list(folder);
            if (files != null) {
                for (String fileName : files) {
                    if (fileName.endsWith(".txt")) {
                        // 妫€鏌ユ槸鍚﹀凡缁忓湪鍒楄〃涓紙閬垮厤閲嶅锛?
                        if (isTikuExists(fileName)) {
                            Log.d(TAG, "棰樺簱宸插瓨鍦紝璺宠繃: " + fileName);
                            continue;
                        }

                        String name = fileName.replace(".txt", "");
                        TikuData tiku = new TikuData(name, fileName);
                        tiku.setFromYincang(folder.equals("YincangTiku"));
                        loadTikuFromAssetsFile(tiku, folder);
                        tikuList.add(tiku);
                        Log.d(TAG, "从assets加载题库: " + name + ", 共 " + tiku.getTotalCount() + " 题");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "浠巃ssets鍔犺浇棰樺簱澶辫触: " + e.getMessage());
        }
    }

    /**
     * 浠?assets 鍔犺浇鍗曚釜棰樺簱鏂囦欢
     */
    private void loadTikuFromAssetsFile(TikuData tiku, String folder) {
        List<String> fenList = new ArrayList<>();
        List<String> initialFENList = new ArrayList<>();

        try {
            InputStream is = context.getAssets().open(folder + "/" + tiku.getFileName());
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("//")) {
                    fenList.add(line);
                    initialFENList.add(line);
                }
            }

            reader.close();
            is.close();
            tiku.setFenList(fenList);
            tiku.setInitialFENList(initialFENList);

        } catch (Exception e) {
            Log.e(TAG, "鍔犺浇assets棰樺簱鏂囦欢澶辫触: " + tiku.getFileName() + " - " + e.getMessage());
        }
    }

    /**
     * 浠庡閮ㄥ瓨鍌ㄥ姞杞介搴?
     */
    private void loadTikuFromExternal() {
        try {
            File externalDir = Environment.getExternalStorageDirectory();
            File canju12Dir = new File(externalDir, "canju12");
            File tikuDir = new File(canju12Dir, "tiku");

            if (!tikuDir.exists()) {
                Log.d(TAG, "澶栭儴棰樺簱鐩綍涓嶅瓨鍦? " + tikuDir.getAbsolutePath());
                return;
            }

            File[] files = tikuDir.listFiles();
            if (files == null || files.length == 0) {
                Log.d(TAG, "澶栭儴棰樺簱鐩綍涓虹┖");
                return;
            }

            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".txt")) {
                    // 妫€鏌ユ槸鍚﹀凡缁忓湪鍒楄〃涓紙閬垮厤閲嶅锛?
                    if (isTikuExists(file.getName())) {
                        Log.d(TAG, "棰樺簱宸插瓨鍦紝璺宠繃: " + file.getName());
                        continue;
                    }

                    String name = file.getName().replace(".txt", "");
                    TikuData tiku = new TikuData(name, file.getName());
                    loadTikuFromExternalFile(tiku, file);
                    tikuList.add(tiku);
                    Log.d(TAG, "从外部加载题库: " + name + ", 共 " + tiku.getTotalCount() + " 题");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "浠庡閮ㄥ瓨鍌ㄥ姞杞介搴撳け璐? " + e.getMessage());
        }
    }

    /**
     * 浠庡閮ㄥ瓨鍌ㄥ姞杞藉崟涓搴撴枃浠?
     */
    private void loadTikuFromExternalFile(TikuData tiku, File file) {
        List<String> fenList = new ArrayList<>();
        List<String> initialFENList = new ArrayList<>();

        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("//")) {
                    fenList.add(line);
                    initialFENList.add(line);
                }
            }

            reader.close();
            fis.close();
            tiku.setFenList(fenList);
            tiku.setInitialFENList(initialFENList);

        } catch (Exception e) {
            Log.e(TAG, "鍔犺浇澶栭儴棰樺簱鏂囦欢澶辫触: " + tiku.getFileName() + " - " + e.getMessage());
        }
    }

    /**
     * 妫€鏌ラ搴撴槸鍚﹀凡瀛樺湪锛堟寜鏂囦欢鍚嶏級
     */
    private boolean isTikuExists(String fileName) {
        for (TikuData tiku : tikuList) {
            if (tiku.getFileName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 鑾峰彇鎵€鏈夐搴撳垪琛?
     */
    public List<TikuData> getTikuList() {
        return tikuList;
    }

    /**
     * 鏍规嵁鍚嶇О鑾峰彇棰樺簱
     */
    public TikuData getTikuByName(String name) {
        for (TikuData tiku : tikuList) {
            if (tiku.getName().equals(name)) {
                return tiku;
            }
        }
        return null;
    }
    /**
     * 鑾峰彇褰撳墠棰樺簱鐨勪笅涓€棰楩EN
     * @return 涓嬩竴棰樼殑FEN锛屽鏋滄病鏈夊垯杩斿洖null
     */
    public String getNextFEN() {
        if (currentTiku == null) {
            Log.e(TAG, "褰撳墠棰樺簱涓虹┖");
            return null;
        }
        return currentTiku.getNextFEN();
    }

    /**
     * 鑾峰彇褰撳墠棰樺簱鐨勫綋鍓嶇储寮?
     */
    public int getCurrentIndex() {
        if (currentTiku == null) {
            return 0;
        }
        return currentTiku.getCurrentIndex();
    }

    /**
     * 鑾峰彇褰撳墠棰樺簱鐨勬€婚鏁?
     */
    public int getTotalCount() {
        if (currentTiku == null) {
            return 0;
        }
        return currentTiku.getTotalCount();
    }

    /**
     * 鑾峰彇褰撳墠棰樺簱鐨勫悕绉?
     */
    public String getCurrentTikuName() {
        if (currentTiku == null) {
            return "鏈€夋嫨棰樺簱";
        }
        return currentTiku.getName();
    }
    /**
     * 璁剧疆褰撳墠棰樺簱
     */
    public void setCurrentTiku(TikuData tiku) {
        this.currentTiku = tiku;
    }

    /**
     * 鑾峰彇褰撳墠棰樺簱
     */
    public TikuData getCurrentTiku() {
        return currentTiku;
    }

    /**
     * 鍒锋柊棰樺簱
     */
    public void reloadTiku() {
        loadAllTiku();
    }
    /**
     * 淇濆瓨棰樺簱杩涘害
     * @param tiku 瑕佷繚瀛樿繘搴︾殑棰樺簱
     */
    public void saveProgress(TikuData tiku) {
        // 宸插純鐢紝鐢?ProgressManager 绠＄悊
    }



}