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
  
    public interface OnPreparedListener {
        void onPrepared(IMediaPlayer mp); 
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

            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 30); 
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", -1);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "safe",0);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 300);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer+flush_packets");
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 100);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", "1");
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 0);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 10*1024*1024); 
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "crypto,file,http,https,tcp,tls,udp,ipv6");
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "hls_timeout", 10000000);
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "hls_max_reload", 5); 
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "hls_demuxer", "live"); 
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "hls_preload_next_segment", 1); 
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "hls_max_ts_seek", 3); 
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "av-sync", 1); 


            mMediaPlayer.setDataSource(path);
            if (mSurfaceHolder != null) mMediaPlayer.setDisplay(mSurfaceHolder);

        
            mMediaPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(IMediaPlayer mp) {
                    if (mPreparedListener != null) {
                        mPreparedListener.onPrepared(mp);
                    } else {
                        mp.start(); 
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
