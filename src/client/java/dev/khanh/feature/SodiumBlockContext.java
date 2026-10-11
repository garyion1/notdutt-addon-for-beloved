package dev.khanh.feature;

import net.minecraft.block.BlockState;

/** Mixed into Sodium's block render context: the block currently being meshed. */
public interface SodiumBlockContext {
    BlockState khanh$state();
}
