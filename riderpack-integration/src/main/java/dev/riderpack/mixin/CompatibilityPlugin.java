package dev.riderpack.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** KRC Villagers already supplies the same GH physical-side correction. */
public final class CompatibilityPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".ImmediatelyFastMeshLayerMixin")
                || mixinClassName.endsWith(".AcceleratedMeshIdentityMixin")) {
            var mods = LoadingModList.get().getMods();
            return mods.stream().anyMatch(mod -> mod.getModId().equals("acceleratedrendering"))
                    && mods.stream().anyMatch(mod -> mod.getModId().equals("immediatelyfast"));
        }
        if (!mixinClassName.endsWith(".MightyCombatSideMixin")) return true;
        return LoadingModList.get().getMods().stream()
                .noneMatch(mod -> mod.getModId().equals("krc_villagers"));
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
