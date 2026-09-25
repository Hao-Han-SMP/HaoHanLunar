package vn.haohan.lunar.robot.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import vn.haohan.displayui.api.DisplayUiService;
import vn.haohan.displayui.api.UiDocument;
import vn.haohan.displayui.api.UiHandle;
import vn.haohan.displayui.api.UiOptions;
import vn.haohan.displayui.api.view.UiFollowOptions;
import vn.haohan.displayui.api.animation.Easings;
import vn.haohan.displayui.api.animation.UiEffects;
import vn.haohan.displayui.api.interaction.UiButton;
import vn.haohan.displayui.api.interaction.UiButtonAction;
import vn.haohan.displayui.api.layout.UiCameraTransform;
import vn.haohan.displayui.api.node.AlignedTextNode;
import vn.haohan.displayui.api.node.BlockNode;
import vn.haohan.displayui.api.text.UiTextAlignment;
import vn.haohan.lunar.robot.LunarRobotEntity;
import vn.haohan.lunar.robot.LunarRobotMechanic;

import java.lang.reflect.Field;
import java.util.*;

public class LunarRobotPuzzleUi {
    private final Plugin plugin;
    private final DisplayUiService uiService;
    private final LunarRobotMechanic mechanic;
    private final Player player;
    private final LunarRobotEntity robot;
    private final LunarDashboardTheme theme;

    private UiHandle handle;
    private BossBar bossBar;
    private final int size = 5;
    private final Tile[][] grid = new Tile[size][size];
    private final int[][] groundTruthRotations = new int[size][size];
    private Terminal startGreen;
    private Terminal startRed;
    private final List<Terminal> targets = new ArrayList<>();
    private boolean solved = false;

    private static final int[] DR = {-1, 0, 1, 0}; // 0: TOP, 1: RIGHT, 2: BOTTOM, 3: LEFT
    private static final int[] DC = {0, 1, 0, -1};
    private static final int[] OPPOSITE = {2, 3, 0, 1};

    public static class Tile {
        final boolean[] greenBase = new boolean[4];
        final boolean[] redBase = new boolean[4];
        boolean hasGreen;
        boolean hasRed;
        int rotation; // 0..3 (each is 90 deg clockwise)
        boolean greenPowered;
        boolean redPowered;

        public boolean hasGreenConnection(int dir) {
            if (!hasGreen) return false;
            return greenBase[(dir - rotation + 4) % 4];
        }

        public boolean hasRedConnection(int dir) {
            if (!hasRed) return false;
            return redBase[(dir - rotation + 4) % 4];
        }

        public boolean isDual() {
            return hasGreen && hasRed;
        }

        public int getNodeCount() {
            int wires = 0;
            for (int d = 0; d < 4; d++) {
                if (hasGreenConnection(d)) wires++;
                if (hasRedConnection(d)) wires++;
            }
            return 3 + wires; // 2 base (border + inner bg) + 1 center hub + directional wires
        }
    }

    public static class Terminal {
        final int row;
        final boolean isLeft; // true: col 0 (dir 3), false: col 4 (dir 1)
        final boolean isGreen;
        boolean connected;

        public Terminal(int row, boolean isLeft, boolean isGreen) {
            this.row = row;
            this.isLeft = isLeft;
            this.isGreen = isGreen;
        }

        public int getCol() {
            return isLeft ? 0 : 4;
        }

        public int getOutsideDir() {
            return isLeft ? 3 : 1;
        }
    }

    public LunarRobotPuzzleUi(Plugin plugin, DisplayUiService uiService, LunarRobotMechanic mechanic,
                              Player player, LunarRobotEntity robot) {
        this.plugin = plugin;
        this.uiService = uiService;
        this.mechanic = mechanic;
        this.player = player;
        this.robot = robot;
        this.theme = (robot != null && robot.getData() != null && robot.getData().getColorTheme() != null)
                ? robot.getData().getColorTheme()
                : LunarDashboardTheme.CYAN_WHITE;
        generatePuzzle();
    }

    public LunarDashboardTheme getTheme() {
        return theme;
    }

