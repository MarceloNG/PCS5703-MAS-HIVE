package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

import java.util.*;

public class PathFinder extends DefaultInternalAction {

    static class Node implements Comparable<Node> {
        int x, y, g, f;
        Node parent;
        Node(int x, int y, int g, int f, Node parent) {
            this.x = x; this.y = y; this.g = g; this.f = f; this.parent = parent;
        }
        public int compareTo(Node o) { return Integer.compare(this.f, o.f); }
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        int fromX = (int) ((NumberTerm) args[0]).solve();
        int fromY = (int) ((NumberTerm) args[1]).solve();
        int toX   = (int) ((NumberTerm) args[2]).solve();
        int toY   = (int) ((NumberTerm) args[3]).solve();

        String dir = astar(fromX, fromY, toX, toY, new HashSet<>());

        return un.unifies(args[4], new Atom(dir));
    }

    private String astar(int fromX, int fromY, int toX, int toY,
                         Set<String> obstacles) {
        if (fromX == toX && fromY == toY) return "skip";

        PriorityQueue<Node> open = new PriorityQueue<>();
        Set<String> closed = new HashSet<>();
        int[][] dirs = {{0,-1}, {0,1}, {1,0}, {-1,0}};
        String[] dirNames = {"n", "s", "e", "w"};

        int h = Math.abs(toX - fromX) + Math.abs(toY - fromY);
        open.add(new Node(fromX, fromY, 0, h, null));

        int maxIter = 2000;
        int iter = 0;

        while (!open.isEmpty() && iter++ < maxIter) {
            Node current = open.poll();
            String ck = current.x + "," + current.y;
            if (closed.contains(ck)) continue;
            closed.add(ck);

            if (current.x == toX && current.y == toY) {
                return firstDirection(current, fromX, fromY, dirNames, dirs);
            }

            for (int i = 0; i < 4; i++) {
                int nx = current.x + dirs[i][0];
                int ny = current.y + dirs[i][1];
                String nk = nx + "," + ny;
                if (!closed.contains(nk) && !obstacles.contains(nk)) {
                    int ng = current.g + 1;
                    int nf = ng + Math.abs(toX - nx) + Math.abs(toY - ny);
                    open.add(new Node(nx, ny, ng, nf, current));
                }
            }
        }
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (Math.abs(dx) >= Math.abs(dy))
            return dx > 0 ? "e" : "w";
        else
            return dy > 0 ? "s" : "n";
    }

    private String firstDirection(Node goal, int fromX, int fromY,
                                  String[] dirNames, int[][] dirs) {
        Node n = goal;
        while (n.parent != null && !(n.parent.x == fromX && n.parent.y == fromY)) {
            n = n.parent;
        }
        int dx = n.x - fromX;
        int dy = n.y - fromY;
        for (int i = 0; i < 4; i++) {
            if (dirs[i][0] == dx && dirs[i][1] == dy) return dirNames[i];
        }
        return "skip";
    }
}
