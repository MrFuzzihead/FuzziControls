package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.AnalogMovement;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerState;

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
        if (!Config.analogMovement) return;

        ControllerManager mgr = ControllerManager.getInstance();
        if (!mgr.isActive()) return;

        ControllerState state = mgr.getState();

        // Only axes the stick is actually deflecting are replaced. A centred stick (dead zone ->
        // exactly 0.0) must leave the keyboard's WASD value untouched, or plugging in a controller
        // would silently disable keyboard movement.
        movementInput.moveForward = AnalogMovement
            .apply(movementInput.moveForward, -state.leftStickY(), movementInput.sneak);
        movementInput.moveStrafe = AnalogMovement
            .apply(movementInput.moveStrafe, -state.leftStickX(), movementInput.sneak);
    }
}
