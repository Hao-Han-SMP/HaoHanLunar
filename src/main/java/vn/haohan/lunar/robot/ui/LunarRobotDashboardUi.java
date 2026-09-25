package vn.haohan.lunar.robot.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import vn.haohan.displayui.api.DisplayUiService;
import vn.haohan.displayui.api.UiDocument;
import vn.haohan.displayui.api.UiHandle;
import vn.haohan.displayui.api.UiOptions;
import vn.haohan.displayui.api.view.UiFollowOptions;
import vn.haohan.displayui.api.animation.Easings;
import vn.haohan.displayui.api.animation.UiAnimation;
import vn.haohan.displayui.api.animation.UiEffects;
import vn.haohan.displayui.api.gradient.UiGradient;
import vn.haohan.displayui.api.gradient.UiGradientPosition;
import vn.haohan.displayui.api.interaction.UiButton;
import vn.haohan.displayui.api.interaction.UiButtonAction;
import vn.haohan.displayui.api.interaction.UiControlChange;
import vn.haohan.displayui.api.interaction.UiScrollList;
import vn.haohan.displayui.api.layout.UiCameraTransform;
import org.bukkit.entity.ItemDisplay;
import vn.haohan.displayui.api.node.AlignedTextNode;
import vn.haohan.displayui.api.node.UiBackgroundNode;
import vn.haohan.displayui.api.node.UiGradientBackgroundNode;
import vn.haohan.displayui.api.node.UiIconNode;
import vn.haohan.displayui.api.node.UiShapeNode;
import vn.haohan.displayui.api.text.UiText;
import vn.haohan.displayui.api.text.UiTextAlignment;
import vn.haohan.displayui.api.text.UiVerticalAlignment;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.robot.LunarRobotData;
import vn.haohan.lunar.robot.LunarRobotEntity;
import vn.haohan.lunar.robot.LunarRobotMechanic;
import vn.haohan.lunar.robot.RobotTask;
import vn.haohan.lunar.robot.battery.RobotBatteryUtil;
import vn.haohan.displayui.api.layer.Layer;
import vn.haohan.displayui.api.layer.LayerManager;
import vn.haohan.displayui.api.container.Container;
import vn.haohan.displayui.api.container.DropdownAnimationType;
import vn.haohan.displayui.api.container.DropdownContainer;
import vn.haohan.displayui.api.component.ButtonComponent;
import vn.haohan.displayui.api.component.TextComponent;
import vn.haohan.displayui.api.component.IconComponent;
import vn.haohan.displayui.api.component.ShapeComponent;
import vn.haohan.displayui.api.component.CustomNodeComponent;
import vn.haohan.displayui.api.bridge.UiDocumentBridge;
import vn.haohan.displayui.api.layout.UiAnchorPoint;
import vn.haohan.displayui.api.node.UiNode;
import vn.haohan.displayui.api.debug.UiDebugInspector;
import vn.haohan.displayui.api.debug.UiDebugState;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Modern, rich UI/UX Dashboard for the Lunar Robot Quadruped.
 * Rebuilt from scratch according to lunar_design.pdf specifications and updated HaoHanDisplayUI APIs.
 */
public class LunarRobotDashboardUi {

    // Global layout coordinate constants (used for rendering & verified by layout unit tests)
    public static final float TOTAL_WIDTH = 224.0f;
    public static final float TOTAL_HEIGHT = 126.0f;
    public static final float MIN_X = -112.0f;
    public static final float MAX_X = 112.0f;
    public static final float MIN_Y = -63.0f;
    public static final float MAX_Y = 63.0f;
    public static final float PIXELS_PER_BLOCK = 75.0f;

    // Corner radius constants for modern rounded UI/UX design
    public static final float PANEL_CORNER_RADIUS = 6.0f;
    public static final float CARD_CORNER_RADIUS = 5.0f;
    public static final float BUTTON_CORNER_RADIUS = 4.0f;
    public static final float PILL_CORNER_RADIUS = 4.0f;
    public static final float SLOT_CORNER_RADIUS = 5.0f;
    public static final float SMALL_BTN_CORNER_RADIUS = 3.0f;

    public static final float LEFT_PANEL_X = -106.0f;
    public static final float LEFT_PANEL_Y = -60.0f;
    public static final float LEFT_PANEL_W = 62.0f;
    public static final float LEFT_PANEL_H = 120.0f;

    public static final float LEFT_BTN_W = 54.0f;
    public static final float LEFT_BTN_H = 18.0f;
    public static final float BTN_MODES_Y = -35.0f;
    public static final float BTN_SETTINGS_Y = -13.0f;
    public static final float BTN_EXIT_Y = 9.0f;

    public static final float RIGHT_PANEL_X = -38.0f;
    public static final float RIGHT_PANEL_Y = -60.0f;
    public static final float RIGHT_PANEL_W = 144.0f;
    public static final float RIGHT_PANEL_H = 120.0f;

    public static final float INFO_CARD_Y = -60.0f;
    public static final float INFO_CARD_H = 46.0f;
    public static final float STATUS_CARD_Y = -10.0f;
    public static final float STATUS_CARD_H = 70.0f;

    public static final float SUB_CARD_W = 62.0f;
    public static final float SUB_CARD_H = 24.0f;
    public static final float SUB_CARD_CONTENT_X = 24.0f;
    public static final float SUB_CARD_CONTENT_W = 34.0f;

    // 2-line symmetry constants (Center-to-Center distance Delta = 3.5f from Axis Y = 12.0f):
    public static final float LINE_1_Y_OFFSET = 5.0f;
    public static final float LINE_2_Y_OFFSET = 12.0f;
    public static final float PROGRESS_BAR_Y_OFFSET = 11.75f;

    // 3-line symmetry constants (Module installed, centered at Y = 12.0f, box mid at 8.5f):
    public static final float LINE_3_ROW1_OFFSET = 3.0f;
    public static final float LINE_3_ROW2_OFFSET = 8.5f;
    public static final float LINE_3_ROW3_OFFSET = 14.0f;

    public static final float MODE_BTN_W = 42.0f;
    public static final float MODE_BTN_H = 36.0f;

    public static final float ACCORDION_CARD_W = 136.0f;
    public static final float ACCORDION_CARD_H = 20.0f;

    public static final float MODULE_SLOT_W = 38.0f;
    public static final float MODULE_SLOT_H = 34.0f;

    public static final float UNBIND_BTN_W = 54.0f;
    public static final float UNBIND_BTN_H = 18.0f;

    public static final int SPLASH_DURATION_TICKS = 20; // Exactly 1.0s logo splash screen
    public static final int CLOSING_DURATION_TICKS = 8;  // Smooth zoom in + fade exit

    // Unified master frame constants (enclosing both Left & Right panels as one cohesive unit)
    public static final float MASTER_FRAME_X = -108.0f;
    public static final float MASTER_FRAME_Y = -62.0f;
    public static final float MASTER_FRAME_W = 216.0f;
    public static final float MASTER_FRAME_H = 124.0f;

    public static final float ACCORDION_HEADER_H = 18.0f;
    public static final float ACCORDION_GAP = 4.0f;

    public enum UiState {
        SPLASH,
        DASHBOARD,
        CLOSING
    }

    public enum Tab {
        OVERVIEW,
        MODES,
        SETTINGS
    }

    public enum SettingsCard {
        COLLAPSED,
        MODULES,
        HEALTH,
        CUSTOMIZE,
        UNBIND
    }

    private final Plugin plugin;
    private final DisplayUiService uiService;
    private final LunarRobotMechanic mechanic;
    private final Player player;
    private final LunarRobotEntity robot;
    private final LunarRobotData data;

    private UiState state = UiState.SPLASH;
    private Tab currentTab = Tab.OVERVIEW;
    private SettingsCard expandedSettingsCard = SettingsCard.COLLAPSED;
    private boolean isAwaitingRename = false;
    private final UiDebugState debugState = new UiDebugState();

    private UiHandle handle;
    private UiDocument currentDocument;
    private World ticketWorld;
    private int ticketX;
    private int ticketZ;
    private Location origin;
    private Listener renameListener;
    private BukkitTask renameTimeoutTask;
    private BukkitTask splashTask;

    public LunarRobotDashboardUi(Plugin plugin, DisplayUiService uiService, LunarRobotMechanic mechanic,
                                 Player player, LunarRobotEntity robot) {
        this.plugin = plugin;
        this.uiService = uiService;
        this.mechanic = mechanic;
        this.player = player;
        this.robot = robot;
        this.data = (robot != null && robot.getData() != null) ? robot.getData() : new LunarRobotData(UUID.randomUUID());
    }

    public LunarRobotDashboardUi(LunarRobotData data) {
        this.plugin = null;
        this.uiService = null;
        this.mechanic = null;
        this.player = null;
        this.robot = null;
        this.data = (data != null) ? data : new LunarRobotData(UUID.randomUUID());
        this.state = UiState.DASHBOARD;
    }

    public static UiAnimation createZoomOutAnimation() {
        return UiAnimation.builder()
                .scale(1.06f, 1.0f)
                .opacity(0.70f, 1.0f)
                .durationTicks(8)
                .easing(Easings.OutCubic)
                .build();
    }

    public static UiAnimation createClosingAnimation() {
        return UiAnimation.builder()
                .scale(1.0f, 1.22f)
                .opacity(1.0f, 0.0f)
                .durationTicks(CLOSING_DURATION_TICKS)
                .easing(Easings.InCubic)
                .build();
    }

    public static UiAnimation createSlideFadeAnimation(boolean fromRight) {
        return UiAnimation.builder()
                .offset(fromRight ? UiAnimation.Direction.RIGHT : UiAnimation.Direction.LEFT, 16.0f)
                .opacity(0.50f, 1.0f)
                .durationTicks(8)
                .easing(Easings.OutQuad)
                .build();
    }

    public static final UiAnimation STATIC_ANIMATION = UiAnimation.builder()
            .durationTicks(8)
            .offset(0.0f, 0.0f, 0.0f)
            .opacity(1.0f, 1.0f)
            .scale(1.0f, 1.0f)
            .build();

    public static final UiAnimation FADE_SELECTION_ANIMATION = UiAnimation.builder()
            .durationTicks(6)
            .offset(0.0f, 0.0f, 0.0f)
            .opacity(0.25f, 1.0f)
            .scale(1.0f, 1.0f)
            .easing(Easings.OutCubic)
            .build();

