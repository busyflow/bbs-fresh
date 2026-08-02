package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Shared deterministic timeline evaluator for server playback and editor preview. */
public final class CrowdMotionEvaluator
{
    private static final double EPSILON = 1.0E-10D;

    private CrowdMotionEvaluator()
    {}

    public static Frame frame(Replay replay, float filmTick)
    {
        if (replay == null || replay.keyframes.crowdMotionPath.isEmpty())
        {
            return null;
        }

        float tick = localTick(replay, filmTick);
        KeyframeSegment<CrowdMotionPath> segment = replay.keyframes.crowdMotionPath.find(tick);

        if (segment == null || segment.a == null || segment.a.getValue() == null)
        {
            return null;
        }

        CrowdMotionPath a = segment.a.getValue();
        CrowdMotionPath b = segment.b == null || segment.b.getValue() == null ? a : segment.b.getValue();
        Vec3d start = a.position();
        Vec3d end = b.position();
        boolean samePosition = start.squaredDistanceTo(end) <= EPSILON;
        double linear = segment.isSame() ? 0D : MathHelper.clamp(segment.x, 0F, 1F);
        float startTick = segment.a.getTick();
        float targetTick = segment.b == null ? startTick : segment.b.getTick();
        float durationTicks = Math.max(0F, targetTick - startTick);
        float remainingTicks = Math.max(0F, targetTick - tick);

        /* Use the channel factory so AUTO/Hermite-style modes curve through neighboring
         * timeline points. Identical positions are pinned above and can never drift. */
        CrowdMotionPath interpolated = samePosition ? a : segment.createInterpolated();
        Vec3d center = interpolated == null ? start : interpolated.position();
        Vec3d direction = end.subtract(start);

        if (direction.lengthSquared() > EPSILON)
        {
            direction = direction.normalize();
        }
        else
        {
            direction = new Vec3d(0D, 0D, 1D);
        }

        float gateWeight = 0F;
        CrowdMotionPath gate = null;

        if (!samePosition && b.gate)
        {
            gate = b;
            gateWeight = smooth((float) linear);
        }
        else if (!samePosition && a.gate)
        {
            gate = a;
            gateWeight = 1F - smooth((float) linear);
        }

        CrowdForm crowd = replay.form.get() instanceof CrowdForm value ? value : null;
        Vec3d formation = crowd != null
            ? CrowdUtils.formationSize(CrowdFormation.get(crowd.formation.get()), crowd.count.get(), crowd.spacing.get(),
                crowd.volume.get().scale.x, crowd.volume.get().scale.y, crowd.volume.get().scale.z,
                crowd.useVolume.get(), crowd.hollow.get())
            : new Vec3d(1D, 0D, 1D);

        if (crowd != null && !crowd.useVolume.get()
            && CrowdFormation.get(crowd.formation.get()) == CrowdFormation.CIRCLE)
        {
            formation = new Vec3d(crowd.radius.get() * 2D, 0D, crowd.radius.get() * 2D);
        }
        Vec3d baseOffset = crowd != null && crowd.useVolume.get()
            ? new Vec3d(crowd.volume.get().translate.x, crowd.volume.get().translate.y, crowd.volume.get().translate.z)
            : Vec3d.ZERO;

        return new Frame(a, b, segment.a, segment.b, baseOffset, center, direction, (float) linear, gateWeight, gate,
            !samePosition && linear > 0D && linear < 1D,
            Math.max(0.1D, formation.x), Math.max(0.1D, formation.z), startTick, targetTick, durationTicks, remainingTicks);
    }

    public static MemberSample member(Frame frame, int index, int count)
    {
        return member(frame, index, count, null);
    }

    public static MemberSample member(Frame frame, int index, int count, Vec3d startLocal)
    {
        return sampleMember(frame, startLocal, frame.center, frame.gate, frame.gateWeight, frame.progress);
    }

