package com.enn3developer.gtblocker.core;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod entry point. Its only job is registering {@link RecipeMatchTransformer}, which gates every
 * {@code IRecipe.matches} implementation; mixins into GregTech and AE2 are registered separately through
 * {@link com.enn3developer.gtblocker.mixins.LateMixinLoader}.
 * <p>
 * Sorted after FML's deobfuscation transformer (index 1000), so the transformer sees SRG member names and MCP
 * class names in production, the same shape mod classes ship in.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("GTBlocker core")
@IFMLLoadingPlugin.SortingIndex(1100)
@IFMLLoadingPlugin.TransformerExclusions({ "com.enn3developer.gtblocker.core" })
public class GTBlockerCore implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { RecipeMatchTransformer.class.getName() };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
