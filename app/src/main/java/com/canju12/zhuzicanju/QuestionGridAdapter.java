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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class QuestionGridAdapter extends BaseAdapter {

    private Context context;
    private TikuData tiku;
    private int currentIndex;
    private LayoutInflater inflater;
    private ProgressManager progressManager;

    public QuestionGridAdapter(Context context, TikuData tiku, int currentIndex) {
        this.context = context;
        this.tiku = tiku;
        this.currentIndex = currentIndex;
        this.inflater = LayoutInflater.from(context);
// ===== 每次创建适配器时重新加载进度 =====
        this.progressManager = ProgressManager.getInstance(context);
        this.progressManager.reload();
        // ======================================

    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return tiku == null ? 0 : tiku.getTotalCount();
    }

    @Override
    public Object getItem(int position) {
        return tiku == null ? null : tiku.getFENByIndex(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_question_selector, parent, false);
            holder = new ViewHolder();
            holder.tvQuestionNumber = convertView.findViewById(R.id.tvQuestionNumber);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.tvQuestionNumber.setText(String.valueOf(position + 1));

        // ===== 使用 ProgressManager 判断是否已完成 =====
        boolean isDone = progressManager.isCompleted(tiku.getName(), position);
        boolean isCurrent = (position == currentIndex);

        if (isDone) {
            // 已完成 - 绿色
            holder.tvQuestionNumber.setSelected(true);
            holder.tvQuestionNumber.setActivated(false);
            holder.tvQuestionNumber.setTextColor(context.getResources().getColor(android.R.color.white));
        } else if (isCurrent) {
            // 当前题目 - 蓝色高亮
            holder.tvQuestionNumber.setSelected(false);
            holder.tvQuestionNumber.setActivated(true);
            holder.tvQuestionNumber.setTextColor(context.getResources().getColor(android.R.color.white));
        } else {
            // 未完成 - 灰色
            holder.tvQuestionNumber.setSelected(false);
            holder.tvQuestionNumber.setActivated(false);
            holder.tvQuestionNumber.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
        }

        return convertView;
    }

    static class ViewHolder {
        TextView tvQuestionNumber;
    }
}