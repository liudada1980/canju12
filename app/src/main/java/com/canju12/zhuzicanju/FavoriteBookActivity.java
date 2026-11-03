package com.canju12.zhuzicanju;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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

public class FavoriteBookActivity extends AppCompatActivity {
    private static final String BOOK_NAME = "收藏夹";
    private static final String FILE_NAME = "收藏夹.txt";

    private RecyclerView recyclerView;
    private WrongBookAdapter adapter;
    private WrongBookManager manager;
    private ChessView chessView;
    private LinearLayout mainLayout;
    private Button btnBack, btnPaste, btnExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_book);

        chessView = new ChessView(this);
        manager = new WrongBookManager(BOOK_NAME, FILE_NAME);

        initViews();
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
        int outerPadding = isLandscape ? 0 : Math.max(1, Math.round(screenWidth * 0.02f));
        recyclerView.setPadding(outerPadding,
                recyclerView.getPaddingTop(),
                outerPadding,
                recyclerView.getPaddingBottom());

        int verticalSpacing = isLandscape
                ? Math.max(1, (int) (getResources().getDisplayMetrics().heightPixels * 0.02f))
                : Math.max(1, Math.round(screenWidth * 0.02f));
        int horizontalSpacing = isLandscape ? Math.max(1, verticalSpacing / 2) : verticalSpacing / 2;
        recyclerView.addItemDecoration(new SpacesItemDecoration(verticalSpacing, horizontalSpacing, isLandscape));
    }

    public static class SpacesItemDecoration extends RecyclerView.ItemDecoration {
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

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnPaste.setOnClickListener(v -> pasteFromClipboard());
        btnExport.setOnClickListener(v -> exportFavoriteBook());
    }

    private void setupRoomBackground() {
        Bitmap roomBitmap = UiAssetCache.getRoom(this);
        try {
            if (roomBitmap != null) {
                mainLayout.setBackground(new BitmapDrawable(getResources(), roomBitmap));
            }
        } catch (Throwable e) {
            Log.w("FavoriteBookActivity", "external room load failed", e);
        }
        if (roomBitmap == null) {
            try {
                mainLayout.setBackgroundResource(R.drawable.room);
            } catch (Throwable e) {
                mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#E8D5A2"));
            }
        }
        UiThemeHelper.applyRoomButtonTheme(this, mainLayout, roomBitmap);
    }

    private void loadData() {
        manager.load();
        List<String> data = manager.getList();
        if (adapter == null) {
            adapter = new WrongBookAdapter(this, data, chessView);
            adapter.setOnItemClickListener(this::goToMainActivity);
            adapter.setOnItemLongClickListener(this::showDeleteDialog);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(data);
        }
    }

    private void showDeleteDialog(int position) {
        if (position < 0 || position >= manager.getCount()) return;
        new AlertDialog.Builder(this)
                .setTitle("删除收藏夹")
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
            ToastUtil.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence pastedText = clip.getItemAt(0).getText();
        String text = pastedText == null ? "" : pastedText.toString();
        if (TextUtils.isEmpty(text)) {
            ToastUtil.makeText(this, "内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = 0;
        for (String line : text.split("\\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                manager.add(line);
                count++;
            }
        }
        ToastUtil.makeText(this, "添加了 " + count + " 条收藏夹", Toast.LENGTH_SHORT).show();
        loadData();
    }

    private void exportFavoriteBook() {
        String content = manager.export();
        if (TextUtils.isEmpty(content)) {
            ToastUtil.makeText(this, "收藏夹为空", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            ToastUtil.makeText(this, "剪贴板不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(BOOK_NAME, content));
        ToastUtil.makeText(this, "已复制 " + manager.getCount() + " 条收藏夹", Toast.LENGTH_SHORT).show();
    }

    private void goToMainActivity(int position) {
        List<String> favoriteList = manager.getList();
        if (position < 0 || position >= favoriteList.size()) {
            ToastUtil.makeText(this, "收藏夹题目不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("load_favorite", true);
        intent.putExtra("favorite_index", position + 1);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
