package vn.haohan.lunar.mechanics;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

/** Creates the animated, client-side-looking protection field around lunar beacons. */
public final class BeaconShieldMechanic implements Listener {

    private static final String LUNAR_WORLD = "haohan:lunar";
    private final List<Shield> shields = new ArrayList<>();
    private final double maxRadius;
    private final double expansionPerTick;
    private final double edgeParticleSpacing;
    private final int renderInterval;
    private final double boundaryParticleDistance;
    private final double rotationSpeed;
    private final int collapseDuration;
    private final List<ItemDisplay> displays = new ArrayList<>();
    private long animationTick;

    private static final List<Vector3f> POLYHEDRON_VERTICES = createPolyhedronVertices();
    private static final List<int[]> POLYHEDRON_EDGES = createPolyhedronEdges();
    private static final List<Face> POLYHEDRON_FACES = createPolyhedronFaces();

    public BeaconShieldMechanic(HaoHanLunarPlugin plugin) {
        this.maxRadius = Math.max(4.0, plugin.getConfig().getDouble("beacon-shield.radius", 48.0));
        this.expansionPerTick = Math.max(0.25, plugin.getConfig().getDouble("beacon-shield.expansion-speed", 3.0));
        this.edgeParticleSpacing = Math.max(0.75,
                plugin.getConfig().getDouble("beacon-shield.edge-particle-spacing", 1.0));
        this.renderInterval = Math.max(1,
                plugin.getConfig().getInt("beacon-shield.render-interval", 2));
        this.boundaryParticleDistance = Math.max(1.0,
                plugin.getConfig().getDouble("beacon-shield.boundary-particle-distance", 8.0));
        this.rotationSpeed = plugin.getConfig().getDouble("beacon-shield.rotation-speed", 0.012);
        this.collapseDuration = Math.max(6,
                plugin.getConfig().getInt("beacon-shield.collapse-duration", 24));
    }

