package vn.haohan.lunar.core.subsystem.features.beacon;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.features.beacon.BeaconShield;
import vn.haohan.lunar.core.features.beacon.BeaconShieldGeometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconShieldTest {

    @Test
    void testGeometryVerticesAndEdges() {
        var vertices = BeaconShieldGeometry.vertices();
        assertNotNull(vertices);
        assertFalse(vertices.isEmpty());

        for (Vector3f v : vertices) {
            float length = v.length();
            assertEquals(1.0f, length, 0.01f, "Vertex must be normalized to unit sphere");
        }

        var edges = BeaconShieldGeometry.edges();
        assertNotNull(edges);
        assertFalse(edges.isEmpty());

        for (int[] edge : edges) {
            assertEquals(2, edge.length);
            assertTrue(edge[0] >= 0 && edge[0] < vertices.size());
            assertTrue(edge[1] >= 0 && edge[1] < vertices.size());
        }
    }

    @Test
    void testGeometryFaces() {
        var faces = BeaconShieldGeometry.faces();
        assertNotNull(faces);
        assertFalse(faces.isEmpty());

        for (BeaconShieldGeometry.Face face : faces) {
            assertNotNull(face.vertices());
            assertTrue(face.vertices().size() >= 3);
            assertNotNull(face.center());
            assertTrue(face.radius() > 0.0);
            assertEquals(1.0f, face.normal().length(), 0.01f);
        }
    }

    @Test
    void testGeometryRotation() {
        Vector3f initial = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f rotated90 = BeaconShieldGeometry.rotate(initial, Math.PI / 2.0);

        assertEquals(0.0f, rotated90.x, 0.001f);
        assertEquals(0.0f, rotated90.y, 0.001f);
        assertEquals(1.0f, rotated90.z, 0.001f);
    }

    @Test
    void testShieldCollapseLifecycle() {
        BeaconShield shield = new BeaconShield(null);
        shield.setRadius(25.0);

        assertFalse(shield.isCollapsing());
        shield.beginCollapse();
        assertTrue(shield.isCollapsing());
        assertEquals(25.0, shield.getCollapseStartRadius(), 0.001);
        assertEquals(0, shield.getCollapseTick());

        shield.incrementCollapseTick();
        assertEquals(1, shield.getCollapseTick());

        assertFalse(shield.isInShield(null));
    }
}
