package com.dog.vaultoptimise.mixin.automation;

import com.dog.vaultoptimise.config.AutomationConfig;
import com.dog.vaultoptimise.config.AutomationConfig.Patch;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class AutomationMixinPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE = "com.dog.vaultoptimise.mixin.automation.";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (FMLLoader.getDist() != Dist.DEDICATED_SERVER || !enabled("enabled")
                || !versionMatches("minecraft", "1.18.2") || !mixinClassName.startsWith(PACKAGE)) {
            return false;
        }

        return switch (mixinClassName.substring(PACKAGE.length())) {
            case "building.CoalToSteelGuardMixin" ->
                    configured(Patch.COAL_TO_STEEL) && enabled("building") && versionMatches("davebuildingmod", "6.0");
            case "building.SteelToCoalGuardMixin" ->
                    configured(Patch.STEEL_TO_IRON) && enabled("building") && versionMatches("davebuildingmod", "6.0");
            case "powah.IgnoredRedstoneMixin" ->
                    configured(Patch.POWAH_REDSTONE) && enabled("powah") && versionMatches("powah", "3.0.8");
            case "botany.BotanyRecipeListsMixin", "botany.RecipeManagerAccessor" ->
                    configured(Patch.BOTANY_RECIPES) && enabled("botany") && versionMatches("botanypots", "8.1.32");
            case "ae2.CompositeStorageInventoryCacheMixin" ->
                    configured(Patch.AE2_INVENTORY) && enabled("ae2") && versionMatches("ae2", "11.7.6");
            case "ae2.StorageServiceWatcherDiffMixin" ->
                    configured(Patch.AE2_WATCHERS) && enabled("ae2") && enabled("ae2WatcherDiff") && versionMatches("ae2", "11.7.6");
            case "vaultfilters.VFCacheMixin", "vaultfilters.VFTestsInvoker" ->
                    configured(Patch.VAULT_FILTERS) && enabled("vaultfilters") && versionMatches("vaultfilters", "1.33.0")
                            && versionMatches("ae2", "11.7.6");
            case "pipez.OrderedDrawerInsertionMixin", "drawers.DrawerItemHandlerMixin",
                    "drawers.StandardDrawerGroupMixin", "drawers.CompactingDrawerGroupMixin",
                    "drawers.DrawerControllerMixin", "drawers.DrawerSlaveMixin" ->
                    configured(Patch.PIPEZ_DRAWERS) && enabled("pipezDrawers") && versionMatches("pipez", "1.18.2-1.1.5")
                            && versionMatches("storagedrawers", "10.2.1");
            case "vault.DiffuserUnusedScanMixin" ->
                    configured(Patch.DIFFUSER) && enabled("diffuser") && versionMatches("the_vault", "1.18.2-3.21.62");
            default -> false;
        };
    }

    private static boolean configured(Patch patch) {
        return ConfigHolder.CONFIG.enabled(patch);
    }

    private static final class ConfigHolder {
        private static final AutomationConfig CONFIG = load();

        private static AutomationConfig load() {
            var file = FMLPaths.CONFIGDIR.get().resolve(AutomationConfig.FILE_NAME);
            try {
                return AutomationConfig.load(file);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load VaultOptimise automation config: " + file, e);
            }
        }
    }

    private static boolean enabled(String patch) {
        return Boolean.parseBoolean(System.getProperty("vaultoptimise.automation." + patch, "true"));
    }

    private static boolean versionMatches(String id, String version) {
        var mods = LoadingModList.get();
        var file = mods == null ? null : mods.getModFileById(id);
        return file != null && file.getMods().stream().anyMatch(mod ->
                mod.getModId().equals(id) && mod.getVersion().toString().equals(version));
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
