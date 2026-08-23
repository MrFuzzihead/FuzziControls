package com.mrfuzzihead.fuzzicontrols.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.mrfuzzihead.fuzzicontrols.Config;

public enum Mixins implements IMixins {

    GUI_SCREEN_ACCESSORS(new MixinBuilder().setPhase(Phase.EARLY)
        .addClientMixins("GuiScreenAccessors")),

    GUI_OPTION_SLIDER_ACCESSORS(new MixinBuilder().setPhase(Phase.EARLY)
        .addClientMixins("GuiOptionSliderAccessors")),

    GUI_SLOT_ACCESSORS(new MixinBuilder().setPhase(Phase.EARLY)
        .addClientMixins("GuiSlotAccessors")),

    GUI_SCREEN_SLOT_TRACKER(new MixinBuilder().setPhase(Phase.EARLY)
        .addClientMixins("MixinGuiScreen_SlotTracker")),

    GUI_OPTIONS_ROW_LIST_ROW_ACCESSORS(new MixinBuilder().setPhase(Phase.EARLY)
        .addClientMixins("GuiOptionsRowListRowAccessors")),

    ANALOG_MOVEMENT(new MixinBuilder().setPhase(Phase.EARLY)
        .addClientMixins("MixinEntityPlayerSP")
        .setApplyIf(() -> Config.analogMovement));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
