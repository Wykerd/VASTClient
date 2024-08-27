package dev.wykerd.sps;

import org.json.JSONException;
import org.json.JSONObject;

public class CircularRegion implements Region {
    private final Point center;
    private final double radius;

    CircularRegion(Point center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    @Override
    public void populateJSONFields(JSONObject obj) throws JSONException {
        obj.put("x", center.getX());
        obj.put("y", center.getY());
        obj.put("radius", radius);
    }
}
