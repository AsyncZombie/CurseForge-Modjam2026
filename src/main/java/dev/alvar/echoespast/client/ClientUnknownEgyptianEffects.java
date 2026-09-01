package dev.alvar.echoespast.client;

import dev.alvar.echoespast.network.UnknownEgyptianEffectsPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Client mirror of lasting Duat panels and the royal chariot raid. */
public final class ClientUnknownEgyptianEffects {
    private static int bossEntityId = -1;
    private static final List<Wall> walls = new ArrayList<>();
    private static final List<ChariotArrow> chariotArrows = new ArrayList<>();
    private static ChariotRaid chariotRaid = ChariotRaid.INACTIVE;

    private ClientUnknownEgyptianEffects() {
    }

    public static void receive(UnknownEgyptianEffectsPayload payload) {
        bossEntityId = payload.bossEntityId();
        walls.clear();
        for (UnknownEgyptianEffectsPayload.Wall wall : payload.walls()) {
            walls.add(new Wall(
                    new Vec3(wall.x(), wall.y(), wall.z()),
                    new Vec3(wall.dirX(), 0.0D, wall.dirZ()),
                    wall.halfSpan(),
                    wall.spawnGameTime(),
                    wall.expireGameTime()));
        }
        UnknownEgyptianEffectsPayload.ChariotRaid raid = payload.chariotRaid();
        if (!raid.active()) {
            chariotRaid = ChariotRaid.INACTIVE;
            chariotArrows.clear();
            return;
        }
        chariotRaid = new ChariotRaid(
                true,
                raid.minimumX(),
                raid.maximumX(),
                raid.minimumZ(),
                raid.maximumZ(),
                raid.seed(),
                raid.startGameTime(),
                raid.expireGameTime(),
                raid.ruins());
        chariotArrows.clear();
        for (UnknownEgyptianEffectsPayload.ChariotArrow arrow : payload.chariotArrows()) {
            chariotArrows.add(new ChariotArrow(
                    arrow.id(),
                    arrow.chariotIndex(),
                    point(arrow.start()),
                    point(arrow.end()),
                    arrow.launchGameTime(),
                    arrow.impactGameTime()));
        }
    }

    public static void clear() {
        bossEntityId = -1;
        walls.clear();
        chariotArrows.clear();
        chariotRaid = ChariotRaid.INACTIVE;
    }

    public static int bossEntityId() {
        return bossEntityId;
    }

    public static List<Wall> walls() {
        return walls;
    }

    public static ChariotRaid chariotRaid() {
        return chariotRaid;
    }

    public static List<ChariotArrow> chariotArrows() {
        return chariotArrows;
    }

    private static Vec3 point(UnknownEgyptianEffectsPayload.Point point) {
        return new Vec3(point.x(), point.y(), point.z());
    }

    public record Wall(
            Vec3 center,
            Vec3 direction,
            double halfSpan,
            long spawnGameTime,
            long expireGameTime) {
    }

    public record ChariotRaid(
            boolean active,
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            int seed,
            long startGameTime,
            long expireGameTime,
            boolean ruins) {
        private static final ChariotRaid INACTIVE =
                new ChariotRaid(false, 0, 0, 0, 0, 0, 0, 0, false);
    }

    public record ChariotArrow(
            int id,
            int chariotIndex,
            Vec3 start,
            Vec3 end,
            long launchGameTime,
            long impactGameTime) {
    }
}
