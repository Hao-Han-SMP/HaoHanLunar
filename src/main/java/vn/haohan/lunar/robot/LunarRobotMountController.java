package vn.haohan.lunar.robot;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.manager.MountManager;
import com.ticxo.modelengine.api.model.bone.type.Mount;
import com.ticxo.modelengine.api.mount.controller.impl.AbstractMountController;
import com.ticxo.modelengine.api.nms.entity.wrapper.MoveController;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Optional;

public class LunarRobotMountController extends AbstractMountController {
    private final LunarRobotEntity robot;

    public LunarRobotMountController(Entity entity, Mount mount, LunarRobotEntity robot) {
        super(entity, mount);
        this.robot = robot;
    }

    @Override
    public void updateDriverMovement(MoveController moveController, ActiveModel activeModel) {
        Optional<?> optMm = activeModel.getMountManager();
        if (optMm.isEmpty()) return;
        Object bm = optMm.get();
        if (!(bm instanceof MountManager mm)) return;

        // 1. Shift key handling: dismount driver
        if (this.input != null && this.input.isSneak()) {
            mm.dismountDriver();
            moveController.move(0, 0, 0, 0);
            return;
        }

        // 2. Horizontal movement
        float side = this.input != null ? this.input.getSide() : 0f;
        float front = this.input != null ? this.input.getFront() : 0f;
        moveController.move(side, 0.0f, front, 1.0f);

        // 3. Task-specific vertical / jump / flight behavior
        boolean isJump = this.input != null && this.input.isJump();
        RobotTask task = robot.getData().getActiveTask();

        if (task == RobotTask.SPEED) {
            // In SPEED mode: Space is used for sprint boost, NO JUMPING, NO FLYING
            Player rider = this.entity instanceof Player p ? p : null;
            robot.handleSpeedBoostInMountController(isJump, rider);
            // Intentionally do NOT call moveController.jump()
        } else if (task == RobotTask.THRUST) {
            // In THRUST mode: finite duration jetpack flight & jumping
            Player rider = this.entity instanceof Player p ? p : null;
            robot.handleThrustFlightInMountController(isJump, moveController, rider);
        } else {
            // Other tasks: standard jump if on ground
            if (isJump && (moveController.isOnGround() || moveController.isInWater())) {
                moveController.jump();
            }
        }
    }

    @Override
    public void updatePassengerMovement(MoveController moveController, ActiveModel activeModel) {
        Optional<?> optMm = activeModel.getMountManager();
        if (optMm.isEmpty()) return;
        Object bm = optMm.get();
        if (bm instanceof MountManager mm) {
            if (this.input != null && this.input.isSneak()) {
                mm.dismountRider(this.entity);
            }
        }
    }
}
