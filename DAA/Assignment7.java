import java.util.*;
import java.util.stream.Collectors;

/**
 * ExamScheduler.java
 * - Build conflict graph from student enrollments
 * - Greedy, Welsh-Powell, DSATUR colouring
 * - Room allocation per timeslot (smallest available room that fits)
 *
 * Keep input in main() - change as needed or adapt to file I/O.
 */
public class Assignment7 {

    // Graph: course -> set of adjacent courses
    private final Map<String, Set<String>> graph = new HashMap<>();
    // course -> number of students (for room sizing)
    private final Map<String, Integer> courseSizes = new HashMap<>();

    // Rooms
    static class Room { String id; int capacity; Room(String id, int capacity){this.id=id;this.capacity=capacity;} }
    private final List<Room> rooms = new ArrayList<>();

    // ---------- Graph building ----------
    public void addCourse(String course) {
        graph.computeIfAbsent(course, k -> new HashSet<>());
        courseSizes.putIfAbsent(course, 0);
    }

    // Register student enrollment: list of courses student takes
    public void addStudentEnrollment(List<String> courses) {
        // increment student count per course
        for (String c : courses) {
            addCourse(c);
            courseSizes.put(c, courseSizes.getOrDefault(c, 0) + 1);
        }
        // add edges between every pair of courses taken by the student
        for (int i = 0; i < courses.size(); i++) {
            for (int j = i + 1; j < courses.size(); j++) {
                String a = courses.get(i), b = courses.get(j);
                graph.get(a).add(b);
                graph.get(b).add(a);
            }
        }
    }

    public void addRoom(String id, int capacity) { rooms.add(new Room(id, capacity)); }

    // ---------- Colouring helpers ----------
    private Map<String, Integer> initColorMap() {
        Map<String, Integer> color = new HashMap<>();
        for (String v : graph.keySet()) color.put(v, -1);
        return color;
    }

    // Greedy colouring in given order
    public Map<String, Integer> greedyColoring(List<String> order) {
        Map<String, Integer> color = initColorMap();
        for (String u : order) {
            boolean[] used = new boolean[graph.size() + 1];
            for (String v : graph.get(u)) {
                int c = color.getOrDefault(v, -1);
                if (c >= 0 && c < used.length) used[c] = true;
            }
            int col = 0;
            while (col < used.length && used[col]) col++;
            color.put(u, col);
        }
        return color;
    }

    // Welsh-Powell: sort vertices by degree descending then greedy
    public Map<String, Integer> welshPowell() {
        List<String> order = graph.keySet().stream()
                .sorted((a, b) -> Integer.compare(graph.get(b).size(), graph.get(a).size()))
                .collect(Collectors.toList());
        return greedyColoring(order);
    }

    // Simple Greedy using natural order (insertion order of graph.keySet())
    public Map<String, Integer> simpleGreedy() {
        List<String> order = new ArrayList<>(graph.keySet());
        return greedyColoring(order);
    }

    // DSATUR implementation
    public Map<String, Integer> dsaturColoring() {
        Map<String, Integer> color = initColorMap();
        Map<String, Set<Integer>> neighborColors = new HashMap<>();
        Map<String, Integer> degree = new HashMap<>();
        for (String v : graph.keySet()) {
            neighborColors.put(v, new HashSet<>());
            degree.put(v, graph.get(v).size());
        }

        // pick initial vertex: highest degree
        String start = graph.keySet().stream().max(Comparator.comparingInt(degree::get)).orElse(null);
        if (start != null) color.put(start, 0);

        // update neighborColors for start
        if (start != null) {
            for (String nb : graph.get(start)) neighborColors.get(nb).add(0);
        }

        int colored = (start == null) ? 0 : 1;
        int n = graph.size();

        while (colored < n) {
            // choose vertex with highest saturation degree, break ties by degree
            String pick = null;
            int bestSat = -1, bestDeg = -1;
            for (String v : graph.keySet()) {
                if (color.get(v) != -1) continue; // already colored
                int sat = neighborColors.get(v).size();
                int deg = degree.get(v);
                if (sat > bestSat || (sat == bestSat && deg > bestDeg) || (sat == bestSat && deg == bestDeg && (pick==null || v.compareTo(pick)<0))) {
                    bestSat = sat; bestDeg = deg; pick = v;
                }
            }
            if (pick == null) break; // safety

            // assign smallest available color
            boolean[] used = new boolean[n + 1];
            for (String nb : graph.get(pick)) {
                int c = color.getOrDefault(nb, -1);
                if (c >= 0) used[c] = true;
            }
            int col = 0; while (col < used.length && used[col]) col++;
            color.put(pick, col);
            // update neighbors' saturation
            for (String nb : graph.get(pick)) neighborColors.get(nb).add(col);
            colored++;
        }
        return color;
    }

