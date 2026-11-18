package com.mrfuzzihead.fuzzicontrols.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Converts client-side gameplay feedback into short controller rumble pulses.
 *
 * <p>
 * Damage is detected by watching the local player's health rather than using
 * {@code LivingHurtEvent}: in multiplayer the client does not run the server-side damage method,
 * but the health update always reaches the client. This also catches fall, fire, drowning and mod
 * damage without needing a mixin.
 */
@SideOnly(Side.CLIENT)
public final class ControllerRumbleHandler {

    /** Previous health value, or {@code -1} before the first tick with a player. */
    private float lastHealth = -1f;

    /** The player instance the health value belongs to; resets on respawn/dimension change. */
    private EntityPlayer lastPlayer;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            lastHealth = -1f;
            lastPlayer = null;
            return;
        }

        if (mc.thePlayer != lastPlayer) {
            lastPlayer = mc.thePlayer;
            lastHealth = mc.thePlayer.getHealth();
            return;
        }

        float health = mc.thePlayer.getHealth();
        if (lastHealth >= 0f && health < lastHealth - 0.01f) {
            float damage = lastHealth - health;
            float strength = Math.min(0.9f, 0.35f + damage / 20f);
            ControllerManager.getInstance()
                .pulseRumble(strength, strength * 0.75f, 8);
        }
        lastHealth = health;
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;

        EntityPlayer player = event.entityPlayer;
        if (player != mc.thePlayer || event.target == null) return;

        ControllerManager.getInstance()
            .pulseRumble(0.45f, 0.3f, 4);
    }
}
