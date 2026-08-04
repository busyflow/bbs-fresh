package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Turns a crowd's walk keyframes into per-member positions. Shared by server playback and the
 * editor preview so both agree exactly.
 *
 * <p>A trip is described entirely by the segment between two waypoints. Progress along it is
 * shaped by the departing waypoint's ease, then offset per member by its stagger, so members
 * set off at slightly different moments while every one of them lands on the target waypoint
 * at the target tick — no overshoot, no rubber-banding back into place.</p>
 */
public final class CrowdWalkEvaluator
{
    private static final double EPSILON = 1.0E-10D;

    private CrowdWalkEvaluator()
    {}

    public static Frame frame(Replay replay, float filmTick)
    {
        if (replay == null || replay.keyframes.crowdWalk.isEmpty())
        {
            return null;
        }

        float tick = localTick(replay, filmTick);
        KeyframeSegment<CrowdWalk> segment = replay.keyframes.crowdWalk.find(tick);

        if (segment == null || segment.a == null || segment.a.getValue() == null)
        {
            return null;
        }

        CrowdWalk from = segment.a.getValue();
        CrowdWalk to = segment.b == null || segment.b.getValue() == null ? from : segment.b.getValue();
        Vec3d start = from.position();
        Vec3d end = to.position();
        boolean samePosition = start.squaredDistanceTo(end) <= EPSILON;
        float linear = segment.isSame() ? 0F : MathHelper.clamp(segment.x, 0F, 1F);
        float startTick = segment.a.getTick();
        float targetTick = segment.b == null ? startTick : segment.b.getTick();

        Vec3d direction = end.subtract(start);

        direction = direction.lengthSquared() > EPSILON ? direction.normalize() : new Vec3d(0D, 0D, 1D);

        return new Frame(from, to, start, end, direction, ease(linear, from.ease),
            !samePosition && linear > 0F && linear < 1F, startTick, targetTick);
    }

    /**
     * Where a member starting at {@code startLocal} sits right now, written into
     * {@code output}. Allocation-free: the visual crowd calls this once per drawn member
     * per frame.
     */
    public static void memberPosition(Frame frame, int index, double startX, double startY, double startZ,
        double[] output)
    {
        if (frame == null || output == null || output.length < 3)
        {
            return;
        }

        double progress = memberProgress(frame, index);
        double loosen = 1D + frame.from.spread * Math.sin(Math.PI * progress) * 0.35D;

        output[0] = MathHelper.lerp(progress, frame.start.x, frame.end.x) + startX * loosen;
        output[1] = MathHelper.lerp(progress, frame.start.y, frame.end.y) + startY;
        output[2] = MathHelper.lerp(progress, frame.start.z, frame.end.z) + startZ * loosen;
    }

    /** Allocating form of {@link #memberPosition}, for the live actor tier. */
    public static Vec3d member(Frame frame, int index, Vec3d startLocal)
    {
        double[] output = new double[3];
        Vec3d local = startLocal == null ? Vec3d.ZERO : startLocal;

        memberPosition(frame, index, local.x, local.y, local.z, output);

        return new Vec3d(output[0], output[1], output[2]);
    }

    /**
     * A member's own progress along the trip. Stagger delays members by a deterministic
     * amount, then the whole range is rescaled so the last one still reaches 1 exactly when
     * the crowd's progress does.
     */
    public static double memberProgress(Frame frame, int index)
    {
        float stagger = MathHelper.clamp(frame.from.stagger, 0F, 1F);

        if (stagger <= 0F)
        {
            return frame.progress;
        }

        double delay = stagger * offset(index);

        return MathHelper.clamp((frame.progress * (1D + stagger) - delay) / 1D, 0D, 1D);
    }

    /** Deterministic per-member value in [0, 1). Same member always leaves at the same moment. */
    private static double offset(int index)
    {
        int value = index * 0x9e3779b9;

        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;

        return (value & 0x00ffffff) / 16777216D;
    }

    /**
     * Blend between constant speed and a smoothstep that leaves and arrives at zero velocity.
     * Both ends are pinned, so easing never changes when the crowd arrives.
     */
    private static float ease(float linear, float amount)
    {
        float clamped = MathHelper.clamp(linear, 0F, 1F);
        float smooth = clamped * clamped * clamped * (clamped * (clamped * 6F - 15F) + 10F);

        return MathHelper.lerp(MathHelper.clamp(amount, 0F, 1F), clamped, smooth);
    }

    public static Vec3d replayOrigin(Replay replay, float filmTick)
    {
        float tick = localTick(replay, filmTick);

        return new Vec3d(
            replay.keyframes.x.interpolate(tick),
            replay.keyframes.y.interpolate(tick),
            replay.keyframes.z.interpolate(tick)
        );
    }

    private static float localTick(Replay replay, float filmTick)
    {
        int loop = replay.looping.get();

        if (loop <= 0)
        {
            return filmTick;
        }

        float wrapped = filmTick % loop;

        return wrapped < 0F ? wrapped + loop : wrapped;
    }

    public record Frame(CrowdWalk from, CrowdWalk to, Vec3d start, Vec3d end, Vec3d forward,
                        float progress, boolean moving, float startTick, float targetTick)
    {
        /** The waypoint whose settings govern this trip. */
        public CrowdWalk path()
        {
            return this.from;
        }

        public CrowdWalk target()
        {
            return this.to;
        }

        /** Crowd centre right now, before per-member stagger. */
        public Vec3d center()
        {
            return new Vec3d(
                MathHelper.lerp(this.progress, this.start.x, this.end.x),
                MathHelper.lerp(this.progress, this.start.y, this.end.y),
                MathHelper.lerp(this.progress, this.start.z, this.end.z)
            );
        }
    }
}
