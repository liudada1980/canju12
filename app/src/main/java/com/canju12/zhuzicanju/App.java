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

import android.app.Application;
import android.util.Log;

/**
 * 全局 Application：安装未捕获异常处理器作为最后一道防线。
 *
 * 背景：某些硬件上内置 drawable（如 board.png）可能解码失败甚至抛 OOM，
 * 即便各加载/绘制路径已做 null 保护与 try/catch，仍可能漏出未预期异常。
 * 这里在进程级兜底：记录日志后吞掉，避免程序直接退出。
 * （棋盘读不出时界面会留白或用纯色背景，但程序继续可用。）
 */
public class App extends Application {

    private static final String TAG = "App";

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                // 记录但不交给默认处理（默认会杀进程并弹“应用停止运行”）
                Log.e(TAG, "未捕获异常(已拦截，程序不退出): " + Log.getStackTraceString(e));
                // 不调用 defaultHandler，避免进程被杀；
                // 也不主动 System.exit，保证程序继续运行。
            }
        });
    }
}
