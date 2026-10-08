package com.fox;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nullable;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("FoxTinkerTraits")
@IFMLLoadingPlugin.SortingIndex(1001)
public class FoxCorePlugin implements IFMLLoadingPlugin {

    public FoxCorePlugin() {
        System.out.println("[FoxTinkerTraits] CoreMod loaded");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.fox.asm.WeaponMasterTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Mixins.addConfiguration("mixins.fox.json");
        System.out.println("[FoxTinkerTraits] Mixin configuration registered");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}