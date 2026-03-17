package com.android.mytv.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RadioChannel implements Serializable {
    public String name;
    public List<String> urls = new ArrayList<>();
    public int currentUrlIndex = 0;
}
