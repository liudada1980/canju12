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

        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerView.setLayoutManager(layoutManager);

        int spacing = (int) (getResources().getDisplayMetrics().widthPixels * 0.01);
        recyclerView.addItemDecoration(new SpacesItemDecoration(spacing));
    }

    public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
        private int space;

        public SpacesItemDecoration(int space) {
            this.space = space;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position % 2 == 0) {
                outRect.right = space / 1;
            } else {
                outRect.left = space / 1;
            }
            outRect.bottom = space;
        }
    }

    private void calculateThumbnailSize() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        thumbnailWidth = (int) (screenWidth * 0.44);
        thumbnailHeight = (int) (thumbnailWidth * 0.9);

        Log.d("WrongBook", "屏幕宽度: " + screenWidth);
        Log.d("WrongBook", "缩略图宽度: " + thumbnailWidth);
        Log.d("WrongBook", "缩略图高度: " + thumbnailHeight);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnPaste.setOnClickListener(v -> pasteFromClipboard());
        btnExport.setOnClickListener(v -> exportWrongBook());
    }

    private void setupRoomBackground() {
        try {
            File externalDir = Environment.getExternalStorageDirectory();
            File canju12Dir = new File(externalDir, "canju12");
            File uiStyleDir = new File(canju12Dir, "uistyle");
            File roomFile = new File(uiStyleDir, "room.png");

            if (roomFile.exists()) {
                Bitmap roomBitmap = android.graphics.BitmapFactory.decodeFile(roomFile.getAbsolutePath());
                if (roomBitmap != null) {
                    BitmapDrawable drawable = new BitmapDrawable(getResources(), roomBitmap);
                    mainLayout.setBackground(drawable);
                    return;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            mainLayout.setBackgroundResource(R.drawable.room);
        } catch (Throwable e) {
            // 内置 room.png 在某些硬件上也可能解码失败，最终用纯色兜底，绝不退出
            e.printStackTrace();
            mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#E8D5A2"));
        }
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
        new AlertDialog.Builder(this)
                .setTitle("删除错题")
                .setMessage("确定删除第 " + (position + 1) + " 题吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    manager.remove(position);
                    loadData();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "内容为空", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "添加了 " + count + " 条错题", Toast.LENGTH_SHORT).show();
        loadData();
    }

    private void exportWrongBook() {
        String content = manager.export();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "错题本为空", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("错题本", content));
        Toast.makeText(this, "已复制 " + manager.getCount() + " 条错题", Toast.LENGTH_SHORT).show();
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