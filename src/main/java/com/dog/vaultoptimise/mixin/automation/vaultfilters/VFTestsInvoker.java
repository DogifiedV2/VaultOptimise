package com.dog.vaultoptimise.mixin.automation.vaultfilters;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.joseph.vaultfilters.VFTests", remap = false)
public interface VFTestsInvoker {
    @Invoker(value = "basicFilterTest", remap = false)
    static boolean vaultoptimise$basicFilterTest(ItemStack stack, Object filterStack, Level level) {
        throw new AssertionError();
    }
}
