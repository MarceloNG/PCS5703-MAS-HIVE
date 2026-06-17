package env;

import cartago.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TaskBoard extends Artifact {

    private ConcurrentHashMap<String, TaskInfo> knownTasks;
    private ConcurrentHashMap<String, List<Bid>> bids;
    private ConcurrentHashMap<String, String> assignedTasks;
    private ConcurrentHashMap<String, List<int[]>> taskRequirements;

    static class TaskInfo {
        String name;
        int deadline, reward, nBlocks;
        List<String> blockTypes;
        TaskInfo(String n, int d, int r, int nb) {
            this.name = n; this.deadline = d; this.reward = r; this.nBlocks = nb;
            this.blockTypes = new ArrayList<>();
        }
    }

    static class Bid {
        String squadId;
        double value;
        Bid(String s, double v) { this.squadId = s; this.value = v; }
    }

    void init() {
        knownTasks = new ConcurrentHashMap<>();
        bids = new ConcurrentHashMap<>();
        assignedTasks = new ConcurrentHashMap<>();
        taskRequirements = new ConcurrentHashMap<>();
    }

    private ConcurrentHashMap<String, Long> signaledTasks = new ConcurrentHashMap<>();

    @OPERATION
    void register_task(Object oname, Object odeadline, Object oreward, Object onBlocks) {
        String name = oname.toString();
        int deadline = toInt(odeadline);
        int reward = toInt(oreward);
        int nBlocks = toInt(onBlocks);
        knownTasks.putIfAbsent(name, new TaskInfo(name, deadline, reward, nBlocks));
    }

    @OPERATION
    void signal_task_ready(Object oname) {
        String name = oname.toString();
        if (!assignedTasks.containsKey(name)) {
            long now = System.currentTimeMillis();
            Long lastSignal = signaledTasks.get(name);
            if (lastSignal == null || now - lastSignal > 2000) {
                signaledTasks.put(name, now);
                TaskInfo info = knownTasks.get(name);
                if (info != null) {
                    signal("new_task_available", name, info.deadline, info.reward, info.nBlocks);
                }
            }
        }
    }

    @OPERATION
    void evaluate_task(Object oname, Object odeadline, Object oreward, Object onBlocks,
                       OpFeedbackParam<Double> score) {
        int reward = toInt(oreward);
        int nBlocks = toInt(onBlocks);
        double s = (nBlocks > 0) ? (double) reward / nBlocks : 0;
        score.set(s);
    }

    @OPERATION
    void place_bid(Object otaskName, Object osquadId, Object obidValue) {
        String taskName = otaskName.toString();
        String squadId = osquadId.toString();
        double bidValue = toDouble(obidValue);
        bids.computeIfAbsent(taskName, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new Bid(squadId, bidValue));
    }

    /** Maior lance vence; null se a lista for vazia/nula. Pura e testável (backfill Track 1). */
    static Bid bestBid(List<Bid> taskBids) {
        if (taskBids == null || taskBids.isEmpty()) return null;
        return taskBids.stream()
            .max(Comparator.comparingDouble(b -> b.value))
            .orElse(null);
    }

    @OPERATION
    void resolve_auction(Object otaskName,
                         OpFeedbackParam<String> winnerSquad) {
        String taskName = otaskName.toString();
        List<Bid> taskBids = bids.get(taskName);
        if (taskBids == null || taskBids.isEmpty()) {
            winnerSquad.set("none");
            return;
        }
        Bid best = bestBid(taskBids);
        if (best != null) {
            assignedTasks.put(taskName, best.squadId);
            signal("task_assigned", taskName, best.squadId);
            winnerSquad.set(best.squadId);
        } else {
            winnerSquad.set("none");
        }
        bids.remove(taskName);
    }

    @OPERATION
    void complete_task(Object otaskName) {
        String taskName = otaskName.toString();
        assignedTasks.remove(taskName);
        signaledTasks.remove(taskName);
    }

    @OPERATION
    void remove_expired(Object ocurrentStep) {
        int step = toInt(ocurrentStep);
        List<String> expired = new ArrayList<>();
        for (var entry : knownTasks.entrySet()) {
            if (entry.getValue().deadline <= step) {
                expired.add(entry.getKey());
            }
        }
        for (String name : expired) {
            knownTasks.remove(name);
            assignedTasks.remove(name);
            bids.remove(name);
            signaledTasks.remove(name);
        }
    }

    @OPERATION
    void is_task_assigned(Object otaskName, OpFeedbackParam<Boolean> result) {
        result.set(assignedTasks.containsKey(otaskName.toString()));
    }

    @OPERATION
    void register_task_block(Object otaskName, Object oblockType) {
        String taskName = otaskName.toString();
        String blockType = oblockType.toString();
        TaskInfo info = knownTasks.get(taskName);
        if (info != null) {
            info.blockTypes.add(blockType);
        }
    }

    @OPERATION
    void get_task_first_block(Object otaskName, OpFeedbackParam<String> blockType) {
        String taskName = otaskName.toString();
        TaskInfo info = knownTasks.get(taskName);
        if (info != null && !info.blockTypes.isEmpty()) {
            blockType.set(info.blockTypes.get(0));
        } else {
            blockType.set("b0");
        }
    }

    @OPERATION
    void get_task_blocks(Object otaskName,
                         OpFeedbackParam<String> block1Type,
                         OpFeedbackParam<String> block2Type) {
        String taskName = otaskName.toString();
        TaskInfo info = knownTasks.get(taskName);
        if (info != null && info.blockTypes.size() >= 2) {
            block1Type.set(info.blockTypes.get(0));
            block2Type.set(info.blockTypes.get(1));
        } else if (info != null && info.blockTypes.size() == 1) {
            block1Type.set(info.blockTypes.get(0));
            block2Type.set("b0");
        } else {
            block1Type.set("b0");
            block2Type.set("b1");
        }
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }

    private double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }
}
