package vn.haohan.lunar.robot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.displayui.api.UiDocument;
import vn.haohan.displayui.api.container.Container;
import vn.haohan.displayui.api.layer.Layer;
import vn.haohan.displayui.api.layer.LayerManager;
import vn.haohan.displayui.api.debug.UiDebugInspector;
import vn.haohan.displayui.api.debug.UiDebugRegistry;
import vn.haohan.displayui.api.debug.UiDebugState;
import vn.haohan.lunar.robot.ui.LunarRobotDashboardUi;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite verifying the debug UI system integration for Lunar Dashboard UI
 * using the core HaoHanDisplayUI debug engine.
 */
public class LunarDashboardDebugTest {

    @Test
    @DisplayName("1. Verify Layer visibility toggling filters entire layer out of compiled UiDocument")
    public void testLayerVisibilityToggling() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        // Initially all layers are visible
        UiDocument docDefault = ui.renderDocument();
        assertNotNull(docDefault);
        assertTrue(docDefault.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_modes")));

        // Hide left_nav_layer
        ui.getDebugState().setLayerVisibility("left_nav_layer", false);
        UiDocument docNoLeftNav = ui.renderDocument();

        // Left nav buttons must not be in compiled document
        assertFalse(docNoLeftNav.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_modes")),
                "btn_nav_modes must be culled when left_nav_layer is hidden");
        assertFalse(docNoLeftNav.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_settings")));
        assertFalse(docNoLeftNav.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_exit")));

        // Right panel elements still present
        LayerManager lm = ui.getCurrentLayerManager();
        assertTrue(lm.getLayer("right_content_layer").orElseThrow().isVisible());

        // Re-show left_nav_layer
        ui.getDebugState().setLayerVisibility("left_nav_layer", true);
        UiDocument docRestored = ui.renderDocument();
        assertTrue(docRestored.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_modes")));
    }

    @Test
    @DisplayName("2. Verify Container visibility toggling hides container and all its inner components")
    public void testContainerVisibilityToggling() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        // Hide container pill_robot_name
        ui.getDebugState().setContainerVisibility("pill_robot_name", false);
        LayerManager lm = ui.getCurrentLayerManager();

        Layer rightLayer = lm.getLayer("right_content_layer").orElseThrow();
        Container rightPanel = rightLayer.getContainer("right_panel").orElseThrow();
        Container pill = rightPanel.findContainer("pill_robot_name").orElseThrow();
        assertFalse(pill.isVisible(), "pill_robot_name container must have visible = false");

        // pill_active_hours must remain visible
        Container pill2 = rightPanel.findContainer("pill_active_hours").orElseThrow();
        assertTrue(pill2.isVisible(), "pill_active_hours must remain visible");

        // Compile and verify node count difference
        UiDocument docHiddenPill = ui.renderDocument();
        ui.getDebugState().reset();
        UiDocument docFull = ui.renderDocument();

        assertTrue(docFull.nodes().size() > docHiddenPill.nodes().size(),
                "Document with hidden pill must have fewer nodes than full document");
    }

    @Test
    @DisplayName("3. Verify Component visibility toggling hides specific component while keeping container")
    public void testComponentVisibilityToggling() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        // Full document
        UiDocument docFull = ui.renderDocument();

        // Hide progress bar component box_integrity_pb
        ui.getDebugState().setComponentVisibility("box_integrity_pb", false);
        LayerManager lm = ui.getCurrentLayerManager();

        Container boxIntegrity = lm.getLayer("right_content_layer").orElseThrow()
                .getContainer("right_panel").orElseThrow()
                .findContainer("box_integrity").orElseThrow();

        assertTrue(boxIntegrity.isVisible(), "Container box_integrity must stay visible");
        vn.haohan.displayui.api.component.Component pbComp = boxIntegrity.findComponent("box_integrity_pb").orElseThrow();
        assertFalse(pbComp.isVisible(), "Component box_integrity_pb must be set to visible = false");

        // Other components in box_integrity stay visible
        assertTrue(boxIntegrity.findComponent("box_integrity_bg").orElseThrow().isVisible());
        assertTrue(boxIntegrity.findComponent("box_integrity_l1").orElseThrow().isVisible());

        // Compiled document has exactly 1 fewer node
        UiDocument docHiddenPb = ui.renderDocument();
        assertEquals(docFull.nodes().size() - 1, docHiddenPb.nodes().size(),
                "Hiding single component box_integrity_pb must reduce compiled nodes by exactly 1");
    }

    @Test
    @DisplayName("4. Verify Solo Mode for Layer, Container, and Component")
    public void testSoloMode() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        // 1. Solo Layer
        ui.getDebugState().setSolo(UiDebugState.SoloType.LAYER, "left_nav_layer");
        LayerManager lmLayer = ui.getCurrentLayerManager();
        assertTrue(lmLayer.getLayer("left_nav_layer").orElseThrow().isVisible());
        assertFalse(lmLayer.getLayer("right_content_layer").orElseThrow().isVisible());
        assertFalse(lmLayer.getLayer("background_canvas").orElseThrow().isVisible());

        // 2. Solo Container
        ui.getDebugState().setSolo(UiDebugState.SoloType.CONTAINER, "pill_robot_name");
        LayerManager lmCont = ui.getCurrentLayerManager();
        Container rp = lmCont.getLayer("right_content_layer").orElseThrow().getContainer("right_panel").orElseThrow();
        assertTrue(rp.findContainer("pill_robot_name").orElseThrow().isVisible());
        assertFalse(rp.findContainer("pill_active_hours").orElseThrow().isVisible());
        assertFalse(rp.findContainer("box_integrity").orElseThrow().isVisible());

        // 3. Solo Component
        ui.getDebugState().setSolo(UiDebugState.SoloType.COMPONENT, "title_dashboard");
        LayerManager lmComp = ui.getCurrentLayerManager();
        Container lp = lmComp.getLayer("left_nav_layer").orElseThrow().getContainer("left_panel").orElseThrow();
        assertTrue(lp.findComponent("title_dashboard").orElseThrow().isVisible());
        assertFalse(lp.findComponent("btn_nav_modes").orElseThrow().isVisible());
        assertFalse(lp.findComponent("btn_nav_settings").orElseThrow().isVisible());

        // 4. Unsolo / Clear Solo
        ui.getDebugState().clearSolo();
        assertFalse(ui.getDebugState().isSoloActive());
        LayerManager lmRestored = ui.getCurrentLayerManager();
        assertTrue(lmRestored.getLayer("left_nav_layer").orElseThrow().isVisible());
        assertTrue(lmRestored.getLayer("right_content_layer").orElseThrow().isVisible());
    }

