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

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class WrongBookActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private WrongBookAdapter adapter;
    private WrongBookManager manager;
    private ChessView chessView;
    private LinearLayout mainLayout;
    private Button btnBack, btnPaste, btnExport;
    private int thumbnailWidth;
    private int thumbnailHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_book);

        chessView = new ChessView(this);
        manager = new WrongBookManager();

        initViews();
        calculateThumbnailSize();
        setupListeners();
        setupRoomBackground();
        loadData();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        mainLayout = findViewById(R.id.mainLayout);
        btnBack = findViewById(R.id.btnBack);
        btnPaste = findViewById(R.id.btnPaste);
        btnExport = findViewById(R.id.btnExport);

        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        GridLayoutManager layoutManager = isLandscape
                ? new GridLayoutManager(this, 2, RecyclerView.HORIZONTAL, false)
                : new GridLayoutManager(this, 2);
        recyclerView.setLayoutManager(layoutManager);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int outerPadding = isLandscape ? 0 : Math.max(1, Math.round(screenWidth * 0.02f));
        // 竖屏布局：左右各 2%，中间列间距合计 2%。
        recyclerView.setPadding(outerPadding,
                recyclerView.getPaddingTop(),
                outerPadding,
                recyclerView.getPaddingBottom());

        int rowSpace = isLandscape
                ? Math.max(1, Math.round(screenHeight * 0.02f))
                : Math.max(1, Math.round(screenWidth * 0.02f));
        int columnSpace = isLandscape
                ? Math.max(1, rowSpace / 2)
                : Math.max(1, Math.round(screenWidth * 0.01f));
        recyclerView.addItemDecoration(new SpacesItemDecoration(rowSpace, columnSpace, isLandscape));
    }

    public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
        private final int verticalSpace;
        private final int horizontalSpace;
        private final boolean horizontal;

        public SpacesItemDecoration(int verticalSpace, int horizontalSpace, boolean horizontal) {
            this.verticalSpace = verticalSpace;
            this.horizontalSpace = horizontalSpace;
            this.horizontal = horizontal;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (!horizontal) {
                if (position % 2 == 0) {
                    outRect.right = Math.max(1, verticalSpace / 2);
                } else {
                    outRect.left = Math.max(1, verticalSpace / 2);
                }
                outRect.bottom = verticalSpace;
            } else {
                if (position % 2 == 0) {
                    outRect.top = verticalSpace;
                    outRect.bottom = verticalSpace / 2;
                } else {
                    outRect.top = verticalSpace / 2;
                    outRect.bottom = verticalSpace;
                }
                outRect.right = horizontalSpace;
            }
        }
    }

    private void calculateThumbnailSize() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        float boardAspect = chessView != null ? chessView.getBoardAspect() : 0.9f;
        if (isLandscape) {
            thumbnailHeight = Math.max(1, (int) (screenHeight * 0.47f));
            thumbnailWidth = Math.max(1, Math.round(thumbnailHeight * boardAspect));
        } else {
            thumbnailWidth = Math.max(1, Math.round(screenWidth * 0.47f));
            thumbnailHeight = Math.max(1, Math.round(thumbnailWidth / boardAspect));
        }

        Log.d("WrongBook", "屏幕宽度: " + screenWidth + ", 屏幕高度: " + screenHeight);
        Log.d("WrongBook", "缩略图宽度: " + thumbnailWidth);
        Log.d("WrongBook", "缩略图高度: " + thumbnailHeight);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnPaste.setOnClickListener(v -> pasteFromClipboard());
        btnExport.setOnClickListener(v -> exportWrongBook());
    }

    private void setupRoomBackground() {
        Bitmap roomBitmap = UiAssetCache.getRoom(this);
        try {
            if (roomBitmap != null) {
                mainLayout.setBackground(new BitmapDrawable(getResources(), roomBitmap));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (roomBitmap == null) {
            try {
                mainLayout.setBackgroundResource(R.drawable.room);
            } catch (Throwable e) {
                // 内置 room.png 在某些硬件上也可能解码失败，最终用纯色兜底，绝不退出
                e.printStackTrace();
                mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#E8D5A2"));
            }
        }
        UiThemeHelper.applyRoomButtonTheme(this, mainLayout, roomBitmap);
    }

    /**
     * 加载错题本数据
     * 交互方式：
     * - 单击缩略图：加载错题本对应序号的题目，回到主界面
     * - 长按缩略图：弹出删除确认对话框
     */
    private void loadData() {
        manager.load();
        List<String> data = manager.getList();

        if (adapter == null) {
            adapter = new WrongBookAdapter(this, data, chessView);

            // ===== 单击：传递序号，加载错题本对应题目 =====
            adapter.setOnItemClickListener(position -> {
                int questionNumber = position + 1;
                goToMainActivity(questionNumber);
            });

            // ===== 长按删除 =====
            adapter.setOnItemLongClickListener(position -> {
                showDeleteDialog(position);
            });

            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(data);
        }
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteDialog(int position) {
        if (position < 0 || position >= manager.getCount()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除错题")
                .setMessage("确定删除第 " + (position + 1) + " 题吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    manager.remove(position);
                    loadData();
                    ToastUtil.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            ToastUtil.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0) == null) {
            ToastUtil.makeText(this, "clipboard is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence pastedText = clip.getItemAt(0).getText();
        String text = pastedText == null ? "" : pastedText.toString();
        if (TextUtils.isEmpty(text)) {
            ToastUtil.makeText(this, "内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] lines = text.split("\n");
        int count = 0;
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                manager.add(line);
                count++;
            }
        }
        ToastUtil.makeText(this, "添加了 " + count + " 条错题", Toast.LENGTH_SHORT).show();
        loadData();
    }

    private void exportWrongBook() {
        if (getSystemService(Context.CLIPBOARD_SERVICE) == null) {
            ToastUtil.makeText(this, "clipboard unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        String content = manager.export();
        if (TextUtils.isEmpty(content)) {
            ToastUtil.makeText(this, "错题本为空", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("错题本", content));
        ToastUtil.makeText(this, "已复制 " + manager.getCount() + " 条错题", Toast.LENGTH_SHORT).show();
    }

    /**
     * 跳转到主界面，加载错题本对应序号的题目
     * @param questionNumber 错题序号（从1开始）
     */
    private void goToMainActivity(int questionNumber) {
        Log.d("WrongBookActivity", "跳转到主界面，序号: " + questionNumber);
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("load_wrong", true);
        intent.putExtra("wrong_index", questionNumber);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