    public static List<UiAnimation> createTabSwitchAnimations(UiDocument doc, boolean fromRight) {
        List<UiAnimation> anims = new ArrayList<>(doc.nodes().size());
        UiAnimation rightPanelAnim = createSlideFadeAnimation(fromRight);

        for (UiNode node : doc.nodes()) {
            // Layer 0: Background gradient panels (depth <= 0.002f) MUST ALWAYS stay completely static
            if (node.depth() <= 0.002f) {
                anims.add(STATIC_ANIMATION);
            } else if (node.x() >= RIGHT_PANEL_X) {
                // Keep the right panel fixed shield/header area static during tab transitions
                if (node.depth() >= 0.012f) {
                    anims.add(STATIC_ANIMATION);
                } else {
                    anims.add(rightPanelAnim);
                }
            } else if (node.depth() >= 0.003f && node.y() >= BTN_MODES_Y - 2.0f && node.y() <= BTN_SETTINGS_Y + LEFT_BTN_H + 2.0f) {
                // Nav buttons in left panel (Chế độ / Cài đặt): fade selection transition in-place
                anims.add(FADE_SELECTION_ANIMATION);
            } else {
                // Left panel outer container, title, exit button: stay completely static in 3D space
                anims.add(STATIC_ANIMATION);
            }
        }
        return anims;
    }

    private void animateTabTransition(boolean fromRight) {
        // Kept purely static during investigation
    }

    public void open() {
        uiService.removeOwnedBy("haohanlunar:robot_dashboard/" + player.getUniqueId());

        if (robot != null && robot.getEntity() != null && robot.getEntity().isValid()) {
            Location loc = robot.getEntity().getLocation();
            ticketWorld = loc.getWorld();
            if (ticketWorld != null) {
                ticketX = loc.getBlockX() >> 4;
                ticketZ = loc.getBlockZ() >> 4;
                ticketWorld.addPluginChunkTicket(ticketX, ticketZ, plugin);
            }
        }

        this.origin = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(2.2));
        this.origin.setYaw(player.getEyeLocation().getYaw() + 180.0f);
        this.origin.setPitch(-player.getEyeLocation().getPitch());

        this.state = UiState.SPLASH;
        UiDocument doc = renderSplashDocument();
        UiOptions options = new UiOptions(PIXELS_PER_BLOCK, 8.0, false, 1.0f, "haohan_robot_dashboard",
                UiCameraTransform.fixed());

        handle = uiService.create("haohanlunar:robot_dashboard/" + player.getUniqueId(), this.origin, doc, options,
                candidate -> candidate.getUniqueId().equals(player.getUniqueId()));

        // Splash entry animation (gentle fade in)
        handle.animate(UiEffects.fadeIn(8));

        UiFollowOptions followOptions = UiFollowOptions.builder()
                .distance(2.2)
                .bounds(MIN_X, MAX_X, MIN_Y, MAX_Y)
                .build();
        handle.follow(player, followOptions);

