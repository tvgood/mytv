package com.android.mytv;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import com.android.mytv.utils.PlaylistParser;
import com.android.mytv.model.ChannelGroup;
import java.io.Serializable;
import java.util.List;

public class SplashActivity extends AppCompatActivity {
    private static final String URL = "TXT格式电视直播列表网址";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 全屏+常亮设置
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_splash);

        new Thread(() -> {
            try {
                // 调用 Radio APP 的同步解析逻辑
                List<ChannelGroup> data = PlaylistParser.syncFetchList(URL);

                if (data != null && !data.isEmpty()) {
                    Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                    intent.putExtra("data", (Serializable) data);
                    startActivity(intent);
                    finish();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 建议这里弹个 Toast 提示网络错误
            }
        }).start();
    }
}