    public void tick() {
        animationTick++;
        Iterator<Shield> iterator = shields.iterator();
        while (iterator.hasNext()) {
            Shield shield = iterator.next();
            if (shield.collapsing) {
                if (tickCollapse(shield)) {
                    removeDisplays(shield);
                    iterator.remove();
                }
                continue;
            }
            if (!isValid(shield)) {
                removeDisplays(shield);
                iterator.remove();
                continue;
            }

            shield.radius = Math.min(maxRadius, shield.radius + expansionPerTick);
            updateDisplays(shield);
            if (animationTick % renderInterval == 0 && shield.radius < maxRadius) {
                render(shield);
            }
            if (shield.radius >= maxRadius) {
                if (animationTick % 10 == 0) updateGroundContacts(shield);
                if (animationTick % 2 == 0) renderGroundContacts(shield);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType().name().equals("BEACON") && isLunar(block.getWorld())) {
            shields.removeIf(shield -> shield.beacon.equals(block.getLocation()));
            Shield shield = new Shield(block.getLocation().clone().add(0.5, 0.0, 0.5));
            shields.add(shield);
            createDisplays(shield);
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.8f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType().name().equals("BEACON")) {
            for (Shield shield : shields) {
                if (shield.beacon.getBlock().equals(event.getBlock())) {
                    shield.beginCollapse();
                }
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        removeShields(shield -> shield.beacon.getWorld() == event.getWorld());
    }

    public void removeAll() {
        removeShields(shield -> true);
    }

    public boolean isInShield(Location location) {
        if (location == null || !isLunar(location.getWorld())) return false;
        for (Shield shield : shields) {
            if (shield.beacon.getWorld() != location.getWorld()) continue;
            double dx = location.getX() - shield.beacon.getX();
            double dy = location.getY() - shield.beacon.getY();
            double dz = location.getZ() - shield.beacon.getZ();
            if (!shield.collapsing && dx * dx + dy * dy + dz * dz <= shield.radius * shield.radius) return true;
        }
        return false;
    }

    private void render(Shield shield) {
        World world = shield.beacon.getWorld();
        if (world == null) return;

        double radius = shield.radius;
        Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(45, 190, 255), 1.15f);
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(25, 90, 255), 1.0f);

        // During expansion, particles trace the growing boundary. Once expanded,
        // edge particles stop; proximity particles are rendered across the open faces.
        if (shield.radius >= maxRadius) {
            renderInteriorParticles(shield, cyan, blue);
            return;
        }

        // Rhombicuboctahedron wireframe: 24 vertices and 48 connected edges.
        for (int edgeIndex = 0; edgeIndex < POLYHEDRON_EDGES.size(); edgeIndex++) {
            int[] edge = POLYHEDRON_EDGES.get(edgeIndex);
            Vector3f from = rotated(POLYHEDRON_VERTICES.get(edge[0]));
            Vector3f to = rotated(POLYHEDRON_VERTICES.get(edge[1]));
            double edgeLength = Math.sqrt(from.distanceSquared(to)) * radius;
            int samples = Math.max(2, (int) Math.ceil(edgeLength / edgeParticleSpacing));
            for (int sample = 0; sample <= samples; sample++) {
                double t = (double) sample / samples;
                double x = from.x + (to.x - from.x) * t;
                double y = from.y + (to.y - from.y) * t;
                double z = from.z + (to.z - from.z) * t;
                spawn(world, new Location(world, shield.beacon.getX() + x * radius,
                        shield.beacon.getY() + y * radius,
                        shield.beacon.getZ() + z * radius), Particle.DUST,
                        (sample + edgeIndex) % 5 == 0 ? blue : cyan, 1);
            }
        }
    }

    /** Shrinks the displays while throwing pieces of the field away from its centre. */
    private boolean tickCollapse(Shield shield) {
        shield.collapseTick++;
        double progress = Math.min(1.0, (double) shield.collapseTick / collapseDuration);
        double remaining = 1.0 - progress;
        // Ease-in toward the end so the shield hangs briefly before disintegrating.
        shield.radius = shield.collapseStartRadius * remaining * remaining;

        updateDisplays(shield);
        if (shield.radius > 0.05) {
            renderCollapseParticles(shield, progress);
        }
        return shield.collapseTick >= collapseDuration;
    }

    private void renderCollapseParticles(Shield shield, double progress) {
        World world = shield.beacon.getWorld();
        if (world == null) return;

        double radius = Math.max(0.05, shield.radius);
        double fragmentChance = 0.35 + progress * 0.45;
        Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(45, 190, 255), 1.1f);
        Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 0.8f);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int edgeIndex = 0; edgeIndex < POLYHEDRON_EDGES.size(); edgeIndex++) {
            int[] edge = POLYHEDRON_EDGES.get(edgeIndex);
            Vector3f from = rotated(POLYHEDRON_VERTICES.get(edge[0]));
            Vector3f to = rotated(POLYHEDRON_VERTICES.get(edge[1]));
            int samples = Math.max(2, (int) Math.ceil(from.distance(to) * radius / 2.5));

            for (int sample = 0; sample <= samples; sample++) {
                if (random.nextDouble() > fragmentChance) continue;
                double t = (double) sample / samples;
                double x = from.x + (to.x - from.x) * t;
                double y = from.y + (to.y - from.y) * t;
                double z = from.z + (to.z - from.z) * t;

                // Give each piece a small outward displacement, making the wireframe
                // look as if it is breaking apart instead of simply fading.
                Vector direction = new Vector(x, y, z).normalize();
                double spread = 0.12 + progress * 0.7;
                Location fragment = new Location(world,
                        shield.beacon.getX() + x * radius + random.nextGaussian() * spread,
                        shield.beacon.getY() + y * radius + random.nextGaussian() * spread,
                        shield.beacon.getZ() + z * radius + random.nextGaussian() * spread);

                world.spawnParticle(Particle.DUST, fragment, 1,
                        direction.getX() * spread, direction.getY() * spread, direction.getZ() * spread,
                        0.01, (edgeIndex + sample) % 4 == 0 ? white : cyan);
                if (random.nextDouble() < 0.32) {
                    world.spawnParticle(Particle.END_ROD, fragment, 1,
                            direction.getX() * spread, direction.getY() * spread, direction.getZ() * spread,
                            0.015);
                }
            }
        }
    }

    private void spawn(World world, Location location, Particle particle, Object data, int count) {
        if (count <= 0) return;
        if (data == null) world.spawnParticle(particle, location, count, 0, 0, 0, 0.01);
        else world.spawnParticle(particle, location, count, 0, 0, 0, 0.01, data);
    }

    private boolean isValid(Shield shield) {
        Block block = shield.beacon.getBlock();
        return isLunar(shield.beacon.getWorld()) && block.getType().name().equals("BEACON")
                && shield.beacon.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    private void createDisplays(Shield shield) {
        World world = shield.beacon.getWorld();
        for (int i = 0; i < POLYHEDRON_EDGES.size(); i++) {
            ItemDisplay display = world.spawn(shield.beacon, ItemDisplay.class);
            display.setItemStack(borderItem(0));
            display.setBillboard(Display.Billboard.FIXED);
            display.setInterpolationDuration(2);
            display.setPersistent(false);
            shield.displays.add(display);
            displays.add(display);
        }
        updateDisplays(shield);
    }

    private void updateDisplays(Shield shield) {
        for (int i = 0; i < shield.displays.size(); i++) {
            ItemDisplay display = shield.displays.get(i);
            if (!display.isValid()) continue;

            int[] edge = POLYHEDRON_EDGES.get(i);
            Vector3f from = rotated(POLYHEDRON_VERTICES.get(edge[0]));
            Vector3f to = rotated(POLYHEDRON_VERTICES.get(edge[1]));
            Vector3f direction = new Vector3f(to).sub(from);
            float edgeLength = direction.length() * (float) shield.radius;
            direction.normalize();
            Vector3f midpoint = new Vector3f(from).add(to).mul(0.5f).mul((float) shield.radius);
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), direction);

            display.teleport(new Location(display.getWorld(),
                    shield.beacon.getX() + midpoint.x,
                    shield.beacon.getY() + midpoint.y,
                    shield.beacon.getZ() + midpoint.z));
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f().set(rotation),
                    new Vector3f(edgeLength * 0.90f, 0.42f, 0.42f), new AxisAngle4f()));
            display.setItemStack(borderItem((int) (animationTick / 3 % 6)));
        }
    }

    private void renderInteriorParticles(Shield shield, Particle.DustOptions cyan,
                                          Particle.DustOptions blue) {
        World world = shield.beacon.getWorld();
        if (world == null) return;
        double radius = shield.radius;
        for (Player player : world.getPlayers()) {
            Location playerLocation = player.getLocation();
            double dx = playerLocation.getX() - shield.beacon.getX();
            double dy = playerLocation.getY() - shield.beacon.getY();
            double dz = playerLocation.getZ() - shield.beacon.getZ();
            double playerDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double closeness = 1.0 - Math.abs(playerDistance - radius) / boundaryParticleDistance;
            if (closeness <= 0.0) continue;

            Vector3f playerDirection = new Vector3f((float) dx, (float) dy, (float) dz).normalize();
            for (Face face : POLYHEDRON_FACES) {
                Vector3f center = rotated(face.center());
                if (center.dot(playerDirection) < 0.2f) continue;
                double faceRadius = face.radius() * radius;
                double faceDistance = playerLocation.distance(new Location(world,
                        shield.beacon.getX() + center.x * radius,
                        shield.beacon.getY() + center.y * radius,
                        shield.beacon.getZ() + center.z * radius));
                double patchRadius = Math.min(faceRadius, 1.5 + closeness * faceRadius * 0.9);
                if (faceDistance > boundaryParticleDistance + faceRadius) continue;

                Vector3f first = rotated(POLYHEDRON_VERTICES.get(face.vertices.get(0)));
                for (int i = 1; i < face.vertices.size() - 1; i++) {
                    Vector3f second = rotated(POLYHEDRON_VERTICES.get(face.vertices.get(i)));
                    Vector3f third = rotated(POLYHEDRON_VERTICES.get(face.vertices.get(i + 1)));
                    double longestSide = Math.max(first.distance(second),
                            Math.max(second.distance(third), third.distance(first))) * radius;
                    int steps = Math.max(2, (int) Math.ceil(longestSide / 3.0));
                    for (int a = 0; a <= steps; a++) {
                        for (int b = 0; b <= steps - a; b++) {
                            double u = (double) a / steps;
                            double v = (double) b / steps;
                            double w = 1.0 - u - v;
                            double x = first.x * w + second.x * u + third.x * v;
                            double y = first.y * w + second.y * u + third.y * v;
                            double z = first.z * w + second.z * u + third.z * v;
                            Location particleLocation = new Location(world,
                                    shield.beacon.getX() + x * radius,
                                    shield.beacon.getY() + y * radius,
                                    shield.beacon.getZ() + z * radius);
                            if (particleLocation.distanceSquared(playerLocation) > patchRadius * patchRadius) continue;
                            if ((a + b) % 4 == 0) {
                                player.spawnParticle(Particle.DUST, particleLocation, 1,
                                        0.04, 0.04, 0.04, 0.0, blue);
                            } else {
                                player.spawnParticle(Particle.END_ROD, particleLocation, 1,
                                        0.04, 0.04, 0.04, 0.01);
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateGroundContacts(Shield shield) {
        World world = shield.beacon.getWorld();
        if (world == null) return;
        shield.groundContacts.clear();
        double radius = shield.radius;

        for (Face face : POLYHEDRON_FACES) {
            Vector3f first = rotated(POLYHEDRON_VERTICES.get(face.vertices.get(0)));
            Vector3f normal = rotated(face.normal());
            for (int i = 1; i < face.vertices.size() - 1; i++) {
                Vector3f second = rotated(POLYHEDRON_VERTICES.get(face.vertices.get(i)));
                Vector3f third = rotated(POLYHEDRON_VERTICES.get(face.vertices.get(i + 1)));
                double longestSide = Math.max(first.distance(second),
                        Math.max(second.distance(third), third.distance(first))) * radius;
                int steps = Math.max(2, (int) Math.ceil(longestSide / 3.0));
                for (int a = 0; a <= steps; a++) {
                    for (int b = 0; b <= steps - a; b++) {
                        double u = (double) a / steps;
                        double v = (double) b / steps;
                        double w = 1.0 - u - v;
                        double x = first.x * w + second.x * u + third.x * v;
                        double y = first.y * w + second.y * u + third.y * v;
                        double z = first.z * w + second.z * u + third.z * v;
                        Location boundary = new Location(world,
                                shield.beacon.getX() + x * radius,
                                shield.beacon.getY() + y * radius,
                                shield.beacon.getZ() + z * radius);
                        if (!isBlockTouchingFace(world, boundary, normal)) continue;
                        Location contact = boundary.clone().add(normal.x * 0.06,
                                normal.y * 0.06, normal.z * 0.06);
                        boolean duplicate = false;
                        for (Location existing : shield.groundContacts) {
                            if (existing.distanceSquared(contact) < 2.25) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) shield.groundContacts.add(contact);
                    }
                }
            }
        }
    }

    private boolean isBlockTouchingFace(World world, Location facePoint, Vector3f normal) {
        // Check both sides of the mathematical face. This catches ground, ceilings,
        // vertical walls, and blocks whose top is above or below the face point.
        double[] offsets = {-1.35, -0.85, -0.35, 0.35, 0.85, 1.35};
        for (double offset : offsets) {
            Location probe = facePoint.clone().add(normal.x * offset,
                    normal.y * offset, normal.z * offset);
            if (world.getBlockAt(probe).isSolid()) return true;
        }
        return false;
    }

    private Vector3f rotated(Vector3f vector) {
        double angle = animationTick * rotationSpeed;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new Vector3f(vector.x * cos - vector.z * sin,
                vector.y,
                vector.x * sin + vector.z * cos);
    }

    private void renderGroundContacts(Shield shield) {
        if (shield.groundContacts.isEmpty()) return;
        World world = shield.beacon.getWorld();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            Location eye = player.getEyeLocation();
            for (Location contact : shield.groundContacts) {
                if (eye.distanceSquared(contact) > 96.0 * 96.0) continue;
                if (!player.hasLineOfSight(contact)) continue;
                player.spawnParticle(Particle.DUST, contact, 2, 0.28, 0.025, 0.28, 0.0,
                        new Particle.DustOptions(Color.WHITE, 1.35f));
                player.spawnParticle(Particle.END_ROD, contact, 1, 0.16, 0.04, 0.16, 0.01);
            }
        }
    }

    private static List<Vector3f> createPolyhedronVertices() {
        double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
        Vector3f[] base = new Vector3f[]{
                new Vector3f(-1, (float) phi, 0), new Vector3f(1, (float) phi, 0),
                new Vector3f(-1, (float) -phi, 0), new Vector3f(1, (float) -phi, 0),
                new Vector3f(0, -1, (float) phi), new Vector3f(0, 1, (float) phi),
                new Vector3f(0, -1, (float) -phi), new Vector3f(0, 1, (float) -phi),
                new Vector3f((float) phi, 0, -1), new Vector3f((float) phi, 0, 1),
                new Vector3f((float) -phi, 0, -1), new Vector3f((float) -phi, 0, 1)
        };
        for (Vector3f vertex : base) vertex.normalize();
        int[][] baseFaces = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
        };
        int subdivisions = 2;
        List<Vector3f> vertices = new ArrayList<>();
        Map<String, Integer> indexByPosition = new HashMap<>();
        for (int[] face : baseFaces) {
            Vector3f first = base[face[0]], second = base[face[1]], third = base[face[2]];
            for (int a = 0; a <= subdivisions; a++) {
                for (int b = 0; b <= subdivisions - a; b++) {
                    double u = (double) a / subdivisions;
                    double v = (double) b / subdivisions;
                    double w = 1.0 - u - v;
                    Vector3f point = new Vector3f(first).mul((float) w)
                            .add(new Vector3f(second).mul((float) u))
                            .add(new Vector3f(third).mul((float) v)).normalize();
                    String key = String.format(java.util.Locale.ROOT, "%.5f,%.5f,%.5f",
                            point.x, point.y, point.z);
                    if (!indexByPosition.containsKey(key)) {
                        indexByPosition.put(key, vertices.size());
                        vertices.add(point);
                    }
                }
            }
        }
        return vertices;
    }

    private static List<int[]> createPolyhedronEdges() {
        List<int[]> edges = new ArrayList<>();
        float shortest = Float.MAX_VALUE;
        for (int i = 0; i < POLYHEDRON_VERTICES.size(); i++) {
            for (int j = i + 1; j < POLYHEDRON_VERTICES.size(); j++) {
                shortest = Math.min(shortest, POLYHEDRON_VERTICES.get(i)
                        .distanceSquared(POLYHEDRON_VERTICES.get(j)));
            }
        }
        // Subdivision on a sphere slightly changes chord lengths; allow the
        // neighbouring midpoint edges without pulling in diagonals.
        float edgeLimit = shortest * 1.20f;
        for (int i = 0; i < POLYHEDRON_VERTICES.size(); i++) {
            for (int j = i + 1; j < POLYHEDRON_VERTICES.size(); j++) {
                if (POLYHEDRON_VERTICES.get(i).distanceSquared(POLYHEDRON_VERTICES.get(j)) <= edgeLimit) {
                    edges.add(new int[]{i, j});
                }
            }
        }
        return edges;
    }

    private static List<Face> createPolyhedronFaces() {
        List<Face> faces = new ArrayList<>();
        Set<String> known = new HashSet<>();
        final float epsilon = 0.0001f;
        for (int i = 0; i < POLYHEDRON_VERTICES.size(); i++) {
            for (int j = i + 1; j < POLYHEDRON_VERTICES.size(); j++) {
                for (int k = j + 1; k < POLYHEDRON_VERTICES.size(); k++) {
                    Vector3f a = POLYHEDRON_VERTICES.get(i);
                    Vector3f normal = new Vector3f(POLYHEDRON_VERTICES.get(j)).sub(a)
                            .cross(new Vector3f(POLYHEDRON_VERTICES.get(k)).sub(a));
                    if (normal.lengthSquared() < epsilon) continue;
                    normal.normalize();
                    float distance = normal.dot(a);
                    List<Integer> coplanar = new ArrayList<>();
                    float min = Float.MAX_VALUE;
                    float max = -Float.MAX_VALUE;
                    for (int vertex = 0; vertex < POLYHEDRON_VERTICES.size(); vertex++) {
                        float side = normal.dot(POLYHEDRON_VERTICES.get(vertex)) - distance;
                        min = Math.min(min, side);
                        max = Math.max(max, side);
                        if (Math.abs(side) < epsilon) coplanar.add(vertex);
                    }
                    if (min < -epsilon && max > epsilon) continue;
                    Collections.sort(coplanar);
                    String key = coplanar.toString();
                    if (coplanar.size() >= 3 && known.add(key)) {
                        faces.add(new Face(sortFaceVertices(coplanar, normal)));
                    }
                }
            }
        }
        return faces;
    }

    private static List<Integer> sortFaceVertices(List<Integer> source, Vector3f normal) {
        List<Integer> sorted = new ArrayList<>(source);
        Vector3f center = new Vector3f();
        for (int index : sorted) center.add(POLYHEDRON_VERTICES.get(index));
        center.div(sorted.size());
        Vector3f basis = new Vector3f(POLYHEDRON_VERTICES.get(sorted.get(0))).sub(center).normalize();
        Vector3f bitangent = new Vector3f(normal).cross(basis).normalize();
        sorted.sort((left, right) -> {
            Vector3f l = new Vector3f(POLYHEDRON_VERTICES.get(left)).sub(center);
            Vector3f r = new Vector3f(POLYHEDRON_VERTICES.get(right)).sub(center);
            double la = Math.atan2(l.dot(bitangent), l.dot(basis));
            double ra = Math.atan2(r.dot(bitangent), r.dot(basis));
            return Double.compare(la, ra);
        });
        return sorted;
    }

    private static final class Face {
        private final List<Integer> vertices;

        private Face(List<Integer> vertices) {
            this.vertices = vertices;
        }

        private Vector3f center() {
            Vector3f center = new Vector3f();
            for (int vertex : vertices) center.add(POLYHEDRON_VERTICES.get(vertex));
            return center.div(vertices.size());
        }

        private double radius() {
            Vector3f center = center();
            double maximum = 0.0;
            for (int vertex : vertices) {
                maximum = Math.max(maximum, center.distance(POLYHEDRON_VERTICES.get(vertex)));
            }
            return maximum;
        }

        private Vector3f normal() {
            return center().normalize();
        }
    }

    @SuppressWarnings("deprecation")
    private ItemStack borderItem(int frame) {
        ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(910006 + Math.max(0, Math.min(5, frame)));
        item.setItemMeta(meta);
        return item;
    }

    private void removeShields(java.util.function.Predicate<Shield> predicate) {
        Iterator<Shield> iterator = shields.iterator();
        while (iterator.hasNext()) {
            Shield shield = iterator.next();
            if (!predicate.test(shield)) continue;
            removeDisplays(shield);
            iterator.remove();
        }
    }

    private void removeDisplays(Shield shield) {
        for (ItemDisplay display : shield.displays) {
            displays.remove(display);
            if (display.isValid()) display.remove();
        }
        shield.displays.clear();
    }

    private boolean isLunar(World world) {
        return world != null && world.getKey().toString().equals(LUNAR_WORLD);
    }

    private static final class Shield {
        private final Location beacon;
        private double radius;
        private double collapseStartRadius;
        private int collapseTick;
        private boolean collapsing;
        private final List<ItemDisplay> displays = new ArrayList<>();
        private final List<Location> groundContacts = new ArrayList<>();

        private Shield(Location beacon) {
            this.beacon = beacon;
        }

        private void beginCollapse() {
            if (collapsing) return;
            collapseStartRadius = radius;
            collapseTick = 0;
            collapsing = true;
            groundContacts.clear();
        }
    }
}
