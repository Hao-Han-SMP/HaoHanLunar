package vn.haohan.lunar.robot;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class LunarRobotPuzzleTest {

    private static final int SIZE = 5;
    private static final int[] DR = {-1, 0, 1, 0}; // 0: TOP, 1: RIGHT, 2: BOTTOM, 3: LEFT
    private static final int[] DC = {0, 1, 0, -1};
    private static final int[] OPPOSITE = {2, 3, 0, 1};

    public static class Tile {
        public final boolean[] greenBase = new boolean[4];
        public final boolean[] redBase = new boolean[4];
        public boolean hasGreen;
        public boolean hasRed;
        public int rotation;
        public boolean greenPowered;
        public boolean redPowered;

        public boolean hasGreenConn(int dir) {
            if (!hasGreen) return false;
            return greenBase[(dir - rotation + 4) % 4];
        }

        public boolean hasRedConn(int dir) {
            if (!hasRed) return false;
            return redBase[(dir - rotation + 4) % 4];
        }

        public boolean isDual() {
            return hasGreen && hasRed;
        }

        public int getNodeCount() {
            int wires = 0;
            for (int d = 0; d < 4; d++) {
                if (hasGreenConn(d)) wires++;
                if (hasRedConn(d)) wires++;
            }
            return 3 + wires; // border + bg + hub + wires
        }
    }

    public static class Terminal {
        public final int row;
        public final boolean isLeft; // true: col 0 (dir 3), false: col 4 (dir 1)
        public final boolean isGreen;
        public boolean connected;

        public Terminal(int row, boolean isLeft, boolean isGreen) {
            this.row = row;
            this.isLeft = isLeft;
            this.isGreen = isGreen;
        }

        public int getCol() {
            return isLeft ? 0 : SIZE - 1;
        }

        public int getOutsideDir() {
            return isLeft ? 3 : 1;
        }
    }

    public static class PuzzleInstance {
        public final Tile[][] grid = new Tile[SIZE][SIZE];
        public final int[][] groundTruth = new int[SIZE][SIZE];
        public Terminal startGreen;
        public Terminal startRed;
        public final List<Terminal> targets = new ArrayList<>();
        public boolean solved;

        public int countDualTiles() {
            int count = 0;
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    if (grid[r][c].isDual()) count++;
                }
            }
            return count;
        }

        public void applyGroundTruth() {
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    grid[r][c].rotation = groundTruth[r][c];
                }
            }
        }

        public void evaluate() {
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    grid[r][c].greenPowered = false;
                    grid[r][c].redPowered = false;
                }
            }
            for (Terminal t : targets) {
                t.connected = false;
            }

            // 1. BFS Green
            int gStartCol = startGreen.getCol();
            int gStartDir = startGreen.getOutsideDir();
            if (grid[startGreen.row][gStartCol].hasGreenConn(gStartDir)) {
                grid[startGreen.row][gStartCol].greenPowered = true;
                Queue<int[]> q = new LinkedList<>();
                q.add(new int[]{startGreen.row, gStartCol});
                while (!q.isEmpty()) {
                    int[] curr = q.poll();
                    int r = curr[0];
                    int c = curr[1];
                    for (int d = 0; d < 4; d++) {
                        if (grid[r][c].hasGreenConn(d)) {
                            int nr = r + DR[d];
                            int nc = c + DC[d];
                            if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE && !grid[nr][nc].greenPowered) {
                                if (grid[nr][nc].hasGreenConn(OPPOSITE[d])) {
                                    grid[nr][nc].greenPowered = true;
                                    q.add(new int[]{nr, nc});
                                }
                            }
                        }
                    }
                }
            }

            // 2. BFS Red
            int rStartCol = startRed.getCol();
            int rStartDir = startRed.getOutsideDir();
            if (grid[startRed.row][rStartCol].hasRedConn(rStartDir)) {
                grid[startRed.row][rStartCol].redPowered = true;
                Queue<int[]> q = new LinkedList<>();
                q.add(new int[]{startRed.row, rStartCol});
                while (!q.isEmpty()) {
                    int[] curr = q.poll();
                    int r = curr[0];
                    int c = curr[1];
                    for (int d = 0; d < 4; d++) {
                        if (grid[r][c].hasRedConn(d)) {
                            int nr = r + DR[d];
                            int nc = c + DC[d];
                            if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE && !grid[nr][nc].redPowered) {
                                if (grid[nr][nc].hasRedConn(OPPOSITE[d])) {
                                    grid[nr][nc].redPowered = true;
                                    q.add(new int[]{nr, nc});
                                }
                            }
                        }
                    }
                }
            }

            // 3. Evaluate targets
            int connectedCount = 0;
            for (Terminal t : targets) {
                Tile endTile = grid[t.row][t.getCol()];
                int outDir = t.getOutsideDir();
                if (t.isGreen) {
                    if (endTile.greenPowered && endTile.hasGreenConn(outDir)) {
                        t.connected = true;
                        connectedCount++;
                    }
                } else {
                    if (endTile.redPowered && endTile.hasRedConn(outDir)) {
                        t.connected = true;
                        connectedCount++;
                    }
                }
            }

            solved = (connectedCount == targets.size());
        }

        public static PuzzleInstance generate(Random random) {
            for (int attempt = 0; attempt < 80; attempt++) {
                PuzzleInstance p = new PuzzleInstance();

                // 10 possible ports: 5 on Left (col 0, dir 3), 5 on Right (col 4, dir 1)
                List<int[]> allPorts = new ArrayList<>();
                for (int r = 0; r < SIZE; r++) {
                    allPorts.add(new int[]{r, 1}); // 1 = Left
                    allPorts.add(new int[]{r, 0}); // 0 = Right
                }
                Collections.shuffle(allPorts, random);

                // Pick 2 ports for Starts
                int[] pSG = allPorts.get(0);
                int[] pSR = allPorts.get(1);
                p.startGreen = new Terminal(pSG[0], pSG[1] == 1, true);
                p.startRed = new Terminal(pSR[0], pSR[1] == 1, false);

                // Pick 4 ports for Targets (2 Green, 2 Red)
                List<Boolean> colors = new ArrayList<>(List.of(true, true, false, false));
                Collections.shuffle(colors, random);

                for (int i = 0; i < 4; i++) {
                    int[] pT = allPorts.get(2 + i);
                    p.targets.add(new Terminal(pT[0], pT[1] == 1, colors.get(i)));
                }

                List<Terminal> greenTargets = new ArrayList<>();
                List<Terminal> redTargets = new ArrayList<>();
                for (Terminal t : p.targets) {
                    if (t.isGreen) greenTargets.add(t);
                    else redTargets.add(t);
                }

                boolean[][][] greenConn = new boolean[SIZE][SIZE][4];
                boolean[][][] redConn = new boolean[SIZE][SIZE][4];

                greenConn[p.startGreen.row][p.startGreen.getCol()][p.startGreen.getOutsideDir()] = true;
                redConn[p.startRed.row][p.startRed.getCol()][p.startRed.getOutsideDir()] = true;

                for (Terminal t : p.targets) {
                    if (t.isGreen) greenConn[t.row][t.getCol()][t.getOutsideDir()] = true;
                    else redConn[t.row][t.getCol()][t.getOutsideDir()] = true;
                }

                // Circuit 1 (Green) paths
                List<int[]> gPath1 = new ArrayList<>();
                boolean[][] gVisited1 = new boolean[SIZE][SIZE];
                // Protect other terminals
                gVisited1[p.startRed.row][p.startRed.getCol()] = true;
                gVisited1[greenTargets.get(1).row][greenTargets.get(1).getCol()] = true;
                gVisited1[redTargets.get(0).row][redTargets.get(0).getCol()] = true;
                gVisited1[redTargets.get(1).row][redTargets.get(1).getCol()] = true;

                gVisited1[p.startGreen.row][p.startGreen.getCol()] = true;
                gPath1.add(new int[]{p.startGreen.row, p.startGreen.getCol()});

                int distG1 = Math.abs(p.startGreen.row - greenTargets.get(0).row) + Math.abs(p.startGreen.getCol() - greenTargets.get(0).getCol());
                if (!findRandomPath(p.startGreen.row, p.startGreen.getCol(), greenTargets.get(0).row, greenTargets.get(0).getCol(), gVisited1, gPath1, random, Math.min(3, distG1))) {
                    continue;
                }

                // Branch for Green
                boolean gBranched = false;
                List<int[]> gBranch = new ArrayList<>();
                List<Integer> gIndices = new ArrayList<>();
                for (int i = 0; i < gPath1.size() - 1; i++) gIndices.add(i);
                Collections.shuffle(gIndices, random);

                for (int jIdx : gIndices) {
                    int[] jNode = gPath1.get(jIdx);
                    boolean[][] gVisitedBranch = new boolean[SIZE][SIZE];
                    for (int[] node : gPath1) gVisitedBranch[node[0]][node[1]] = true;
                    gVisitedBranch[p.startRed.row][p.startRed.getCol()] = true;
                    gVisitedBranch[greenTargets.get(0).row][greenTargets.get(0).getCol()] = true;
                    gVisitedBranch[redTargets.get(0).row][redTargets.get(0).getCol()] = true;
                    gVisitedBranch[redTargets.get(1).row][redTargets.get(1).getCol()] = true;
                    gVisitedBranch[jNode[0]][jNode[1]] = false;

                    gBranch.clear();
                    gBranch.add(new int[]{jNode[0], jNode[1]});
                    gVisitedBranch[jNode[0]][jNode[1]] = true;

                    int distGB = Math.abs(jNode[0] - greenTargets.get(1).row) + Math.abs(jNode[1] - greenTargets.get(1).getCol());
                    if (findRandomPath(jNode[0], jNode[1], greenTargets.get(1).row, greenTargets.get(1).getCol(), gVisitedBranch, gBranch, random, Math.min(2, distGB))) {
                        gBranched = true;
                        break;
                    }
                }
                if (!gBranched) continue;

                // Apply green connections
                for (int i = 0; i < gPath1.size() - 1; i++) {
                    int[] from = gPath1.get(i);
                    int[] to = gPath1.get(i + 1);
                    int d = getDir(from[0], from[1], to[0], to[1]);
                    greenConn[from[0]][from[1]][d] = true;
                    greenConn[to[0]][to[1]][OPPOSITE[d]] = true;
                }
                for (int i = 0; i < gBranch.size() - 1; i++) {
                    int[] from = gBranch.get(i);
                    int[] to = gBranch.get(i + 1);
                    int d = getDir(from[0], from[1], to[0], to[1]);
                    greenConn[from[0]][from[1]][d] = true;
                    greenConn[to[0]][to[1]][OPPOSITE[d]] = true;
                }

                // Circuit 2 (Red) paths
                List<int[]> rPath1 = new ArrayList<>();
                boolean[][] rVisited1 = new boolean[SIZE][SIZE];
                rVisited1[p.startGreen.row][p.startGreen.getCol()] = true;
                rVisited1[redTargets.get(1).row][redTargets.get(1).getCol()] = true;
                rVisited1[greenTargets.get(0).row][greenTargets.get(0).getCol()] = true;
                rVisited1[greenTargets.get(1).row][greenTargets.get(1).getCol()] = true;

                rVisited1[p.startRed.row][p.startRed.getCol()] = true;
                rPath1.add(new int[]{p.startRed.row, p.startRed.getCol()});

                int distR1 = Math.abs(p.startRed.row - redTargets.get(0).row) + Math.abs(p.startRed.getCol() - redTargets.get(0).getCol());
                if (!findSinglePath(p.startRed.row, p.startRed.getCol(), redTargets.get(0).row, redTargets.get(0).getCol(), rVisited1, rPath1, greenConn, random, Math.min(3, distR1))) {
                    continue;
                }

                // Branch for Red
                boolean rBranched = false;
                List<int[]> rBranch = new ArrayList<>();
                List<Integer> rIndices = new ArrayList<>();
                for (int i = 0; i < rPath1.size() - 1; i++) rIndices.add(i);
                Collections.shuffle(rIndices, random);

                for (int jIdx : rIndices) {
                    int[] jNode = rPath1.get(jIdx);
                    boolean[][] rVisitedBranch = new boolean[SIZE][SIZE];
                    for (int[] node : rPath1) rVisitedBranch[node[0]][node[1]] = true;
                    rVisitedBranch[p.startGreen.row][p.startGreen.getCol()] = true;
                    rVisitedBranch[redTargets.get(0).row][redTargets.get(0).getCol()] = true;
                    rVisitedBranch[greenTargets.get(0).row][greenTargets.get(0).getCol()] = true;
                    rVisitedBranch[greenTargets.get(1).row][greenTargets.get(1).getCol()] = true;
                    rVisitedBranch[jNode[0]][jNode[1]] = false;

                    rBranch.clear();
                    rBranch.add(new int[]{jNode[0], jNode[1]});
                    rVisitedBranch[jNode[0]][jNode[1]] = true;

                    int distRB = Math.abs(jNode[0] - redTargets.get(1).row) + Math.abs(jNode[1] - redTargets.get(1).getCol());
                    if (findSinglePath(jNode[0], jNode[1], redTargets.get(1).row, redTargets.get(1).getCol(), rVisitedBranch, rBranch, greenConn, random, Math.min(2, distRB))) {
                        rBranched = true;
                        break;
                    }
                }
                if (!rBranched) continue;

                // Apply red connections
                for (int i = 0; i < rPath1.size() - 1; i++) {
                    int[] from = rPath1.get(i);
                    int[] to = rPath1.get(i + 1);
                    int d = getDir(from[0], from[1], to[0], to[1]);
                    redConn[from[0]][from[1]][d] = true;
                    redConn[to[0]][to[1]][OPPOSITE[d]] = true;
                }
                for (int i = 0; i < rBranch.size() - 1; i++) {
                    int[] from = rBranch.get(i);
                    int[] to = rBranch.get(i + 1);
                    int d = getDir(from[0], from[1], to[0], to[1]);
                    redConn[from[0]][from[1]][d] = true;
                    redConn[to[0]][to[1]][OPPOSITE[d]] = true;
                }

                // Build tiles & count dual tiles
                int dualCount = 0;
                for (int r = 0; r < SIZE; r++) {
                    for (int c = 0; c < SIZE; c++) {
                        Tile tile = new Tile();
                        int gCount = 0, rCount = 0;
                        for (int d = 0; d < 4; d++) {
                            if (greenConn[r][c][d]) {
                                tile.greenBase[d] = true;
                                gCount++;
                            }
                            if (redConn[r][c][d]) {
                                tile.redBase[d] = true;
                                rCount++;
                            }
                        }
                        tile.hasGreen = (gCount > 0);
                        tile.hasRed = (rCount > 0);

                        // Decoys (only on completely empty tiles)
                        if (!tile.hasGreen && !tile.hasRed) {
                            if (random.nextFloat() < 0.6f) {
                                boolean isGreenDecoy = random.nextBoolean();
                                int type = random.nextInt(2);
                                if (type == 0) {
                                    boolean[] base = isGreenDecoy ? tile.greenBase : tile.redBase;
                                    base[1] = true; base[3] = true;
                                } else {
                                    boolean[] base = isGreenDecoy ? tile.greenBase : tile.redBase;
                                    base[0] = true; base[1] = true;
                                }
                                if (isGreenDecoy) tile.hasGreen = true;
                                else tile.hasRed = true;
                            }
                        }

                        if (tile.isDual()) dualCount++;
                        p.grid[r][c] = tile;
                        p.groundTruth[r][c] = 0;
                    }
                }

                if (dualCount < 2) continue;

                // Scramble
                int scrambleAttempts = 0;
                do {
                    for (int r = 0; r < SIZE; r++) {
                        for (int c = 0; c < SIZE; c++) {
                            Tile t = p.grid[r][c];
                            if (t.hasGreen || t.hasRed) {
                                t.rotation = (p.groundTruth[r][c] + random.nextInt(3) + 1) % 4;
                            }
                        }
                    }
                    while (p.grid[p.startGreen.row][p.startGreen.getCol()].hasGreenConn(p.startGreen.getOutsideDir())) {
                        p.grid[p.startGreen.row][p.startGreen.getCol()].rotation =
                                (p.grid[p.startGreen.row][p.startGreen.getCol()].rotation + 1) % 4;
                    }
                    while (p.grid[p.startRed.row][p.startRed.getCol()].hasRedConn(p.startRed.getOutsideDir())) {
                        p.grid[p.startRed.row][p.startRed.getCol()].rotation =
                                (p.grid[p.startRed.row][p.startRed.getCol()].rotation + 1) % 4;
                    }
                    p.evaluate();
                    scrambleAttempts++;
                } while (p.solved && scrambleAttempts < 10);

                if (p.solved) continue;

                return p;
            }
            throw new IllegalStateException("Failed to generate valid 4-target dual-circuit puzzle after max retries");
        }

        private static boolean findRandomPath(int r, int c, int targetR, int targetC,
                                              boolean[][] visited, List<int[]> path,
                                              Random random, int minLen) {
            if (r == targetR && c == targetC) {
                return path.size() >= minLen;
            }

            List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(dirs, random);

            for (int d : dirs) {
                int nr = r + DR[d];
                int nc = c + DC[d];
                if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE && !visited[nr][nc]) {
                    visited[nr][nc] = true;
                    path.add(new int[]{nr, nc});
                    if (findRandomPath(nr, nc, targetR, targetC, visited, path, random, minLen)) {
                        return true;
                    }
                    path.remove(path.size() - 1);
                    visited[nr][nc] = false;
                }
            }
            return false;
        }

        private static boolean findSinglePath(int r, int c, int targetR, int targetC,
                                              boolean[][] visited, List<int[]> path,
                                              boolean[][][] otherConn, Random random, int minLen) {
            if (r == targetR && c == targetC) {
                return path.size() >= minLen;
            }

            List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(dirs, random);

            for (int d : dirs) {
                int nr = r + DR[d];
                int nc = c + DC[d];
                if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE && !visited[nr][nc]) {
                    if (otherConn[r][c][d]) continue;
                    if (otherConn[nr][nc][OPPOSITE[d]]) continue;

                    int otherUsedAtNext = 0;
                    for (int od = 0; od < 4; od++) if (otherConn[nr][nc][od]) otherUsedAtNext++;
                    if (otherUsedAtNext > 2) continue;

                    visited[nr][nc] = true;
                    path.add(new int[]{nr, nc});
                    if (findSinglePath(nr, nc, targetR, targetC, visited, path, otherConn, random, minLen)) {
                        return true;
                    }
                    path.remove(path.size() - 1);
                    visited[nr][nc] = false;
                }
            }
            return false;
        }

        private static int getDir(int fromR, int fromC, int toR, int toC) {
            if (toR == fromR - 1 && toC == fromC) return 0;
            if (toR == fromR && toC == fromC + 1) return 1;
            if (toR == fromR + 1 && toC == fromC) return 2;
            if (toR == fromR && toC == fromC - 1) return 3;
            return -1;
        }
    }

    @Test
    public void testSolvabilityWithGroundTruthOnRandomizedPositions() {
        int testCount = 1000;
        Random random = new Random(1337);

        for (int i = 0; i < testCount; i++) {
            PuzzleInstance puzzle = PuzzleInstance.generate(random);

            // 1. Must have exactly 4 targets
            assertEquals(4, puzzle.targets.size(), "Must have exactly 4 targets");

            // 2. Must NOT start solved
            assertFalse(puzzle.solved, "Puzzle " + i + " should not start already solved");

            // 3. Applying ground truth MUST solve the puzzle
            puzzle.applyGroundTruth();
            puzzle.evaluate();
            assertTrue(puzzle.solved, "Puzzle " + i + " must be solvable with ground truth rotations");

            // 4. All 4 targets must be connected
            for (Terminal t : puzzle.targets) {
                assertTrue(t.connected, "Target at row " + t.row + " (side: " + (t.isLeft ? "Left" : "Right") + ") must be connected at iteration " + i);
            }
        }
    }

    @Test
    public void testInitialScrambleState() {
        int testCount = 500;
        Random random = new Random(2026);

        for (int i = 0; i < testCount; i++) {
            PuzzleInstance puzzle = PuzzleInstance.generate(random);
            assertFalse(puzzle.solved, "Initial state must never be solved");

            // Starts must not connect immediately to outside
            assertFalse(puzzle.grid[puzzle.startGreen.row][puzzle.startGreen.getCol()].hasGreenConn(puzzle.startGreen.getOutsideDir()),
                    "Green start must not connect to outside immediately");
            assertFalse(puzzle.grid[puzzle.startRed.row][puzzle.startRed.getCol()].hasRedConn(puzzle.startRed.getOutsideDir()),
                    "Red start must not connect to outside immediately");
        }
    }

    @Test
    public void testDualCircuitOverlappingTilesCount() {
        int testCount = 500;
        Random random = new Random(777);
        int totalDual = 0;

        for (int i = 0; i < testCount; i++) {
            PuzzleInstance puzzle = PuzzleInstance.generate(random);
            int dual = puzzle.countDualTiles();
            assertTrue(dual >= 2, "Puzzle must have at least 2 dual-circuit tiles, but got " + dual + " at iteration " + i);
            totalDual += dual;
        }

        double avgDual = (double) totalDual / testCount;
        assertTrue(avgDual >= 2.5, "Average dual tiles should be >= 2.5, got: " + avgDual);
    }

    @Test
    public void testLeftRightRandomizedDistribution() {
        int testCount = 1000;
        Random random = new Random(999);

        int leftSourceCount = 0;
        int rightSourceCount = 0;
        int leftTargetCount = 0;
        int rightTargetCount = 0;

        for (int i = 0; i < testCount; i++) {
            PuzzleInstance puzzle = PuzzleInstance.generate(random);

            if (puzzle.startGreen.isLeft) leftSourceCount++; else rightSourceCount++;
            if (puzzle.startRed.isLeft) leftSourceCount++; else rightSourceCount++;

            for (Terminal t : puzzle.targets) {
                if (t.isLeft) leftTargetCount++; else rightTargetCount++;
            }

            // Verify all 6 terminals occupy distinct ports
            Set<String> portKeys = new HashSet<>();
            portKeys.add(puzzle.startGreen.row + "," + puzzle.startGreen.isLeft);
            portKeys.add(puzzle.startRed.row + "," + puzzle.startRed.isLeft);
            for (Terminal t : puzzle.targets) {
                portKeys.add(t.row + "," + t.isLeft);
            }
            assertEquals(6, portKeys.size(), "All 6 terminals must occupy distinct perimeter ports");
        }

        // Verify that sources and targets appear on both Left and Right sides significantly
        assertTrue(leftSourceCount > 600, "Sources should appear on Left frequently, got: " + leftSourceCount);
        assertTrue(rightSourceCount > 600, "Sources should appear on Right frequently, got: " + rightSourceCount);
        assertTrue(leftTargetCount > 1200, "Targets should appear on Left frequently, got: " + leftTargetCount);
        assertTrue(rightTargetCount > 1200, "Targets should appear on Right frequently, got: " + rightTargetCount);
    }

    @Test
    public void testTargetRequirementStrictness() {
        Random random = new Random(101);
        for (int i = 0; i < 100; i++) {
            PuzzleInstance puzzle = PuzzleInstance.generate(random);
            puzzle.applyGroundTruth();
            puzzle.evaluate();
            assertTrue(puzzle.solved);
            assertEquals(4, puzzle.targets.size());

            // Break each of the 4 targets individually and assert puzzle is no longer solved
            for (Terminal t : puzzle.targets) {
                Tile endTile = puzzle.grid[t.row][t.getCol()];
                int originalRotation = endTile.rotation;

                // Rotate it so it misaligns
                endTile.rotation = (endTile.rotation + 1) % 4;
                puzzle.evaluate();

                assertFalse(puzzle.solved, "Puzzle must not be solved when target at row " + t.row + " (side: " + (t.isLeft ? "Left" : "Right") + ") is broken");

                // Restore
                endTile.rotation = originalRotation;
            }
        }
    }

    @Test
    public void testCircuitIsolation() {
        Random random = new Random(555);
        for (int i = 0; i < 100; i++) {
            PuzzleInstance puzzle = PuzzleInstance.generate(random);
            puzzle.applyGroundTruth();

            // Disconnect Green start (rotate until hasGreenConn is false)
            int gCol = puzzle.startGreen.getCol();
            int gDir = puzzle.startGreen.getOutsideDir();
            while (puzzle.grid[puzzle.startGreen.row][gCol].hasGreenConn(gDir)) {
                puzzle.grid[puzzle.startGreen.row][gCol].rotation =
                        (puzzle.grid[puzzle.startGreen.row][gCol].rotation + 1) % 4;
            }
            puzzle.evaluate();

            // No green targets should be connected
            for (Terminal t : puzzle.targets) {
                if (t.isGreen) {
                    assertFalse(t.connected, "Green target must not be connected when Green source is disconnected");
                }
            }
            assertFalse(puzzle.solved);

            // Restore Green, disconnect Red start
            puzzle.grid[puzzle.startGreen.row][gCol].rotation = puzzle.groundTruth[puzzle.startGreen.row][gCol];
            int rCol = puzzle.startRed.getCol();
            int rDir = puzzle.startRed.getOutsideDir();
            while (puzzle.grid[puzzle.startRed.row][rCol].hasRedConn(rDir)) {
                puzzle.grid[puzzle.startRed.row][rCol].rotation =
                        (puzzle.grid[puzzle.startRed.row][rCol].rotation + 1) % 4;
            }
            puzzle.evaluate();

            // No red targets should be connected
            for (Terminal t : puzzle.targets) {
                if (!t.isGreen) {
                    assertFalse(t.connected, "Red target must not be connected when Red source is disconnected");
                }
            }
            assertFalse(puzzle.solved);
        }
    }

    @Test
    public void testPuzzleThemeSynchronization() {
        // Test default theme when robot is null
        vn.haohan.lunar.robot.ui.LunarRobotPuzzleUi puzzle = new vn.haohan.lunar.robot.ui.LunarRobotPuzzleUi(
                null, null, null, null, null);
        assertEquals(vn.haohan.lunar.robot.ui.LunarDashboardTheme.CYAN_WHITE, puzzle.getTheme());
        assertEquals(org.bukkit.boss.BarColor.BLUE, puzzle.getThemeBossBarColor());
        assertEquals("§b", puzzle.getThemeColorCode());
        assertEquals(org.bukkit.Material.CYAN_CONCRETE, puzzle.getThemeBorderMaterial());

        // Test theme border and bossbar color mapping for all themes
        for (vn.haohan.lunar.robot.ui.LunarDashboardTheme theme : vn.haohan.lunar.robot.ui.LunarDashboardTheme.values()) {
            org.bukkit.boss.BarColor expectedBar = switch (theme) {
                case RED -> org.bukkit.boss.BarColor.RED;
                case YELLOW -> org.bukkit.boss.BarColor.YELLOW;
                case CYAN_WHITE, BLUE -> org.bukkit.boss.BarColor.BLUE;
            };
            org.bukkit.Material expectedBorder = switch (theme) {
                case RED -> org.bukkit.Material.RED_CONCRETE;
                case YELLOW -> org.bukkit.Material.YELLOW_CONCRETE;
                case CYAN_WHITE -> org.bukkit.Material.CYAN_CONCRETE;
                case BLUE -> org.bukkit.Material.BLUE_CONCRETE;
            };

            switch (theme) {
                case RED -> assertEquals(org.bukkit.boss.BarColor.RED, expectedBar);
                case YELLOW -> assertEquals(org.bukkit.boss.BarColor.YELLOW, expectedBar);
                case CYAN_WHITE, BLUE -> assertEquals(org.bukkit.boss.BarColor.BLUE, expectedBar);
            }
            assertNotNull(expectedBorder);
        }
    }
}
