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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TikuData implements Serializable {
    private static final int MAX_FEN_LINE_LENGTH = 4096;
    private String name;           // 题库名称（不含扩展名）
    private String fileName;       // 文件名
    private List<String> rawFenList;       // 原始FEN（含2#前缀，如果有的话）
    private List<String> fenList;  // 所有题目FEN
    private List<String> titleList; // 每道题标题（从FEN尾部提取）
    private List<Boolean> doneList; // 每道题是否已完成
    private List<String> initialFENList; // 每道题的初始FEN
    private List<Boolean> flippedList;     // 记录每道题是否翻转
    private int currentIndex;      // 当前进度
    private boolean fromYincang;   // 是否来自隐藏题库文件夹

    public TikuData(String name, String fileName) {
        this.name = name;
        this.fileName = fileName;
        this.fenList = new ArrayList<>();
        this.rawFenList = new ArrayList<>();
        this.titleList = new ArrayList<>();
        this.doneList = new ArrayList<>();
        this.initialFENList = new ArrayList<>();
        this.flippedList = new ArrayList<>();
        this.currentIndex = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<String> getFenList() {
        return fenList;
    }

    public void setFenList(List<String> fenList) {
        clearFenData();
        if (fenList == null) {
            return;
        }
        for (String fen : fenList) {
            addFenLine(fen);
        }
    }

    void clearFenData() {
        this.fenList = new ArrayList<>();
        this.rawFenList = new ArrayList<>();
        this.titleList = new ArrayList<>();
        this.initialFENList = new ArrayList<>();
        this.doneList = new ArrayList<>();
        this.flippedList = new ArrayList<>();
        this.currentIndex = 0;
    }

    void addFenLine(String fen) {
        if (fen == null || fen.length() > MAX_FEN_LINE_LENGTH) {
            return;
        }
        String rawFen = stripBom(fen);
        ParsedFen parsed = parseFenLine(fen);
        String cleanFen = parsed.fen;
        String title = parsed.title;
        boolean isFlipped = parsed.isFlipped;

        this.fenList.add(cleanFen);
        this.rawFenList.add(rawFen);
        this.titleList.add(title);
        this.initialFENList.add(cleanFen);
        this.doneList.add(false);
        this.flippedList.add(isFlipped);
    }

    private static class ParsedFen {
        final String fen;
        final String title;
        final boolean isFlipped;
        ParsedFen(String fen, String title, boolean isFlipped) {
            this.fen = fen;
            this.title = title;
            this.isFlipped = isFlipped;
        }
    }

    private ParsedFen parseFenLine(String line) {
        if (line == null) {
            return new ParsedFen("", "", false);
        }

        String value = stripBom(line);
        int length = value.length();
        int position = 0;
        int tokenIndex = 0;
        int titleStart = -1;
        boolean isFlipped = false;
        StringBuilder fenBuilder = new StringBuilder(Math.min(length, 96));

        // 扫描需要的字段，避免 String.split() 创建正则对象和临时字符串数组。
        while (position < length) {
            while (position < length && Character.isWhitespace(value.charAt(position))) {
                position++;
            }
            if (position >= length) {
                break;
            }

            int tokenStart = position;
            while (position < length && !Character.isWhitespace(value.charAt(position))) {
                position++;
            }
            int tokenEnd = position;

            if (tokenIndex == 0) {
                isFlipped = value.startsWith("2#", tokenStart);
                int fenStart = isFlipped ? tokenStart + 2 : tokenStart;
                if (fenStart < tokenEnd) {
                    fenBuilder.append(value, fenStart, tokenEnd);
                }
            } else if (tokenIndex < 6) {
                fenBuilder.append(' ').append(value, tokenStart, tokenEnd);
            } else if (titleStart < 0) {
                titleStart = findFirstHan(value, tokenStart, tokenEnd);
            }
            tokenIndex++;
        }

        String title = titleStart >= 0 ? value.substring(titleStart).trim() : "";
        return new ParsedFen(fenBuilder.toString(), title, isFlipped);
    }

    private int findFirstHan(String value, int start, int end) {
        for (int i = start; i < end;) {
            int cp = value.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                return i;
            }
            i += Character.charCount(cp);
        }
        return -1;
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }
    /**
     * 获取某道题的原始FEN（含2#前缀）
     */
    public String getRawFEN(int index) {
        if (index >= 0 && index < rawFenList.size()) {
            return rawFenList.get(index);
        }
        return null;
    }

    /**
     * 获取某道题是否需要翻转
     */
    public boolean getFlipped(int index) {
        if (index >= 0 && index < flippedList.size()) {
            return flippedList.get(index);
        }
        return false;
    }

    /**
     * 获取当前题的原始FEN（含2#前缀）
     */
    public String getCurrentRawFEN() {
        return getRawFEN(currentIndex);
    }

    public String getQuestionTitle(int index) {
        if (titleList != null && index >= 0 && index < titleList.size()) {
            return titleList.get(index);
        }
        return "";
    }
    public List<Boolean> getDoneList() {
        return doneList;
    }

    /**
     * 重置所有进度
     */
    public void resetAllProgress() {
        if (doneList == null) {
            return;
        }
        for (int i = 0; i < doneList.size(); i++) {
            doneList.set(i, false);
        }
        currentIndex = 0;
    }

    public void setDoneList(List<Boolean> doneList) {
        this.doneList = doneList;
    }

    public List<String> getInitialFENList() {
        return initialFENList;
    }

    public void setInitialFENList(List<String> initialFENList) {
        this.initialFENList = new ArrayList<>();
        if (initialFENList == null) {
            return;
        }
        for (String fenLine : initialFENList) {
            this.initialFENList.add(parseFenLine(fenLine).fen);
        }
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public int getTotalCount() {
        return fenList.size();
    }

    public int getDoneCount() {
        int count = 0;
        for (Boolean done : doneList) {
            if (done) count++;
        }
        return count;
    }

    public double getProgress() {
        if (fenList.isEmpty()) return 0;
        return (double) getDoneCount() / fenList.size();
    }

    public String getFENByIndex(int index) {
        if (index >= 0 && index < fenList.size()) {
            return fenList.get(index);
        }
        return null;
    }

    /**
     * 获取当前FEN（不递增索引）
     */
    public String getCurrentFEN() {
        if (currentIndex >= 0 && currentIndex < fenList.size()) {
            return fenList.get(currentIndex);
        }
        return null;
    }

    /**
     * 获取下一题并递增索引
     * @return 下一题的FEN
     */
    public String getNextFEN() {
        if (currentIndex < fenList.size()) {
            String fen = fenList.get(currentIndex);
            currentIndex++;
            return fen;
        }
        return null;
    }


    public void goToIndex(int index) {
        if (index >= 0 && index < fenList.size()) {
            currentIndex = index;
        }
    }

    public void resetToFirst() {
        currentIndex = 0;
    }

    /**
     * 标记题目为已完成
     */
    public void markDone(int index) {
        if (index >= 0 && index < doneList.size()) {
            doneList.set(index, true);
        }
    }

    /**
     * 判断题目是否已完成
     */
    public boolean isDone(int index) {
        if (index >= 0 && index < doneList.size()) {
            return doneList.get(index);
        }
        return false;
    }

    public boolean isFromYincang() { return fromYincang; }
    public void setFromYincang(boolean v) { fromYincang = v; }

    /**
     * 获取某道题的初始FEN
     */
    public String getInitialFEN(int index) {
        if (index >= 0 && index < initialFENList.size()) {
            return initialFENList.get(index);
        }
        // 如果没有保存初始FEN，返回当前FEN
        return getFENByIndex(index);
    }
}
