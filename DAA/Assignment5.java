import java.util.*;

import java.util.stream.*;

class Node {
    final int id;
    final int stage;
    Node(int id, int stage){ this.id = id; this.stage = stage; }
    public String toString(){ return "N"+id+"(S"+stage+")"; }
}

class Edge {
    final int from, to;
    double cost;        // cost metric (could be time, fuel cost, etc)
    double travelTime;  // optional separate metric
    Edge(int f,int t,double c,double tt){ from=f; to=t; cost=c; travelTime=tt; }
}

class Graph {
    final Map<Integer, Node> nodes = new HashMap<>();
    final Map<Integer, List<Edge>> adj = new HashMap<>();
    final Map<Integer, List<Integer>> stageNodes = new HashMap<>();

    void addNode(int id, int stage){
        Node n = new Node(id, stage);
        nodes.put(id, n);
        stageNodes.computeIfAbsent(stage, k->new ArrayList<>()).add(id);
        adj.computeIfAbsent(id, k->new ArrayList<>());
    }

    void addEdge(int from, int to, double cost, double time){
        if(!nodes.containsKey(from) || !nodes.containsKey(to)) throw new RuntimeException("Unknown node");
        Edge e = new Edge(from,to,cost,time);
        adj.get(from).add(e);
    }

    void updateEdgeCost(int from, int to, double newCost){
        List<Edge> edges = adj.getOrDefault(from, Collections.emptyList());
        for(Edge e: edges) if(e.to==to) e.cost = newCost;
    }

    // DP for multistage graph: assumes every path must move from source.stage .. dest.stage
    // edges may go to any later stage (>= current stage+1). Complexity ~ sum(edges between stages)
    Result findMinCostRouteDP(int sourceId, int destId){
        Node s = nodes.get(sourceId), d = nodes.get(destId);
        if(s==null || d==null) return Result.empty("invalid nodes");
        if(s.stage > d.stage) return Result.empty("source after dest");

        // sorted stage list from s.stage to d.stage
        int start = s.stage, end = d.stage;
        Map<Integer, Double> dp = new HashMap<>();
        Map<Integer, Integer> parent = new HashMap<>();
        for(Integer id: nodes.keySet()) dp.put(id, Double.POSITIVE_INFINITY);
        dp.put(sourceId, 0.0);

        for(int st = start; st <= end; st++){
            List<Integer> currStageNodes = stageNodes.getOrDefault(st, Collections.emptyList());
            for(int uId : currStageNodes){
                double costU = dp.getOrDefault(uId, Double.POSITIVE_INFINITY);
                if(costU==Double.POSITIVE_INFINITY) continue;
                for(Edge e: adj.getOrDefault(uId, Collections.emptyList())){
                    Node toNode = nodes.get(e.to);
                    if(toNode.stage < st) continue; // do not go backwards
                    // ensure we progress to later stage or stay allowed? we allow same-stage forward edges if present
                    double nc = costU + e.cost;
                    if(nc < dp.get(e.to)){
                        dp.put(e.to, nc);
                        parent.put(e.to, uId);
                    }
                }
            }
        }

        double finalCost = dp.getOrDefault(destId, Double.POSITIVE_INFINITY);
        if(finalCost==Double.POSITIVE_INFINITY) return Result.empty("no path");
        // reconstruct path
        LinkedList<Integer> path = new LinkedList<>();
        int cur = destId;
        while(cur!=sourceId){
            path.addFirst(cur);
            cur = parent.getOrDefault(cur, -1);
            if(cur==-1) return Result.empty("reconstruction failed");
        }
        path.addFirst(sourceId);
        return new Result(finalCost, path);
    }

