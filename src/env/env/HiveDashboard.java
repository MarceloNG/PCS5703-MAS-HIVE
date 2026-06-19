package env;

import cartago.*;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HiveDashboard extends Artifact {

    private DashboardWsServer wsServer;
    private int currentStep;
    private int currentScore;
    private final ConcurrentHashMap<String, JSONObject> squads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JSONObject> tasks = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<JSONObject> events = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<JSONObject> auctions = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<JSONObject> scoreHistory = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, JSONObject> agentStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JSONObject> mapDispensers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JSONObject> mapGoalZones = new ConcurrentHashMap<>();

    void init() {
        init(Integer.getInteger("hive.dashboard.port", 8765));
    }

    void init(int port) {
        currentStep = 0;
        currentScore = 0;
        try {
            wsServer = new DashboardWsServer(new InetSocketAddress(port), this);
            wsServer.setDaemon(true);
            wsServer.start();
            System.out.println("[DASHBOARD] WebSocket server started on port " + port);
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Failed to start WebSocket: " + e.getMessage());
        }
    }

    @OPERATION
    void log_event(Object oType, Object oAgent, Object oData) {
        String type = str(oType);
        String agent = str(oAgent);
        String data = str(oData);

        JSONObject ev = new JSONObject();
        ev.put("type", "event");
        ev.put("ts", System.currentTimeMillis());
        ev.put("step", currentStep);
        ev.put("event", type);
        ev.put("agent", agent);

        try {
            ev.put("data", new JSONObject(data));
        } catch (Exception e) {
            JSONObject d = new JSONObject();
            d.put("raw", data);
            ev.put("data", d);
        }

        if ("agent_state".equals(type)) {
            try {
                JSONObject stateData = new JSONObject(data);
                stateData.put("name", agent);
                stateData.put("lastUpdate", currentStep);
                agentStates.put(agent, stateData);
            } catch (Exception ignored) {}
        }

        events.add(ev);
        while (events.size() > 500) events.remove(0);

        broadcast(ev.toString());
    }

    @OPERATION
    void set_step(Object oStep) {
        currentStep = toInt(oStep);
        JSONObject ev = new JSONObject();
        ev.put("type", "step_update");
        ev.put("step", currentStep);
        ev.put("score", currentScore);
        broadcast(ev.toString());
    }

    @OPERATION
    void update_score(Object oScore) {
        currentScore = toInt(oScore);
        JSONObject pt = new JSONObject();
        pt.put("step", currentStep);
        pt.put("score", currentScore);
        scoreHistory.add(pt);

        JSONObject ev = new JSONObject();
        ev.put("type", "event");
        ev.put("ts", System.currentTimeMillis());
        ev.put("step", currentStep);
        ev.put("event", "score_update");
        ev.put("agent", "system");
        JSONObject d = new JSONObject();
        d.put("score", currentScore);
        ev.put("data", d);
        events.add(ev);
        broadcast(ev.toString());
    }

    @OPERATION
    void update_task_phase(Object oTask, Object oPhase, Object oProgress) {
        String taskName = str(oTask);
        String phase = str(oPhase);
        int progress = toInt(oProgress);

        JSONObject t = tasks.computeIfAbsent(taskName, k -> new JSONObject());
        t.put("name", taskName);
        t.put("phase", phase);
        t.put("progress", progress);

        JSONObject ev = new JSONObject();
        ev.put("type", "event");
        ev.put("ts", System.currentTimeMillis());
        ev.put("step", currentStep);
        ev.put("event", "task_phase_update");
        ev.put("agent", "system");
        JSONObject d = new JSONObject();
        d.put("task", taskName);
        d.put("phase", phase);
        d.put("progress", progress);
        ev.put("data", d);
        events.add(ev);
        broadcast(ev.toString());
    }

    @OPERATION
    void update_squad(Object oSquadId, Object oMembersJson) {
        String squadId = str(oSquadId);
        try {
            JSONObject sq = new JSONObject();
            sq.put("id", squadId);
            sq.put("members", new JSONArray(str(oMembersJson)));
            squads.put(squadId, sq);

            JSONObject ev = new JSONObject();
            ev.put("type", "event");
            ev.put("ts", System.currentTimeMillis());
            ev.put("step", currentStep);
            ev.put("event", "squad_update");
            ev.put("agent", "system");
            JSONObject d = new JSONObject();
            d.put("squad", squadId);
            d.put("members", sq.getJSONArray("members"));
            ev.put("data", d);
            events.add(ev);
            broadcast(ev.toString());
        } catch (Exception e) {
            System.err.println("[DASHBOARD] squad parse error: " + e.getMessage());
        }
    }

    @OPERATION
    void register_map_dispenser(Object ox, Object oy, Object otype) {
        int x = toInt(ox), y = toInt(oy);
        String type = str(otype);
        String key = x + "," + y + ":" + type;
        JSONObject d = new JSONObject();
        d.put("x", x); d.put("y", y); d.put("type", type);
        if (mapDispensers.putIfAbsent(key, d) == null) {
            JSONObject ev = new JSONObject();
            ev.put("type", "event"); ev.put("ts", System.currentTimeMillis());
            ev.put("step", currentStep); ev.put("event", "map_dispenser");
            ev.put("agent", "system"); ev.put("data", d);
            events.add(ev);
            broadcast(ev.toString());
        }
    }

    @OPERATION
    void register_map_goal_zone(Object ox, Object oy) {
        int x = toInt(ox), y = toInt(oy);
        String key = x + "," + y;
        JSONObject d = new JSONObject();
        d.put("x", x); d.put("y", y);
        if (mapGoalZones.putIfAbsent(key, d) == null) {
            JSONObject ev = new JSONObject();
            ev.put("type", "event"); ev.put("ts", System.currentTimeMillis());
            ev.put("step", currentStep); ev.put("event", "map_goal_zone");
            ev.put("agent", "system"); ev.put("data", d);
            events.add(ev);
            broadcast(ev.toString());
        }
    }

    @OPERATION
    void remove_task(Object oTask) {
        String taskName = str(oTask);
        tasks.remove(taskName);
        auctions.removeIf(a -> taskName.equals(a.optString("task")));
    }

    String buildSnapshot() {
        JSONObject snap = new JSONObject();
        snap.put("type", "snapshot");
        snap.put("step", currentStep);
        snap.put("score", currentScore);
        snap.put("squads", new JSONArray(squads.values()));
        snap.put("tasks", new JSONArray(tasks.values()));
        snap.put("auctions", new JSONArray(auctions));

        JSONArray evArr = new JSONArray();
        List<JSONObject> recent = events.subList(Math.max(0, events.size() - 100), events.size());
        for (JSONObject e : recent) evArr.put(e);
        snap.put("events", evArr);

        snap.put("scoreHistory", new JSONArray(scoreHistory));
        snap.put("agents", new JSONArray(agentStates.values()));
        snap.put("dispensers", new JSONArray(mapDispensers.values()));
        snap.put("goalZones", new JSONArray(mapGoalZones.values()));
        return snap.toString();
    }

    void broadcast(String msg) {
        if (wsServer != null) wsServer.broadcastMessage(msg);
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    static class DashboardWsServer extends WebSocketServer {
        private final HiveDashboard dashboard;

        DashboardWsServer(InetSocketAddress addr, HiveDashboard dashboard) {
            super(addr);
            this.dashboard = dashboard;
            setReuseAddr(true);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            System.out.println("[DASHBOARD] Client connected: " + conn.getRemoteSocketAddress());
            conn.send(dashboard.buildSnapshot());
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            System.out.println("[DASHBOARD] Client disconnected");
        }

        @Override
        public void onMessage(WebSocket conn, String message) { }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            System.err.println("[DASHBOARD] WS error: " + ex.getMessage());
        }

        @Override
        public void onStart() {
            System.out.println("[DASHBOARD] WS server ready");
        }

        void broadcastMessage(String msg) {
            for (WebSocket conn : getConnections()) {
                if (conn.isOpen()) {
                    try { conn.send(msg); } catch (Exception ignored) { }
                }
            }
        }
    }
}
