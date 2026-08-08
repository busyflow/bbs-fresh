package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdJump;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;

/** How much of the crowd is in the air, and whether they vary. */
public class CrowdJumpKeyframeFactory implements IKeyframeFactory<CrowdJump>
{
    @Override
    public CrowdJump fromData(BaseType data)
    {
        return CrowdJump.fromData(data);
    }

    @Override
    public BaseType toData(CrowdJump value)
    {
        return (value == null ? new CrowdJump() : value).toData();
    }

    @Override
    public CrowdJump createEmpty()
    {
        return new CrowdJump();
    }

    @Override
    public CrowdJump copy(CrowdJump value)
    {
        return value == null ? new CrowdJump() : value.copy();
    }

    @Override
    public CrowdJump interpolate(CrowdJump preA, CrowdJump av, CrowdJump bv, CrowdJump postB,
        IInterp interpolation, float x)
    {
        if (av == null)
        {
            return new CrowdJump();
        }

        if (bv == null)
        {
            return av.copy();
        }

        /* Intensity is how much of the crowd is up, so it interpolates - that is what lets a
         * crowd be brought to a boil over a few seconds. Whether they vary is a choice about the
         * look and holds until the next keyframe says otherwise. */
        float intensity = interpolation.interpolate(av.intensity, bv.intensity, x);

        return new CrowdJump(intensity, av.random);
    }
}
