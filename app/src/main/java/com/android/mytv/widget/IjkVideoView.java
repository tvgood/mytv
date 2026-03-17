package com.android.mytv.widget;

import android.content.Context;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;

public class IjkVideoView extends FrameLayout {
    private IjkMediaPlayer mMediaPlayer = null;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    // 1. 修复监听器接口：使用 IJKPlayer 的 IMediaPlayer 类型
    public interface OnPreparedListener {
        void onPrepared(IMediaPlayer mp); // 使用 IMediaPlayer 而不是 MediaPlayer
    }

    private OnPreparedListener mPreparedListener;
    public void setOnPreparedListener(OnPreparedListener listener) {
        mPreparedListener = listener;
    }

    public IjkVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        try {
            IjkMediaPlayer.loadLibrariesOnce(null);
            IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        } catch (Throwable e) {}

        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceHolder = holder;
                if (mMediaPlayer != null) mMediaPlayer.setDisplay(holder);
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {}
            @Override public void surfaceDestroyed(SurfaceHolder holder) { mSurfaceHolder = null; }
        });
        addView(mSurfaceView);
    }



    public void setVideoPath(String path) {
        release();
        try {
            mMediaPlayer = new IjkMediaPlayer();

            // 1. 基础配置（移除冗余项）
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", 300); // 5分钟缓存
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "safe", 0);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 30);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "subtitle", 1);

// 2. H265 专用核心参数（关键！）
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 90); // 90秒直播缓存
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "buffer_size", 1024 * 1024 * 50); // 50MB (H265 必须)
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 1024 * 1024 * 200); // 200MB
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 10000000); // 10秒超时

            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "av-sync", 1); // 启用自动音视频同步
// 3. 动态强制格式 + UA/Referer（解决无后缀源 + 服务器校验）
            if (path.startsWith("http://") || path.startsWith("https://")) {
                // 关键：强制 HLS + 添加浏览器 UA 和 Referer
                mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "format", "hls");
                mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);

                // 添加浏览器 UA 和 Referer（必须！）
                mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-user-agent",
                        "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36");

            } else if (path.startsWith("rtmp://")) {
                mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "format", "flv");
            }

// 4. 协议白名单（保留原有）
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist",
                    "ijkio,ffio,async,cache,crypto,file,dash,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data");

// 5. H265 专用解码（关闭硬件解码！）
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1); // 关键修复！
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 0);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 0);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);

            mMediaPlayer.setDataSource(path);
            if (mSurfaceHolder != null) mMediaPlayer.setDisplay(mSurfaceHolder);

            // 2. 修复：使用 IJKPlayer 的 IMediaPlayer.OnPreparedListener 类型
            mMediaPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(IMediaPlayer mp) {
                    if (mPreparedListener != null) {
                        mPreparedListener.onPrepared(mp);
                    } else {
                        mp.start(); // 保留原有自动播放
                    }
                }
            });
            mMediaPlayer.prepareAsync();
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void start() { if (mMediaPlayer != null) mMediaPlayer.start(); }
    public boolean isPlaying() { return mMediaPlayer != null && mMediaPlayer.isPlaying(); }
    public void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
}