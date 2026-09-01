package dev.alvar.echoespast.network;

import dev.alvar.echoespast.relic.EyeHazardType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record EyeHazardSignal(BlockPos position, EyeHazardType type, Direction direction) {
}
