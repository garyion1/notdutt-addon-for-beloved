package dev.khanh.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Applies the Sodium compatibility mixins only when Sodium is installed. */
public final class SodiumMixinPlugin implements IMixinConfigPlugin {
    private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return SODIUM; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() {
        return SODIUM ? List.of("SodiumDeepslateMixins$Culling", "SodiumDeepslateMixins$Downgrade",
            "SodiumDeepslateMixins$Visibility", "SodiumDeepslateMixins$Light") : List.of();
    }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
