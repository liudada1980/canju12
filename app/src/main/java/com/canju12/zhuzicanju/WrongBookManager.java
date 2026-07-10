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
import java.util.ArrayList;
import java.util.List;

public class WrongBookManager {

    private List<String> wrongList = new ArrayList<>();

    public WrongBookManager() {
        // 无参数构造函数
    }

    /**
     * 加载错题本
     * 去重规则：只去除连续重复行（含2#前缀的数据和无2#前缀的数据视为不同数据），
     * 不连续的重复行保留。
     */
    public void load() {
        wrongList.clear();
        try {
            File file = getWrongBookFile();
            if (!file.exists()) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line, lastLine = null;
            boolean hasDuplicate = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // 相邻去重：只去连续重复，2#前缀参与完整字符串比较
                    if (!line.equals(lastLine)) {
                        wrongList.add(line);
                    } else {
                        hasDuplicate = true;
                    }
                    lastLine = line;
                }
            }
            reader.close();

            // 如果有相邻重复，重写文件
            if (hasDuplicate) {
                save();
                Log.d("WrongBookManager", "已清理相邻重复行");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存错题本
     */
    public void save() {
        try {
            File file = getWrongBookFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write("# 错题本\n\n");
            for (String fen : wrongList) {
                writer.write(fen + "\n");
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取错题本文件
     */
    private File getWrongBookFile() {
        File externalDir = Environment.getExternalStorageDirectory();
        File canju12Dir = new File(externalDir, "canju12");
        File tikuDir = new File(canju12Dir, "tiku");
        return new File(tikuDir, "错题本.txt");
    }

    public List<String> getList() {
        return wrongList;
    }

    public int getCount() {
        return wrongList.size();
    }

    /**
     * 添加错题（只检查与最后一行是否连续重复，不检查全局）
     * 2#前缀参与完整字符串比较，"2#xxx"和"xxx"视为不同数据
     */
    public void add(String fen) {
        // 只检查与列表最后一行是否相同（防止连续重复）
        if (!wrongList.isEmpty() && wrongList.get(wrongList.size() - 1).equals(fen)) {
            return;
        }
        wrongList.add(fen);
        save();
    }

    public void remove(int position) {
        if (position >= 0 && position < wrongList.size()) {
            wrongList.remove(position);
            save();
        }
    }

    public void move(int from, int to) {
        if (from < 0 || from >= wrongList.size() || to < 0 || to >= wrongList.size()) return;
        String item = wrongList.remove(from);
        wrongList.add(to, item);
        save();
    }

    public String export() {
        StringBuilder sb = new StringBuilder();
        for (String fen : wrongList) {
            sb.append(fen).append("\n");
        }
        return sb.toString();
    }
}