    @Test
    @DisplayName("5. Verify Bulk operations: hideAll, showAll, and reset")
    public void testBulkOperations() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        // hideAll
        ui.getDebugState().hideAll(ui.getCurrentLayerManager());
        UiDocument docEmpty = ui.renderDocument();
        assertTrue(docEmpty.nodes().isEmpty(), "hideAll must result in an empty rendered document");
        assertTrue(docEmpty.buttons().isEmpty());

        // showAll
        ui.getDebugState().showAll(ui.getCurrentLayerManager());
        UiDocument docFull = ui.renderDocument();
        assertFalse(docFull.nodes().isEmpty(), "showAll must restore rendered nodes");

        // reset
        ui.getDebugState().setLayerVisibility("left_nav_layer", false);
        ui.getDebugState().reset();
        UiDocument docReset = ui.renderDocument();
        assertTrue(docReset.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_modes")));
    }

    @Test
    @DisplayName("6. Verify Tree Hierarchy collection produces accurate tree structure")
    public void testTreeHierarchyCollection() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        LayerManager lm = ui.getCurrentLayerManager();
        List<UiDebugState.UiTreeNode> tree = ui.getDebugState().buildTree(lm);

        assertNotNull(tree);
        assertEquals(3, tree.size(), "Dashboard must have 3 root layer tree nodes");

        // Check Layer 1 (left_nav_layer)
        UiDebugState.UiTreeNode leftNavNode = tree.stream()
                .filter(n -> n.id().equals("left_nav_layer")).findFirst().orElseThrow();
        assertEquals("LAYER", leftNavNode.type());
        assertTrue(leftNavNode.visible());
        assertFalse(leftNavNode.children().isEmpty());

        // Check Container left_panel inside left_nav_layer
        UiDebugState.UiTreeNode leftPanelNode = leftNavNode.children().get(0);
        assertEquals("CONTAINER", leftPanelNode.type());
        assertEquals("left_panel", leftPanelNode.id());

