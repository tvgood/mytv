package com.android.mytv.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ChannelGroup implements Serializable {
    public String groupName;
    public List<RadioChannel> channels = new ArrayList<>();
}