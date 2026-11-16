package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;

import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.AnalogMovement;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerState;
import com.mrfuzzihead.fuzzicontrols.util.KeyboardHelper;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP {

    @Shadow
    public MovementInput movementInput;

    /**
     * Marker merged into {@link EntityPlayerSP}, with no mixin annotation so it survives as a real
     * method on the target.
     *
     * <p>
     * {@code @Inject} handlers are inlined and never exist on the transformed class, so without this
     * marker there is no way to tell whether this mixin was applied (its {@code setApplyIf} depends on
     * {@code Config.analogMovement}). The client self-test asserts its presence.
     */
    @SuppressWarnings("unused")
    private static boolean fuzzicontrols$analogMovementApplied() {
        return true;
    }

    @Inject(
        method = "onLivingUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/MovementInput;updatePlayerMoveState()V",
            shift = At.Shift.AFTER))
    private void fuzzicontrols$scaleAnalogMovement(CallbackInfo ci) {
        // Always clear first: if analogue scaling is disabled or the controller is idle, vanilla
        // keyboard movement must run at full speed.
        AnalogMovement.clearAnalogSpeedScale();

        if (!Config.analogMovement) return;

        ControllerManager mgr = ControllerManager.getInstance();
        if (!mgr.isActive()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.currentScreen != null || !Display.isActive()) return;

        ControllerState state = mgr.getState();

        // Keyboard takes priority per axis. driveKey() sets the KeyBinding state for the
        // controller, so movementInput alone cannot tell us whether a physical keyboard key is
        // down; ask LWJGL directly.
        boolean keyboardForward = isPhysicallyDown(mc.gameSettings.keyBindForward)
            || isPhysicallyDown(mc.gameSettings.keyBindBack);
        boolean keyboardStrafe = isPhysicallyDown(mc.gameSettings.keyBindLeft)
            || isPhysicallyDown(mc.gameSettings.keyBindRight);

        float stickForward = -state.leftStickY();
        float stickStrafe = -state.leftStickX();

        // Only axes the stick is actually deflecting are replaced. A centred stick (dead zone ->
        // exactly 0.0) must leave the keyboard's WASD value untouched, or plugging in a controller
        // would silently disable keyboard movement.
        if (!keyboardForward) {
            movementInput.moveForward = AnalogMovement.apply(movementInput.moveForward, stickForward);
        }
        if (!keyboardStrafe) {
            movementInput.moveStrafe = AnalogMovement.apply(movementInput.moveStrafe, stickStrafe);
        }

        // Vanilla moveFlying() normalises the input vector to length 1, so the per-axis analogue
        // values only carry direction. Scale the movement friction by the stick's radial magnitude
        // to get true analogue speed. Skip it for hybrid keyboard + stick input so the keyboard
        // keeps its normal speed.
        if (!keyboardForward && !keyboardStrafe) {
            float scale = AnalogMovement.speedScale(stickStrafe, stickForward, movementInput.sneak);
            // EntityPlayerSP applies this after updatePlayerMoveState() for bow drawing/eating.
            if (mc.thePlayer != null && mc.thePlayer.isUsingItem() && !mc.thePlayer.isRiding()) {
                scale *= 0.2f;
            }
            if (scale > 0f) AnalogMovement.setAnalogSpeedScale(scale);
        }
    }

    private static boolean isPhysicallyDown(KeyBinding binding) {
        return binding != null && KeyboardHelper.isPhysicallyDown(binding.getKeyCode());
    }
}
