package com.dog.vaultoptimise.mixin.automation.botany;

import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Pseudo
@Mixin(targets = "net.darkhax.botanypots.BotanyPotHelper", remap = false)
public abstract class BotanyRecipeListsMixin {
    @SuppressWarnings("unchecked")
    @Redirect(method = {"findCrop", "findSoil"},
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getAllRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/List;"),
            remap = false, require = 2, expect = 2, allow = 2)
    private static <C extends Container, T extends Recipe<C>> List<T> vaultoptimise$recipeSnapshot(
            RecipeManager manager, RecipeType<T> type) {
        if (manager.getClass() != RecipeManager.class) {
            return manager.getAllRecipesFor(type);
        }

        Map<ResourceLocation, Recipe<C>> recipes = ((RecipeManagerAccessor) manager).vaultoptimise$recipesByType(type);
        if (recipes instanceof ImmutableMap<?, ?>) {
            // Botany only iterates this list. An immutable map retains the snapshot across reloads.
            return (List<T>) (List<?>) ((ImmutableMap<ResourceLocation, Recipe<C>>) recipes).values().asList();
        }

        // Script-owned mutable maps still need a snapshot, not a cached result or a live iterator.
        // Copy directly instead of constructing the stream pipeline and growing its result list.
        return (List<T>) (List<?>) new ArrayList<>(recipes.values());
    }
}
