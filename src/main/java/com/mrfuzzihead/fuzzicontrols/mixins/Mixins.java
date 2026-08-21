package com.mrfuzzihead.fuzzicontrols.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.mrfuzzihead.fuzzicontrols.Config;

public enum Mixins implements IMixins {

    GUI_SCREEN_ACCESSORS(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("GuiScreenAccessors")),

    ANALOG_MOVEMENT(new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("MixinEntityPlayerSP")
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
