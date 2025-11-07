import java.util.*;

/**
 * TSPBranchAndBound.java
 * LC Branch & Bound using reduced cost matrix (best-first search).
 * Minimal comments; straightforward structure.
 */
public class Assignment8 {

    static final int INF = Integer.MAX_VALUE / 4;

    static class Node implements Comparable<Node> {
        int[][] reducedMatrix;
        int level;               // how many edges chosen (start at 0)
        int vertex;              // current city (last in path)
        int pathCost;            // cost accumulated so far (including edge costs)
        int bound;               // pathCost + reductionCost
        List<Integer> path;      // sequence of visited cities (start included)

        Node(int n) {
            reducedMatrix = new int[n][n];
            path = new ArrayList<>();
        }

        @Override
        public int compareTo(Node o) {
            if (this.bound != o.bound) return Integer.compare(this.bound, o.bound);
            return Integer.compare(this.level, o.level); // tie-breaker (deeper = prefer)
        }
    }

    private final int n;
    private final int[][] cost;   // original cost matrix
    private long nodesExplored = 0;

    public Assignment8(int[][] cost) {
        this.n = cost.length;
        this.cost = cost;
    }

    // Deep copy matrix
    private static int[][] copyMatrix(int[][] m) {
        int n = m.length;
        int[][] c = new int[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(m[i], 0, c[i], 0, n);
        return c;
    }

    // Reduce matrix in-place; return total reduction added to lower bound
    private static int reduceMatrix(int[][] m) {
        int n = m.length;
        int reduction = 0;

        // Row reduction
        for (int i = 0; i < n; i++) {
            int min = INF;
            for (int j = 0; j < n; j++) if (m[i][j] < min) min = m[i][j];
            if (min == INF || min == 0) continue;
            for (int j = 0; j < n; j++) if (m[i][j] < INF) m[i][j] -= min;
            reduction += min;
        }

        // Column reduction
        for (int j = 0; j < n; j++) {
            int min = INF;
            for (int i = 0; i < n; i++) if (m[i][j] < min) min = m[i][j];
            if (min == INF || min == 0) continue;
            for (int i = 0; i < n; i++) if (m[i][j] < INF) m[i][j] -= min;
            reduction += min;
        }

        return reduction;
    }

    // Set entire row r to INF
    private static void setRowInf(int[][] m, int r) {
        int n = m.length;
        for (int j = 0; j < n; j++) m[r][j] = INF;
    }

    // Set entire column c to INF
    private static void setColInf(int[][] m, int c) {
        int n = m.length;
        for (int i = 0; i < n; i++) m[i][c] = INF;
    }

    // Main B&B solver, startCity index assumed 0
    public Result solve(int startCity) {
        nodesExplored = 0;
        PriorityQueue<Node> pq = new PriorityQueue<>();
        int[][] rootMatrix = copyMatrix(cost);
        for (int i = 0; i < n; i++) rootMatrix[i][i] = INF;

        int rootReduction = reduceMatrix(rootMatrix);
        Node root = new Node(n);
        root.reducedMatrix = rootMatrix;
        root.level = 0;
        root.vertex = startCity;
        root.pathCost = 0;
        root.bound = rootReduction;
        root.path.add(startCity);
        pq.add(root);

        int bestCost = INF;
        List<Integer> bestPath = null;

        while (!pq.isEmpty()) {
            Node node = pq.poll();
            nodesExplored++;

            if (node.bound >= bestCost) continue; // prune

            if (node.level == n - 1) {
                // Only one city left to visit; compute final cost to return to start
                int last = node.vertex;
                // find the remaining city (not in path)
                int remaining = -1;
                boolean[] visited = new boolean[n];
                for (int v : node.path) visited[v] = true;
                for (int i = 0; i < n; i++) if (!visited[i]) { remaining = i; break; }

                if (remaining == -1) {
                    // path already has all cities; just add cost to return
                    int finalCost = node.pathCost + cost[last][startCity];
                    if (finalCost < bestCost) {
                        bestCost = finalCost;
                        bestPath = new ArrayList<>(node.path);
                        bestPath.add(startCity);
                    }
                    continue;
                }

                // Expand to remaining city then return
                if (cost[last][remaining] < INF && cost[remaining][startCity] < INF) {
                    int total = node.pathCost + cost[last][remaining] + cost[remaining][startCity];
                    if (total < bestCost) {
                        bestCost = total;
                        bestPath = new ArrayList<>(node.path);
                        bestPath.add(remaining);
                        bestPath.add(startCity);
                    }
                }
                continue;
            }

            // Expand children: try every city not yet visited
            for (int city = 0; city < n; city++) {
                if (node.path.contains(city)) continue;
                if (node.reducedMatrix[node.vertex][city] == INF) continue; // edge not allowed

                Node child = new Node(n);
                child.reducedMatrix = copyMatrix(node.reducedMatrix);
                // cost to go from current vertex -> city (note: after reduction matrix entries are reduced values;
                // but we must use original cost for actual pathCost increment)
                int edgeCost = cost[node.vertex][city];
                if (edgeCost >= INF) continue;

                // Block row of current vertex and column of the chosen city
                setRowInf(child.reducedMatrix, node.vertex);
                setColInf(child.reducedMatrix, city);
                // Prevent returning immediately to the parent city: set [city][startCity] = INF
                child.reducedMatrix[city][startCity] = INF;

                // Compute reduction cost of the child matrix
                int reduction = reduceMatrix(child.reducedMatrix);

                child.level = node.level + 1;
                child.vertex = city;
                child.path = new ArrayList<>(node.path);
                child.path.add(city);
                child.pathCost = node.pathCost + edgeCost;
                // bound = accumulated path costs + reductions (node.bound already contained parent's reductions)
                child.bound = child.pathCost + reduction;

                if (child.bound < bestCost) {
                    pq.add(child);
                }
                // else pruned
            }
        }

        return new Result(bestCost, bestPath, nodesExplored);
    }

    public static class Result {
        public final int bestCost;
        public final List<Integer> bestPath;
        public final long nodesExplored;
        public Result(int bestCost, List<Integer> bestPath, long nodesExplored) {
            this.bestCost = bestCost; this.bestPath = bestPath; this.nodesExplored = nodesExplored;
        }
    }

    // Demo with a small example (5 cities). INF means no direct route.
    public static void main(String[] args) {
        // Example cost matrix (symmetric). Modify as needed.
        int[][] cost = {
                {INF, 10, 8,  9,  7},
                {10,  INF, 10, 5,  6},
                {8,  10, INF, 8,  9},
                {9,  5,  8, INF, 6},
                {7,  6,  9,  6, INF}
        };

        Assignment8 solver = new Assignment8(cost);
        int startCity = 0;

        long t0 = System.currentTimeMillis();
        Result res = solver.solve(startCity);
        long t1 = System.currentTimeMillis();

        if (res.bestPath == null) {
            System.out.println("No feasible tour found.");
        } else {
            System.out.println("Optimal tour cost: " + res.bestCost);
            System.out.print("Path: ");
            for (int i = 0; i < res.bestPath.size(); i++) {
                System.out.print(res.bestPath.get(i) + (i + 1 < res.bestPath.size() ? " -> " : ""));
            }
            System.out.println();
            System.out.println("Nodes explored: " + res.nodesExplored);
            System.out.println("Time taken: " + (t1 - t0) + " ms");
        }
    }
}


 
