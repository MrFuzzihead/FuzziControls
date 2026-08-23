package com.mrfuzzihead.fuzzicontrols.mixins.early;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mrfuzzihead.fuzzicontrols.interfaces.SlotAwareScreen;

/**
 * Mixin that caches a reference to the first {@link GuiSlot} found on this screen.
 *
 * <p>
 * Injects into {@link GuiScreen#setWorldAndResolution}, which is called by Minecraft for
 * every screen before {@code initGui()} and is <em>never overridden</em> by concrete screens.
 * This ensures the slot scan fires for all screens, including those that override
 * {@code initGui()}.
 */
@Mixin(value = GuiScreen.class, priority = 500)
public abstract class MixinGuiScreen_SlotTracker implements SlotAwareScreen {

    @Unique
    private GuiSlot fuzzicontrols$guiSlot = null;

    @Inject(method = "setWorldAndResolution", at = @At("TAIL"))
    private void fuzzicontrols$onSetWorldAndResolution(Minecraft mc, int width, int height, CallbackInfo ci) {
        // Scan the concrete runtime class hierarchy for any GuiSlot field.
        fuzzicontrols$guiSlot = null;
        GuiScreen self = (GuiScreen) (Object) this;
        Class<?> clazz = self.getClass();
        while (clazz != GuiScreen.class) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (GuiSlot.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        Object val = field.get(self);
                        if (val instanceof GuiSlot slot) {
                            GuiSlotAccessors acc = (GuiSlotAccessors) slot;
                            if (acc.callGetSize() > 0) {
                                fuzzicontrols$guiSlot = slot;
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    @Override
    @Nullable
    public GuiSlot getSlot() {
        return fuzzicontrols$guiSlot;
    }
}
