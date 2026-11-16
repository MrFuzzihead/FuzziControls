package com.mrfuzzihead.fuzzicontrols.mixins.early;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.mrfuzzihead.fuzzicontrols.controller.AnalogMovement;

/**
 * Preserves analogue movement magnitude through vanilla's {@code Entity.moveFlying()}.
 *
 * <p>
 * {@code moveFlying()} normalises any non-zero input vector to length 1, so writing an analogue
 * value into {@code moveForward}/{@code moveStrafe} only changes the direction. The magnitude is
 * applied here by scaling the friction argument instead, but only for the local player and only
 * when {@link AnalogMovement} has an active scale for this tick.
 */
@Mixin(Entity.class)
public abstract class MixinEntity_MoveFlying {

    /** Marker merged into {@link Entity} for the client self-test. */
    @SuppressWarnings("unused")
    private static boolean fuzzicontrols$analogMoveFlyingApplied() {
        return true;
    }

    @ModifyVariable(method = "moveFlying", at = @At("HEAD"), argsOnly = true, index = 3)
    private float fuzzicontrols$scaleAnalogFriction(float friction) {
        if (!(((Object) this) instanceof EntityPlayerSP)) return friction;

        return AnalogMovement.scaleFriction(friction, AnalogMovement.getAnalogSpeedScale());
    }
}
