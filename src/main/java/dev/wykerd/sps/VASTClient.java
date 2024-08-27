package dev.wykerd.sps;

import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * A port of the <a href="https://github.com/Wykerd/VAST.js">VAST.js</a> client implementation to Java
 *
 * @author Daniel Wykerd
 */
public class VASTClient implements SPSClient {
    private Socket socket;
    private final Logger logger;
    private boolean connected;
    private int matcherId = 0;
    private MatcherAddr matcherAddr = null;
    private Point position;
    private String hostname;
    private String clientID;
    private Point matcherPos = null;
    private boolean assigned = false;
    private final Map<String, JSONObject> subscriptions;
    private int pubCount = 0;
    private final Map<String, List<ChannelPublishListener>> listeners = new ConcurrentHashMap<>();

    VASTClient(Logger logger, Point position, String clientID) {
        this.logger = logger;
        this.position = position;
        this.clientID = clientID;
        this.subscriptions = new ConcurrentHashMap<>();
        try {
            this.hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            this.hostname = "unknown";
        }
    }

    @Override
    public CompletableFuture<Boolean> connect(URI uri) {
        socket = IO.socket(uri);

        CompletableFuture<Boolean> assignedFuture = new CompletableFuture<>();

        socket.on(Socket.EVENT_CONNECT, event -> {
            this.connected = true;
            this.logger.info("VASTClient: connection established");
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, event -> {
            assignedFuture.complete(false);
            this.connected = false;
            this.logger.info("VASTClient: connection error");
        });

        socket.on("connect_timeout", event -> {
            assignedFuture.complete(false);
            this.connected = false;
            this.logger.info("VASTClient: connection timeout");
        });

        socket.on("request_info", event -> {
            JSONObject obj = new JSONObject();
            try {
                obj.put("matcherID", this.matcherId);
                obj.put("matcherAddr", this.matcherAddr == null ? JSONObject.NULL : this.matcherAddr.toJSON());
                obj.put("hostname", this.hostname);
                obj.put("clientID", this.clientID);
                obj.put("pos", this.position.toJSON());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            logger.info("VASTClient: request info: " + obj);
            socket.emit("client_info", obj);
        });

        socket.on("assign_matcher", event -> {
            JSONObject obj = (JSONObject) event[0];
            boolean isNewMatcher;
            try {
                this.clientID = obj.getString("clientID");
                JSONObject matcherPos = obj.getJSONObject("matcherPos");
                this.matcherPos = new Point(matcherPos.getDouble("x"), matcherPos.getDouble("y"));
                JSONObject matcherAddr = obj.getJSONObject("matcherAddr");
                MatcherAddr addr = new MatcherAddr(matcherAddr.getString("host"), matcherAddr.getInt("port"));
                isNewMatcher = addr.equals(this.matcherAddr);
                this.matcherAddr = addr;
                this.matcherId = obj.getInt("matcherID");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            if (isNewMatcher) {
                socket.disconnect();
                this.assigned = false;
                this.connected = false;
                this.connect(this.matcherAddr.toURI());
            }
        });

        socket.on("confirm_matcher", event -> {
            JSONObject obj = (JSONObject) event[0];
            try {
                this.clientID = obj.getString("clientID");
                JSONObject matcherPos = obj.getJSONObject("matcherPos");
                this.matcherPos = new Point(matcherPos.getDouble("x"), matcherPos.getDouble("y"));
                JSONObject matcherAddr = obj.getJSONObject("matcherAddr");
                this.matcherAddr = new MatcherAddr(matcherAddr.getString("host"), matcherAddr.getInt("port"));
                this.matcherId = obj.getInt("matcherID");
                this.assigned = true;
                assignedFuture.complete(true);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            logger.info("VASTClient: assigned to matcher with id " + this.matcherId);
        });

        socket.on(Socket.EVENT_DISCONNECT, event -> {
            this.connected = false;
            this.assigned = false;
            this.logger.info("VASTClient: connection disconnected");
        });

        socket.on("subscribe_r", event -> {
            JSONObject obj = (JSONObject) event[0];
            try {
                String subId = obj.getString("subID");
                subscriptions.put(subId, obj);
                logger.info("VASTClient: new subscription with id " + subId);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });

        socket.on("unsubscribe_r", event -> {
            JSONObject obj = (JSONObject) event[0];
            try {
                String subId = obj.getString("subID");
                subscriptions.remove(subId);
                logger.info("VASTClient: removed subscription with id " + subId);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });

        socket.on("publication", event -> {
            JSONObject pack = (JSONObject) event[0];

            try {
                emitPublication(pack.getString("channel"), pack);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });

        socket.onAnyIncoming(event -> {
           logger.info(Arrays.toString(event));
        });

        socket.connect();

        return assignedFuture;
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    @Override
    public boolean isReady() {
        return this.assigned;
    }

    @Override
    public void move(Point position) {
        try {
            socket.emit("move", position.toJSON());
            this.position = position;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void subscribe(Region region, String channel, boolean followClient) {
        JSONObject payload = new JSONObject();

        try {
            payload.put("channel", channel);
            payload.put("followClient", followClient ? 1 : 0);
            region.populateJSONFields(payload);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        socket.emit("subscribe", payload);
    }

    @Override
    public void publish(Region region, String channel, Object payload) {
        if (!(region instanceof CircularRegion)) {
            throw new IllegalArgumentException("Region must be an instance of CircularRegion. Other regions aren't currently supported.");
        }

        JSONObject pack = new JSONObject();

        int pubID = this.pubCount++;

        try {
            pack.put("pubID", this.clientID + "-" + pubID);
            pack.put("channel", channel);
            pack.put("payload", payload);
            region.populateJSONFields(pack);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        socket.emit("publish", pack);
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        socket.emit("unsubscribe", subscriptionId);
    }

    @Override
    public void clearSubscriptions(String channel) {
        for (Map.Entry<String, JSONObject> subscription : subscriptions.entrySet()) {
            if (channel != null) {
                try {
                    String channelName = subscription.getValue().getString("channel");
                    if (!channelName.equals(channel)) {
                        continue;
                    }
                } catch (JSONException e) {
                    continue;
                }
            }
            this.unsubscribe(subscription.getKey());
        }
    }

    @Override
    public void onPublication(String channel, ChannelPublishListener listener) {
        listeners.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void offPublication(String channel, ChannelPublishListener listener) {
        List<ChannelPublishListener> listenersList = listeners.get(channel);
        if (listenersList != null) {
            listenersList.remove(listener);
        }
    }

    private void emitPublication(String channel, JSONObject message) {
        List<ChannelPublishListener> listenersList = listeners.get(channel);
        if (listenersList != null) {
            for (ChannelPublishListener listener : listenersList) {
                listener.onMessage(message);
            }
        }
    }

    @Override
    public void disconnect() {
        this.assigned = false;
        this.connected = false;
        this.subscriptions.clear();
        this.socket.disconnect();
    }
}
