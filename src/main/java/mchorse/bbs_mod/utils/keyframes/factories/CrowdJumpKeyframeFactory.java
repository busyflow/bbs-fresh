package mchorse.bbs_mod.utils.keyframes.factories;

/** Jump frequency in average jumps per member per second. */
public class CrowdJumpKeyframeFactory extends DoubleKeyframeFactory
{
    @Override
    public Double createEmpty()
    {
        return 1D;
    }
}
