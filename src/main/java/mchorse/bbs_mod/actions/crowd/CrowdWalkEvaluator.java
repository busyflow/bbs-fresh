package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Turns a crowd's walk keyframes into per-member positions. Shared by server playback and the
 * editor preview so both agree exactly.
 *
 * <p>The waypoints are a route, not a series of trips. The crowd is carried along a curve that
 * passes through every one of them and only comes to rest at the two ends, so a waypoint in the
 * middle is a place the route goes through rather than a place the crowd stops. Easing at every
 * waypoint is what made a three-point walk read as three separate walks.</p>
 *
 * <p>What a member is given is a displacement - how far the crowd has moved from its first
 * waypoint - which is added to wherever that member was standing. The alternative, placing
 * members around the waypoint itself, cannot work for painted ground: painted ground is somewhere
 * in particular, and a crowd standing on it is not free to be re-centred.</p>
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

        List<Keyframe<CrowdWalk>> list = (List<Keyframe<CrowdWalk>>) replay.keyframes.crowdWalk.getKeyframes();
        int size = list.size();
        float tick = localTick(replay, filmTick);
        int i = indexAt(list, tick);

        CrowdWalk from = value(list, i);
        CrowdWalk to = value(list, Math.min(i + 1, size - 1));
        float startTick = list.get(i).getTick();
        float targetTick = list.get(Math.min(i + 1, size - 1)).getTick();
        float span = targetTick - startTick;
        float raw = span <= 0F ? 0F : MathHelper.clamp((tick - startTick) / span, 0F, 1F);

        /* The curve leaves from rest only at the first waypoint and settles only at the last.
         * Everywhere between, it is passing through. */
        boolean easeIn = i == 0;
        boolean easeOut = i + 1 >= size - 1;

        Vec3d p1 = from.position();
        Vec3d p2 = to.position();
        Vec3d m1 = tangent(list, i, span);
        Vec3d m2 = tangent(list, i + 1, span);
        Vec3d first = value(list, 0).position();

        float shaped = shape(raw, from.ease, easeIn, easeOut);
        Vec3d centre = hermite(p1, p2, m1, m2, shaped);
        Vec3d direction = p2.subtract(p1);

        direction = direction.lengthSquared() > EPSILON ? direction.normalize() : new Vec3d(0D, 0D, 1D);

        boolean moving = p1.squaredDistanceTo(p2) > EPSILON && raw > 0F && raw < 1F;

        return new Frame(from, to, p1, p2, m1, m2, first, centre, direction,
            raw, shaped, from.ease, easeIn, easeOut, moving, startTick, targetTick);
    }

    /** The keyframe the given tick sits on or after. */
    private static int indexAt(List<Keyframe<CrowdWalk>> list, float tick)
    {
        int size = list.size();

        if (tick <= list.get(0).getTick())
        {
            return 0;
        }

        if (tick >= list.get(size - 1).getTick())
        {
            return size - 1;
        }

        int low = 0;
        int high = size - 1;

        while (low < high)
        {
            int mid = (low + high + 1) >>> 1;

            if (list.get(mid).getTick() <= tick)
            {
                low = mid;
            }
            else
            {
                high = mid - 1;
            }
        }

        return low;
    }

    private static CrowdWalk value(List<Keyframe<CrowdWalk>> list, int index)
    {
        CrowdWalk value = list.get(MathHelper.clamp(index, 0, list.size() - 1)).getValue();

        return value == null ? new CrowdWalk() : value;
    }

    /**
     * The route's direction at one waypoint, taken from its neighbours either side and scaled
     * into this segment's own length.
     *
     * <p>Divided by the neighbours' tick spread rather than assuming the waypoints are evenly
     * spaced, because they are not - a waypoint dropped close after another would otherwise
     * throw the curve well past both of them.</p>
     */
    private static Vec3d tangent(List<Keyframe<CrowdWalk>> list, int index, float span)
    {
        int size = list.size();
        int i = MathHelper.clamp(index, 0, size - 1);
        int before = Math.max(0, i - 1);
        int after = Math.min(size - 1, i + 1);
        float spread = list.get(after).getTick() - list.get(before).getTick();

        if (spread <= 0F || span <= 0F)
        {
            return Vec3d.ZERO;
        }

        return value(list, after).position().subtract(value(list, before).position())
            .multiply(span / spread);
    }

    /** Cubic Hermite, so the route arrives at each waypoint going the way it leaves. */
    private static Vec3d hermite(Vec3d p1, Vec3d p2, Vec3d m1, Vec3d m2, double t)
    {
        double t2 = t * t;
        double t3 = t2 * t;
        double h00 = 2D * t3 - 3D * t2 + 1D;
        double h10 = t3 - 2D * t2 + t;
        double h01 = -2D * t3 + 3D * t2;
        double h11 = t3 - t2;

        return new Vec3d(
            h00 * p1.x + h10 * m1.x + h01 * p2.x + h11 * m2.x,
            h00 * p1.y + h10 * m1.y + h01 * p2.y + h11 * m2.y,
            h00 * p1.z + h10 * m1.z + h01 * p2.z + h11 * m2.z
        );
    }

    /**
     * Where a member starting at {@code base} sits right now, written into {@code output}.
     * Allocation-free: the visual crowd calls this once per drawn member per frame.
     *
     * <p>{@code centre} is the middle of the crowd's own arrangement, which spread pushes members
     * away from. It has to be given rather than assumed to be the origin - a painted crowd's
     * positions are world coordinates, and scaling those about the world origin throws the crowd
     * a hundred blocks sideways and back again over the course of one walk.</p>
     */
    public static void memberPosition(Frame frame, int index, double baseX, double baseY, double baseZ,
        double centreX, double centreY, double centreZ, double[] output)
    {
        if (frame == null || output == null || output.length < 3)
        {
            return;
        }

        double raw = memberProgress(frame, index);
        double t = shape((float) raw, frame.easeAmount, frame.easeIn, frame.easeOut);
        Vec3d at = hermite(frame.p1, frame.p2, frame.m1, frame.m2, t);
        double loosen = frame.from.spread * Math.sin(Math.PI * t) * 0.35D;

        output[0] = baseX + (baseX - centreX) * loosen + (at.x - frame.first.x);
        output[1] = baseY + (at.y - frame.first.y);
        output[2] = baseZ + (baseZ - centreZ) * loosen + (at.z - frame.first.z);
    }

    /** Allocating form of {@link #memberPosition}, for the live actor tier. */
    public static Vec3d member(Frame frame, int index, Vec3d base, Vec3d centre)
    {
        double[] output = new double[3];
        Vec3d at = base == null ? Vec3d.ZERO : base;
        Vec3d mid = centre == null ? Vec3d.ZERO : centre;

        memberPosition(frame, index, at.x, at.y, at.z, mid.x, mid.y, mid.z, output);

        return new Vec3d(output[0], output[1], output[2]);
    }

    /**
     * A member's own progress along the segment. Stagger delays members by a deterministic
     * amount, then the whole range is rescaled so the last one still reaches 1 exactly when
     * the crowd's progress does.
     */
    public static double memberProgress(Frame frame, int index)
    {
        float stagger = MathHelper.clamp(frame.from.stagger, 0F, 1F);

        if (stagger <= 0F)
        {
            return frame.raw;
        }

        double delay = stagger * offset(index);

        return MathHelper.clamp(frame.raw * (1D + stagger) - delay, 0D, 1D);
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
     * Blend between constant speed and one that starts or stops from a standstill.
     *
     * <p>Only the ends that are really ends are eased. A waypoint in the middle of a route gets
     * neither, so the crowd carries its speed through it instead of stopping dead at every point
     * it was told to visit.</p>
     */
    private static float shape(float t, float amount, boolean easeIn, boolean easeOut)
    {
        float clamped = MathHelper.clamp(t, 0F, 1F);
        float strength = MathHelper.clamp(amount, 0F, 1F);

        if (strength <= 0F || (!easeIn && !easeOut))
        {
            return clamped;
        }

        float shaped;

        if (easeIn && easeOut)
        {
            shaped = clamped * clamped * clamped * (clamped * (clamped * 6F - 15F) + 10F);
        }
        else if (easeIn)
        {
            shaped = clamped * clamped;
        }
        else
        {
            float inverse = 1F - clamped;

            shaped = 1F - inverse * inverse;
        }

        return MathHelper.lerp(strength, clamped, shaped);
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

    public record Frame(CrowdWalk from, CrowdWalk to, Vec3d p1, Vec3d p2, Vec3d m1, Vec3d m2,
                        Vec3d first, Vec3d centre, Vec3d forward, float raw, float progress,
                        float easeAmount, boolean easeIn, boolean easeOut, boolean moving,
                        float startTick, float targetTick)
    {
        /** The waypoint whose settings govern this stretch of the route. */
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
            return this.centre;
        }
    }
}
