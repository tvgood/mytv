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


    private int mGroupIdx, mChannelIdx;
    private int mLastFocusedId = R.id.list_channel;
  
    private int mSelectedGroupIdxForMenu = 0;
    private int mLastGroupPos = 0;   
    private int mLastChannelPos = 0;  

    private long lastMenuHideTime = 0;
    private Handler mHandler = new Handler();
    private GestureDetector mGestureDetector;
    private LiveAdapter mGroupAdapter, mChannelAdapter;
    private Runnable mPlayTimeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
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

       
        getWindow().getDecorView().setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));

    }

    private void initHistory() {
        SharedPreferences sp = getSharedPreferences("TV_SETTING", MODE_PRIVATE);
        mGroupIdx = sp.getInt("g", 0);
        mChannelIdx = sp.getInt("c", 0);
        playCurrent(false);
    }

    private void initMenu() {
       
        List<String> gNames = new ArrayList<>();
        for (ChannelGroup g : mData) gNames.add(g.groupName);
        mGroupAdapter = new LiveAdapter(this, gNames);
        mListGroup.setAdapter(mGroupAdapter);

        
        mListGroup.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                
                mLastFocusedId = R.id.list_group;
            }
        });

        
        mListGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelectedGroupIdxForMenu = position;
               
                updateChannelList(position);
               
                mGroupAdapter.setPersistentSelected(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        
        mListGroup.setOnItemClickListener((p, v, pos, id) -> {
            mSelectedGroupIdxForMenu = pos;
            updateChannelList(pos);
            mGroupAdapter.setPersistentSelected(pos);
        });

        
        mListChannel.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mLastFocusedId = R.id.list_channel;
                
                mGroupAdapter.setPersistentSelected(mSelectedGroupIdxForMenu);
            }
        });

        
        mListChannel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                mLastChannelPos = pos;
               
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        
        mListChannel.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    mListGroup.requestFocus(); 
                    return true;
                }
            }
            return false;
        });

       
        mListChannel.setOnItemClickListener((p, v, pos, id) -> {
            mGroupIdx = mSelectedGroupIdxForMenu;
            mChannelIdx = pos;
            playCurrent(true); 
        });
    }

    private void updateChannelList(int gIdx) {
        List<String> cNames = new ArrayList<>();
        for (RadioChannel c : mData.get(gIdx).channels) cNames.add(c.name);
        mChannelAdapter = new LiveAdapter(this, cNames);
        mListChannel.setAdapter(mChannelAdapter);
        mListChannel.setVisibility(View.VISIBLE);

        
        if (gIdx == mGroupIdx) {
            mChannelAdapter.setPlayingPosition(mChannelIdx);
        }
    }

    private void toggleMenu() {
        if (mMenuContainer.getVisibility() == View.VISIBLE) {
            mMenuContainer.setVisibility(View.GONE);
        } else {
            mMenuContainer.setVisibility(View.VISIBLE);

          
            mSelectedGroupIdxForMenu = mGroupIdx;

            
            mGroupAdapter.setPersistentSelected(mGroupIdx);
            mListGroup.setSelection(mGroupIdx);

           
            updateChannelList(mGroupIdx);

          
            mHandler.postDelayed(() -> {
                mListChannel.setSelection(mChannelIdx);
                mListChannel.requestFocus(); 
               
                mLastChannelPos = mChannelIdx;
                mLastFocusedId = R.id.list_channel;
            }, 150);
        }
    }

    private void playCurrent(boolean resetUrl) {
        if (mData == null || mData.isEmpty()) return;
        mCurrentChannel = mData.get(mGroupIdx).channels.get(mChannelIdx);
        if (resetUrl) mCurrentChannel.currentUrlIndex = 0;

       
        if (mPlayTimeoutRunnable != null) {
            mHandler.removeCallbacks(mPlayTimeoutRunnable);
        }

        
        mVideoView.setOnPreparedListener(mp -> {
            if (mPlayTimeoutRunnable != null) {
                mHandler.removeCallbacks(mPlayTimeoutRunnable);
                mPlayTimeoutRunnable = null;
            }
            mp.start(); 
        });

  
        mPlayTimeoutRunnable = () -> {
            
            mCurrentChannel.currentUrlIndex = (mCurrentChannel.currentUrlIndex + 1) % mCurrentChannel.urls.size();
            playCurrent(false); 
        };
        mHandler.postDelayed(mPlayTimeoutRunnable, 15000); 

        String url = mCurrentChannel.urls.get(mCurrentChannel.currentUrlIndex);
        mVideoView.setVideoPath(url); 

        updateIndexUI();
        mMenuContainer.setVisibility(View.GONE);
        lastMenuHideTime = System.currentTimeMillis();
        saveHistory();
    }

    private void switchSource(boolean next) {
        if (mCurrentChannel == null || mCurrentChannel.urls.size() <= 1) return;

        int size = mCurrentChannel.urls.size();
        if (next) {
           
            mCurrentChannel.currentUrlIndex = (mCurrentChannel.currentUrlIndex + 1) % size;
        } else {
           
            mCurrentChannel.currentUrlIndex = (mCurrentChannel.currentUrlIndex - 1 + size) % size;
        }

      
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
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            toggleMenu();
            return true;
        }
       
        if (mMenuContainer.getVisibility() != View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                switchSource(false);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                switchSource(true);
                return true;
            }
        }
       
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
            mVideoView.release(); 
        }
        mHandler.removeCallbacksAndMessages(null);
    }
}
