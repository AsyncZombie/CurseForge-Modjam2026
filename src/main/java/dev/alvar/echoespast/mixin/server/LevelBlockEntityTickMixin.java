package dev.alvar.echoespast.mixin.server;

import dev.alvar.echoespast.server.MaterializedEchoManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
public abstract class LevelBlockEntityTickMixin {
    @Redirect(
            method = "tickBlockEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void echoesShowThePast$pauseTemporaryBlockEntity(TickingBlockEntity ticker) {
        Level level = (Level) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)
                || !MaterializedEchoManager.isProtected(serverLevel, ticker.getPos())) {
            ticker.tick();
        }
    }
}