    // Dijkstra for arbitrary graph (no stage constraint). Use when stages are not strict or graph is dense.
    Result findShortestPathDijkstra(int sourceId, int destId){
        if(!nodes.containsKey(sourceId) || !nodes.containsKey(destId)) return Result.empty("invalid nodes");
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> parent = new HashMap<>();
        for(Integer id: nodes.keySet()) dist.put(id, Double.POSITIVE_INFINITY);
        dist.put(sourceId, 0.0);
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a->a[1]));
        pq.add(new int[]{sourceId,0});
        while(!pq.isEmpty()){
            int[] cur = pq.poll(); int u = cur[0];
            double dU = dist.get(u);
            for(Edge e: adj.getOrDefault(u, Collections.emptyList())){
                double nd = dU + e.cost;
                if(nd < dist.get(e.to)){
                    dist.put(e.to, nd);
                    parent.put(e.to, u);
                    pq.add(new int[]{e.to, 0}); // weight handled via dist map
                }
            }
        }
        double fc = dist.getOrDefault(destId, Double.POSITIVE_INFINITY);
        if(fc==Double.POSITIVE_INFINITY) return Result.empty("no path");
        LinkedList<Integer> path = new LinkedList<>();
        int cur = destId;
        while(cur!=sourceId){
            path.addFirst(cur);
            cur = parent.getOrDefault(cur, -1);
            if(cur==-1) break;
        }
        path.addFirst(sourceId);
        return new Result(fc, path);
    }

    // Batch processing with parallel stream (thread-safe read-only)
    List<Result> processBatchRequests(List<Query> queries){
        return queries.parallelStream()
                .map(q -> {
                    if(q.enforceStages) return findMinCostRouteDP(q.src, q.dst);
                    else return findShortestPathDijkstra(q.src, q.dst);
                })
                .collect(Collectors.toList());
    }

    static class Result {
        final boolean ok;
        final double cost;
        final List<Integer> path;
        final String message;
        Result(double cost, List<Integer> path){ this.ok=true; this.cost=cost; this.path=path; this.message="ok"; }
        Result(boolean ok, double cost, List<Integer> p, String msg){ this.ok=ok; this.cost=cost; this.path=p; this.message=msg; }
        static Result empty(String msg){ return new Result(false, Double.POSITIVE_INFINITY, Collections.emptyList(), msg); }
        public String toString(){ if(!ok) return "NO_PATH: "+message; return "cost="+cost+" path="+path; }
    }

    static class Query { final int src, dst; final boolean enforceStages; Query(int s,int d, boolean e){ src=s; dst=d; enforceStages=e; } }
}

public class Assignment5 {
    public static void main(String[] args) {
        Graph g = new Graph();

        // create nodes across 4 stages: 0(source stage),1,2,3(dest stage)
        g.addNode(1,0); g.addNode(2,1); g.addNode(3,1); g.addNode(4,2); g.addNode(5,2); g.addNode(6,3);

        // add edges: from->to, cost, travelTime (time optional)
        g.addEdge(1,2,5,10); g.addEdge(1,3,6,12);
        g.addEdge(2,4,4,8); g.addEdge(2,5,7,14);
        g.addEdge(3,4,3,6); g.addEdge(3,5,5,10);
        g.addEdge(4,6,6,12); g.addEdge(5,6,4,9);

        // alternative direct leap skipping a stage (allowed but must still progress)
        g.addEdge(1,4,12,24); // costlier but possible

        // DP that enforces passing nodes in each stage
        Graph.Result r1 = g.findMinCostRouteDP(1,6);
        System.out.println("DP multistage route: " + r1);

        // Dijkstra (no stage constraint)
        Graph.Result r2 = g.findShortestPathDijkstra(1,6);
        System.out.println("Dijkstra route: " + r2);

        // simulate real-time update: road closure increases cost of edge 3->4
        g.updateEdgeCost(3,4, 20.0);
        Graph.Result r3 = g.findMinCostRouteDP(1,6);
        System.out.println("After update DP route: " + r3);

        // Batch requests
        List<Graph.Query> qs = Arrays.asList(
            new Graph.Query(1,6,true),
            new Graph.Query(1,6,false)
        );
        List<Graph.Result> batchRes = g.processBatchRequests(qs);
        System.out.println("Batch results: "+batchRes);
    }
}

