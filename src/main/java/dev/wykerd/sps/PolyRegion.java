package dev.wykerd.sps;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class PolyRegion implements Region {
    private final ArrayList<Double> points;

    PolyRegion(ArrayList<Double> points) {
        this.points = points;
    }

    @Override
    public void populateJSONFields(JSONObject obj) throws JSONException {
        JSONArray pointsArray = new JSONArray();

        for (Double point : points) {
            pointsArray.put(point);
        }

        obj.put("points", pointsArray);
    }
}
