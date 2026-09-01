package dev.alvar.echoespast.relic;

/** Held Medusa-head pose: rest and active Euler rotations, blended on use. */
public final class MedusaHeadAimMath {
    public static final float ACTIVATION_POSE_TICKS = 6.0F;
    public static final PoseEuler REST = new PoseEuler(0.0F, 0.0F, -90.0F);
    public static final PoseEuler ACTIVE = new PoseEuler(0.0F, 0.0F, -90.0F);
    /**
     * The geo head cube starts two pixels above the item origin. Pull that
     * severed neck onto the origin after the hand Euler so the palm holds
     * the head instead of empty space.
     */
    public static final float HAND_GRIP_Y = -2.0F / 16.0F;
    public static final float REST_TILT_DEGREES = REST.y();
    public static final float ACTIVE_TILT_DEGREES = ACTIVE.y();

    private MedusaHeadAimMath() {}

    public static float activationPoseBlend(float elapsedUseTicks) {
        return Math.clamp(elapsedUseTicks / ACTIVATION_POSE_TICKS, 0.0F, 1.0F);
    }

    public static float tiltDegrees(float poseBlend) {
        return rotation(poseBlend).y();
    }

    public static PoseEuler rotation(float poseBlend) {
        return REST.lerp(ACTIVE, Math.clamp(poseBlend, 0.0F, 1.0F));
    }

    public record PoseEuler(float x, float y, float z) {
        public PoseEuler lerp(PoseEuler other, float amount) {
            float t = Math.clamp(amount, 0.0F, 1.0F);
            return new PoseEuler(
                    x + (other.x - x) * t,
                    y + (other.y - y) * t,
                    z + (other.z - z) * t);
        }

        public PoseEuler wrap() {
            return new PoseEuler(
                    wrapDegrees(x),
                    wrapDegrees(y),
                    wrapDegrees(z));
        }

        /**
         * Collapse equivalent Euler triples. With {@code Rx * Ry * Rz},
         * {@code Y = ±90} locks X and Z onto one axis, so many number
         * triples render as the same pose.
         */
        public PoseEuler canonical() {
            PoseEuler wrapped = wrap();
            if (Math.abs(wrapped.y - 90.0F) <= 0.05F) {
                return new PoseEuler(
                        0.0F,
                        90.0F,
                        wrapDegrees(wrapped.z + wrapped.x));
            }
            if (Math.abs(wrapped.y + 90.0F) <= 0.05F) {
                return new PoseEuler(
                        0.0F,
                        -90.0F,
                        wrapDegrees(wrapped.z - wrapped.x));
            }
            return wrapped;
        }

        public PoseEuler withAxis(char axis, float degrees) {
            return switch (Character.toLowerCase(axis)) {
                case 'x' -> new PoseEuler(degrees, y, z);
                case 'y' -> new PoseEuler(x, degrees, z);
                case 'z' -> new PoseEuler(x, y, degrees);
                default -> this;
            };
        }

        public PoseEuler addAxis(char axis, float delta) {
            return withAxis(axis, axisDegrees(axis) + delta).canonical();
        }

        public float axisDegrees(char axis) {
            return switch (Character.toLowerCase(axis)) {
                case 'x' -> x;
                case 'y' -> y;
                case 'z' -> z;
                default -> 0.0F;
            };
        }

        private static float wrapDegrees(float degrees) {
            float wrapped = degrees % 360.0F;
            if (wrapped >= 180.0F) {
                return wrapped - 360.0F;
            }
            if (wrapped < -180.0F) {
                return wrapped + 360.0F;
            }
            return wrapped;
        }
    }
}
