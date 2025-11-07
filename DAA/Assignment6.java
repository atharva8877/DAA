import java.util.*;

class Item {
    int weight, utility;
    boolean perishable;
    Item(int weight, int utility, boolean perishable) {
        this.weight = weight;
        this.utility = utility;
        this.perishable = perishable;
    }
}

public class Assignment6 {

    // Brute Force (Recursive)
    static int bruteForce(Item[] items, int n, int W) {
        if (n == 0 || W == 0) return 0;
        if (items[n - 1].weight > W) return bruteForce(items, n - 1, W);
        return Math.max(
                items[n - 1].utility + bruteForce(items, n - 1, W - items[n - 1].weight),
                bruteForce(items, n - 1, W)
        );
    }

    // Dynamic Programming
    static int dynamicProgramming(Item[] items, int W) {
        int n = items.length;
        int[][] dp = new int[n + 1][W + 1];
        for (int i = 1; i <= n; i++) {
            for (int w = 1; w <= W; w++) {
                if (items[i - 1].weight <= w)
                    dp[i][w] = Math.max(items[i - 1].utility + dp[i - 1][w - items[i - 1].weight], dp[i - 1][w]);
                else dp[i][w] = dp[i - 1][w];
            }
        }
        return dp[n][W];
    }

    // Greedy (approximation) — by utility/weight ratio
    static int greedyApprox(Item[] items, int W) {
        Arrays.sort(items, (a, b) -> Double.compare((double)b.utility / b.weight, (double)a.utility / a.weight));
        int totalUtility = 0, currWeight = 0;
        for (Item item : items) {
            if (currWeight + item.weight <= W) {
                currWeight += item.weight;
                totalUtility += item.utility;
            }
        }
        return totalUtility;
    }

    // Priority for perishable items
    static int dynamicWithPriority(Item[] items, int W) {
        Arrays.sort(items, (a, b) -> Boolean.compare(!a.perishable, !b.perishable)); 
        return dynamicProgramming(items, W);
    }

    // Multiple trucks scenario
    static int multiTruck(Item[] items, int[] capacities) {
        int totalUtility = 0;
        for (int cap : capacities)
            totalUtility += dynamicProgramming(items, cap);
        return totalUtility;
    }

    public static void main(String[] args) {
        Item[] items = {
                new Item(10, 60, true),
                new Item(20, 100, false),
                new Item(30, 120, true),
                new Item(15, 80, false)
        };
        int W = 50;
        System.out.println("Brute Force: " + bruteForce(items, items.length, W));
        System.out.println("Dynamic Programming: " + dynamicProgramming(items, W));
        System.out.println("Greedy Approximation: " + greedyApprox(items, W));
        System.out.println("Priority-Based Optimization: " + dynamicWithPriority(items, W));
        System.out.println("Multi-Truck Optimization: " + multiTruck(items, new int[]{40, 30}));
    }
}
