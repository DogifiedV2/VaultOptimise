package com.dog.vaultoptimise.mixin.automation.vaultfilters;

import com.google.common.cache.Cache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = "net.joseph.vaultfilters.VFCache", remap = false)
public abstract class VFCacheMixin {
    @Shadow(remap = false)
    @Final
    private static Cache<Object, ConcurrentHashMap<Object, Boolean>> ITEM_OUTER_CACHE;

    @Shadow(remap = false)
    public static ItemStack getStackFromObject(Object object) {
        throw new AssertionError();
    }

    /**
     * @author VaultOptimise
     * @reason Use one inner-cache lookup without changing VaultFilters 1.33.0 cache policy.
     */
    @Overwrite(remap = false)
    public static boolean getOrCreateFilter(Object stack, Object filterStack, Level level) {
        ConcurrentHashMap<Object, Boolean> innerCache = ITEM_OUTER_CACHE.getIfPresent(stack);
        if (innerCache == null) {
            innerCache = new ConcurrentHashMap<>();
            boolean result = VFTestsInvoker.vaultoptimise$basicFilterTest(
                    getStackFromObject(stack), filterStack, level);
            innerCache.put(filterStack, result);
            ITEM_OUTER_CACHE.put(stack, innerCache);
            return result;
        }

        Boolean cached = innerCache.get(filterStack);
        if (cached != null) {
            return cached;
        }

        boolean result = VFTestsInvoker.vaultoptimise$basicFilterTest(
                getStackFromObject(stack), filterStack, level);
        innerCache.put(filterStack, result);
        return result;
    }
}