        // Check components inside left_panel
        assertTrue(leftPanelNode.children().stream().anyMatch(c -> c.id().equals("title_dashboard")));
        assertTrue(leftPanelNode.children().stream().anyMatch(c -> c.id().equals("btn_nav_modes")));
    }

    @Test
    @DisplayName("7. Verify Mode Buttons in Tab MODES are Containers and can be independently toggled")
    public void testModesContainersToggling() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        ui.setCurrentTabAndRefresh(LunarRobotDashboardUi.Tab.MODES);

        LayerManager lm = ui.getCurrentLayerManager();
        Container rightPanel = lm.getLayer("right_content_layer").orElseThrow()
                .getContainer("right_panel").orElseThrow();

        // Verify mode_idle container exists
        assertTrue(rightPanel.findContainer("mode_idle").isPresent());
        assertTrue(rightPanel.findContainer("mode_ore").isPresent());
        assertTrue(rightPanel.findContainer("mode_combat").isPresent());

        // Toggle container mode_idle off
        ui.getDebugState().setContainerVisibility("mode_idle", false);
        LayerManager lmHidden = ui.getCurrentLayerManager();
        Container rp2 = lmHidden.getLayer("right_content_layer").orElseThrow()
                .getContainer("right_panel").orElseThrow();
        assertFalse(rp2.findContainer("mode_idle").orElseThrow().isVisible());
        assertTrue(rp2.findContainer("mode_ore").orElseThrow().isVisible());

        // Verify button clicking still targets mode_idle
        UiDocument doc = ui.renderDocument();
        // mode_idle button is not in doc when container is hidden
        assertFalse(doc.buttons().stream().anyMatch(b -> b.id().equals("mode_idle")));
        // mode_ore button is in doc
        assertTrue(doc.buttons().stream().anyMatch(b -> b.id().equals("mode_ore")));
    }

    @Test
    @DisplayName("8. Verify Debug Registry lists all expected layer, container, and component IDs")
    public void testDebugRegistryCompleteness() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        LayerManager lm = ui.getCurrentLayerManager();

        var layers = UiDebugRegistry.getAvailableLayerIds(lm);
        assertFalse(layers.isEmpty());
        assertTrue(layers.contains("left_nav_layer"));
        assertTrue(layers.contains("right_content_layer"));

        var containers = UiDebugRegistry.getAvailableContainerIds(lm);
        assertFalse(containers.isEmpty());
        assertTrue(containers.contains("pill_robot_name"));
        assertTrue(containers.contains("box_integrity"));

        var components = UiDebugRegistry.getAvailableComponentIds(lm);
        assertFalse(components.isEmpty());
        assertTrue(components.contains("title_dashboard"));
        assertTrue(components.contains("box_integrity_pb"));

        // Switch to MODES tab to discover mode containers
        ui.setCurrentTabAndRefresh(LunarRobotDashboardUi.Tab.MODES);
        var modeContainers = UiDebugRegistry.getAvailableContainerIds(ui.getCurrentLayerManager());
        assertTrue(modeContainers.contains("mode_idle"));

        // Switch to SETTINGS tab to discover settings containers & components
        ui.setCurrentTabAndRefresh(LunarRobotDashboardUi.Tab.SETTINGS);
        var settingsContainers = UiDebugRegistry.getAvailableContainerIds(ui.getCurrentLayerManager());
        assertTrue(settingsContainers.contains("card_modules"));
        var settingsComponents = UiDebugRegistry.getAvailableComponentIds(ui.getCurrentLayerManager());
        assertTrue(settingsComponents.contains("slot_0"));
    }

    @Test
    @DisplayName("9. Verify Inspector outputs complete properties for Layer, Container, and Component")
    public void testInspectProperties() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        LayerManager lm = ui.getCurrentLayerManager();

        // 1. Inspect Layer
        Layer leftLayer = lm.getLayer("left_nav_layer").orElseThrow();
        List<net.kyori.adventure.text.Component> layerInspect = UiDebugInspector.inspectLayer(leftLayer, ui.getDebugState());
        assertNotNull(layerInspect);
        assertFalse(layerInspect.isEmpty());
        assertTrue(layerInspect.size() >= 5);

        // 2. Inspect Container
        Container leftPanel = leftLayer.getContainer("left_panel").orElseThrow();
        List<net.kyori.adventure.text.Component> containerInspect = UiDebugInspector.inspectContainer(leftPanel, ui.getDebugState());
        assertNotNull(containerInspect);
        assertFalse(containerInspect.isEmpty());
        assertTrue(containerInspect.size() >= 8);

        // 3. Inspect Component
        vn.haohan.displayui.api.component.Component titleComp = leftPanel.findComponent("title_dashboard").orElseThrow();
        List<net.kyori.adventure.text.Component> compInspect = UiDebugInspector.inspectComponent(titleComp, ui.getDebugState());
        assertNotNull(compInspect);
        assertFalse(compInspect.isEmpty());
        assertTrue(compInspect.size() >= 8);
    }

    @Test
    @DisplayName("10. Verify inspectAny auto-discovers layer, container, and component by ID")
    public void testInspectAnyAutoDiscovery() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        LayerManager lm = ui.getCurrentLayerManager();

        // Auto-discover Layer
        List<net.kyori.adventure.text.Component> resLayer = UiDebugInspector.inspectAny(lm, ui.getDebugState(), "left_nav_layer");
        assertNotNull(resLayer);

        // Auto-discover Container
        List<net.kyori.adventure.text.Component> resCont = UiDebugInspector.inspectAny(lm, ui.getDebugState(), "pill_robot_name");
        assertNotNull(resCont);

        // Auto-discover Component
        List<net.kyori.adventure.text.Component> resComp = UiDebugInspector.inspectAny(lm, ui.getDebugState(), "box_integrity_pb");
        assertNotNull(resComp);

        // Non-existent target returns null
        List<net.kyori.adventure.text.Component> resUnknown = UiDebugInspector.inspectAny(lm, ui.getDebugState(), "non_existent_element_xyz");
        assertNull(resUnknown);
    }

    @Test
    @DisplayName("11. Verify Click-to-Inspect mode injects raycast hitboxes for non-button components & containers")
    public void testClickInspectModeEnablesHitboxes() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        // Standard render without click inspect
        UiDocument docStandard = ui.renderDocument();
        int normalButtonCount = docStandard.buttons().size();

        // Enable click inspect
        ui.getDebugState().setClickInspectEnabled(true);
        assertTrue(ui.getDebugState().isClickInspectEnabled());

        UiDocument docWithHitboxes = ui.renderDocument();
        int inspectButtonCount = docWithHitboxes.buttons().size();

        // Must have significantly more interactive hitboxes covering text, shapes, icons, and containers
        assertTrue(inspectButtonCount > normalButtonCount,
                "Click-to-Inspect mode must inject additional raycast hitboxes for all elements");

        // Verify presence of synthetic component hitboxes
        assertTrue(docWithHitboxes.buttons().stream().anyMatch(b -> b.id().equals("__inspect_comp_title_dashboard")));
        assertTrue(docWithHitboxes.buttons().stream().anyMatch(b -> b.id().equals("__inspect_cont_right_panel")));

        // Disable and verify cleanup
        ui.getDebugState().setClickInspectEnabled(false);
        UiDocument docRestored = ui.renderDocument();
        assertEquals(normalButtonCount, docRestored.buttons().size(),
                "Disabling click inspect must restore default button count");
    }

    @Test
    @DisplayName("12. Verify Click-to-Inspect intercepts handleButtonClick without triggering gameplay actions")
    public void testClickInspectButtonClickInterception() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        ui.setCurrentTab(LunarRobotDashboardUi.Tab.OVERVIEW);

        // Enable click inspect
        ui.getDebugState().setClickInspectEnabled(true);

        // Click nav button "btn_nav_modes" in inspect mode - tab should NOT change to MODES
        ui.handleButtonClick("btn_nav_modes");
        assertEquals(LunarRobotDashboardUi.Tab.OVERVIEW, ui.getCurrentTab(),
                "Clicking in Click-to-Inspect mode must NOT change tab");

        // Click synthetic inspect button - should not error or change state
        assertDoesNotThrow(() -> ui.handleButtonClick("__inspect_comp_title_dashboard"));
        assertEquals(LunarRobotDashboardUi.Tab.OVERVIEW, ui.getCurrentTab());

        // Turn off click inspect - regular button click now changes tab
        ui.getDebugState().setClickInspectEnabled(false);
        ui.handleButtonClick("btn_nav_modes");
        assertEquals(LunarRobotDashboardUi.Tab.MODES, ui.getCurrentTab(),
                "Clicking in normal mode must change tab to MODES");
    }

    @Test
    @DisplayName("13. Verify Animation Overrides on Components and Containers compile with animations")
    public void testAnimationApplicationOnComponentAndContainer() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        var anim = vn.haohan.displayui.api.animation.UiAnimation.builder().scale(0.8f, 1.0f).durationTicks(15).build();
        ui.getDebugState().setComponentAnimation("btn_nav_modes", anim);
        ui.getDebugState().setContainerAnimation("pill_robot_name", anim);

        LayerManager lm = ui.getCurrentLayerManager();
        var compiled = vn.haohan.displayui.api.bridge.UiDocumentBridge.compileWithAnimations(lm);

        assertTrue(compiled.hasAnimations(), "Compiled UI must contain active animations");
        assertFalse(compiled.nodeAnimations().isEmpty());
        assertTrue(compiled.nodeAnimations().stream().anyMatch(a -> a != null && a.durationTicks() == 15),
                "Nodes of animated component and container must inherit durationTicks=15");
    }
}
