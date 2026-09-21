package vn.haohan.lunar.core.features.beacon;

import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles presentation and visual effects for Beacon Shields, including
 * ItemDisplay transforms, wireframe particles, interior boundary feedback, and collapse effects.
 */
public final class BeaconShieldRenderer {

    private BeaconShieldRenderer() {
    }

    @SuppressWarnings("deprecation")
    public static ItemStack borderItem(int frame) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(910006 + Math.max(0, Math.min(5, frame)));
        item.setItemMeta(meta);
        return item;
    }

    public static void createDisplays(BeaconShield shield, List<ItemDisplay> globalDisplays,
                                      long animationTick, double rotationSpeed) {
        World world = shield.getBeacon().getWorld();
        if (world == null) return;

        List<int[]> edges = BeaconShieldGeometry.edges();
        for (int i = 0; i < edges.size(); i++) {
            ItemDisplay display = world.spawn(shield.getBeacon(), ItemDisplay.class);
            display.setItemStack(borderItem(0));
            display.setBillboard(Display.Billboard.FIXED);
            display.setInterpolationDuration(2);
            display.setPersistent(false);
            shield.getDisplays().add(display);
            if (globalDisplays != null) {
                globalDisplays.add(display);
            }
        }
        updateDisplays(shield, animationTick, rotationSpeed);
    }

    public static void updateDisplays(BeaconShield shield, long animationTick, double rotationSpeed) {
        List<ItemDisplay> displays = shield.getDisplays();
        List<int[]> edges = BeaconShieldGeometry.edges();
        List<Vector3f> vertices = BeaconShieldGeometry.vertices();
        double angle = animationTick * rotationSpeed;
        Location beacon = shield.getBeacon();
        double radius = shield.getRadius();

        for (int i = 0; i < displays.size(); i++) {
            ItemDisplay display = displays.get(i);
            if (!display.isValid()) continue;

            int[] edge = edges.get(i);
            Vector3f from = BeaconShieldGeometry.rotate(vertices.get(edge[0]), angle);
            Vector3f to = BeaconShieldGeometry.rotate(vertices.get(edge[1]), angle);
            Vector3f direction = new Vector3f(to).sub(from);
            float edgeLength = direction.length() * (float) radius;
            direction.normalize();
            Vector3f midpoint = new Vector3f(from).add(to).mul(0.5f).mul((float) radius);
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), direction);

            display.teleport(new Location(display.getWorld(),
                    beacon.getX() + midpoint.x,
                    beacon.getY() + midpoint.y,
                    beacon.getZ() + midpoint.z));
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f().set(rotation),
                    new Vector3f(edgeLength * 0.90f, 0.42f, 0.42f), new AxisAngle4f()));
            display.setItemStack(borderItem((int) (animationTick / 3 % 6)));
        }
    }

    public static void removeDisplays(BeaconShield shield, List<ItemDisplay> globalDisplays) {
        for (ItemDisplay display : shield.getDisplays()) {
            if (globalDisplays != null) {
                globalDisplays.remove(display);
            }
            if (display.isValid()) display.remove();
        }
        shield.getDisplays().clear();
    }

    public static void renderExpansionWireframe(BeaconShield shield, double edgeParticleSpacing, double rotationAngle) {
        World world = shield.getBeacon().getWorld();
        if (world == null) return;

        double radius = shield.getRadius();
        Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(45, 190, 255), 1.15f);
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(25, 90, 255), 1.0f);

        List<int[]> edges = BeaconShieldGeometry.edges();
        List<Vector3f> vertices = BeaconShieldGeometry.vertices();
        Location beacon = shield.getBeacon();

        for (int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
            int[] edge = edges.get(edgeIndex);
            Vector3f from = BeaconShieldGeometry.rotate(vertices.get(edge[0]), rotationAngle);
            Vector3f to = BeaconShieldGeometry.rotate(vertices.get(edge[1]), rotationAngle);
            double edgeLength = Math.sqrt(from.distanceSquared(to)) * radius;
            int samples = Math.max(2, (int) Math.ceil(edgeLength / edgeParticleSpacing));
            for (int sample = 0; sample <= samples; sample++) {
                double t = (double) sample / samples;
                double x = from.x + (to.x - from.x) * t;
                double y = from.y + (to.y - from.y) * t;
                double z = from.z + (to.z - from.z) * t;
                spawn(world, new Location(world, beacon.getX() + x * radius,
                                beacon.getY() + y * radius,
                                beacon.getZ() + z * radius), Particle.DUST,
                        (sample + edgeIndex) % 5 == 0 ? blue : cyan, 1);
            }
        }
    }

    public static void renderInteriorParticles(BeaconShield shield, double boundaryParticleDistance, double rotationAngle) {
        World world = shield.getBeacon().getWorld();
        if (world == null) return;

        double radius = shield.getRadius();
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(25, 90, 255), 1.0f);
        Location beacon = shield.getBeacon();
        List<Vector3f> vertices = BeaconShieldGeometry.vertices();

        for (Player player : world.getPlayers()) {
            Location playerLocation = player.getLocation();
            double dx = playerLocation.getX() - beacon.getX();
            double dy = playerLocation.getY() - beacon.getY();
            double dz = playerLocation.getZ() - beacon.getZ();
            double playerDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double closeness = 1.0 - Math.abs(playerDistance - radius) / boundaryParticleDistance;
            if (closeness <= 0.0) continue;

            Vector3f playerDirection = new Vector3f((float) dx, (float) dy, (float) dz).normalize();
            for (BeaconShieldGeometry.Face face : BeaconShieldGeometry.faces()) {
                Vector3f center = BeaconShieldGeometry.rotate(face.center(), rotationAngle);
                if (center.dot(playerDirection) < 0.2f) continue;
                double faceRadius = face.radius() * radius;
                double faceDistance = playerLocation.distance(new Location(world,
                        beacon.getX() + center.x * radius,
                        beacon.getY() + center.y * radius,
                        beacon.getZ() + center.z * radius));
                double patchRadius = Math.min(faceRadius, 1.5 + closeness * faceRadius * 0.9);
                if (faceDistance > boundaryParticleDistance + faceRadius) continue;

                List<Integer> faceVerts = face.vertices();
                Vector3f first = BeaconShieldGeometry.rotate(vertices.get(faceVerts.get(0)), rotationAngle);
                for (int i = 1; i < faceVerts.size() - 1; i++) {
                    Vector3f second = BeaconShieldGeometry.rotate(vertices.get(faceVerts.get(i)), rotationAngle);
                    Vector3f third = BeaconShieldGeometry.rotate(vertices.get(faceVerts.get(i + 1)), rotationAngle);
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
                                    beacon.getX() + x * radius,
                                    beacon.getY() + y * radius,
                                    beacon.getZ() + z * radius);
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

    public static void updateGroundContacts(BeaconShield shield, double rotationAngle) {
        World world = shield.getBeacon().getWorld();
        if (world == null) return;
        shield.getGroundContacts().clear();
        double radius = shield.getRadius();
        Location beacon = shield.getBeacon();
        List<Vector3f> vertices = BeaconShieldGeometry.vertices();

        for (BeaconShieldGeometry.Face face : BeaconShieldGeometry.faces()) {
            List<Integer> faceVerts = face.vertices();
            Vector3f first = BeaconShieldGeometry.rotate(vertices.get(faceVerts.get(0)), rotationAngle);
            Vector3f normal = BeaconShieldGeometry.rotate(face.normal(), rotationAngle);
            for (int i = 1; i < faceVerts.size() - 1; i++) {
                Vector3f second = BeaconShieldGeometry.rotate(vertices.get(faceVerts.get(i)), rotationAngle);
                Vector3f third = BeaconShieldGeometry.rotate(vertices.get(faceVerts.get(i + 1)), rotationAngle);
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
                                beacon.getX() + x * radius,
                                beacon.getY() + y * radius,
                                beacon.getZ() + z * radius);
                        if (!isBlockTouchingFace(world, boundary, normal)) continue;
                        Location contact = boundary.clone().add(normal.x * 0.06,
                                normal.y * 0.06, normal.z * 0.06);
                        boolean duplicate = false;
                        for (Location existing : shield.getGroundContacts()) {
                            if (existing.distanceSquared(contact) < 2.25) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) shield.getGroundContacts().add(contact);
                    }
                }
            }
        }
    }

    private static boolean isBlockTouchingFace(World world, Location facePoint, Vector3f normal) {
        double[] offsets = {-1.35, -0.85, -0.35, 0.35, 0.85, 1.35};
        for (double offset : offsets) {
            Location probe = facePoint.clone().add(normal.x * offset,
                    normal.y * offset, normal.z * offset);
            if (world.getBlockAt(probe).isSolid()) return true;
        }
        return false;
    }

    public static void renderGroundContacts(BeaconShield shield) {
        if (shield.getGroundContacts().isEmpty()) return;
        World world = shield.getBeacon().getWorld();
        if (world == null) return;
        Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 1.35f);

        for (Player player : world.getPlayers()) {
            Location eye = player.getEyeLocation();
            for (Location contact : shield.getGroundContacts()) {
                if (eye.distanceSquared(contact) > 96.0 * 96.0) continue;
                if (!player.hasLineOfSight(contact)) continue;
                player.spawnParticle(Particle.DUST, contact, 2, 0.28, 0.025, 0.28, 0.0, white);
                player.spawnParticle(Particle.END_ROD, contact, 1, 0.16, 0.04, 0.16, 0.01);
            }
        }
    }

    public static void renderCollapseParticles(BeaconShield shield, double progress, double rotationAngle) {
        World world = shield.getBeacon().getWorld();
        if (world == null) return;

        double radius = Math.max(0.05, shield.getRadius());
        double fragmentChance = 0.35 + progress * 0.45;
        Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(45, 190, 255), 1.1f);
        Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 0.8f);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location beacon = shield.getBeacon();

        List<int[]> edges = BeaconShieldGeometry.edges();
        List<Vector3f> vertices = BeaconShieldGeometry.vertices();

        for (int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
            int[] edge = edges.get(edgeIndex);
            Vector3f from = BeaconShieldGeometry.rotate(vertices.get(edge[0]), rotationAngle);
            Vector3f to = BeaconShieldGeometry.rotate(vertices.get(edge[1]), rotationAngle);
            int samples = Math.max(2, (int) Math.ceil(from.distance(to) * radius / 2.5));

            for (int sample = 0; sample <= samples; sample++) {
                if (random.nextDouble() > fragmentChance) continue;
                double t = (double) sample / samples;
                double x = from.x + (to.x - from.x) * t;
                double y = from.y + (to.y - from.y) * t;
                double z = from.z + (to.z - from.z) * t;

                Vector direction = new Vector(x, y, z).normalize();
                double spread = 0.12 + progress * 0.7;
                Location fragment = new Location(world,
                        beacon.getX() + x * radius + random.nextGaussian() * spread,
                        beacon.getY() + y * radius + random.nextGaussian() * spread,
                        beacon.getZ() + z * radius + random.nextGaussian() * spread);

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

    private static void spawn(World world, Location location, Particle particle, Object data, int count) {
        if (count <= 0) return;
        if (data == null) world.spawnParticle(particle, location, count, 0, 0, 0, 0.01);
        else world.spawnParticle(particle, location, count, 0, 0, 0, 0.01, data);
    }
}
