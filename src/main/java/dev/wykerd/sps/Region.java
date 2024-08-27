package dev.wykerd.sps;

import org.json.JSONException;
import org.json.JSONObject;

public interface Region {
    void populateJSONFields(JSONObject obj) throws JSONException;
}
