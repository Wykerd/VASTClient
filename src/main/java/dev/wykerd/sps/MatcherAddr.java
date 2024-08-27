package dev.wykerd.sps;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;

public class MatcherAddr {
    private final String host;
    private final int port;

    MatcherAddr(String host, int port) {
        this.host = host.toLowerCase();
        this.port = port;
    }

    public boolean equals(MatcherAddr other) {
        return this.host.equals(other.host) && this.port == other.port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("host", this.host);
        json.put("port", this.port);
        return json;
    }

    public URI toURI() {
        return URI.create("ws://" + this.host + ":" + this.port);
    }
}
