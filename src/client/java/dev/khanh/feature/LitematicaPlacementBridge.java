package dev.khanh.feature;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Optional bridge: Zero does not load or bundle Litematica when it is absent. */
final class LitematicaPlacementBridge {
    private static String diagnostic = "Select and enable a Litematica placement";

    static String diagnostic() { return diagnostic; }
    record Placement(String name, World schematicWorld, BlockPos min, BlockPos max, List<Region> regions) {
        boolean contains(BlockPos pos) {
            if (!(pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ())) return false;
            for (Region region : regions) if (region.contains(pos)) return true;
            return false;
        }
    }

    private record Region(BlockPos min, BlockPos max) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    private LitematicaPlacementBridge() { }

    static boolean installed() {
        return FabricLoader.getInstance().isModLoaded("litematica");
    }

    static Placement selected() {
        if (!installed()) {
            diagnostic = "Install Litematica and MaLiLib";
            return null;
        }
        try {
            Object manager = call(Class.forName("fi.dy.masa.litematica.data.DataManager"), null,
                "getSchematicPlacementManager");
            if (manager == null) {
                diagnostic = "Litematica placement manager is unavailable";
                return null;
            }
            Object selected = call(manager.getClass(), manager, "getSelectedSchematicPlacement");
            if (selected == null) {
                Object all = call(manager.getClass(), manager, "getAllSchematicsPlacements");
                if (all instanceof List<?> placements) {
                    for (Object candidate : placements) {
                        if (candidate == null || !Boolean.TRUE.equals(
                            call(candidate.getClass(), candidate, "isEnabled"))) continue;
                        if (selected != null) {
                            diagnostic = "Select one placement; multiple are enabled";
                            return null;
                        }
                        selected = candidate;
                    }
                }
                if (selected == null) {
                    diagnostic = "Enable a Litematica placement";
                    return null;
                }
            }
            if (!Boolean.TRUE.equals(call(selected.getClass(), selected, "isEnabled"))) {
                diagnostic = "Enable the selected Litematica placement";
                return null;
            }
            Object box = call(selected.getClass(), selected, "getEclosingBox");
            Object world = call(Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler"), null,
                "getSchematicWorld");
            if (box == null || !(world instanceof World schematicWorld)) {
                diagnostic = "Litematica has not loaded the selected schematic world";
                return null;
            }
            Region enclosing = bounds(box);
            if (enclosing == null) {
                diagnostic = "Selected placement has no valid bounds";
                return null;
            }
            Class<?> filter = Class.forName(
                "fi.dy.masa.litematica.schematic.placement.SubRegionPlacement$RequiredEnabled");
            Object enabled = filter.getField("PLACEMENT_ENABLED").get(null);
            Object subRegions = selected.getClass().getMethod("getSubRegionBoxes", filter)
                .invoke(selected, enabled);
            if (!(subRegions instanceof Map<?, ?> boxes) || boxes.isEmpty()) {
                diagnostic = "Enable at least one schematic sub-region";
                return null;
            }
            List<Region> regions = new ArrayList<>();
            for (Object subRegion : boxes.values()) {
                Region region = bounds(subRegion);
                if (region != null) regions.add(region);
            }
            if (regions.isEmpty()) {
                diagnostic = "No valid schematic sub-regions were found";
                return null;
            }
            Object name = call(selected.getClass(), selected, "getName");
            diagnostic = "Placement ready";
            return new Placement(name instanceof String s ? s : "Schematic", schematicWorld,
                enclosing.min(), enclosing.max(), List.copyOf(regions));
        } catch (ReflectiveOperationException | LinkageError error) {
            diagnostic = "Litematica bridge error: " + error.getClass().getSimpleName();
            return null;
        }
    }

    static ItemStack requiredItem(BlockState state) {
        if (installed()) {
            try {
                Class<?> cacheType = Class.forName("fi.dy.masa.litematica.materials.MaterialCache");
                Object cache = call(cacheType, null, "getInstance");
                if (cache != null) {
                    Method method = cacheType.getMethod("getRequiredBuildItemForState", BlockState.class);
                    Object stack = method.invoke(cache, state);
                    if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) return itemStack;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Simple blocks still work if Litematica changes its material helper.
            }
        }
        return new ItemStack(state.getBlock().asItem());
    }

    private static Object call(Class<?> type, Object target, String method) throws ReflectiveOperationException {
        return type.getMethod(method).invoke(target);
    }

    private static Region bounds(Object box) throws ReflectiveOperationException {
        if (box == null) return null;
        Object first = call(box.getClass(), box, "getPos1");
        Object second = call(box.getClass(), box, "getPos2");
        if (!(first instanceof BlockPos a) || !(second instanceof BlockPos b)) return null;
        return new Region(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ())), new BlockPos(Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }
}
