package dev.wykerd.sps;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class PolyRegion implements Region {
    private final ArrayList<Point> points;

    public PolyRegion(ArrayList<Point> points) {
        this.points = points;
    }

    @Override
    public void populateJSONFields(JSONObject obj) throws JSONException {
        JSONArray pointsArray = new JSONArray();

        for (Point point : points) {
            JSONArray pointTuple = new JSONArray();
            pointTuple.put(point.getX());
            pointTuple.put(point.getY());

            pointsArray.put(pointTuple);
        }

        obj.put("points", pointsArray);
    }
}
