package com.enn3developer.gtblocker.mixins;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.ITargetMod;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.gtnewhorizon.gtnhmixins.builders.TargetModBuilder;

/**
 * Late mixins into GregTech and AE2, each applied only when its mod is loaded. All of them are required: if a pack
 * update moves a target, the game refuses to start instead of silently letting locked crafts through.
 */
public enum Mixins implements IMixins {

    ;

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @NotNull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }

    enum TargetedMod implements ITargetMod {

        GREGTECH("gregtech"),
        AE2("appliedenergistics2");

        private final TargetModBuilder builder;

        TargetedMod(String modId) {
            this.builder = new TargetModBuilder().setModId(modId);
        }

        @NotNull
        @Override
        public TargetModBuilder getBuilder() {
            return builder;
        }
    }
}
