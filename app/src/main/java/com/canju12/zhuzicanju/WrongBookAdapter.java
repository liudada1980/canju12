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
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WrongBookAdapter extends RecyclerView.Adapter<WrongBookAdapter.ViewHolder> {

    private static final int BOARD_STYLE_VERSION = 1;
    private static final LruCache<String, Bitmap> THUMBNAIL_CACHE =
            new LruCache<String, Bitmap>(8 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap == null ? 0 : bitmap.getByteCount() / 1024;
                }
            };

    private Context context;
    private List<String> data;
    private ChessView chessView;
    private final ChessView thumbnailChessView;
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
    private int thumbnailWidth;
    private int thumbnailHeight;
    private final boolean landscapeMode;
    private OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }

    public WrongBookAdapter(Context context, List<String> data, ChessView chessView) {
        this.context = context;
        this.data = data;
        this.chessView = chessView;
        this.thumbnailChessView = new ChessView(context.getApplicationContext());

        // ===== 修改缩略图尺寸计算：横屏按高度，竖屏按宽度 =====
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        landscapeMode = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        float boardAspect = chessView != null ? chessView.getBoardAspect() : 0.9f;
        if (landscapeMode) {
            thumbnailHeight = Math.max(1, (int) (screenHeight * 0.47f));
            thumbnailWidth = Math.max(1, Math.round(thumbnailHeight * boardAspect));
        } else {
            thumbnailWidth = Math.max(1, Math.round(screenWidth * 0.47f));
            thumbnailHeight = Math.max(1, Math.round(thumbnailWidth / boardAspect));
        }
        // =============================================
        // 调试日志（可选）
        android.util.Log.d("WrongBookAdapter", "屏幕宽度: " + screenWidth);
        android.util.Log.d("WrongBookAdapter", "屏幕高度: " + screenHeight);
        android.util.Log.d("WrongBookAdapter", "缩略图宽度: " + thumbnailWidth);
        android.util.Log.d("WrongBookAdapter", "缩略图高度: " + thumbnailHeight);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void updateData(List<String> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_wrong_book, parent, false);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(params);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (data == null || position < 0 || position >= data.size()) {
            clearThumbnail(holder);
            setIndexText(holder, "");
            return;
        }

        String fen = data.get(position);
        if (fen == null || fen.trim().isEmpty()) {
            clearThumbnail(holder);
            setIndexText(holder, "");
            return;
        }
        boolean flipped = fen.startsWith("2#");
        String cleanFen = flipped ? fen.substring(2) : fen;

        // ===== 强制设置宽高 =====
        ViewGroup.LayoutParams params = holder.ivThumbnail.getLayoutParams();
        params.width = thumbnailWidth;
        params.height = thumbnailHeight;
        holder.ivThumbnail.setLayoutParams(params);
        if (landscapeMode && holder.tvIndex != null) {
            holder.tvIndex.setVisibility(View.GONE);
        } else if (holder.tvIndex != null) {
            holder.tvIndex.setVisibility(View.VISIBLE);
        }
        // ========================

        String cacheKey = buildCacheKey(cleanFen, flipped);
        clearThumbnail(holder);
        holder.itemView.setTag(cacheKey);
        Bitmap thumbnail = THUMBNAIL_CACHE.get(cacheKey);
        if (thumbnail != null) {
            holder.thumbnailBitmap = thumbnail;
            holder.ivThumbnail.setImageBitmap(thumbnail);
        } else {
            holder.ivThumbnail.setImageDrawable(null);
            if (chessView != null) {
                thumbnailExecutor.execute(() -> {
                    Bitmap generated = thumbnailChessView.captureChessView(
                            thumbnailWidth, thumbnailHeight, cleanFen, flipped);
                    if (generated != null) {
                        THUMBNAIL_CACHE.put(cacheKey, generated);
                    }
                    holder.itemView.post(() -> {
                        if (cacheKey.equals(holder.itemView.getTag())) {
                            Bitmap result = THUMBNAIL_CACHE.get(cacheKey);
                            if (result != null) {
                                holder.thumbnailBitmap = result;
                                holder.ivThumbnail.setImageBitmap(result);
                            }
                        }
                    });
                });
            }
        }
        setIndexText(holder, landscapeMode ? "" : "序号 " + (position + 1));

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (listener != null && adapterPosition != RecyclerView.NO_POSITION) {
                listener.onItemClick(adapterPosition);
            }
        });

        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (longClickListener != null && adapterPosition != RecyclerView.NO_POSITION) {
                longClickListener.onItemLongClick(adapterPosition);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    private void clearThumbnail(ViewHolder holder) {
        holder.ivThumbnail.setImageDrawable(null);
        holder.thumbnailBitmap = null;
    }

    private String buildCacheKey(String fen, boolean flipped) {
        return fen + "|" + flipped + "|" + thumbnailWidth + "x" + thumbnailHeight
                + "|style=" + BOARD_STYLE_VERSION;
    }

    private void setIndexText(ViewHolder holder, String text) {
        if (holder.tvIndex != null) {
            holder.tvIndex.setText(text);
        }
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.itemView.setTag(null);
        clearThumbnail(holder);
        super.onViewRecycled(holder);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        thumbnailExecutor.shutdownNow();
        super.onDetachedFromRecyclerView(recyclerView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvIndex;
        Bitmap thumbnailBitmap;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
            tvIndex = itemView.findViewById(R.id.tvIndex);
        }
    }
}