    private void generatePuzzle() {
        Random random = new Random();

        for (int attempt = 0; attempt < 80; attempt++) {
            targets.clear();

            // 10 possible ports: 5 on Left (col 0, dir 3), 5 on Right (col 4, dir 1)
            List<int[]> allPorts = new ArrayList<>();
            for (int r = 0; r < size; r++) {
                allPorts.add(new int[]{r, 1}); // 1 = Left
                allPorts.add(new int[]{r, 0}); // 0 = Right
            }
            Collections.shuffle(allPorts, random);

            // 1. Pick 2 ports for Starts (Green & Red)
            int[] pSG = allPorts.get(0);
            int[] pSR = allPorts.get(1);
            startGreen = new Terminal(pSG[0], pSG[1] == 1, true);
            startRed = new Terminal(pSR[0], pSR[1] == 1, false);

            // 2. Pick 4 ports for Targets (2 Green, 2 Red)
            List<Boolean> colors = new ArrayList<>(List.of(true, true, false, false));
            Collections.shuffle(colors, random);

            for (int i = 0; i < 4; i++) {
                int[] pT = allPorts.get(2 + i);
                targets.add(new Terminal(pT[0], pT[1] == 1, colors.get(i)));
            }

            List<Terminal> greenTargets = new ArrayList<>();
            List<Terminal> redTargets = new ArrayList<>();
            for (Terminal t : targets) {
                if (t.isGreen) greenTargets.add(t);
                else redTargets.add(t);
            }

            boolean[][][] greenConn = new boolean[size][size][4];
            boolean[][][] redConn = new boolean[size][size][4];

            greenConn[startGreen.row][startGreen.getCol()][startGreen.getOutsideDir()] = true;
            redConn[startRed.row][startRed.getCol()][startRed.getOutsideDir()] = true;

            for (Terminal t : targets) {
                if (t.isGreen) greenConn[t.row][t.getCol()][t.getOutsideDir()] = true;
                else redConn[t.row][t.getCol()][t.getOutsideDir()] = true;
            }

            // Circuit 1 (Green) paths
            List<int[]> gPath1 = new ArrayList<>();
            boolean[][] gVisited1 = new boolean[size][size];
            gVisited1[startRed.row][startRed.getCol()] = true;
            gVisited1[greenTargets.get(1).row][greenTargets.get(1).getCol()] = true;
            gVisited1[redTargets.get(0).row][redTargets.get(0).getCol()] = true;
            gVisited1[redTargets.get(1).row][redTargets.get(1).getCol()] = true;

            gVisited1[startGreen.row][startGreen.getCol()] = true;
            gPath1.add(new int[]{startGreen.row, startGreen.getCol()});

            int distG1 = Math.abs(startGreen.row - greenTargets.get(0).row) + Math.abs(startGreen.getCol() - greenTargets.get(0).getCol());
            if (!findRandomPath(startGreen.row, startGreen.getCol(), greenTargets.get(0).row, greenTargets.get(0).getCol(), gVisited1, gPath1, random, Math.min(3, distG1))) {
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
                boolean[][] gVisitedBranch = new boolean[size][size];
                for (int[] node : gPath1) gVisitedBranch[node[0]][node[1]] = true;
                gVisitedBranch[startRed.row][startRed.getCol()] = true;
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
                int d = getDirection(from[0], from[1], to[0], to[1]);
                greenConn[from[0]][from[1]][d] = true;
                greenConn[to[0]][to[1]][OPPOSITE[d]] = true;
            }
            for (int i = 0; i < gBranch.size() - 1; i++) {
                int[] from = gBranch.get(i);
                int[] to = gBranch.get(i + 1);
                int d = getDirection(from[0], from[1], to[0], to[1]);
                greenConn[from[0]][from[1]][d] = true;
                greenConn[to[0]][to[1]][OPPOSITE[d]] = true;
            }

            // Circuit 2 (Red) paths
            List<int[]> rPath1 = new ArrayList<>();
            boolean[][] rVisited1 = new boolean[size][size];
            rVisited1[startGreen.row][startGreen.getCol()] = true;
            rVisited1[redTargets.get(1).row][redTargets.get(1).getCol()] = true;
            rVisited1[greenTargets.get(0).row][greenTargets.get(0).getCol()] = true;
            rVisited1[greenTargets.get(1).row][greenTargets.get(1).getCol()] = true;

            rVisited1[startRed.row][startRed.getCol()] = true;
            rPath1.add(new int[]{startRed.row, startRed.getCol()});

            int distR1 = Math.abs(startRed.row - redTargets.get(0).row) + Math.abs(startRed.getCol() - redTargets.get(0).getCol());
            if (!findSinglePath(startRed.row, startRed.getCol(), redTargets.get(0).row, redTargets.get(0).getCol(), rVisited1, rPath1, greenConn, random, Math.min(3, distR1))) {
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
                boolean[][] rVisitedBranch = new boolean[size][size];
                for (int[] node : rPath1) rVisitedBranch[node[0]][node[1]] = true;
                rVisitedBranch[startGreen.row][startGreen.getCol()] = true;
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
                int d = getDirection(from[0], from[1], to[0], to[1]);
                redConn[from[0]][from[1]][d] = true;
                redConn[to[0]][to[1]][OPPOSITE[d]] = true;
            }
            for (int i = 0; i < rBranch.size() - 1; i++) {
                int[] from = rBranch.get(i);
                int[] to = rBranch.get(i + 1);
                int d = getDirection(from[0], from[1], to[0], to[1]);
                redConn[from[0]][from[1]][d] = true;
                redConn[to[0]][to[1]][OPPOSITE[d]] = true;
            }

            // Construct Tile objects
            int dualCount = 0;
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
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

                    // Add subtle decoys on completely empty tiles
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
                    grid[r][c] = tile;
                    groundTruthRotations[r][c] = 0;
                }
            }