        handle.onClick(click -> handleButtonClick(click.button().id()));
        handle.onControlChange(this::handleControlChange);

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 2.0f);

        if (uiService != null && player != null) {
            uiService.debug().registerSession(player.getUniqueId(), new vn.haohan.displayui.api.debug.UiDebugSession() {
                @Override
                public String name() {
                    return "Lunar Robot Dashboard (" + (data != null && data.getName() != null ? data.getName() : "Robot") + ")";
                }

                @Override
                public LayerManager getCurrentLayerManager() {
                    return LunarRobotDashboardUi.this.getCurrentLayerManager();
                }

                @Override
                public vn.haohan.displayui.api.debug.UiDebugState getDebugState() {
                    return debugState;
                }

                @Override
                public void forceUpdate() {
                    LunarRobotDashboardUi.this.forceUpdate();
                }
            });
        }

        // Schedule transition to full dashboard after exactly 1.0s (20 ticks)
        splashTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::transitionToDashboard, SPLASH_DURATION_TICKS);
    }

    private void transitionToDashboard() {
        splashTask = null;
        if (state == UiState.CLOSING || handle == null || !handle.isValid() || !player.isOnline()) {
            return;
        }
        state = UiState.DASHBOARD;
        currentDocument = renderDocument();
        handle.update(currentDocument);
        handle.animate(createZoomOutAnimation());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.8f);
    }

    public void handleButtonClick(String btnId) {
        if (state == UiState.CLOSING) {
            return;
        }

        // Intercept for Click-to-Inspect mode or synthetic inspect hitboxes
        if (btnId != null && (btnId.startsWith("__inspect_") || debugState.isClickInspectEnabled())) {
            String targetId = btnId;
            if (targetId.startsWith("__inspect_comp_")) {
                targetId = targetId.substring("__inspect_comp_".length());
            } else if (targetId.startsWith("__inspect_cont_")) {
                targetId = targetId.substring("__inspect_cont_".length());
            } else if (targetId.startsWith("__inspect_layer_")) {
                targetId = targetId.substring("__inspect_layer_".length());
            }

            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.8f);
                boolean found = UiDebugInspector.inspectAndSend(
                        player, getCurrentLayerManager(), targetId, null, debugState);
                if (!found) {
                    UiDebugInspector.inspectAndSend(
                            player, getCurrentLayerManager(), btnId, null, debugState);
                }
            }
            return;
        }

        if (state == UiState.SPLASH) {
            return;
        }

        switch (btnId) {
            // Navigation with smooth tab switch transitions
            case "btn_nav_overview" -> {
                if (currentTab != Tab.OVERVIEW) {
                    currentTab = Tab.OVERVIEW;
                    playClickSound(1.2f);
                    updateUi();
                    animateTabTransition(false);
                }
            }
            case "btn_nav_modes" -> {
                if (currentTab != Tab.MODES) {
                    boolean fromRight = (currentTab == Tab.OVERVIEW);
                    currentTab = Tab.MODES;
                    playClickSound(1.3f);
                    updateUi();
                    animateTabTransition(fromRight);
                }
            }
            case "btn_nav_settings" -> {
                if (currentTab != Tab.SETTINGS) {
                    currentTab = Tab.SETTINGS;
                    expandedSettingsCard = SettingsCard.COLLAPSED;
                    playClickSound(1.3f);
                    updateUi();
                    animateTabTransition(true);
                }
            }
            case "btn_nav_exit" -> {
                playClickSound(0.9f);
                closeWithAnimation();
            }
            case "frame_hitbox", "splash_hitbox" -> {
                // Background frame click consumed safely
            }

            // Modes
            case "mode_idle" -> {
                data.setActiveTask(RobotTask.IDLE);
                robot.updateCustomName();
                playClickSound(1.2f);
                player.sendMessage("§7§l[Robot] §eĐã chuyển sang chế độ: §7§lNGHỈ NGƠI / THEO CHỦ");
                updateUi();
            }
            case "mode_ore" -> {
                if (data.hasModule("ore_scan")) {
                    robot.dismountRider();
                    data.setActiveTask(RobotTask.ORE_SCAN);
                    robot.updateCustomName();
                    playClickSound(1.4f);
                    player.sendMessage("§6§l[Robot] §eĐã chuyển sang chế độ: §6§lDÒ QUẶNG");
                    updateUi();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    player.sendMessage("§c§l[Robot] §cBạn chưa lắp Module Dò Quặng!");
                }
            }
            case "mode_combat" -> {
                if (data.hasModule("combat")) {
                    robot.dismountRider();
                    data.setActiveTask(RobotTask.COMBAT);
                    robot.updateCustomName();
                    playClickSound(1.4f);
                    player.sendMessage("§c§l[Robot] §eĐã chuyển sang chế độ: §c§lCHIẾN ĐẤU");
                    updateUi();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    player.sendMessage("§c§l[Robot] §cBạn chưa lắp Module Chiến Đấu!");
                }
            }
            case "mode_speed" -> {
                if (data.hasModule("speed")) {
                    data.setActiveTask(RobotTask.SPEED);
                    robot.updateCustomName();
                    playClickSound(1.4f);
                    player.sendMessage("§b§l[Robot] §eĐã chuyển sang chế độ: §b§lTỐC HÀNH (Chuột phải robot để cưỡi)");
                    updateUi();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    player.sendMessage("§c§l[Robot] §cBạn chưa lắp Module Tốc Hành!");
                }
            }
            case "mode_thrust" -> {
                if (data.hasModule("thrust")) {
                    data.setActiveTask(RobotTask.THRUST);
                    robot.updateCustomName();
                    playClickSound(1.4f);
                    player.sendMessage("§e§l[Robot] §eĐã chuyển sang chế độ: §e§lĐẨY PHẢN LỰC (Chuột phải robot để cưỡi)");
                    updateUi();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    player.sendMessage("§c§l[Robot] §cBạn chưa lắp Module Đẩy Phản Lực!");
                }
            }
            case "mode_follow" -> {
                data.setFollowOwner(!data.isFollowOwner());
                playClickSound(1.2f);
                player.sendMessage("§b§l[Robot] §7Theo chủ: " + (data.isFollowOwner() ? "§aBẬT" : "§cTẮT"));
                updateUi();
            }

            // Settings Accordion Cards (Dropdown in-place)
            case "card_modules", "card_modules_toggle", "dropdown_modules_toggle" -> {
                toggleSettingsCard(SettingsCard.MODULES);
            }
            case "card_health", "card_health_toggle", "dropdown_health_toggle" -> {
                toggleSettingsCard(SettingsCard.HEALTH);
            }
            case "card_customize", "card_customize_toggle", "dropdown_customize_toggle" -> {
                toggleSettingsCard(SettingsCard.CUSTOMIZE);
            }
            case "card_unbind", "card_unbind_toggle", "dropdown_unbind_toggle" -> {
                toggleSettingsCard(SettingsCard.UNBIND);
            }
            case "collapse_card" -> {
                toggleSettingsCard(SettingsCard.COLLAPSED);
            }

            // Customize Actions
            case "action_change_theme" -> {
                LunarDashboardTheme nextTheme = data.getColorTheme().next();
                data.setColorTheme(nextTheme);
                playClickSound(1.5f);
                player.sendMessage("§a§l[Robot] §eĐã đổi màu giao diện sang: §b" + nextTheme.getDisplayName());
                updateUi();
            }
            case "action_rename_robot" -> {
                startRenamePrompt();
            }

            // Unbind Actions
            case "confirm_unbind" -> {
                closeImmediate();
                robot.dismountRider();
                mechanic.unbindRobot(player, robot);
            }
            case "cancel_unbind" -> {
                toggleSettingsCard(SettingsCard.COLLAPSED);
            }

            default -> {
                // Interactive Module Slot Clicks (slot_0, slot_1, slot_2)
                if (btnId.startsWith("slot_")) {
                    try {
                        int slot = Integer.parseInt(btnId.substring(5));
                        handleModuleSlotInteraction(slot);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    private void handleControlChange(UiControlChange change) {
    }

    private void toggleSettingsCard(SettingsCard card) {
        if (expandedSettingsCard == card) {
            expandedSettingsCard = SettingsCard.COLLAPSED;
            playClickSound(1.0f);
        } else {
            expandedSettingsCard = card;
            if (card == SettingsCard.UNBIND) {
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                }
            } else {
                playClickSound(1.2f);
            }
        }
        updateUi();
    }

    private void handleModuleSlotInteraction(int slot) {
        if (slot < 0 || slot > 2) return;

        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (slot == 0 || slot == 1) {
            // SLOTS 1 & 2: MODULE SLOTS
            if (RobotBatteryUtil.isBatteryItem(handItem)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
                player.sendMessage("§c§l[Robot] §cÔ " + (slot + 1) + " chỉ dành cho Module! Pin hãy đặt ở Ô 3.");
                return;
            }

            String oldModId = data.getModuleId(slot);

            if (handItem != null && handItem.getType() != Material.AIR && isModuleItem(handItem)) {
                // Player is holding a module -> Install or Swap
                String newModId = getCustomItemId(handItem);
                double newEff = getEfficiency(handItem);

                // Install new module
                data.setModuleId(slot, newModId);
                data.setModuleEfficiency(slot, newEff);

                // Consume 1 item from hand
                if (handItem.getAmount() > 1) {
                    handItem.setAmount(handItem.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }

                // Return old module to inventory if present
                if (oldModId != null) {
                    giveItemToPlayer(createModuleItem(oldModId));
                }

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
                player.sendMessage("§a§l[Robot] §eĐã lắp đặt thành công: §b" + formatModuleName(newModId) + " §evào Ô " + (slot + 1));
                robot.updateCustomName();
                updateUi();
            } else {
                // Player hand is empty or not holding a module -> Remove if slot is occupied
                if (oldModId != null) {
                    data.setModuleId(slot, null);
                    data.setModuleEfficiency(slot, 100.0);

                    giveItemToPlayer(createModuleItem(oldModId));

                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.7f, 1.2f);
                    player.sendMessage("§e§l[Robot] §cĐã tháo: §f" + formatModuleName(oldModId) + " §ctừ Ô " + (slot + 1));
                    robot.updateCustomName();
                    updateUi();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
                    player.sendMessage("§c§l[Robot] §7Hãy cầm module trên tay và bấm vào ô để lắp!");
                }
            }
        } else {
            // SLOT 3: BATTERY SLOT
            if (isModuleItem(handItem)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
                player.sendMessage("§c§l[Robot] §cÔ 3 chỉ dành cho Pin! Module hãy đặt ở Ô 1 hoặc Ô 2.");
                return;
            }

            String oldType = data.getBatteryType();
            int oldEnergy = data.getEnergy();
            boolean hasOldBattery = oldType != null && !"none".equalsIgnoreCase(oldType) && RobotBatteryUtil.getCapacity(oldType) > 0;

            if (RobotBatteryUtil.isBatteryItem(handItem)) {
                // Player is holding a battery -> Install or Swap
                String newType = RobotBatteryUtil.getBatteryType(handItem);
                int newEnergy = RobotBatteryUtil.getBatteryEnergy(handItem);

                // Install new battery
                data.setBatteryType(newType);
                data.setEnergy(newEnergy);

                // Consume 1 item from hand
                if (handItem.getAmount() > 1) {
                    handItem.setAmount(handItem.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }

                // Return old battery to player if present
                if (hasOldBattery) {
                    giveItemToPlayer(RobotBatteryUtil.createBattery(oldType, oldEnergy));
                }

                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.4f);
                player.sendMessage("§a§l[Robot] §eĐã nạp Pin §a(" + formatBatteryName(newType) + ") §evào Robot! Năng lượng: §a" + String.format("%,d", newEnergy) + "§7/§f" + String.format("%,d", data.getMaxEnergy()) + " EU");
                robot.updateCustomName();
                updateUi();
            } else {
                // Player hand is empty or not holding a battery -> Remove if occupied
                if (hasOldBattery) {
                    data.setBatteryType("none");
                    data.setEnergy(0);

                    giveItemToPlayer(RobotBatteryUtil.createBattery(oldType, oldEnergy));

                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.7f, 1.2f);
                    player.sendMessage("§e§l[Robot] §cĐã tháo: §f" + formatBatteryName(oldType) + " §ctừ Ô 3 (Pin)");
                    robot.updateCustomName();
                    updateUi();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
                    player.sendMessage("§c§l[Robot] §7Hãy cầm Pin trên tay và bấm vào ô để lắp!");
                }
            }
        }
    }

    private void giveItemToPlayer(ItemStack item) {
        if (item == null) return;
        var leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack drop : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private ItemStack createModuleItem(String moduleId) {
        if (moduleId == null || moduleId.isBlank() || moduleId.equalsIgnoreCase("empty")) {
            moduleId = "haohan:empty_module_slot";
        } else if (!moduleId.contains(":")) {
            moduleId = "haohan:robot_module_" + moduleId.toLowerCase();
        }
        try {
            ItemStack item = HaoHanItemCore.get().getItemFactory().create(moduleId, 1);
            if (item != null) return item;
        } catch (Throwable ignored) {}
        try {
            return new ItemStack(Material.PAPER);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isModuleItem(ItemStack item) {
        String id = getCustomItemId(item);
        return id != null && id.contains("haohan:robot_module_");
    }

    private String getCustomItemId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        try {
            String id = HaoHanItemCore.get().getItemService().getId(item);
            if (id != null) return id;
        } catch (Throwable ignored) {}
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id1 = pdc.get(new org.bukkit.NamespacedKey("haohanitemcore", "item_id"), PersistentDataType.STRING);
        if (id1 != null) return id1;
        return pdc.get(new org.bukkit.NamespacedKey("haohan", "item_id"), PersistentDataType.STRING);
    }

    private double getEfficiency(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return 100.0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Double eff = pdc.get(new org.bukkit.NamespacedKey("haohanitemcore", "module_efficiency"), PersistentDataType.DOUBLE);
        if (eff == null) {
            eff = pdc.get(new org.bukkit.NamespacedKey("haohan", "module_efficiency"), PersistentDataType.DOUBLE);
        }
        return eff != null ? eff : 100.0;
    }

    private void startRenamePrompt() {
        isAwaitingRename = true;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.5f);
        player.sendMessage(" ");
        player.sendMessage("§b§l[Robot] §eHãy nhập tên mới cho robot vào khung chat!");
        player.sendMessage("§7(Gõ §c'cancel'§7 hoặc chờ 30 giây để hủy bỏ)");
        player.sendMessage(" ");
        updateUi();

        renameListener = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncPlayerChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                event.setCancelled(true);

                String msg = event.getMessage().trim();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    cleanupRenamePrompt();
                    isAwaitingRename = false;
                    if (msg.equalsIgnoreCase("cancel")) {
                        player.sendMessage("§c§l[Robot] §7Đã hủy đổi tên robot.");
                        updateUi();
                        return;
                    }
                    if (msg.length() > 24) {
                        player.sendMessage("§c§l[Robot] §cTên quá dài! Tối đa 24 ký tự.");
                        updateUi();
                        return;
                    }
                    data.setName(msg);
                    robot.updateCustomName();
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                    player.sendMessage("§a§l[Robot] §eĐã đổi tên robot thành: §b" + msg);
                    updateUi();
                });
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    cleanupRenamePrompt();
                }
            }
        };

        plugin.getServer().getPluginManager().registerEvents(renameListener, plugin);
        renameTimeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            cleanupRenamePrompt();
            isAwaitingRename = false;
            player.sendMessage("§c§l[Robot] §7Hết thời gian nhập tên robot.");
            updateUi();
        }, 600L); // 30s
    }

    private void cleanupRenamePrompt() {
        if (renameListener != null) {
            HandlerList.unregisterAll(renameListener);
            renameListener = null;
        }
        if (renameTimeoutTask != null) {
            renameTimeoutTask.cancel();
            renameTimeoutTask = null;
        }
        isAwaitingRename = false;
    }

    private void playClickSound(float pitch) {
        if (player != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, pitch);
        }
    }

    private void updateUi() {
        if (handle != null && handle.isValid() && state == UiState.DASHBOARD) {
            currentDocument = renderDocument();
            handle.update(currentDocument);
        }
    }

    public UiDocument getCurrentDocument() {
        return currentDocument;
    }

    public void tick() {
    }

    public void closeWithAnimation() {
        if (!plugin.isEnabled()) {
            closeImmediate();
            return;
        }
        if (state == UiState.CLOSING) return;
        state = UiState.CLOSING;
        if (splashTask != null) {
            splashTask.cancel();
            splashTask = null;
        }
        cleanupRenamePrompt();
        if (handle != null && handle.isValid() && player.isOnline()) {
            handle.animate(createClosingAnimation());
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.6f);
            plugin.getServer().getScheduler().runTaskLater(plugin, this::closeImmediate, CLOSING_DURATION_TICKS);
        } else {
            closeImmediate();
        }
    }

    public void closeImmediate() {
        cleanupRenamePrompt();
        if (splashTask != null) {
            splashTask.cancel();
            splashTask = null;
        }
        if (ticketWorld != null) {
            try {
                ticketWorld.removePluginChunkTicket(ticketX, ticketZ, plugin);
            } catch (Exception ignored) {}
            ticketWorld = null;
        }
        if (handle != null) {
            handle.remove();
            handle = null;
        }
        if (uiService != null && player != null) {
            uiService.debug().unregisterSession(player.getUniqueId());
        }
        mechanic.unregisterActiveDashboard(player.getUniqueId());
    }

    public void close() {
        if (!plugin.isEnabled()) {
            closeImmediate();
        } else {
            closeWithAnimation();
        }
    }

    public Player getPlayer() {
        return player;
    }

    public LunarRobotEntity getRobot() {
        return robot;
    }

    public UiState getState() {
        return state;
    }

    public SettingsCard getExpandedSettingsCard() {
        return expandedSettingsCard;
    }

    public boolean isInRobotSettings() {
        return currentTab == Tab.SETTINGS;
    }

    public Tab getCurrentTab() {
        return currentTab;
    }

    public void setCurrentTab(Tab tab) {
        this.currentTab = tab;
    }

    public void setExpandedSettingsCard(SettingsCard card) {
        this.expandedSettingsCard = card;
    }

    // ==========================================
    // DOCUMENT RENDERING (LUNAR DESIGN PDF SPECS)
    // ==========================================

    public static void addRoundedRect(UiDocument.Builder b, float x, float y, float depth, float w, float h, Color color, float radius) {
        b.add(UiShapeNode.builder("rounded_rect", x, y, w, h)
                .color(color)
                .cornerRadius(radius)
                .depth(depth)
                .build());
    }

    public static void addRoundedGradient(UiDocument.Builder b, float x, float y, float depth, float width, float height,
                                          UiGradient gradient, float cornerRadius) {
        if (cornerRadius <= 0.5f) {
            b.add(new UiGradientBackgroundNode(x, y, depth, width, height, gradient));
            return;
        }

        float r = Math.max(0, Math.min(cornerRadius, Math.min(width * 0.5f, height * 0.5f)));
        int slicesX = gradient.isVertical() ? 1 : 6;
        int slicesY = gradient.isHorizontal() ? 1 : 6;
        int cornerSteps = 6;
        float stepH = r / cornerSteps;

        // 1. Top corner slices
        for (int i = 0; i < cornerSteps; i++) {
            float sliceY = y + i * stepH;
            float midY = (i + 0.5f) * stepH;
            float d = r - midY;
            float indent = (float) (r - Math.sqrt(Math.max(0, r * r - d * d)));
            float rowX = x + indent;
            float rowW = width - 2.0f * indent;
            if (rowW > 0.05f && stepH > 0.05f) {
                float cellW = rowW / slicesX;
                for (int c = 0; c < slicesX; c++) {
                    float cellX = rowX + c * cellW;
                    float u = (cellX + cellW * 0.5f - x) / width;
                    float v = (sliceY + stepH * 0.5f - y) / height;
                    Color color = gradient.evaluate(u, v);
                    b.add(new UiBackgroundNode(cellX, sliceY, depth, cellW, stepH, color));
                }
            }
        }

        // 2. Middle main rectangular body
        float middleH = height - 2.0f * r;
        if (middleH > 0.05f) {
            float midStepH = middleH / slicesY;
            float cellW = width / slicesX;
            for (int row = 0; row < slicesY; row++) {
                float sliceY = y + r + row * midStepH;
                for (int c = 0; c < slicesX; c++) {
                    float cellX = x + c * cellW;
                    float u = (cellX + cellW * 0.5f - x) / width;
                    float v = (sliceY + midStepH * 0.5f - y) / height;
                    Color color = gradient.evaluate(u, v);
                    b.add(new UiBackgroundNode(cellX, sliceY, depth, cellW, midStepH, color));
                }
            }
        }

        // 3. Bottom corner slices
        for (int i = 0; i < cornerSteps; i++) {
            float sliceY = y + height - r + i * stepH;
            float midY = (i + 0.5f) * stepH;
            float d = midY;
            float indent = (float) (r - Math.sqrt(Math.max(0, r * r - d * d)));
            float rowX = x + indent;
            float rowW = width - 2.0f * indent;
            if (rowW > 0.05f && stepH > 0.05f) {
                float cellW = rowW / slicesX;
                for (int c = 0; c < slicesX; c++) {
                    float cellX = rowX + c * cellW;
                    float u = (cellX + cellW * 0.5f - x) / width;
                    float v = (sliceY + stepH * 0.5f - y) / height;
                    Color color = gradient.evaluate(u, v);
                    b.add(new UiBackgroundNode(cellX, sliceY, depth, cellW, stepH, color));
                }
            }
        }
    }

    public LayerManager buildSplashLayerManager() {
        LayerManager manager = new LayerManager();
        LunarDashboardTheme theme = data.getColorTheme();
        Color gradStart = theme.getGradStart();
        Color gradEnd = theme.getGradEnd();

        Layer splashLayer = manager.createLayer("splash_layer", 0);
        splashLayer.bounds(MIN_X, MIN_Y, TOTAL_WIDTH, TOTAL_HEIGHT);

        float cardW = 120.0f;
        float cardH = 72.0f;

        Container splashCard = Container.builder("splash_card")
                .anchor(UiAnchorPoint.CENTER)
                .origin(UiAnchorPoint.CENTER)
                .size(cardW, cardH)
                .backgroundColor(gradStart)
                .borderRound(CARD_CORNER_RADIUS)
                .build();

        ItemStack logoItem = null;
        try {
            logoItem = HaoHanItemCore.get().getItemFactory().create("haohan:robot_tablet", 1);
            if (logoItem == null) logoItem = new ItemStack(Material.NETHER_STAR);
        } catch (Throwable ignored) {}

        final ItemStack finalLogoItem = logoItem;
        splashCard.addComponent(CustomNodeComponent.builder("splash_logo")
                .anchor(UiAnchorPoint.CENTER_TOP)
                .origin(UiAnchorPoint.CENTER_TOP)
                .offset(0.0f, 8.0f)
                .size(26.0f, 26.0f)
                .factory((x, y, w, h, depth, scale) -> finalLogoItem != null ? new UiIconNode(
                        finalLogoItem, x, y, depth, w, h, 20.0f, 20.0f, ItemDisplay.ItemDisplayTransform.FIXED, false
                ) : null)
                .build());

        splashCard.addComponent(TextComponent.builder("splash_title")
                .anchor(UiAnchorPoint.CENTER_TOP)
                .origin(UiAnchorPoint.CENTER_TOP)
                .offset(0.0f, 38.0f)
                .size(cardW, 10.0f)
                .text(Component.text("LUNAR QUADRUPED SYSTEM", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(6.5f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        splashCard.addComponent(TextComponent.builder("splash_subtitle")
                .anchor(UiAnchorPoint.CENTER_TOP)
                .origin(UiAnchorPoint.CENTER_TOP)
                .offset(0.0f, 50.0f)
                .size(cardW, 8.0f)
                .text(Component.text("KHỞI ĐỘNG HỆ THỐNG...", NamedTextColor.DARK_BLUE, TextDecoration.BOLD))
                .fontSize(4.0f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        splashLayer.addContainer(splashCard);
        return manager;
    }

    public LayerManager buildDashboardLayerManager() {
        LayerManager manager = new LayerManager();
        LunarDashboardTheme theme = data.getColorTheme();

        Color gradStart = theme.getGradStart();
        Color gradEnd = theme.getGradEnd();
        Color accent = theme.getAccentColor();
        Color cardBg = theme.getCardBg();
        Color btnBg = theme.getButtonBg();

        // 1. Layer 0: Background Canvas (Master canvas)
        Layer bgLayer = manager.createLayer("background_canvas", 0);
        bgLayer.bounds(MIN_X, MIN_Y, TOTAL_WIDTH, TOTAL_HEIGHT);

        // 2. Layer 1: Left Navigation Panel
        Layer leftLayer = manager.createLayer("left_nav_layer", 1);
        Container leftContainer = Container.builder("left_panel")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(LEFT_PANEL_X, LEFT_PANEL_Y)
                .size(LEFT_PANEL_W, LEFT_PANEL_H)
                .backgroundColor(gradStart)
                .borderRound(PANEL_CORNER_RADIUS)
                .build();

        leftContainer.addComponent(TextComponent.builder("title_dashboard")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(4.0f, 6.0f)
                .size(LEFT_PANEL_W - 8.0f, 14.0f)
                .text(Component.text("Dashboard", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(7.5f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        leftContainer.addComponent(ButtonComponent.builder("btn_nav_overview")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(4.0f, 6.0f)
                .size(LEFT_PANEL_W - 8.0f, 14.0f)
                .backgroundColor(Color.fromARGB(0, 0, 0, 0))
                .hitSlop(2.0f)
                .action(UiButtonAction.none())
                .build());

        Color b1Bg = (currentTab == Tab.MODES) ? cardBg : btnBg;
        addNavButtonToContainer(leftContainer, "btn_nav_modes", 4.0f, BTN_MODES_Y - LEFT_PANEL_Y,
                LEFT_BTN_W, LEFT_BTN_H, LunarDashboardIcons.CHAR_MODE, "Chế độ", b1Bg, currentTab == Tab.MODES);

        Color b2Bg = (currentTab == Tab.SETTINGS) ? cardBg : btnBg;
        addNavButtonToContainer(leftContainer, "btn_nav_settings", 4.0f, BTN_SETTINGS_Y - LEFT_PANEL_Y,
                LEFT_BTN_W, LEFT_BTN_H, LunarDashboardIcons.CHAR_SETTINGS, "Cài đặt", b2Bg, currentTab == Tab.SETTINGS);

        addNavButtonToContainer(leftContainer, "btn_nav_exit", 4.0f, BTN_EXIT_Y - LEFT_PANEL_Y,
                LEFT_BTN_W, LEFT_BTN_H, LunarDashboardIcons.CHAR_EXIT, "Thoát", btnBg, false);

        leftLayer.addContainer(leftContainer);

        // 3. Layer 2: Right Content Panel
        Layer rightLayer = manager.createLayer("right_content_layer", 2);
        Container rightContainer = Container.builder("right_panel")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(RIGHT_PANEL_X, RIGHT_PANEL_Y)
                .size(RIGHT_PANEL_W, RIGHT_PANEL_H)
                .backgroundColor(gradStart)
                .borderRound(PANEL_CORNER_RADIUS)
                .build();

        if (currentTab == Tab.OVERVIEW) {
            buildOverviewContainer(rightContainer, cardBg);
        } else if (currentTab == Tab.MODES) {
            buildModesContainer(rightContainer, cardBg, accent);
        } else {
            buildSettingsContainer(rightContainer, gradStart, gradEnd, cardBg, accent, btnBg);
        }
        rightLayer.addContainer(rightContainer);

        return manager;
    }

    private void addNavButtonToContainer(Container container, String id, float x, float y, float w, float h,
                                         char iconGlyph, String label, Color bg, boolean isActive) {
        container.addComponent(ButtonComponent.builder(id)
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .size(w, h)
                .backgroundColor(bg)
                .borderRound(BUTTON_CORNER_RADIUS)
                .hitSlop(2.0f)
                .action(UiButtonAction.none())
                .build());

        float iconW = 12.0f;
        float gap = 4.5f;
        float textW = UiText.estimateWidth(Component.text(label), 5.0f);
        float totalW = iconW + gap + textW;
        float startX = x + Math.max(2.0f, (w - totalW) * 0.5f);

        container.addComponent(CustomNodeComponent.builder(id + "_icon")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(startX, y)
                .size(iconW, h)
                .factory((ix, iy, iw, ih, depth, scale) -> LunarDashboardIcons.createIconNode(
                        LunarDashboardIcons.icon(iconGlyph), ix, iy, iw, ih, 9.5f * scale, depth))
                .build());

        container.addComponent(CustomNodeComponent.builder(id + "_text")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(startX + iconW + gap, y)
                .size(textW + 2.0f, h)
                .factory((tx, ty, tw, th, depth, scale) -> new AlignedTextNode(
                        Component.text(label, isActive ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY, TextDecoration.BOLD),
                        tx, ty, tw, th, depth, UiTextAlignment.LEFT, 0.0f, 0.0f, 5.0f * scale, tw, UiVerticalAlignment.CENTER, 0.0f, false, false))
                .build());
    }

    private void buildOverviewContainer(Container parent, Color cardBg) {
        // Info Section Header
        parent.addComponent(TextComponent.builder("overview_info_title")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(8.0f, 4.0f)
                .size(40.0f, 8.0f)
                .text(Component.text("Info", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(7.0f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        float pill1X = 8.0f;
        float pill2X = 74.0f;
        float pillY = 16.0f;
        float pillW = SUB_CARD_W;
        float pillH = SUB_CARD_H;

        // Pill 1: Tên robot
        buildPillContainer(parent, "pill_robot_name", pill1X, pillY, pillW, pillH, cardBg,
                LunarDashboardIcons.infoIcon(), LunarDashboardIcons.INFO_ICON_OPTICAL_OFFSET_Y,
                "Tên robot", getTruncatedRobotName());

        // Pill 2: Giờ hoạt động
        long hoursPassed = Math.max(0, (System.currentTimeMillis() - data.getLinkedTimestamp()) / 3600000L);
        buildPillContainer(parent, "pill_active_hours", pill2X, pillY, pillW, pillH, cardBg,
                LunarDashboardIcons.calendarIcon(), LunarDashboardIcons.INFO_ICON_OPTICAL_OFFSET_Y,
                "Giờ hoạt động", hoursPassed + " giờ");

        // Status Section Header
        float statusRelY = STATUS_CARD_Y - RIGHT_PANEL_Y; // 50.0f
        parent.addComponent(TextComponent.builder("overview_status_title")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(8.0f, statusRelY + 4.0f)
                .size(40.0f, 8.0f)
                .text(Component.text("Status", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(7.0f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        float row1Y = statusRelY + 14.0f;
        float row2Y = statusRelY + 40.0f;

        // Status Grid: Box 1 (Độ bền)
        int integrityInt = (int) Math.round(data.getIntegrityPercentage());
        buildProgressBoxContainer(parent, "box_integrity", pill1X, row1Y, pillW, pillH, cardBg,
                LunarDashboardIcons.batteryIcon(), LunarDashboardIcons.BATTERY_ICON_OPTICAL_OFFSET_Y,
                "Độ bền: " + integrityInt + "%", LunarDashboardIcons.cyanProgressBar(integrityInt / 100.0));

        // Status Grid: Box 2 (Pin / Năng lượng)
        double energyPct = data.getMaxEnergy() > 0 ? (double) data.getEnergy() / data.getMaxEnergy() : 0.0;
        int pctInt = (int) (energyPct * 100);
        buildProgressBoxContainer(parent, "box_energy", pill2X, row1Y, pillW, pillH, cardBg,
                LunarDashboardIcons.batteryIcon(), LunarDashboardIcons.BATTERY_ICON_OPTICAL_OFFSET_Y,
                "Pin: " + pctInt + "%", LunarDashboardIcons.greenProgressBar(energyPct));

        // Status Grid: Box 3 (Module 1)
        buildModuleBoxContainer(parent, "box_module1", pill1X, row2Y, pillW, pillH, cardBg,
                data.getModule1Id(), data.getModule1Efficiency(), "Module 1:");

        // Status Grid: Box 4 (Module 2)
        buildModuleBoxContainer(parent, "box_module2", pill2X, row2Y, pillW, pillH, cardBg,
                data.getModule2Id(), data.getModule2Efficiency(), "Module 2:");
    }

    private String getTruncatedRobotName() {
        String rName = data.getName();
        if (rName == null) return "Lunar Robot";
        if (rName.length() > 13) {
            return rName.substring(0, 11) + "..";
        }
        return rName;
    }

    private void buildPillContainer(Container parent, String id, float x, float y, float w, float h,
                                   Color bg, Component iconComp, float iconOpticalOffsetY,
                                   String line1, String line2) {
        Container container = Container.builder(id)
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .size(w, h)
                .build();

        container.addComponent(CustomNodeComponent.builder(id + "_bg")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(0.0f, 0.0f)
                .size(w, h)
                .factory((bx, by, bw, bh, depth, scale) -> UiShapeNode.builder("rounded_rect", bx, by, bw, bh)
                        .color(bg)
                        .cornerRadius(PILL_CORNER_RADIUS * scale)
                        .depth(depth)
                        .build())
                .build());

        container.addComponent(CustomNodeComponent.builder(id + "_icon")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(4.5f, iconOpticalOffsetY)
                .size(16.0f, h)
                .factory((ix, iy, iw, ih, depth, scale) -> LunarDashboardIcons.createIconNode(
                        iconComp, ix, iy, iw, ih, 8.5f * scale, depth))
                .build());

        container.addComponent(TextComponent.builder(id + "_l1")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(SUB_CARD_CONTENT_X, LINE_1_Y_OFFSET)
                .size(SUB_CARD_CONTENT_W, 7.0f)
                .text(Component.text(line1, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(4.2f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        container.addComponent(TextComponent.builder(id + "_l2")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(SUB_CARD_CONTENT_X, LINE_2_Y_OFFSET)
                .size(SUB_CARD_CONTENT_W, 7.0f)
                .text(Component.text(line2, NamedTextColor.WHITE, TextDecoration.BOLD))
                .fontSize(4.2f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        parent.addContainer(container);
    }

    private void buildProgressBoxContainer(Container parent, String id, float x, float y, float w, float h,
                                           Color bg, Component iconComp, float iconOpticalOffsetY,
                                           String line1, Component progressBarComp) {
        Container container = Container.builder(id)
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .size(w, h)
                .build();

        container.addComponent(CustomNodeComponent.builder(id + "_bg")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(0.0f, 0.0f)
                .size(w, h)
                .factory((bx, by, bw, bh, depth, scale) -> UiShapeNode.builder("rounded_rect", bx, by, bw, bh)
                        .color(bg)
                        .cornerRadius(PILL_CORNER_RADIUS * scale)
                        .depth(depth)
                        .build())
                .build());

        container.addComponent(CustomNodeComponent.builder(id + "_icon")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(4.5f, iconOpticalOffsetY)
                .size(16.0f, h)
                .factory((ix, iy, iw, ih, depth, scale) -> LunarDashboardIcons.createIconNode(
                        iconComp, ix, iy, iw, ih, 8.5f * scale, depth))
                .build());

        container.addComponent(TextComponent.builder(id + "_l1")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(SUB_CARD_CONTENT_X, LINE_1_Y_OFFSET)
                .size(SUB_CARD_CONTENT_W, 7.0f)
                .text(Component.text(line1, NamedTextColor.WHITE, TextDecoration.BOLD))
                .fontSize(3.8f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        container.addComponent(CustomNodeComponent.builder(id + "_pb")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(SUB_CARD_CONTENT_X, PROGRESS_BAR_Y_OFFSET)
                .size(SUB_CARD_CONTENT_W, 7.0f)
                .factory((px, py, pw, ph, depth, scale) -> LunarDashboardIcons.createProgressBarNode(
                        progressBarComp, px, py, depth))
                .build());

        parent.addContainer(container);
    }

    private void buildModuleBoxContainer(Container parent, String id, float x, float y, float w, float h,
                                         Color bg, String modId, double eff, String label) {
        Container container = Container.builder(id)
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .size(w, h)
                .build();

        container.addComponent(CustomNodeComponent.builder(id + "_bg")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(0.0f, 0.0f)
                .size(w, h)
                .factory((bx, by, bw, bh, depth, scale) -> UiShapeNode.builder("rounded_rect", bx, by, bw, bh)
                        .color(bg)
                        .cornerRadius(PILL_CORNER_RADIUS * scale)
                        .depth(depth)
                        .build())
                .build());

        ItemStack mItem = createModuleItem(modId);
        container.addComponent(CustomNodeComponent.builder(id + "_icon")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(4.5f, 4.0f)
                .size(16.0f, 16.0f)
                .factory((ix, iy, iw, ih, depth, scale) -> mItem != null ? new UiIconNode(
                        mItem, ix, iy, depth, iw, ih, 16.0f, 16.0f, ItemDisplay.ItemDisplayTransform.FIXED, false) : null)
                .build());

        if (modId == null) {
            container.addComponent(TextComponent.builder(id + "_l1")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(SUB_CARD_CONTENT_X, LINE_1_Y_OFFSET)
                    .size(SUB_CARD_CONTENT_W, 7.0f)
                    .text(Component.text(label, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                    .fontSize(4.0f)
                    .alignment(UiTextAlignment.LEFT)
                    .shadow(false)
                    .build());

            container.addComponent(TextComponent.builder(id + "_l2")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(SUB_CARD_CONTENT_X, LINE_2_Y_OFFSET)
                    .size(SUB_CARD_CONTENT_W, 8.0f)
                    .text(Component.text("(Trống)", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .fontSize(4.4f)
                    .alignment(UiTextAlignment.LEFT)
                    .shadow(false)
                    .build());
        } else {
            container.addComponent(TextComponent.builder(id + "_l1")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(SUB_CARD_CONTENT_X, LINE_3_ROW1_OFFSET)
                    .size(SUB_CARD_CONTENT_W, 6.0f)
                    .text(Component.text(label, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                    .fontSize(3.6f)
                    .alignment(UiTextAlignment.LEFT)
                    .shadow(false)
                    .build());

            container.addComponent(TextComponent.builder(id + "_l2")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(SUB_CARD_CONTENT_X, LINE_3_ROW2_OFFSET)
                    .size(SUB_CARD_CONTENT_W, 7.0f)
                    .text(Component.text(formatModuleName(modId), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .fontSize(4.0f)
                    .alignment(UiTextAlignment.LEFT)
                    .shadow(false)
                    .build());

            container.addComponent(TextComponent.builder(id + "_l3")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(SUB_CARD_CONTENT_X - 2.0f, LINE_3_ROW3_OFFSET)
                    .size(SUB_CARD_CONTENT_W, 6.0f)
                    .text(Component.text("Status: Hoạt động (" + (int) eff + "%)", NamedTextColor.DARK_GRAY))
                    .fontSize(3.2f)
                    .alignment(UiTextAlignment.LEFT)
                    .shadow(false)
                    .build());
        }
        parent.addContainer(container);
    }

    private void buildModesContainer(Container parent, Color cardBg, Color accent) {
        Component modeHeader = Component.text(String.valueOf(LunarDashboardIcons.CHAR_MODE))
                .font(LunarDashboardIcons.FONT_KEY)
                .append(Component.text(" Chế độ hoạt động").font(Key.key("minecraft:default")));

        parent.addComponent(TextComponent.builder("modes_header")
                .anchor(UiAnchorPoint.CENTER_TOP)
                .origin(UiAnchorPoint.CENTER_TOP)
                .offset(0.0f, 5.0f)
                .size(RIGHT_PANEL_W, 10.0f)
                .text(modeHeader.color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD))
                .fontSize(6.5f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        float startX = 6.0f;
        float gapX = 46.0f;
        float row1Y = 18.0f;
        float row2Y = 58.0f;

        buildModeButtonContainer(parent, "mode_idle", startX, row1Y, "Nghỉ ngơi",
                data.getActiveTask() == RobotTask.IDLE, accent, cardBg, createModuleItem("haohan:empty_module_slot"));
        buildModeButtonContainer(parent, "mode_ore", startX + gapX, row1Y, "Dò Quặng",
                data.getActiveTask() == RobotTask.ORE_SCAN, accent, cardBg, createModuleItem("haohan:robot_module_ore_scan"));
        buildModeButtonContainer(parent, "mode_combat", startX + gapX * 2, row1Y, "Chiến Đấu",
                data.getActiveTask() == RobotTask.COMBAT, accent, cardBg, createModuleItem("haohan:robot_module_combat"));

        buildModeButtonContainer(parent, "mode_speed", startX, row2Y, "Tốc Hành",
                data.getActiveTask() == RobotTask.SPEED, accent, cardBg, createModuleItem("haohan:robot_module_speed"));
        buildModeButtonContainer(parent, "mode_thrust", startX + gapX, row2Y, "Đẩy Phản Lực",
                data.getActiveTask() == RobotTask.THRUST, accent, cardBg, createModuleItem("haohan:robot_module_thrust"));
        buildModeButtonContainer(parent, "mode_follow", startX + gapX * 2, row2Y,
                data.isFollowOwner() ? "Theo chủ: BẬT" : "Theo chủ: TẮT", data.isFollowOwner(), accent, cardBg, createModuleItem("haohan:empty_module_slot"));

        String currentModeStr = data.getActiveTask().getDisplayName();
        parent.addComponent(TextComponent.builder("modes_current_text")
                .anchor(UiAnchorPoint.CENTER_TOP)
                .origin(UiAnchorPoint.CENTER_TOP)
                .offset(0.0f, 98.0f)
                .size(RIGHT_PANEL_W, 10.0f)
                .text(Component.text("Chế độ hiện tại: " + currentModeStr, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(4.5f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());
    }

    private void buildModeButtonContainer(Container parent, String id, float x, float y, String name,
                                          boolean isSelected, Color activeBg, Color inactiveBg, ItemStack iconItem) {
        Color bg = isSelected ? activeBg : inactiveBg;

        Container modeCard = Container.builder(id)
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .size(MODE_BTN_W, MODE_BTN_H)
                .build();

        modeCard.addComponent(ButtonComponent.builder(id)
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(0.0f, 0.0f)
                .size(MODE_BTN_W, MODE_BTN_H)
                .backgroundColor(bg)
                .borderRound(BUTTON_CORNER_RADIUS)
                .hitSlop(2.0f)
                .action(UiButtonAction.none())
                .build());

        modeCard.addComponent(CustomNodeComponent.builder(id + "_icon")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset((MODE_BTN_W - 18.0f) / 2.0f, 4.0f)
                .size(18.0f, 18.0f)
                .factory((ix, iy, iw, ih, depth, scale) -> iconItem != null ? new UiIconNode(
                        iconItem, ix, iy, depth, iw, ih, 16.0f, 16.0f, ItemDisplay.ItemDisplayTransform.FIXED, false) : null)
                .build());

        modeCard.addComponent(TextComponent.builder(id + "_label")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(0.0f, 23.0f)
                .size(MODE_BTN_W, 9.0f)
                .text(Component.text(name, isSelected ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(3.8f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        parent.addContainer(modeCard);
    }

    private void buildSettingsContainer(Container parent, Color gradStart, Color gradEnd, Color cardBg, Color accent, Color btnBg) {
        // Shield and Header
        Component settingsHeader = Component.text(String.valueOf(LunarDashboardIcons.CHAR_SETTINGS))
                .font(LunarDashboardIcons.FONT_KEY)
                .append(Component.text(" Cài đặt").font(Key.key("minecraft:default")));

        parent.addComponent(TextComponent.builder("settings_header")
                .anchor(UiAnchorPoint.CENTER_TOP)
                .origin(UiAnchorPoint.CENTER_TOP)
                .offset(0.0f, 4.0f)
                .size(RIGHT_PANEL_W, 10.0f)
                .text(settingsHeader.color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD))
                .fontSize(6.5f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        float cardX = 4.0f;
        float cardW = ACCORDION_CARD_W;

        boolean isModExpanded = (expandedSettingsCard == SettingsCard.MODULES);
        boolean isHealthExpanded = (expandedSettingsCard == SettingsCard.HEALTH);
        boolean isCustExpanded = (expandedSettingsCard == SettingsCard.CUSTOMIZE);
        boolean isUnbindExpanded = (expandedSettingsCard == SettingsCard.UNBIND);

        float currentY = 16.0f;

        // 1. Modules Dropdown
        DropdownContainer modDropdown = buildModulesDropdown(cardX, currentY, cardW, cardBg, accent, btnBg, isModExpanded);
        parent.addContainer(modDropdown);
        currentY += modDropdown.height() + ACCORDION_GAP;

        // 2. Health Dropdown
        DropdownContainer healthDropdown = buildHealthDropdown(cardX, currentY, cardW, cardBg, accent, btnBg, isHealthExpanded);
        parent.addContainer(healthDropdown);
        currentY += healthDropdown.height() + ACCORDION_GAP;

        // 3. Customize Dropdown
        DropdownContainer custDropdown = buildCustomizeDropdown(cardX, currentY, cardW, cardBg, accent, btnBg, isCustExpanded);
        parent.addContainer(custDropdown);
        currentY += custDropdown.height() + ACCORDION_GAP;

        // 4. Unbind Dropdown
        DropdownContainer unbindDropdown = buildUnbindDropdown(cardX, currentY, cardW, isUnbindExpanded);
        parent.addContainer(unbindDropdown);
    }

    private DropdownContainer buildModulesDropdown(float x, float y, float w, Color cardBg, Color accent, Color btnBg, boolean expanded) {
        DropdownContainer dropdown = DropdownContainer.builder("card_modules")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .width(w)
                .headerHeight(ACCORDION_CARD_H)
                .headerTitle(Component.text("Thiết lập Robot", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .headerBackgroundColor(cardBg)
                .headerExpandedBackgroundColor(btnBg)
                .headerBorderRound(BUTTON_CORNER_RADIUS)
                .headerTitleFontSize(4.5f)
                .indicatorCollapsed("▶")
                .indicatorExpanded("▼")
                .indicatorColor(TextColor.color(accent.asRGB()))
                .animationType(DropdownAnimationType.SLIDE_AND_FADE)
                .animationDurationTicks(8)
                .animationEasing(Easings.OutCubic)
                .slideDistance(10.0f)
                .contentBackgroundColor(cardBg)
                .contentBorderRound(CARD_CORNER_RADIUS)
                .contentPadding(0.0f)
                .itemSpacing(2.0f)
                .expanded(expanded)
                .build();

        dropdown.addComponent(CustomNodeComponent.builder("card_modules_stripe")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(2.0f, 2.0f)
                .size(3.0f, ACCORDION_CARD_H - 4.0f)
                .factory((sx, sy, sw, sh, depth, scale) -> UiShapeNode.builder("rounded_rect", sx, sy, sw, sh)
                        .color(accent)
                        .cornerRadius(1.5f * scale)
                        .depth(depth)
                        .build())
                .build());

        dropdown.addDropdownItem(buildAccordionModuleBodyContainer(w, cardBg, accent, btnBg));
        return dropdown;
    }

    private Container buildAccordionModuleBodyContainer(float cardW, Color cardBg, Color accent, Color buttonBg) {
        float bodyH = 54.0f;
        Container body = Container.builder("card_modules_body")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .size(cardW, bodyH)
                .backgroundColor(cardBg)
                .borderRound(CARD_CORNER_RADIUS)
                .build();

        body.addComponent(CustomNodeComponent.builder("accordion_mod_body_stripe")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(2.0f, 2.0f)
                .size(3.0f, bodyH - 4.0f)
                .factory((sx, sy, sw, sh, depth, scale) -> UiShapeNode.builder("rounded_rect", sx, sy, sw, sh)
                        .color(accent)
                        .cornerRadius(1.5f * scale)
                        .depth(depth)
                        .build())
                .build());

        float slotY = 4.0f;
        float slotW = MODULE_SLOT_W;
        float slotH = MODULE_SLOT_H;
        float startX = 8.0f;
        float gap = 42.0f;

        for (int i = 0; i < 2; i++) {
            float sx = startX + i * gap;
            String modId = data.getModuleId(i);
            String modName = (modId != null) ? formatModuleName(modId) : "(Trống)";
            double eff = data.getModuleEfficiency(i);

            body.addComponent(ButtonComponent.builder("slot_" + i)
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(sx, slotY)
                    .size(slotW, slotH)
                    .backgroundColor(buttonBg)
                    .borderRound(SLOT_CORNER_RADIUS)
                    .hitSlop(2.0f)
                    .action(UiButtonAction.none())
                    .build());

            ItemStack slotItem = createModuleItem(modId);
            body.addComponent(CustomNodeComponent.builder("slot_" + i + "_icon")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(sx + (slotW - 18.0f) / 2.0f, slotY + 2.0f)
                    .size(18.0f, 18.0f)
                    .factory((ix, iy, iw, ih, depth, scale) -> slotItem != null ? new UiIconNode(
                            slotItem, ix, iy, depth, iw, ih, 16.0f, 16.0f, ItemDisplay.ItemDisplayTransform.FIXED, false) : null)
                    .build());

            body.addComponent(TextComponent.builder("slot_" + i + "_l1")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(sx, slotY + 20.0f)
                    .size(slotW, 6.0f)
                    .text(Component.text("Module " + (i + 1), NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                    .fontSize(3.5f)
                    .alignment(UiTextAlignment.CENTER)
                    .shadow(false)
                    .build());

            body.addComponent(TextComponent.builder("slot_" + i + "_l2")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(sx, slotY + 25.0f)
                    .size(slotW, 6.0f)
                    .text(Component.text(modName, modId != null ? NamedTextColor.WHITE : NamedTextColor.GRAY, TextDecoration.BOLD))
                    .fontSize(3.4f)
                    .alignment(UiTextAlignment.CENTER)
                    .shadow(false)
                    .build());

            Component effText = (modId != null)
                    ? Component.text((int) eff + "%", NamedTextColor.GREEN, TextDecoration.BOLD)
                    : Component.empty();
            body.addComponent(TextComponent.builder("slot_" + i + "_l3")
                    .anchor(UiAnchorPoint.TOP_LEFT)
                    .origin(UiAnchorPoint.TOP_LEFT)
                    .offset(sx, slotY + 30.0f)
                    .size(slotW, 5.0f)
                    .text(effText)
                    .fontSize(3.0f)
                    .alignment(UiTextAlignment.CENTER)
                    .shadow(false)
                    .build());
        }

        // Slot 2: Battery
        float s3x = startX + 2 * gap;
        String bType = data.getBatteryType();
        boolean hasBattery = bType != null && !bType.equalsIgnoreCase("none") && RobotBatteryUtil.getCapacity(bType) > 0;
        String bName = hasBattery ? formatBatteryName(bType) : "(Trống)";

        body.addComponent(ButtonComponent.builder("slot_2")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(s3x, slotY)
                .size(slotW, slotH)
                .backgroundColor(buttonBg)
                .borderRound(SLOT_CORNER_RADIUS)
                .hitSlop(2.0f)
                .action(UiButtonAction.none())
                .build());

        ItemStack batteryItem = null;
        try {
            batteryItem = hasBattery
                    ? RobotBatteryUtil.createBattery(bType, data.getEnergy())
                    : createModuleItem("haohan:empty_module_slot");
        } catch (Throwable ignored) {}
        if (batteryItem == null) batteryItem = createModuleItem("haohan:empty_module_slot");

        ItemStack finalBatItem = batteryItem;
        body.addComponent(CustomNodeComponent.builder("slot_2_icon")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(s3x + (slotW - 18.0f) / 2.0f, slotY + 2.0f)
                .size(18.0f, 18.0f)
                .factory((ix, iy, iw, ih, depth, scale) -> finalBatItem != null ? new UiIconNode(
                        finalBatItem, ix, iy, depth, iw, ih, 16.0f, 16.0f, ItemDisplay.ItemDisplayTransform.FIXED, false) : null)
                .build());

        body.addComponent(TextComponent.builder("slot_2_l1")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(s3x, slotY + 20.0f)
                .size(slotW, 6.0f)
                .text(Component.text("Pin (Năng lượng)", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(3.3f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        body.addComponent(TextComponent.builder("slot_2_l2")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(s3x, slotY + 25.0f)
                .size(slotW, 6.0f)
                .text(Component.text(bName, hasBattery ? NamedTextColor.WHITE : NamedTextColor.GRAY, TextDecoration.BOLD))
                .fontSize(3.4f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        int energyPct = (hasBattery && data.getMaxEnergy() > 0)
                ? (int) Math.round(((double) data.getEnergy() / data.getMaxEnergy()) * 100.0)
                : 0;
        Component energyText = hasBattery
                ? Component.text(energyPct + "%", NamedTextColor.GREEN, TextDecoration.BOLD)
                : Component.empty();
        body.addComponent(TextComponent.builder("slot_2_l3")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(s3x, slotY + 30.0f)
                .size(slotW, 5.0f)
                .text(energyText)
                .fontSize(3.0f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        body.addComponent(TextComponent.builder("accordion_mod_hint")
                .anchor(UiAnchorPoint.CENTER_TOP)
                .origin(UiAnchorPoint.CENTER_TOP)
                .offset(0.0f, 41.0f)
                .size(cardW - 16.0f, 8.0f)
                .text(Component.text("Cần module hoặc pin trên tay và bấm vào ô để lắp đặt / tháo gỡ", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(3.3f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        return body;
    }

    private DropdownContainer buildHealthDropdown(float x, float y, float w, Color cardBg, Color accent, Color btnBg, boolean expanded) {
        DropdownContainer dropdown = DropdownContainer.builder("card_health")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .width(w)
                .headerHeight(ACCORDION_CARD_H)
                .headerTitle(Component.text("Trạng thái sức khỏe", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .headerBackgroundColor(cardBg)
                .headerExpandedBackgroundColor(btnBg)
                .headerBorderRound(BUTTON_CORNER_RADIUS)
                .headerTitleFontSize(4.5f)
                .indicatorCollapsed("▶")
                .indicatorExpanded("▼")
                .indicatorColor(TextColor.color(accent.asRGB()))
                .animationType(DropdownAnimationType.SLIDE_AND_FADE)
                .animationDurationTicks(8)
                .animationEasing(Easings.OutCubic)
                .slideDistance(10.0f)
                .contentBackgroundColor(cardBg)
                .contentBorderRound(CARD_CORNER_RADIUS)
                .contentPadding(0.0f)
                .itemSpacing(2.0f)
                .expanded(expanded)
                .build();

        dropdown.addComponent(CustomNodeComponent.builder("card_health_stripe")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(2.0f, 2.0f)
                .size(3.0f, ACCORDION_CARD_H - 4.0f)
                .factory((sx, sy, sw, sh, depth, scale) -> UiShapeNode.builder("rounded_rect", sx, sy, sw, sh)
                        .color(accent)
                        .cornerRadius(1.5f * scale)
                        .depth(depth)
                        .build())
                .build());

        dropdown.addDropdownItem(buildAccordionHealthBodyContainer(w, cardBg, accent, btnBg));
        return dropdown;
    }

    private Container buildAccordionHealthBodyContainer(float cardW, Color cardBg, Color accent, Color buttonBg) {
        float bodyH = 54.0f;
        Container body = Container.builder("card_health_body")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .size(cardW, bodyH)
                .backgroundColor(cardBg)
                .borderRound(CARD_CORNER_RADIUS)
                .build();

        body.addComponent(CustomNodeComponent.builder("accordion_health_body_stripe")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(2.0f, 2.0f)
                .size(3.0f, bodyH - 4.0f)
                .factory((sx, sy, sw, sh, depth, scale) -> UiShapeNode.builder("rounded_rect", sx, sy, sw, sh)
                        .color(accent)
                        .cornerRadius(1.5f * scale)
                        .depth(depth)
                        .build())
                .build());

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        String linkedDate = sdf.format(new Date(data.getLinkedTimestamp()));
        long hours = Math.max(0, (System.currentTimeMillis() - data.getLinkedTimestamp()) / 3600000L);
        String owner = data.getOwnerName() != null ? data.getOwnerName() : (player != null ? player.getName() : "Owner");

        body.addComponent(TextComponent.builder("health_detail_title")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(8.0f, 4.0f)
                .size(cardW - 16.0f, 7.0f)
                .text(Component.text("THÔNG TIN HOẠT ĐỘNG CHI TIẾT", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(4.0f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        float colW = (cardW - 20.0f) / 2.0f;
        float col1X = 8.0f;
        float col2X = 8.0f + colW + 4.0f;

        float row1Y = 13.0f;
        float row2Y = 23.0f;
        float row3Y = 33.0f;
        float row4Y = 43.0f;

        buildStatItemContainer(body, "stat_owner", col1X, row1Y, colW, "Chủ sở hữu:", owner, buttonBg);
        buildStatItemContainer(body, "stat_espent", col2X, row1Y, colW, "Năng lượng tiêu hao:", data.getEnergySpent() + " EU", buttonBg);

        buildStatItemContainer(body, "stat_date", col1X, row2Y, colW, "Ngày liên kết:", linkedDate, buttonBg);
        buildStatItemContainer(body, "stat_dmg_dealt", col2X, row2Y, colW, "Sát thương đã gây:", String.format("%.1f", data.getDamageDealt()), buttonBg);

        buildStatItemContainer(body, "stat_hours", col1X, row3Y, colW, "Thời gian HĐ:", hours + " giờ", buttonBg);
        buildStatItemContainer(body, "stat_dmg_taken", col2X, row3Y, colW, "Sát thương nhận vào:", String.format("%.1f", data.getDamageTaken()), buttonBg);

        buildStatItemContainer(body, "stat_steps", col1X, row4Y, colW, "Số bước chạy:", String.valueOf(data.getTotalSteps()), buttonBg);
        buildStatItemContainer(body, "stat_maxe", col2X, row4Y, colW, "Pin tối đa:", data.getMaxEnergy() + " EU", buttonBg);

        return body;
    }

    private void buildStatItemContainer(Container parent, String id, float x, float y, float w, String label, String value, Color pillBg) {
        parent.addComponent(CustomNodeComponent.builder(id + "_bg")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .size(w, 8.5f)
                .factory((bx, by, bw, bh, depth, scale) -> UiShapeNode.builder("rounded_rect", bx, by, bw, bh)
                        .color(pillBg)
                        .cornerRadius(2.0f * scale)
                        .depth(depth)
                        .build())
                .build());

        parent.addComponent(TextComponent.builder(id + "_lbl")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x + 3.0f, y + 1.2f)
                .size(w * 0.60f, 6.0f)
                .text(Component.text(label, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(3.1f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        parent.addComponent(TextComponent.builder(id + "_val")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x + w * 0.45f, y + 1.2f)
                .size(w * 0.55f - 3.0f, 6.0f)
                .text(Component.text(value, NamedTextColor.DARK_BLUE, TextDecoration.BOLD))
                .fontSize(3.1f)
                .alignment(UiTextAlignment.RIGHT)
                .shadow(false)
                .build());
    }

    private DropdownContainer buildCustomizeDropdown(float x, float y, float w, Color cardBg, Color accent, Color btnBg, boolean expanded) {
        DropdownContainer dropdown = DropdownContainer.builder("card_customize")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .width(w)
                .headerHeight(ACCORDION_CARD_H)
                .headerTitle(Component.text("Tùy chỉnh cá nhân", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .headerBackgroundColor(cardBg)
                .headerExpandedBackgroundColor(btnBg)
                .headerBorderRound(BUTTON_CORNER_RADIUS)
                .headerTitleFontSize(4.5f)
                .indicatorCollapsed("▶")
                .indicatorExpanded("▼")
                .indicatorColor(TextColor.color(accent.asRGB()))
                .animationType(DropdownAnimationType.SLIDE_AND_FADE)
                .animationDurationTicks(8)
                .animationEasing(Easings.OutCubic)
                .slideDistance(10.0f)
                .contentBackgroundColor(cardBg)
                .contentBorderRound(CARD_CORNER_RADIUS)
                .contentPadding(0.0f)
                .itemSpacing(2.0f)
                .expanded(expanded)
                .build();

        dropdown.addComponent(CustomNodeComponent.builder("card_customize_stripe")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(2.0f, 2.0f)
                .size(3.0f, ACCORDION_CARD_H - 4.0f)
                .factory((sx, sy, sw, sh, depth, scale) -> UiShapeNode.builder("rounded_rect", sx, sy, sw, sh)
                        .color(accent)
                        .cornerRadius(1.5f * scale)
                        .depth(depth)
                        .build())
                .build());

        dropdown.addDropdownItem(buildAccordionCustomizeBodyContainer(w, cardBg, accent, btnBg));
        return dropdown;
    }

    private Container buildAccordionCustomizeBodyContainer(float cardW, Color cardBg, Color accent, Color buttonBg) {
        float bodyH = 54.0f;
        Container body = Container.builder("card_customize_body")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .size(cardW, bodyH)
                .backgroundColor(cardBg)
                .borderRound(CARD_CORNER_RADIUS)
                .build();

        body.addComponent(CustomNodeComponent.builder("accordion_cust_body_stripe")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(2.0f, 2.0f)
                .size(3.0f, bodyH - 4.0f)
                .factory((sx, sy, sw, sh, depth, scale) -> UiShapeNode.builder("rounded_rect", sx, sy, sw, sh)
                        .color(accent)
                        .cornerRadius(1.5f * scale)
                        .depth(depth)
                        .build())
                .build());

        body.addComponent(TextComponent.builder("cust_detail_title")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(8.0f, 4.0f)
                .size(cardW - 16.0f, 7.0f)
                .text(Component.text("TÙY CHỈNH GIAO DIỆN & TÊN ROBOT", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(4.0f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        float rowW = cardW - 16.0f;
        float rowX = 8.0f;
        float rowH = 17.0f;
        float btnW = 36.0f;
        float btnH = 13.0f;

        // Row 1: Đổi màu giao diện
        float r1Y = 13.0f;
        body.addComponent(CustomNodeComponent.builder("row1_bg")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(rowX, r1Y)
                .size(rowW, rowH)
                .factory((bx, by, bw, bh, depth, scale) -> UiShapeNode.builder("rounded_rect", bx, by, bw, bh)
                        .color(buttonBg)
                        .cornerRadius(BUTTON_CORNER_RADIUS * scale)
                        .depth(depth)
                        .build())
                .build());

        body.addComponent(TextComponent.builder("row1_t1")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(rowX + 5.0f, r1Y + 2.0f)
                .size(rowW - btnW - 8.0f, 6.0f)
                .text(Component.text("Màu sắc giao diện", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(3.6f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        body.addComponent(TextComponent.builder("row1_t2")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(rowX + 5.0f, r1Y + 8.5f)
                .size(rowW - btnW - 8.0f, 6.0f)
                .text(Component.text("Hiện tại: " + data.getColorTheme().getDisplayName(), NamedTextColor.DARK_BLUE))
                .fontSize(3.3f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        float b1X = rowX + rowW - btnW - 2.0f;
        float b1Y = r1Y + 2.0f;
        body.addComponent(ButtonComponent.builder("action_change_theme")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(b1X, b1Y)
                .size(btnW, btnH)
                .backgroundColor(accent)
                .borderRound(2.5f)
                .hitSlop(2.0f)
                .action(UiButtonAction.none())
                .build());

        body.addComponent(TextComponent.builder("btn_change_theme_txt")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(b1X, b1Y + 2.5f)
                .size(btnW, 8.0f)
                .text(Component.text("ĐỔI MÀU", NamedTextColor.WHITE, TextDecoration.BOLD))
                .fontSize(3.4f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        // Row 2: Đổi tên robot
        float r2Y = 32.0f;
        body.addComponent(CustomNodeComponent.builder("row2_bg")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(rowX, r2Y)
                .size(rowW, rowH)
                .factory((bx, by, bw, bh, depth, scale) -> UiShapeNode.builder("rounded_rect", bx, by, bw, bh)
                        .color(buttonBg)
                        .cornerRadius(BUTTON_CORNER_RADIUS * scale)
                        .depth(depth)
                        .build())
                .build());

        body.addComponent(TextComponent.builder("row2_t1")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(rowX + 5.0f, r2Y + 2.0f)
                .size(rowW - btnW - 8.0f, 6.0f)
                .text(Component.text("Tên của robot", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .fontSize(3.6f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        String nameHint = isAwaitingRename ? "§cNhập tên mới vào chat..." : ("Hiện tại: " + data.getName());
        body.addComponent(TextComponent.builder("row2_t2")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(rowX + 5.0f, r2Y + 8.5f)
                .size(rowW - btnW - 8.0f, 6.0f)
                .text(Component.text(nameHint, isAwaitingRename ? NamedTextColor.RED : NamedTextColor.DARK_BLUE))
                .fontSize(3.3f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        float b2X = rowX + rowW - btnW - 2.0f;
        float b2Y = r2Y + 2.0f;
        body.addComponent(ButtonComponent.builder("action_rename_robot")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(b2X, b2Y)
                .size(btnW, btnH)
                .backgroundColor(accent)
                .borderRound(2.5f)
                .hitSlop(2.0f)
                .action(UiButtonAction.none())
                .build());

        body.addComponent(TextComponent.builder("btn_rename_txt")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(b2X, b2Y + 2.5f)
                .size(btnW, 8.0f)
                .text(Component.text(isAwaitingRename ? "HỦY" : "ĐỔI TÊN", NamedTextColor.WHITE, TextDecoration.BOLD))
                .fontSize(3.4f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        return body;
    }

    private DropdownContainer buildUnbindDropdown(float x, float y, float w, boolean expanded) {
        DropdownContainer dropdown = DropdownContainer.builder("card_unbind")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(x, y)
                .width(w)
                .headerHeight(ACCORDION_CARD_H)
                .headerTitle(Component.text("Ngắt kết nối", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .headerBackgroundColor(LunarDashboardTheme.DANGER_BUTTON)
                .headerExpandedBackgroundColor(LunarDashboardTheme.DANGER_BG)
                .headerBorderRound(BUTTON_CORNER_RADIUS)
                .headerTitleFontSize(4.5f)
                .indicatorCollapsed("▶")
                .indicatorExpanded("▼")
                .indicatorColor(NamedTextColor.WHITE)
                .animationType(DropdownAnimationType.SLIDE_AND_FADE)
                .animationDurationTicks(8)
                .animationEasing(Easings.OutCubic)
                .slideDistance(10.0f)
                .contentBackgroundColor(LunarDashboardTheme.DANGER_BG)
                .contentBorderRound(CARD_CORNER_RADIUS)
                .contentPadding(0.0f)
                .itemSpacing(2.0f)
                .expanded(expanded)
                .build();

        dropdown.addComponent(CustomNodeComponent.builder("card_unbind_stripe")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(2.0f, 2.0f)
                .size(3.0f, ACCORDION_CARD_H - 4.0f)
                .factory((sx, sy, sw, sh, depth, scale) -> UiShapeNode.builder("rounded_rect", sx, sy, sw, sh)
                        .color(LunarDashboardTheme.DANGER_BORDER)
                        .cornerRadius(1.5f * scale)
                        .depth(depth)
                        .build())
                .build());

        dropdown.addDropdownItem(buildAccordionUnbindBodyContainer(w));
        return dropdown;
    }

    private Container buildAccordionUnbindBodyContainer(float cardW) {
        float bodyH = 54.0f;
        Container body = Container.builder("card_unbind_body")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .size(cardW, bodyH)
                .backgroundColor(LunarDashboardTheme.DANGER_BG)
                .borderRound(CARD_CORNER_RADIUS)
                .build();

        body.addComponent(CustomNodeComponent.builder("accordion_unbind_body_stripe")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(2.0f, 2.0f)
                .size(3.0f, bodyH - 4.0f)
                .factory((sx, sy, sw, sh, depth, scale) -> UiShapeNode.builder("rounded_rect", sx, sy, sw, sh)
                        .color(LunarDashboardTheme.DANGER_BORDER)
                        .cornerRadius(1.5f * scale)
                        .depth(depth)
                        .build())
                .build());

        body.addComponent(TextComponent.builder("unbind_warn_title")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(8.0f, 4.0f)
                .size(cardW - 16.0f, 7.0f)
                .text(Component.text("CẢNH BÁO: HỦY LIÊN KẾT ROBOT", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .fontSize(4.0f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        body.addComponent(TextComponent.builder("unbind_warn_sub")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(8.0f, 14.0f)
                .size(cardW - 16.0f, 7.0f)
                .text(Component.text("Bạn có chắc chắn muốn hủy liên kết với robot này không?", NamedTextColor.DARK_GRAY))
                .fontSize(3.4f)
                .alignment(UiTextAlignment.LEFT)
                .shadow(false)
                .build());

        float btnY = 26.0f;
        float btnW = 56.0f;
        float btnH = 18.0f;
        float btn1X = 10.0f;
        float btn2X = cardW - btnW - 10.0f;

        body.addComponent(ButtonComponent.builder("confirm_unbind")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(btn1X, btnY)
                .size(btnW, btnH)
                .backgroundColor(LunarDashboardTheme.DANGER_BUTTON)
                .borderRound(BUTTON_CORNER_RADIUS)
                .hitSlop(2.0f)
                .action(UiButtonAction.none())
                .build());

        body.addComponent(TextComponent.builder("btn_confirm_unbind_txt")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(btn1X, btnY + 4.5f)
                .size(btnW, btnH - 8.0f)
                .text(Component.text("XÁC NHẬN HỦY", NamedTextColor.WHITE, TextDecoration.BOLD))
                .fontSize(3.6f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        body.addComponent(ButtonComponent.builder("cancel_unbind")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(btn2X, btnY)
                .size(btnW, btnH)
                .backgroundColor(LunarDashboardTheme.NEUTRAL_BUTTON)
                .borderRound(BUTTON_CORNER_RADIUS)
                .hitSlop(2.0f)
                .action(UiButtonAction.none())
                .build());

        body.addComponent(TextComponent.builder("btn_cancel_unbind_txt")
                .anchor(UiAnchorPoint.TOP_LEFT)
                .origin(UiAnchorPoint.TOP_LEFT)
                .offset(btn2X, btnY + 4.5f)
                .size(btnW, btnH - 8.0f)
                .text(Component.text("HỦY BỎ", NamedTextColor.WHITE, TextDecoration.BOLD))
                .fontSize(3.6f)
                .alignment(UiTextAlignment.CENTER)
                .shadow(false)
                .build());

        return body;
    }

    public UiDocument renderSplashDocument() {
        LayerManager manager = buildSplashLayerManager();
        debugState.apply(manager);
        return UiDocumentBridge.compile(manager);
    }

    public UiDocument renderDocument() {
        LayerManager manager = buildDashboardLayerManager();
        debugState.apply(manager);
        UiDocument doc = UiDocumentBridge.compile(manager);
        this.currentDocument = doc;
        return doc;
    }

    public UiDebugState getDebugState() {
        return debugState;
    }

    public LunarRobotData getData() {
        return data;
    }

    public LayerManager getCurrentLayerManager() {
        LayerManager manager = (state == UiState.SPLASH) ? buildSplashLayerManager() : buildDashboardLayerManager();
        debugState.apply(manager);
        return manager;
    }

    public void forceUpdate() {
        if (handle != null && handle.isValid()) {
            if (state == UiState.SPLASH) {
                currentDocument = renderSplashDocument();
                handle.update(currentDocument);
            } else if (state == UiState.DASHBOARD) {
                currentDocument = renderDocument();
                handle.update(currentDocument);
            }
        }
    }

    public void setCurrentTabAndRefresh(Tab tab) {
        this.currentTab = tab;
        if (this.state != UiState.DASHBOARD) {
            this.state = UiState.DASHBOARD;
        }
        updateUi();
    }

    public void setUiStateAndRefresh(UiState state) {
        this.state = state;
        forceUpdate();
    }

    public void setExpandedSettingsCardAndRefresh(SettingsCard card) {
        this.expandedSettingsCard = card;
        updateUi();
    }

    private String formatModuleName(String id) {
        if (id == null) return "Trống";
        if (id.contains("ore_scan")) return "Dò Quặng";
        if (id.contains("combat")) return "Chiến Đấu";
        if (id.contains("speed")) return "Tốc Hành";
        if (id.contains("thrust")) return "Đẩy Phản Lực";
        return id.replace("haohan:robot_module_", "").replace("_", " ");
    }

    private String formatBatteryName(String type) {
        if (type == null || type.isBlank() || type.equalsIgnoreCase("none")) return "Trống";
        return switch (type.toLowerCase()) {
            case "small" -> "Pin Nhỏ";
            case "medium" -> "Pin Trung";
            case "large" -> "Pin Lớn";
            default -> "Pin (" + type + ")";
        };
    }
}
