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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TikuManager {

    private static final String TAG = "TikuManager";
    private static final long MAX_TIKU_FILE_BYTES = 16L * 1024L * 1024L;

    private final Context context;
    private final List<TikuData> internalTikuList = new ArrayList<>();
    private final List<TikuData> externalTikuList = new ArrayList<>();
    private final Map<String, ExternalTikuCacheEntry> externalTikuCache = new HashMap<>();
    private final List<TikuData> tikuList = new ArrayList<>();
    private TikuData currentTiku = null;

    private static final class ExternalTikuCacheEntry {
        final long length;
        final long lastModified;
        final TikuData data;

        ExternalTikuCacheEntry(long length, long lastModified, TikuData data) {
            this.length = length;
            this.lastModified = lastModified;
            this.data = data;
        }

        boolean matches(File file) {
            return file.length() == length && file.lastModified() == lastModified;
        }
    }

    public TikuManager(Context context) {
        this.context = context;
        initTikuDirectory();
        loadAllTiku();
    }

    private void initTikuDirectory() {
        try {
            File externalDir = Environment.getExternalStorageDirectory();
            File canju12Dir = new File(externalDir, "canju12");
            if (!canju12Dir.exists()) {
                canju12Dir.mkdirs();
            }

            File tikuDir = new File(canju12Dir, "tiku");
            if (!tikuDir.exists()) {
                tikuDir.mkdirs();
                Log.d(TAG, "创建外置题库目录: " + tikuDir.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化题库目录失败: " + e.getMessage());
        }
    }

    public synchronized void loadAllTiku() {
        internalTikuList.clear();
        externalTikuList.clear();

        loadTikuFromAssets("NeizhiTiku", internalTikuList);
        loadTikuFromAssets("YincangTiku", internalTikuList);
        loadTikuFromExternal(externalTikuList);

        rebuildCombinedList();
        Log.d(TAG, "共加载 " + tikuList.size() + " 个题库");
    }

    public synchronized void refreshExternalTiku() {
        externalTikuList.clear();
        loadTikuFromExternal(externalTikuList);
        rebuildCombinedList();
        Log.d(TAG, "刷新外置题库完成，共 " + tikuList.size() + " 个题库");
    }

    private void rebuildCombinedList() {
        tikuList.clear();
        tikuList.addAll(internalTikuList);
        tikuList.addAll(externalTikuList);
    }

    private void loadTikuFromAssets(String folder, List<TikuData> target) {
        try {
            String[] files = context.getAssets().list(folder);
            if (files == null) {
                return;
            }

            for (String fileName : files) {
                if (!fileName.endsWith(".txt") || containsFileName(fileName)) {
                    continue;
                }

                String name = fileName.replace(".txt", "");
                TikuData tiku = new TikuData(name, fileName);
                tiku.setFromYincang("YincangTiku".equals(folder));
                loadTikuFromAssetsFile(tiku, folder);
                target.add(tiku);
                Log.d(TAG, "加载内置题库: " + name + ", 共 " + tiku.getTotalCount() + " 题");
            }
        } catch (Exception e) {
            Log.e(TAG, "加载 assets 题库失败: " + e.getMessage());
        }
    }

    private void loadTikuFromAssetsFile(TikuData tiku, String folder) {
        tiku.clearFenData();

        try (InputStream is = context.getAssets().open(folder + "/" + tiku.getFileName());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 4096) {
                    Log.w(TAG, "skip oversized bundled tiku line: " + tiku.getFileName());
                    continue;
                }
                line = stripBom(line).trim();
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("//")) {
                    tiku.addFenLine(line);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "加载 assets 题库文件失败: " + tiku.getFileName() + " - " + e.getMessage());
        }
    }

    private void loadTikuFromExternal(List<TikuData> target) {
        try {
            File externalDir = Environment.getExternalStorageDirectory();
            File canju12Dir = new File(externalDir, "canju12");
            File tikuDir = new File(canju12Dir, "tiku");

            if (!tikuDir.exists()) {
                externalTikuCache.clear();
                return;
            }

            File[] files = tikuDir.listFiles();
            if (files == null || files.length == 0) {
                externalTikuCache.clear();
                return;
            }

            List<File> tikuFiles = new ArrayList<>();
            for (File file : files) {
                if (file != null && file.isFile() && file.getName().endsWith(".txt")
                        && !containsFileName(file.getName())
                        && file.length() <= MAX_TIKU_FILE_BYTES) {
                    tikuFiles.add(file);
                }
            }
            Collections.sort(tikuFiles, Comparator.comparing(File::getName));
            Map<String, Boolean> currentFiles = new HashMap<>();

            for (File file : tikuFiles) {
                currentFiles.put(file.getName(), Boolean.TRUE);
                ExternalTikuCacheEntry cached = externalTikuCache.get(file.getName());
                TikuData tiku;
                if (cached != null && cached.matches(file)) {
                    tiku = cached.data;
                    Log.d(TAG, "复用外置题库缓存: " + file.getName());
                } else {
                    String name = file.getName().replace(".txt", "");
                    tiku = new TikuData(name, file.getName());
                    loadTikuFromExternalFile(tiku, file);
                    externalTikuCache.put(file.getName(),
                            new ExternalTikuCacheEntry(file.length(), file.lastModified(), tiku));
                }
                target.add(tiku);
                Log.d(TAG, "加载外置题库: " + tiku.getName() + ", 共 " + tiku.getTotalCount() + " 题");
            }

            externalTikuCache.keySet().removeIf(name -> !currentFiles.containsKey(name));
        } catch (Exception e) {
            Log.e(TAG, "加载外置题库失败: " + e.getMessage());
        }
    }

    private void loadTikuFromExternalFile(TikuData tiku, File file) {
        tiku.clearFenData();

        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 4096) {
                    Log.w(TAG, "skip oversized external tiku line: " + file.getName());
                    continue;
                }
                line = stripBom(line).trim();
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("//")) {
                    tiku.addFenLine(line);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "加载外置题库文件失败: " + tiku.getFileName() + " - " + e.getMessage());
        }
    }

    private boolean containsFileName(String fileName) {
        for (TikuData tiku : internalTikuList) {
            if (fileName.equals(tiku.getFileName())) return true;
        }
        for (TikuData tiku : externalTikuList) {
            if (fileName.equals(tiku.getFileName())) return true;
        }
        return false;
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    public synchronized List<TikuData> getTikuList() {
        return new ArrayList<>(tikuList);
    }

    public synchronized TikuData getTikuByName(String name) {
        for (TikuData tiku : tikuList) {
            if (tiku.getName().equals(name)) {
                return tiku;
            }
        }
        return null;
    }

    public TikuData getCurrentTiku() {
        return currentTiku;
    }

    public void setCurrentTiku(TikuData tiku) {
        this.currentTiku = tiku;
    }

    public void reloadTiku() {
        loadAllTiku();
    }

    public void saveProgress(TikuData tiku) {
        // Deprecated: use ProgressManager.
    }

    public String getNextFEN() {
        if (currentTiku == null) {
            Log.e(TAG, "当前题库为空");
            return null;
        }
        return currentTiku.getNextFEN();
    }

    public int getCurrentIndex() {
        if (currentTiku == null) {
            return 0;
        }
        return currentTiku.getCurrentIndex();
    }

    public int getTotalCount() {
        if (currentTiku == null) {
            return 0;
        }
        return currentTiku.getTotalCount();
    }

    public String getCurrentTikuName() {
        if (currentTiku == null) {
            return "未选择题库";
        }
        return currentTiku.getName();
    }
}
