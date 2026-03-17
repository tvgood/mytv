package com.android.mytv.utils;

import com.android.mytv.model.ChannelGroup;
import com.android.mytv.model.RadioChannel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PlaylistParser {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public static List<ChannelGroup> syncFetchList(String urlStr) throws Exception {
        Request request = new Request.Builder().url(urlStr).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());

            List<ChannelGroup> groups = new ArrayList<>();
            ChannelGroup currentGroup = null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.contains(",#genre#")) {
                    String groupName = line.split(",")[0].trim();
                    currentGroup = findOrCreateGroup(groups, groupName);
                } else if (currentGroup != null && line.contains(",")) {
                    int firstComma = line.indexOf(",");
                    if (firstComma > 0) {
                        String name = line.substring(0, firstComma).trim();
                        String url = line.substring(firstComma + 1).trim();
                        if (!url.isEmpty()) {
                            addChannelToGroup(currentGroup, name, url);
                        }
                    }
                }
            }
            return groups;
        }
    }

    private static ChannelGroup findOrCreateGroup(List<ChannelGroup> groups, String name) {
        for (ChannelGroup g : groups) {
            if (g.groupName.equals(name)) return g;
        }
        ChannelGroup newGroup = new ChannelGroup();
        newGroup.groupName = name;
        groups.add(newGroup);
        return newGroup;
    }

    private static void addChannelToGroup(ChannelGroup group, String name, String url) {
        RadioChannel target = null;
        for (RadioChannel c : group.channels) {
            if (c.name.equals(name)) {
                target = c;
                break;
            }
        }
        if (target != null) {
            if (!target.urls.contains(url)) target.urls.add(url);
        } else {
            RadioChannel nc = new RadioChannel();
            nc.name = name;
            nc.urls.add(url);
            group.channels.add(nc);
        }
    }
}