    /**
     * Allocation-free equivalent of {@link #member(Frame, int, int, Vec3d)} for
     * the visual crowd tier, where this is evaluated thousands of times a frame.
     */
    public static void memberPosition(Frame frame, double startX, double startY, double startZ, double[] output)
    {
        if (frame == null || output == null || output.length < 3)
        {
            return;
        }

        double originalX = startX - frame.baseOffset.x;
        double originalY = startY - frame.baseOffset.y;
        double originalZ = startZ - frame.baseOffset.z;
        double offsetX = originalX;
        double offsetY = originalY;
        double offsetZ = originalZ;
        CrowdMotionPath gate = frame.gate;

        if (gate != null && frame.gateWeight > 0F)
        {
            double radians = Math.toRadians(gate.yaw);
            double rightX = Math.cos(radians);
            double rightZ = -Math.sin(radians);
            double forwardX = -rightZ;
            double forwardZ = rightX;
            double lateral = originalX * rightX + originalZ * rightZ;
            double depth = originalX * forwardX + originalZ * forwardZ;
            double normalizedSide = MathHelper.clamp(lateral / Math.max(0.05D, frame.formationWidth * 0.5D), -1D, 1D);
            double normalizedDepth = MathHelper.clamp(depth / Math.max(0.05D, frame.formationDepth * 0.5D), -1D, 1D);
            double spread = 0.15D + MathHelper.clamp(gate.scatter, 0F, 1F) * 0.85D;
            double gateSide = normalizedSide * Math.max(0D, gate.width * 0.5D - 0.3D) * spread;
            double gateDepth = normalizedDepth * Math.max(0D, gate.depth * 0.5D - 0.3D) * spread;
            double compressedX = rightX * gateSide + forwardX * gateDepth;
            double compressedZ = rightZ * gateSide + forwardZ * gateDepth;
            double strength = frame.gateWeight * gate.formationPreservation;

            offsetX = MathHelper.lerp(strength, originalX, compressedX);
            offsetY = originalY;
            offsetZ = MathHelper.lerp(strength, originalZ, compressedZ);
        }

        output[0] = frame.baseOffset.x + frame.center.x + offsetX;
        output[1] = frame.baseOffset.y + frame.center.y + offsetY;
        output[2] = frame.baseOffset.z + frame.center.z + offsetZ;
    }

    /** Exact final member position for the active timeline segment, used by runtime catch-up. */
    public static MemberSample targetMember(Frame frame, int index, int count, Vec3d startLocal)
    {
        CrowdMotionPath gate = frame.target.gate ? frame.target : null;

        return sampleMember(frame, startLocal, frame.target.position(), gate, gate == null ? 0F : 1F, 1D);
    }

    private static MemberSample sampleMember(Frame frame, Vec3d startLocal, Vec3d center,
        CrowdMotionPath gate, float gateWeight, double progress)
    {
        Vec3d original = startLocal == null ? Vec3d.ZERO : startLocal.subtract(frame.baseOffset);
        Vec3d offset = original;

        if (gate != null && gateWeight > 0F)
        {
            Vec3d right = gate.right();
            Vec3d forward = new Vec3d(-right.z, 0D, right.x);
            double lateral = original.dotProduct(right);
            double depth = original.dotProduct(forward);
            double vertical = original.y;
            double normalizedSide = MathHelper.clamp(lateral / Math.max(0.05D, frame.formationWidth * 0.5D), -1D, 1D);
            double normalizedDepth = MathHelper.clamp(depth / Math.max(0.05D, frame.formationDepth * 0.5D), -1D, 1D);
            double spread = 0.15D + MathHelper.clamp(gate.scatter, 0F, 1F) * 0.85D;
            double gateSide = normalizedSide * Math.max(0D, gate.width * 0.5D - 0.3D) * spread;
            double gateDepth = normalizedDepth * Math.max(0D, gate.depth * 0.5D - 0.3D) * spread;
            Vec3d compressed = right.multiply(gateSide).add(forward.multiply(gateDepth)).add(0D, vertical, 0D);
            float strength = gateWeight * gate.formationPreservation;

            offset = original.lerp(compressed, strength);
        }

        Vec3d position = frame.baseOffset.add(center).add(offset);

        return new MemberSample(position, frame.forward, progress);
    }

    public static Vec3d replayOrigin(Replay replay, float filmTick)
    {
        float tick = localTick(replay, filmTick);

        return new Vec3d(replay.keyframes.x.interpolate(tick), replay.keyframes.y.interpolate(tick), replay.keyframes.z.interpolate(tick));
    }

    private static float smooth(float value)
    {
        value = MathHelper.clamp(value, 0F, 1F);

        return value * value * (3F - 2F * value);
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

    public record Frame(CrowdMotionPath path, CrowdMotionPath target, Keyframe<CrowdMotionPath> keyA,
                        Keyframe<CrowdMotionPath> keyB, Vec3d baseOffset, Vec3d center, Vec3d forward, float progress,
                        float gateWeight, CrowdMotionPath gate, boolean moving,
                        double formationWidth, double formationDepth, float startTick, float targetTick,
                        float durationTicks, float remainingTicks)
    {}

    public record MemberSample(Vec3d position, Vec3d forward, double progress)
    {}
}
