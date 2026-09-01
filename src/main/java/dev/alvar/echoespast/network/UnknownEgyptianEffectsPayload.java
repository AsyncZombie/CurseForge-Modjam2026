package dev.alvar.echoespast.network;

import dev.alvar.echoespast.EchoesShowThePast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Syncs lasting Duat gate panels and the royal chariot raid so Unknown can
 * keep fighting while both route-control fields remain live.
 */
public record UnknownEgyptianEffectsPayload(
        int bossEntityId,
        List<Wall> walls,
        ChariotRaid chariotRaid,
        List<ChariotArrow> chariotArrows)
        implements CustomPacketPayload {
    public static final Type<UnknownEgyptianEffectsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EchoesShowThePast.MOD_ID, "unknown_egyptian_effects"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Wall> WALL_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            Wall::x,
            ByteBufCodecs.FLOAT,
            Wall::y,
            ByteBufCodecs.FLOAT,
            Wall::z,
            ByteBufCodecs.FLOAT,
            Wall::dirX,
            ByteBufCodecs.FLOAT,
            Wall::dirZ,
            ByteBufCodecs.FLOAT,
            Wall::halfSpan,
            ByteBufCodecs.VAR_LONG,
            Wall::spawnGameTime,
            ByteBufCodecs.VAR_LONG,
            Wall::expireGameTime,
            Wall::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ChariotRaid> CHARIOT_RAID_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    ChariotRaid::active,
                    ByteBufCodecs.FLOAT,
                    ChariotRaid::minimumX,
                    ByteBufCodecs.FLOAT,
                    ChariotRaid::maximumX,
                    ByteBufCodecs.FLOAT,
                    ChariotRaid::minimumZ,
                    ByteBufCodecs.FLOAT,
                    ChariotRaid::maximumZ,
                    ByteBufCodecs.VAR_INT,
                    ChariotRaid::seed,
                    ByteBufCodecs.VAR_LONG,
                    ChariotRaid::startGameTime,
                    ByteBufCodecs.VAR_LONG,
                    ChariotRaid::expireGameTime,
                    ByteBufCodecs.BOOL,
                    ChariotRaid::ruins,
                    ChariotRaid::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, Point> POINT_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            Point::x,
            ByteBufCodecs.FLOAT,
            Point::y,
            ByteBufCodecs.FLOAT,
            Point::z,
            Point::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ChariotArrow> CHARIOT_ARROW_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ChariotArrow::id,
                    ByteBufCodecs.VAR_INT,
                    ChariotArrow::chariotIndex,
                    POINT_CODEC,
                    ChariotArrow::start,
                    POINT_CODEC,
                    ChariotArrow::end,
                    ByteBufCodecs.VAR_LONG,
                    ChariotArrow::launchGameTime,
                    ByteBufCodecs.VAR_LONG,
                    ChariotArrow::impactGameTime,
                    ChariotArrow::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, UnknownEgyptianEffectsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    UnknownEgyptianEffectsPayload::bossEntityId,
                    ByteBufCodecs.collection(ArrayList::new, WALL_CODEC),
                    UnknownEgyptianEffectsPayload::walls,
                    CHARIOT_RAID_CODEC,
                    UnknownEgyptianEffectsPayload::chariotRaid,
                    ByteBufCodecs.collection(ArrayList::new, CHARIOT_ARROW_CODEC),
                    UnknownEgyptianEffectsPayload::chariotArrows,
                    UnknownEgyptianEffectsPayload::new);

    public record Wall(
            float x,
            float y,
            float z,
            float dirX,
            float dirZ,
            float halfSpan,
            long spawnGameTime,
            long expireGameTime) {
    }

    public record ChariotRaid(
            boolean active,
            float minimumX,
            float maximumX,
            float minimumZ,
            float maximumZ,
            int seed,
            long startGameTime,
            long expireGameTime,
            boolean ruins) {
        public static ChariotRaid inactive() {
            return new ChariotRaid(false, 0, 0, 0, 0, 0, 0, 0, false);
        }
    }

    public record Point(float x, float y, float z) {
    }

    public record ChariotArrow(
            int id,
            int chariotIndex,
            Point start,
            Point end,
            long launchGameTime,
            long impactGameTime) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
