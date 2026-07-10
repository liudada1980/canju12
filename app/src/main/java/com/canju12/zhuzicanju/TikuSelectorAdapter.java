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
import java.util.List;

public class TikuSelectorAdapter extends BaseAdapter {

    private Context context;
    private List<TikuData> tikuList;
    private LayoutInflater inflater;

    public TikuSelectorAdapter(Context context, List<TikuData> tikuList) {
        this.context = context;
        this.tikuList = tikuList;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return tikuList == null ? 0 : tikuList.size();
    }

    @Override
    public Object getItem(int position) {
        return tikuList == null ? null : tikuList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_tiku_selector, parent, false);
            holder = new ViewHolder();
            holder.tvTikuName = convertView.findViewById(R.id.tvTikuName);
            holder.tvTikuInfo = convertView.findViewById(R.id.tvTikuInfo);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        TikuData tiku = tikuList.get(position);
        holder.tvTikuName.setText(tiku.getName());
        holder.tvTikuInfo.setText("共 " + tiku.getTotalCount() + " 题");

        return convertView;
    }

    static class ViewHolder {
        TextView tvTikuName;
        TextView tvTikuInfo;
    }
}