    // ---------- Room allocation ----------
    // Allocates smallest room that fits each exam in a slot; room can be used once per slot.
    public Map<String, String> allocateRooms(Map<String, Integer> colorMap) {
        // sort rooms ascending by capacity to choose smallest fit
        List<Room> roomsAsc = new ArrayList<>(rooms);
        roomsAsc.sort(Comparator.comparingInt(r -> r.capacity));

        // collect courses per timeslot
        Map<Integer, List<String>> slotCourses = new HashMap<>();
        for (var e : colorMap.entrySet()) slotCourses.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());

        Map<String, String> assignedRoom = new HashMap<>();
        for (Map.Entry<Integer, List<String>> slotEntry : slotCourses.entrySet()) {
            int slot = slotEntry.getKey();
            List<String> exams = slotEntry.getValue();
            // schedule bigger exams first to avoid fragmentation
            exams.sort((a, b) -> Integer.compare(courseSizes.getOrDefault(b, 0), courseSizes.getOrDefault(a, 0)));

            // track used rooms for this slot
            Set<String> usedRooms = new HashSet<>();
            for (String course : exams) {
                int need = courseSizes.getOrDefault(course, 0);
                // find smallest free room with capacity >= need
                Room chosen = null;
                for (Room r : roomsAsc) {
                    if (r.capacity >= need && !usedRooms.contains(r.id)) { chosen = r; break; }
                }
                if (chosen != null) {
                    assignedRoom.put(course, chosen.id);
                    usedRooms.add(chosen.id);
                } else {
                    assignedRoom.put(course, "UNASSIGNED"); // not enough rooms
                }
            }
        }
        return assignedRoom;
    }

    // Utility: number of colors used
    public static int colorsUsed(Map<String, Integer> colorMap) {
        return (int) colorMap.values().stream().filter(c -> c >= 0).distinct().count();
    }

    // Pretty print timetable
    public void printTimetable(Map<String, Integer> colorMap, Map<String, String> roomsMap) {
        System.out.printf("%-8s %-8s %-10s %-6s%n", "Course", "Slot", "Room", "Students");
        List<String> courses = new ArrayList<>(graph.keySet());
        courses.sort(Comparator.naturalOrder());
        for (String c : courses) {
            int slot = colorMap.getOrDefault(c, -1);
            String room = roomsMap.getOrDefault(c, "UNASSIGNED");
            int students = courseSizes.getOrDefault(c, 0);
            System.out.printf("%-8s %-8s %-10s %-6d%n", c, "S"+(slot+1), room, students);
        }
    }

    // ---------- Demo / main ----------
    public static void main(String[] args) {
        Assignment7 s = new Assignment7();

        // Sample rooms (id, capacity) - edit for your dataset
        s.addRoom("R1", 100);
        s.addRoom("R2", 80);
        s.addRoom("R3", 50);
        s.addRoom("R4", 30);

        // Sample students and their enrolled courses (change or load from file/db)
        s.addStudentEnrollment(Arrays.asList("C1", "C2"));
        s.addStudentEnrollment(Arrays.asList("C2", "C3"));
        s.addStudentEnrollment(Arrays.asList("C1", "C3"));
        s.addStudentEnrollment(Arrays.asList("C4"));
        // Add more to test scale
        s.addStudentEnrollment(Arrays.asList("C5", "C6"));
        s.addStudentEnrollment(Arrays.asList("C5", "C2"));
        s.addStudentEnrollment(Arrays.asList("C7", "C8", "C9"));
        s.addStudentEnrollment(Arrays.asList("C9", "C2"));

        // You can programmatically add many students/courses here.

        // --- Compare algorithms ---
        long t1 = System.nanoTime();
        Map<String, Integer> greedy = s.simpleGreedy();
        long t2 = System.nanoTime();
        Map<String, Integer> welsh = s.welshPowell();
        long t3 = System.nanoTime();
        Map<String, Integer> dsatur = s.dsaturColoring();
        long t4 = System.nanoTime();

        System.out.println("Algorithm comparison:");
        System.out.printf("Simple Greedy: Colors=%d, Time=%.3f ms%n", colorsUsed(greedy),(t2-t1)/1e6);
        System.out.printf("Welsh-Powell:  Colors=%d, Time=%.3f ms%n", colorsUsed(welsh),(t3-t2)/1e6);
        System.out.printf("DSATUR:        Colors=%d, Time=%.3f ms%n", colorsUsed(dsatur),(t4-t3)/1e6);

        // Choose an algorithm result to allocate rooms (pick best by colors used)
        Map<String, Integer> chosen = dsatur;
        if (colorsUsed(welsh) < colorsUsed(chosen)) chosen = welsh;
        if (colorsUsed(greedy) < colorsUsed(chosen)) chosen = greedy;

        System.out.println("\nSelected algorithm result (fewest colors). Timetable:");
        Map<String, String> allocation = s.allocateRooms(chosen);
        s.printTimetable(chosen, allocation);

        // If any UNASSIGNED results, notify
        long unassigned = allocation.values().stream().filter(r -> r.equals("UNASSIGNED")).count();
        if (unassigned > 0) {
            System.out.println("\nWarning: " + unassigned + " exams couldn't be assigned rooms for their slot (insufficient capacity/rooms).");
        }
    }
}



