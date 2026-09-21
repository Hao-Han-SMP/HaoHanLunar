package vn.haohan.lunar.core.features.beacon;

import org.joml.Vector3f;

import java.util.*;

/**
 * Pure mathematical geometry and spherical polyhedron subdivision for Beacon Shield.
 */
public final class BeaconShieldGeometry {

    private static final List<Vector3f> POLYHEDRON_VERTICES = createPolyhedronVertices();
    private static final List<int[]> POLYHEDRON_EDGES = createPolyhedronEdges();
    private static final List<Face> POLYHEDRON_FACES = createPolyhedronFaces();

    private BeaconShieldGeometry() {
    }

    public static List<Vector3f> vertices() {
        return POLYHEDRON_VERTICES;
    }

    public static List<int[]> edges() {
        return POLYHEDRON_EDGES;
    }

    public static List<Face> faces() {
        return POLYHEDRON_FACES;
    }

    public static Vector3f rotate(Vector3f vector, double angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new Vector3f(vector.x * cos - vector.z * sin,
                vector.y,
                vector.x * sin + vector.z * cos);
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
                    String key = String.format(Locale.ROOT, "%.5f,%.5f,%.5f",
                            point.x, point.y, point.z);
                    if (!indexByPosition.containsKey(key)) {
                        indexByPosition.put(key, vertices.size());
                        vertices.add(point);
                    }
                }
            }
        }
        return Collections.unmodifiableList(vertices);
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
        float edgeLimit = shortest * 1.20f;
        for (int i = 0; i < POLYHEDRON_VERTICES.size(); i++) {
            for (int j = i + 1; j < POLYHEDRON_VERTICES.size(); j++) {
                if (POLYHEDRON_VERTICES.get(i).distanceSquared(POLYHEDRON_VERTICES.get(j)) <= edgeLimit) {
                    edges.add(new int[]{i, j});
                }
            }
        }
        return Collections.unmodifiableList(edges);
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
        return Collections.unmodifiableList(faces);
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

    public static final class Face {
        private final List<Integer> vertices;
        private final Vector3f center;
        private final double radius;
        private final Vector3f normal;

        public Face(List<Integer> vertices) {
            this.vertices = Collections.unmodifiableList(new ArrayList<>(vertices));
            Vector3f c = new Vector3f();
            for (int vertex : vertices) c.add(POLYHEDRON_VERTICES.get(vertex));
            c.div(vertices.size());
            this.center = c;

            double maxDist = 0.0;
            for (int vertex : vertices) {
                maxDist = Math.max(maxDist, c.distance(POLYHEDRON_VERTICES.get(vertex)));
            }
            this.radius = maxDist;
            this.normal = new Vector3f(c).normalize();
        }

        public List<Integer> vertices() {
            return vertices;
        }

        public Vector3f center() {
            return new Vector3f(center);
        }

        public double radius() {
            return radius;
        }

        public Vector3f normal() {
            return new Vector3f(normal);
        }
    }
}
