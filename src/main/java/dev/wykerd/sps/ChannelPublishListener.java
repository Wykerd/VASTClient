package dev.wykerd.sps;

import org.json.JSONObject;

public interface ChannelPublishListener {
    void onMessage(JSONObject publication);
}
