package com.dog.vaultoptimise.mixin.decocraft;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public final class DecocraftDedupMixinPlugin implements IMixinConfigPlugin {
    public static boolean eligible(boolean enabled, boolean dedicatedServer, String version) {
        return enabled && dedicatedServer && "3.0.4-1.18.2".equals(version);
    }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean enabled = Boolean.parseBoolean(System.getProperty("vaultoptimise.decocraft.sourceDedup", "true"));
        boolean dedicatedServer = FMLLoader.getDist() == Dist.DEDICATED_SERVER;
        if (!enabled || !dedicatedServer) return false;
        var mods = LoadingModList.get();
        var file = mods == null ? null : mods.getModFileById("decocraft");
        return file != null && file.getMods().stream().anyMatch(mod -> mod.getModId().equals("decocraft")
                && eligible(enabled, dedicatedServer, mod.getVersion().toString()));
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
