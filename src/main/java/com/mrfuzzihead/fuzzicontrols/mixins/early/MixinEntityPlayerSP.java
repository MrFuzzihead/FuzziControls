package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.fuzzicontrols.Config;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerManager;
import com.mrfuzzihead.fuzzicontrols.controller.ControllerState;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP {

    /** Vanilla sneak multiplier applied to {@code AIMoveSpeed} when sneaking. */
    private static final float SNEAK_FACTOR = 0.3f;

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

        float forwardMag = -state.leftStickY();
        float strafeMag = -state.leftStickX();

        // Quadratic curve so gentle tilt is precise.
        if (movementInput.moveForward != 0f) {
            movementInput.moveForward = Math.copySign(forwardMag * forwardMag, movementInput.moveForward);
        }
        if (movementInput.moveStrafe != 0f) {
            movementInput.moveStrafe = Math.copySign(strafeMag * strafeMag, movementInput.moveStrafe);
        }

        // When sneaking, cap the analog range at vanilla sneak speed instead of letting it
        // reach full walk/run. Vanilla multiplies AIMoveSpeed by 0.3F, so we pre-scale
        // moveForward/moveStrafe so that full stick deflection = the sneak ceiling.
        if (movementInput.sneak) {
            movementInput.moveForward *= SNEAK_FACTOR;
            movementInput.moveStrafe *= SNEAK_FACTOR;
        }
    }
}
