package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdMotionPath;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** A timeline-authored crowd position with optional per-key gate metadata. */
public class CrowdMotionPathKeyframeFactory implements IKeyframeFactory<CrowdMotionPath>
{
    @Override
    public CrowdMotionPath fromData(BaseType data)
    {
        return CrowdMotionPath.fromData(data);
    }

    @Override
    public BaseType toData(CrowdMotionPath value)
    {
        return (value == null ? new CrowdMotionPath() : value).toData();
    }

    @Override
    public CrowdMotionPath createEmpty()
    {
        return new CrowdMotionPath();
    }

    @Override
    public CrowdMotionPath copy(CrowdMotionPath value)
    {
        return value == null ? new CrowdMotionPath() : value.copy();
    }

    @Override
    public CrowdMotionPath interpolate(Keyframe<CrowdMotionPath> preA, Keyframe<CrowdMotionPath> a,
        Keyframe<CrowdMotionPath> b, Keyframe<CrowdMotionPath> postB, IInterp interpolation, float x)
    {
        CrowdMotionPath av = a.getValue();
        CrowdMotionPath bv = b.getValue();

        if (av == null || bv == null || av.position().squaredDistanceTo(bv.position()) <= 1.0E-10D)
        {
            return av == null ? new CrowdMotionPath() : av.copy();
        }

        CrowdMotionPath value = av.copy();
        CrowdMotionPath pre = preA.getValue() == null ? av : preA.getValue();
        CrowdMotionPath post = postB.getValue() == null ? bv : postB.getValue();

        if (interpolation.has(Interpolations.BEZIER))
        {
            /* This custom track has no three-axis handle editor. Preserve Bezier timing without
             * inventing invalid coordinate handles; AUTO modes below provide spatial curves. */
            double t = interpolation.interpolate(0D, 1D, x);
            value.x = (float) (av.x + (bv.x - av.x) * t);
            value.y = (float) (av.y + (bv.y - av.y) * t);
            value.z = (float) (av.z + (bv.z - av.z) * t);
        }
        else if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);

            value.x = (float) AutoBezier.get(pre.x, av.x, bv.x, post.x, preA.getTick(), a.getTick(), b.getTick(), postB.getTick(), clamped, x);
            value.y = (float) AutoBezier.get(pre.y, av.y, bv.y, post.y, preA.getTick(), a.getTick(), b.getTick(), postB.getTick(), clamped, x);
            value.z = (float) AutoBezier.get(pre.z, av.z, bv.z, post.z, preA.getTick(), a.getTick(), b.getTick(), postB.getTick(), clamped, x);
        }
        else
        {
            value.x = (float) interpolation.interpolate(IInterp.context.set(pre.x, av.x, bv.x, post.x, x));
            value.y = (float) interpolation.interpolate(IInterp.context.set(pre.y, av.y, bv.y, post.y, x));
            value.z = (float) interpolation.interpolate(IInterp.context.set(pre.z, av.z, bv.z, post.z, x));
        }

        value.x = clampSegment(value.x, av.x, bv.x);
        value.y = clampSegment(value.y, av.y, bv.y);
        value.z = clampSegment(value.z, av.z, bv.z);

        return value;
    }

    @Override
    public CrowdMotionPath interpolate(CrowdMotionPath preA, CrowdMotionPath a, CrowdMotionPath b, CrowdMotionPath postB, IInterp interpolation, float x)
    {
        if (a == null) return b == null ? new CrowdMotionPath() : b.copy();
        if (b == null) return a.copy();

        CrowdMotionPath value = a.copy();

        if (a.position().squaredDistanceTo(b.position()) > 1.0E-10D)
        {
            double t = interpolation.interpolate(0D, 1D, x);

            value.x = (float) (a.x + (b.x - a.x) * t);
            value.y = (float) (a.y + (b.y - a.y) * t);
            value.z = (float) (a.z + (b.z - a.z) * t);
        }

        return value;
    }

    private static float clampSegment(float value, float a, float b)
    {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), value));
    }
}
