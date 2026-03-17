package com.android.mytv.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class LiveAdapter extends ArrayAdapter<String> {
    private int playingPosition = -1;       // 正在播放的项
    private int persistentSelected = -1;    // 遥控器光标所在的项（即使焦点离开也要变色）

    public LiveAdapter(Context context, java.util.List<String> objects) {
        super(context, android.R.layout.simple_list_item_1, objects);
    }

    public void setPlayingPosition(int position) {
        this.playingPosition = position;
        notifyDataSetChanged();
    }

    public void setPersistentSelected(int position) {
        this.persistentSelected = position;
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view = (TextView) super.getView(position, convertView, parent);
        view.setTextColor(Color.WHITE);

        // 1. 如果是光标所在的分组，或者正在播放的频道
        if (position == persistentSelected || position == playingPosition) {
            view.setTextColor(Color.YELLOW);
            view.setBackgroundColor(Color.parseColor("#88444444")); // 选中背景
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }

        // TV 适配
        float density = getContext().getResources().getDisplayMetrics().density;
        view.setTextSize(density >=2.5 ? 35 : 35);
        return view;
    }
}