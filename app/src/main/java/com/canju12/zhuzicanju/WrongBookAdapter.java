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
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class WrongBookAdapter extends RecyclerView.Adapter<WrongBookAdapter.ViewHolder> {

    private Context context;
    private List<String> data;
    private ChessView chessView;
    private int thumbnailWidth;
    private int thumbnailHeight;
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

        // ===== 修改缩略图尺寸计算：占屏幕宽度的44% =====
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        thumbnailWidth = (int) (screenWidth * 0.45);  // 占屏幕宽度的44%
        thumbnailHeight = (int) (thumbnailWidth / 0.9); // 10:9 比例
        // =============================================
        // 调试日志（可选）
        android.util.Log.d("WrongBookAdapter", "屏幕宽度: " + screenWidth);
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
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String fen = data.get(position);
        boolean flipped = fen.startsWith("2#");
        String cleanFen = flipped ? fen.substring(2) : fen;

        // ===== 强制设置宽高 =====
        ViewGroup.LayoutParams params = holder.ivThumbnail.getLayoutParams();
        params.width = thumbnailWidth;
        params.height = thumbnailHeight;
        holder.ivThumbnail.setLayoutParams(params);
        // ========================

        // 生成缩略图
        Bitmap thumbnail = chessView.captureChessView(thumbnailWidth, thumbnailHeight, cleanFen, flipped);
        if (thumbnail != null) {
            holder.ivThumbnail.setImageBitmap(thumbnail);
        }
        holder.tvIndex.setText("序号 " + (position + 1));

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(position);
            }
        });

        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvIndex;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
            tvIndex = itemView.findViewById(R.id.tvIndex);
        }
    }
}