            if (dualCount < 2) continue;

            // Scramble tiles so initial state is 100% NOT solved
            int scrambleAttempts = 0;
            do {
                solved = false;
                for (int r = 0; r < size; r++) {
                    for (int c = 0; c < size; c++) {
                        Tile t = grid[r][c];
                        if (t.hasGreen || t.hasRed) {
                            t.rotation = (groundTruthRotations[r][c] + random.nextInt(3) + 1) % 4;
                        }
                    }
                }
                while (grid[startGreen.row][startGreen.getCol()].hasGreenConnection(startGreen.getOutsideDir())) {
                    grid[startGreen.row][startGreen.getCol()].rotation =
                            (grid[startGreen.row][startGreen.getCol()].rotation + 1) % 4;
                }
                while (grid[startRed.row][startRed.getCol()].hasRedConnection(startRed.getOutsideDir())) {
                    grid[startRed.row][startRed.getCol()].rotation =
                            (grid[startRed.row][startRed.getCol()].rotation + 1) % 4;
                }
                evaluateCircuit();
                scrambleAttempts++;
            } while (solved && scrambleAttempts < 10);

            if (solved) continue;

            return; // Successfully generated!
        }
    }

    private boolean findRandomPath(int r, int c, int targetR, int targetC,
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
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && !visited[nr][nc]) {
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

    private boolean findSinglePath(int r, int c, int targetR, int targetC,
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
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && !visited[nr][nc]) {
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

    private int getDirection(int fromR, int fromC, int toR, int toC) {
        if (toR == fromR - 1 && toC == fromC) return 0; // TOP
        if (toR == fromR && toC == fromC + 1) return 1; // RIGHT
        if (toR == fromR + 1 && toC == fromC) return 2; // BOTTOM
        if (toR == fromR && toC == fromC - 1) return 3; // LEFT
        return -1;
    }

    public void open() {
        uiService.removeOwnedBy("haohanlunar:robot_puzzle/" + player.getUniqueId());

        Location origin = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(2.2));
        origin.setYaw(player.getEyeLocation().getYaw() + 180.0f);
        origin.setPitch(-player.getEyeLocation().getPitch());

        UiDocument doc = renderDocument();
        UiOptions options = new UiOptions(75.0f, 8.0, false, 1.0f, "haohan_robot_puzzle",
                UiCameraTransform.fixed());

        handle = uiService.create("haohanlunar:robot_puzzle/" + player.getUniqueId(), origin, doc, options,
                candidate -> candidate.getUniqueId().equals(player.getUniqueId()));

        handle.animate(UiEffects.scaleIn(0.65f, Easings.OutCubic));

        UiFollowOptions followOptions = UiFollowOptions.builder()
                .distance(2.2)
                .bounds(-98.0f, 98.0f, -80.0f, 106.0f)
                .build();
        handle.follow(player, followOptions);

        if (robot != null) {
            robot.setFrozen(true);
        }

        handle.onClick(click -> handleTileClick(click.button().id()));

        BarColor themeBarColor = getThemeBossBarColor();
        String themePrefix = getThemeColorCode() + "§l⚡ MẠCH KÉP ROBOT: ";

        // Create BossBar for progress tracking
        bossBar = Bukkit.createBossBar(
                themePrefix + "§fĐang khởi tạo các mạch điện...",
                themeBarColor,
                BarStyle.SEGMENTED_10
        );
        bossBar.addPlayer(player);
        updateBossBar();

        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
    }

    public BarColor getThemeBossBarColor() {
        return switch (theme) {
            case RED -> BarColor.RED;
            case YELLOW -> BarColor.YELLOW;
            case CYAN_WHITE, BLUE -> BarColor.BLUE;
        };
    }

    public String getThemeColorCode() {
        return switch (theme) {
            case RED -> "§c";
            case YELLOW -> "§e";
            case CYAN_WHITE -> "§b";
            case BLUE -> "§9";
        };
    }

    public Material getThemeBorderMaterial() {
        if (solved) {
            return Material.EMERALD_BLOCK;
        }
        return switch (theme) {
            case RED -> Material.RED_CONCRETE;
            case YELLOW -> Material.YELLOW_CONCRETE;
            case CYAN_WHITE -> Material.CYAN_CONCRETE;
            case BLUE -> Material.BLUE_CONCRETE;
        };
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        int connectedCount = 0;
        int greenCount = 0, greenTotal = 0;
        int redCount = 0, redTotal = 0;

        for (Terminal t : targets) {
            if (t.isGreen) {
                greenTotal++;
                if (t.connected) greenCount++;
            } else {
                redTotal++;
                if (t.connected) redCount++;
            }
            if (t.connected) connectedCount++;
        }

        float progress = (float) connectedCount / (float) targets.size();
        bossBar.setProgress(Math.max(0.05f, Math.min(1.0f, progress)));

        if (solved) {
            bossBar.setTitle("§a§l✔ KẾT NỐI 4 LÕI THÀNH CÔNG! ĐANG KHỞI ĐỘNG ROBOT...");
            bossBar.setColor(BarColor.GREEN);
            bossBar.setProgress(1.0);
        } else {
            String gStatus = (greenCount == greenTotal) ? "§a" + greenCount + "/" + greenTotal + "✔" : "§7" + greenCount + "/" + greenTotal;
            String rStatus = (redCount == redTotal) ? "§c" + redCount + "/" + redTotal + "✔" : "§7" + redCount + "/" + redTotal;
            bossBar.setTitle(getThemeColorCode() + "§l⚡ MẠCH KÉP ROBOT: §fĐã kết nối: §e" + connectedCount + "/4 Lõi §f• Xanh: " + gStatus + " §f| Đỏ: " + rStatus);
            bossBar.setColor(getThemeBossBarColor());
        }
    }

    private void handleTileClick(String buttonId) {
        if ("btn_close".equals(buttonId)) {
            close();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            player.sendMessage("§7[Robot] Đã đóng giao diện giải mã mạch.");
            return;
        }

        if (solved) return;
        if (!buttonId.startsWith("tile_")) return;

        String[] parts = buttonId.split("_");
        if (parts.length < 3) return;

        int row = Integer.parseInt(parts[1]);
        int col = Integer.parseInt(parts[2]);

        // Rotate tile 90 deg clockwise
        grid[row][col].rotation = (grid[row][col].rotation + 1) % 4;
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.7f, 1.6f);

        evaluateCircuit();
        updateBossBar();
        handle.update(renderDocument());
        applyTargetedTileInterpolation(row, col);

        if (solved) {
            onPuzzleSolved();
        }
    }

    private static Field nodeEntitiesField = null;
    private static boolean reflectionInitAttempted = false;

    @SuppressWarnings("unchecked")
    private List<List<Display>> getNodeEntities() {
        if (handle == null) return null;
        try {
            if (!reflectionInitAttempted) {
                reflectionInitAttempted = true;
                nodeEntitiesField = handle.getClass().getDeclaredField("nodeEntities");
                nodeEntitiesField.setAccessible(true);
            }
            if (nodeEntitiesField != null) {
                return (List<List<Display>>) nodeEntitiesField.get(handle);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int[] getTileNodeRange(int targetR, int targetC) {
        int startIndex = 5 + 6 + targets.size() * 3;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int count = grid[r][c].getNodeCount();
                if (r == targetR && c == targetC) {
                    return new int[]{startIndex, startIndex + count};
                }
                startIndex += count;
            }
        }
        return new int[]{startIndex, startIndex};
    }

    private void applyTargetedTileInterpolation(int row, int col) {
        List<List<Display>> nodeEntities = getNodeEntities();
        if (nodeEntities == null || nodeEntities.isEmpty()) return;

        int[] range = getTileNodeRange(row, col);
        int start = range[0];
        int end = range[1];

        for (int i = 0; i < nodeEntities.size(); i++) {
            List<Display> displays = nodeEntities.get(i);
            if (displays == null) continue;

            boolean isClickedTile = (i >= start && i < end);
            int duration = isClickedTile ? 4 : 0;

            for (Display display : displays) {
                if (display != null && display.isValid()) {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(duration);
                }
            }
        }
    }

    private void evaluateCircuit() {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                grid[r][c].greenPowered = false;
                grid[r][c].redPowered = false;
            }
        }
        for (Terminal t : targets) {
            t.connected = false;
        }

        // 1. BFS Green traversal from startGreen
        int gStartCol = startGreen.getCol();
        int gStartDir = startGreen.getOutsideDir();
        if (grid[startGreen.row][gStartCol].hasGreenConnection(gStartDir)) {
            grid[startGreen.row][gStartCol].greenPowered = true;
            Queue<int[]> queue = new LinkedList<>();
            queue.add(new int[]{startGreen.row, gStartCol});

            while (!queue.isEmpty()) {
                int[] curr = queue.poll();
                int r = curr[0];
                int c = curr[1];
                for (int d = 0; d < 4; d++) {
                    if (grid[r][c].hasGreenConnection(d)) {
                        int nr = r + DR[d];
                        int nc = c + DC[d];
                        if (nr >= 0 && nr < size && nc >= 0 && nc < size && !grid[nr][nc].greenPowered) {
                            if (grid[nr][nc].hasGreenConnection(OPPOSITE[d])) {
                                grid[nr][nc].greenPowered = true;
                                queue.add(new int[]{nr, nc});
                            }
                        }
                    }
                }
            }
        }

        // 2. BFS Red traversal from startRed
        int rStartCol = startRed.getCol();
        int rStartDir = startRed.getOutsideDir();
        if (grid[startRed.row][rStartCol].hasRedConnection(rStartDir)) {
            grid[startRed.row][rStartCol].redPowered = true;
            Queue<int[]> queue = new LinkedList<>();
            queue.add(new int[]{startRed.row, rStartCol});

            while (!queue.isEmpty()) {
                int[] curr = queue.poll();
                int r = curr[0];
                int c = curr[1];
                for (int d = 0; d < 4; d++) {
                    if (grid[r][c].hasRedConnection(d)) {
                        int nr = r + DR[d];
                        int nc = c + DC[d];
                        if (nr >= 0 && nr < size && nc >= 0 && nc < size && !grid[nr][nc].redPowered) {
                            if (grid[nr][nc].hasRedConnection(OPPOSITE[d])) {
                                grid[nr][nc].redPowered = true;
                                queue.add(new int[]{nr, nc});
                            }
                        }
                    }
                }
            }
        }

        // 3. Check target destinations
        int connectedCount = 0;
        for (Terminal t : targets) {
            Tile endTile = grid[t.row][t.getCol()];
            int outDir = t.getOutsideDir();
            if (t.isGreen) {
                if (endTile.greenPowered && endTile.hasGreenConnection(outDir)) {
                    t.connected = true;
                    connectedCount++;
                }
            } else {
                if (endTile.redPowered && endTile.hasRedConnection(outDir)) {
                    t.connected = true;
                    connectedCount++;
                }
            }
        }

        solved = (connectedCount == targets.size());
    }

    private void onPuzzleSolved() {
        updateBossBar();
        if (handle != null && handle.isValid()) {
            handle.animate(UiEffects.bounceIn());
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
        player.getWorld().spawnParticle(Particle.FIREWORK, robot.getEntity().getLocation().add(0, 1.2, 0), 25, 0.4, 0.4, 0.4, 0.05);

        // Bind robot to player
        mechanic.tameRobot(player, robot);

        // Close puzzle and open dashboard after 1.5s
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            close();
            mechanic.openRobotDashboard(player, robot);
        }, 30L);
    }

    public void tick() {
    }

    public void close() {
        if (robot != null) {
            robot.setFrozen(false);
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (handle != null) {
            handle.remove();
            handle = null;
        }
        mechanic.unregisterActivePuzzle(player.getUniqueId());
    }

    public Player getPlayer() {
        return player;
    }

    public LunarRobotEntity getRobot() {
        return robot;
    }

    private UiDocument renderDocument() {
        UiDocument.Builder b = UiDocument.builder();

        // 1. Outer Frame & High-tech Board (196 x 186)
        b.add(new BlockNode(Material.BLACK_CONCRETE.createBlockData(), -98, -80, 0.0005f, 196, 186, 1));
        b.add(new BlockNode(getThemeBorderMaterial().createBlockData(),
                -96, -78, 0.001f, 192, 182, 1));
        b.add(new BlockNode(Material.GRAY_CONCRETE.createBlockData(), -94, -76, 0.0015f, 188, 178, 1));

        // 2. Header Title & Instructions
        TextColor headerAccent = TextColor.color(theme.getAccentColor().asRGB());
        b.add(new AlignedTextNode(
                Component.text("⚡ BỘ GIẢI MÃ MẠCH KÉP (2 MẠCH XANH - ĐỎ) ⚡", headerAccent, TextDecoration.BOLD),
                -85, -72, 170, 10, UiTextAlignment.CENTER).fontSize(6).atDepth(0.015f).shadowed(true));

        String subText = solved ? "§a§l✔ KẾT NỐI 4 LÕI THÀNH CÔNG! ĐANG KHỞI ĐỘNG..."
                : "§eClick để xoay ô • Nối nguồn §a[XANH] §evà §c[ĐỎ] §evào §64 Lõi Năng Lượng";
        b.add(new AlignedTextNode(Component.text(subText), -85, -60, 170, 8, UiTextAlignment.CENTER).fontSize(4).atDepth(0.015f).shadowed(true));

        // 3. Grid Coordinates setup
        int tileSize = 24;
        int gap = 3;
        int totalSpan = size * tileSize + (size - 1) * gap; // 132
        int startX = -(totalSpan / 2); // -66
        int startY = -48;
        int wireW = 6;
        int halfW = wireW / 2;
        int halfTile = tileSize / 2;
        int endTileX = startX + (size - 1) * (tileSize + gap); // 42

        // START TERMINALS (Left or Right side dynamically)
        // Green Start Terminal
        int gStartY = startY + startGreen.row * (tileSize + gap);
        if (startGreen.isLeft) {
            b.add(new BlockNode(Material.LIME_CONCRETE.createBlockData(), startX - 24, gStartY + 4, 0.004f, 16, 16, 1));
            b.add(new BlockNode(Material.LIME_CONCRETE.createBlockData(), startX - 8, gStartY + 9, 0.005f, 9, wireW, 1));
            b.add(new AlignedTextNode(Component.text("⚡XANH", NamedTextColor.GREEN, TextDecoration.BOLD),
                    startX - 28, gStartY - 7, 24, 8, UiTextAlignment.CENTER).fontSize(4).atDepth(0.015f).shadowed(true));
        } else {
            b.add(new BlockNode(Material.LIME_CONCRETE.createBlockData(), endTileX + tileSize - 1, gStartY + 9, 0.005f, 9, wireW, 1));
            b.add(new BlockNode(Material.LIME_CONCRETE.createBlockData(), endTileX + tileSize + 8, gStartY + 4, 0.004f, 16, 16, 1));
            b.add(new AlignedTextNode(Component.text("XANH⚡", NamedTextColor.GREEN, TextDecoration.BOLD),
                    endTileX + tileSize + 4, gStartY - 7, 26, 8, UiTextAlignment.CENTER).fontSize(4).atDepth(0.015f).shadowed(true));
        }

        // Red Start Terminal
        int rStartY = startY + startRed.row * (tileSize + gap);
        if (startRed.isLeft) {
            b.add(new BlockNode(Material.RED_CONCRETE.createBlockData(), startX - 24, rStartY + 4, 0.004f, 16, 16, 1));
            b.add(new BlockNode(Material.RED_CONCRETE.createBlockData(), startX - 8, rStartY + 9, 0.005f, 9, wireW, 1));
            b.add(new AlignedTextNode(Component.text("⚡ĐỎ", NamedTextColor.RED, TextDecoration.BOLD),
                    startX - 28, rStartY - 7, 24, 8, UiTextAlignment.CENTER).fontSize(4).atDepth(0.015f).shadowed(true));
        } else {
            b.add(new BlockNode(Material.RED_CONCRETE.createBlockData(), endTileX + tileSize - 1, rStartY + 9, 0.005f, 9, wireW, 1));
            b.add(new BlockNode(Material.RED_CONCRETE.createBlockData(), endTileX + tileSize + 8, rStartY + 4, 0.004f, 16, 16, 1));
            b.add(new AlignedTextNode(Component.text("ĐỎ⚡", NamedTextColor.RED, TextDecoration.BOLD),
                    endTileX + tileSize + 4, rStartY - 7, 26, 8, UiTextAlignment.CENTER).fontSize(4).atDepth(0.015f).shadowed(true));
        }

        // TARGET TERMINALS (Left or Right side dynamically - 4 Cores)
        for (int i = 0; i < targets.size(); i++) {
            Terminal t = targets.get(i);
            int tY = startY + t.row * (tileSize + gap);

            Material wireMat = t.connected
                    ? (t.isGreen ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                    : Material.POLISHED_DEEPSLATE;

            Material coreMat;
            if (t.connected) {
                coreMat = t.isGreen ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
            } else {
                coreMat = t.isGreen ? Material.DARK_PRISMARINE : Material.NETHERRACK;
            }

            NamedTextColor labelColor = t.isGreen ? NamedTextColor.GREEN : NamedTextColor.RED;

            if (t.isLeft) {
                String labelText = "⚡LÕI " + (i + 1);
                b.add(new BlockNode(wireMat.createBlockData(), startX - 8, tY + 9, 0.005f, 9, wireW, 1));
                b.add(new BlockNode(coreMat.createBlockData(), startX - 24, tY + 4, 0.004f, 16, 16, 1));
                b.add(new AlignedTextNode(Component.text(labelText, labelColor, TextDecoration.BOLD),
                        startX - 28, tY - 7, 26, 8, UiTextAlignment.CENTER).fontSize(4).atDepth(0.015f).shadowed(true));
            } else {
                String labelText = "LÕI " + (i + 1) + "⚡";
                b.add(new BlockNode(wireMat.createBlockData(), endTileX + tileSize - 1, tY + 9, 0.005f, 9, wireW, 1));
                b.add(new BlockNode(coreMat.createBlockData(), endTileX + tileSize + 8, tY + 4, 0.004f, 16, 16, 1));
                b.add(new AlignedTextNode(Component.text(labelText, labelColor, TextDecoration.BOLD),
                        endTileX + tileSize + 4, tY - 7, 26, 8, UiTextAlignment.CENTER).fontSize(4).atDepth(0.015f).shadowed(true));
            }
        }

        // 4. Grid of 5x5 Tiles
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int x = startX + c * (tileSize + gap);
                int y = startY + r * (tileSize + gap);
                Tile tile = grid[r][c];

                // Determine border material
                Material borderMat;
                if (tile.isDual()) {
                    if (tile.greenPowered && tile.redPowered) {
                        borderMat = Material.GOLD_BLOCK;
                    } else if (tile.greenPowered) {
                        borderMat = Material.CYAN_CONCRETE;
                    } else if (tile.redPowered) {
                        borderMat = Material.RED_CONCRETE;
                    } else {
                        borderMat = Material.PURPLE_CONCRETE;
                    }
                } else if (tile.hasGreen) {
                    borderMat = tile.greenPowered ? Material.CYAN_CONCRETE : Material.BLUE_TERRACOTTA;
                } else if (tile.hasRed) {
                    borderMat = tile.redPowered ? Material.RED_CONCRETE : Material.BROWN_TERRACOTTA;
                } else {
                    borderMat = Material.GRAY_CONCRETE;
                }

                b.add(new BlockNode(borderMat.createBlockData(), x, y, 0.002f, tileSize, tileSize, 1));
                b.add(new BlockNode(Material.BLACK_CONCRETE.createBlockData(), x + 1, y + 1, 0.003f, tileSize - 2, tileSize - 2, 1));

                int cx = x + halfTile;
                int cy = y + halfTile;

                // Center hub
                Material hubMat;
                if (tile.isDual()) {
                    if (tile.greenPowered && tile.redPowered) {
                        hubMat = Material.SEA_LANTERN;
                    } else if (tile.greenPowered) {
                        hubMat = Material.SEA_LANTERN;
                    } else if (tile.redPowered) {
                        hubMat = Material.REDSTONE_BLOCK;
                    } else {
                        hubMat = Material.MAGENTA_GLAZED_TERRACOTTA;
                    }
                } else if (tile.hasGreen) {
                    hubMat = tile.greenPowered ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE;
                } else if (tile.hasRed) {
                    hubMat = tile.redPowered ? Material.REDSTONE_BLOCK : Material.POLISHED_DEEPSLATE;
                } else {
                    hubMat = Material.POLISHED_DEEPSLATE;
                }

                b.add(new BlockNode(hubMat.createBlockData(), cx - halfW, cy - halfW, 0.005f, wireW, wireW, 1));

                // Wire segment materials
                Material gWireMat = tile.greenPowered ? Material.LIME_CONCRETE : Material.DARK_PRISMARINE;
                Material rWireMat = tile.redPowered ? Material.RED_CONCRETE : Material.NETHERRACK;

                // Wire traces in 4 directions: TOP(0), RIGHT(1), BOTTOM(2), LEFT(3)
                for (int d = 0; d < 4; d++) {
                    boolean isG = tile.hasGreenConnection(d);
                    boolean isR = tile.hasRedConnection(d);
                    if (!isG && !isR) continue;

                    Material wMat = isG ? gWireMat : rWireMat;
                    switch (d) {
                        case 0 -> b.add(new BlockNode(wMat.createBlockData(), cx - halfW, y + 1, 0.004f, wireW, halfTile - 1, 1));
                        case 1 -> b.add(new BlockNode(wMat.createBlockData(), cx + halfW, cy - halfW, 0.004f, halfTile - 1, wireW, 1));
                        case 2 -> b.add(new BlockNode(wMat.createBlockData(), cx - halfW, cy + halfW, 0.004f, wireW, halfTile - 1, 1));
                        case 3 -> b.add(new BlockNode(wMat.createBlockData(), x + 1, cy - halfW, 0.004f, halfTile - 1, wireW, 1));
                    }
                }

                // Interactive click button
                String btnId = "tile_" + r + "_" + c;
                String desc = tile.isDual() ? "Xoay ô trùng mạch (" + (r + 1) + "," + (c + 1) + ")"
                        : "Xoay ô (" + (r + 1) + "," + (c + 1) + ")";
                b.button(new UiButton(btnId, x, y, tileSize, tileSize)
                        .describedBy(Component.text(desc))
                        .withAction(UiButtonAction.none()));
            }
        }

        // 5. Close / Cancel Button
        b.add(new BlockNode(Material.RED_CONCRETE.createBlockData(), -35, startY + totalSpan + 6, 0.004f, 70, 12, 1));
        b.add(new AlignedTextNode(Component.text("✕ HỦY & THOÁT", NamedTextColor.WHITE, TextDecoration.BOLD),
                -35, startY + totalSpan + 8, 70, 8, UiTextAlignment.CENTER).fontSize(4).atDepth(0.015f).shadowed(true));
        b.button(new UiButton("btn_close", -35, startY + totalSpan + 6, 70, 12)
                .describedBy(Component.text("Hủy và đóng giao diện"))
                .withAction(UiButtonAction.none()));

        return b.build();
    }
}
