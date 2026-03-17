package com.android.mytv;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.mytv.adapter.LiveAdapter;
import com.android.mytv.model.ChannelGroup;
import com.android.mytv.model.RadioChannel;
import com.android.mytv.widget.IjkVideoView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private IjkVideoView mVideoView;
    private View mMenuContainer;
    private ListView mListGroup, mListChannel;
    private TextView mTxtIndex;

    private List<ChannelGroup> mData;
    private RadioChannel mCurrentChannel;

    // 当前播放的索引
    private int mGroupIdx, mChannelIdx;
    private int mLastFocusedId = R.id.list_channel;
    // 菜单中操作的临时状态记忆
    private int mSelectedGroupIdxForMenu = 0;
    private int mLastGroupPos = 0;    // 15秒内记忆的分组行
    private int mLastChannelPos = 0;  // 15秒内记忆的频道行

    private long lastMenuHideTime = 0;
    private Handler mHandler = new Handler();
    private GestureDetector mGestureDetector;
    private LiveAdapter mGroupAdapter, mChannelAdapter;
    private Runnable mPlayTimeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 全屏+常亮设置
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mData = (List<ChannelGroup>) getIntent().getSerializableExtra("data");
        mVideoView = findViewById(R.id.video_view);
        mMenuContainer = findViewById(R.id.menu_container);
        mListGroup = findViewById(R.id.list_group);
        mListChannel = findViewById(R.id.list_channel);
        mTxtIndex = findViewById(R.id.txt_index);

        initHistory();
        initMenu();
        initGestures();

        // 绑定全屏触控监听
        getWindow().getDecorView().setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));

    }

    private void initHistory() {
        SharedPreferences sp = getSharedPreferences("TV_SETTING", MODE_PRIVATE);
        mGroupIdx = sp.getInt("g", 0);
        mChannelIdx = sp.getInt("c", 0);
        playCurrent(false); // 初始播放不强制重置线路
    }

    private void initMenu() {
        // 1. 初始化分组列表
        List<String> gNames = new ArrayList<>();
        for (ChannelGroup g : mData) gNames.add(g.groupName);
        mGroupAdapter = new LiveAdapter(this, gNames);
        mListGroup.setAdapter(mGroupAdapter);

        // 分组列表焦点监听：控制整体颜色状态
        mListGroup.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 当焦点回到分组，如果当前显示的不是正在播放的分组，频道名颜色应恢复默认
                mLastFocusedId = R.id.list_group;
            }
        });

        // TV遥控器：上下移动分组时自动联动右侧频道列表
        mListGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelectedGroupIdxForMenu = position;
                // 只要焦点在分组上移动，就更新右侧预览
                updateChannelList(position);
                // 设置当前“预览选中”的分组背景变色
                mGroupAdapter.setPersistentSelected(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 手机端：点击分组
        mListGroup.setOnItemClickListener((p, v, pos, id) -> {
            mSelectedGroupIdxForMenu = pos;
            updateChannelList(pos);
            mGroupAdapter.setPersistentSelected(pos);
        });

        // 频道列表焦点监听
        mListChannel.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mLastFocusedId = R.id.list_channel;
                // 当光标移到右侧频道时，左侧对应的分组背景色需通过 adapter 保持高亮
                mGroupAdapter.setPersistentSelected(mSelectedGroupIdxForMenu);
            }
        });

        // 频道列表项选中（仅记录位置，用于返回逻辑）
        mListChannel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                mLastChannelPos = pos;
                // 频道名获取焦点时的颜色变化由 ListView 的 Selector 或 Adapter 处理
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // 【关键修复】TV端左右键逻辑：左移回分组，右移进频道
        mListChannel.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    mListGroup.requestFocus(); // 向左移回分组，频道颜色由系统自动恢复
                    return true;
                }
            }
            return false;
        });

        // 频道点击/OK键播放
        mListChannel.setOnItemClickListener((p, v, pos, id) -> {
            mGroupIdx = mSelectedGroupIdxForMenu;
            mChannelIdx = pos;
            playCurrent(true); // 播放后会执行 toggleMenu() 隐藏菜单
        });
    }

    private void updateChannelList(int gIdx) {
        List<String> cNames = new ArrayList<>();
        for (RadioChannel c : mData.get(gIdx).channels) cNames.add(c.name);
        mChannelAdapter = new LiveAdapter(this, cNames);
        mListChannel.setAdapter(mChannelAdapter);
        mListChannel.setVisibility(View.VISIBLE);

        // 如果该分组是正在播放的分组，频道名显示黄色高亮
        if (gIdx == mGroupIdx) {
            mChannelAdapter.setPlayingPosition(mChannelIdx);
        }
    }

    private void toggleMenu() {
        if (mMenuContainer.getVisibility() == View.VISIBLE) {
            mMenuContainer.setVisibility(View.GONE);
        } else {
            mMenuContainer.setVisibility(View.VISIBLE);

            // --- 始终恢复到当前正在播放的项目 ---
            mSelectedGroupIdxForMenu = mGroupIdx;

            // 1. 同步左侧分组状态并高亮
            mGroupAdapter.setPersistentSelected(mGroupIdx);
            mListGroup.setSelection(mGroupIdx);

            // 2. 刷新右侧频道列表
            updateChannelList(mGroupIdx);

            // 3. 延时定位焦点到正在播放的频道名上
            mHandler.postDelayed(() -> {
                mListChannel.setSelection(mChannelIdx);
                mListChannel.requestFocus(); // 强制焦点落在当前频道名
                // 更新当前标识位置，确保下次操作逻辑连续
                mLastChannelPos = mChannelIdx;
                mLastFocusedId = R.id.list_channel;
            }, 150);
        }
    }

    private void playCurrent(boolean resetUrl) {
        if (mData == null || mData.isEmpty()) return;
        mCurrentChannel = mData.get(mGroupIdx).channels.get(mChannelIdx);
        if (resetUrl) mCurrentChannel.currentUrlIndex = 0;

        // 取消之前的超时任务（避免重复触发）
        if (mPlayTimeoutRunnable != null) {
            mHandler.removeCallbacks(mPlayTimeoutRunnable);
        }

        // 1. 修复：使用 IMediaPlayer 类型
        mVideoView.setOnPreparedListener(mp -> {
            if (mPlayTimeoutRunnable != null) {
                mHandler.removeCallbacks(mPlayTimeoutRunnable);
                mPlayTimeoutRunnable = null;
            }
            mp.start(); // 确保播放开始
        });

        // 10秒超时检查
        mPlayTimeoutRunnable = () -> {
            // 切换到下一个直播源（循环切换）
            mCurrentChannel.currentUrlIndex = (mCurrentChannel.currentUrlIndex + 1) % mCurrentChannel.urls.size();
            playCurrent(false); // 递归尝试新源
        };
        mHandler.postDelayed(mPlayTimeoutRunnable, 15000); // 10秒超时

        String url = mCurrentChannel.urls.get(mCurrentChannel.currentUrlIndex);
        mVideoView.setVideoPath(url); // 内部已包含优化后的缓冲设置

        updateIndexUI();
        mMenuContainer.setVisibility(View.GONE);
        lastMenuHideTime = System.currentTimeMillis();
        saveHistory();
    }

    private void switchSource(boolean next) {
        if (mCurrentChannel == null || mCurrentChannel.urls.size() <= 1) return;

        int size = mCurrentChannel.urls.size();
        if (next) {
            // next=true → 按右键 → 切换到下一个（后一个）
            mCurrentChannel.currentUrlIndex = (mCurrentChannel.currentUrlIndex + 1) % size;
        } else {
            // next=false → 按左键 → 切换到前一个（上一个）
            mCurrentChannel.currentUrlIndex = (mCurrentChannel.currentUrlIndex - 1 + size) % size;
        }

        // 安全播放
        if (mCurrentChannel.urls.size() > 0) {
            mVideoView.setVideoPath(mCurrentChannel.urls.get(mCurrentChannel.currentUrlIndex));
        }
        updateIndexUI();
    }

    private void updateIndexUI() {
        mTxtIndex.setText((mCurrentChannel.currentUrlIndex + 1) + "/" + mCurrentChannel.urls.size());
        mTxtIndex.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(hideIndexRunnable);
        mHandler.postDelayed(hideIndexRunnable, 3000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 1. OK/确定键 切换菜单
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            toggleMenu();
            return true;
        }
        // 2. 菜单关闭时，左右键切换线路
        if (mMenuContainer.getVisibility() != View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                switchSource(false);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                switchSource(true);
                return true;
            }
        }
        // 3. 返回键关闭菜单
        if (keyCode == KeyEvent.KEYCODE_BACK && mMenuContainer.getVisibility() == View.VISIBLE) {
            mMenuContainer.setVisibility(View.GONE);
            lastMenuHideTime = System.currentTimeMillis();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initGestures() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (e.getX() < getWindowManager().getDefaultDisplay().getWidth() / 3) toggleMenu();
                return true;
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (Math.abs(vX) > Math.abs(vY)) {
                    if (vX > 800) switchSource(false);
                    else if (vX < -800) switchSource(true);
                }
                return true;
            }
        });
    }

    private void saveHistory() {
        getSharedPreferences("TV_SETTING", MODE_PRIVATE).edit()
                .putInt("g", mGroupIdx).putInt("c", mChannelIdx).apply();
    }

    private Runnable hideIndexRunnable = () -> mTxtIndex.setVisibility(View.GONE);

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release(); // 释放 VLC 资源
        }
        mHandler.removeCallbacksAndMessages(null);
    }
}