package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.util.math.MathHelper;

/**
 * How much of a crowd is jumping, and whether they look alike doing it.
 *
 * <p>{@link #intensity} is the share of the crowd off the ground at any moment: 0 is nobody, 1 is
 * everybody, and the values between are the fraction in the air. It used to be a rate in jumps per
 * member per second, which is a number nobody could picture - the useful question when animating
 * is how much of the crowd is jumping, not how often one of them does.</p>
 */
public class CrowdJump
{
    /** Ticks one jump takes at the standard size. */
    public static final int DURATION = 12;

    /** How far back the evaluator has to look, given {@link #random} can stretch a jump. */
    public static final int MAX_DURATION = 18;

    public static final double HEIGHT = 0.9D;

    public float intensity;

    /**
     * Vary each member's jump height and how long it takes.
     *
     * <p>Off, everyone jumps the same height at the same speed. They start at their own times
     * already, but identical arcs read as a mechanism rather than a crowd, which is the thing
     * this is for.</p>
     */
    public boolean random = true;

    public CrowdJump()
    {}

    public CrowdJump(float intensity, boolean random)
    {
        this.intensity = intensity;
        this.random = random;
    }

    public CrowdJump copy()
    {
        return new CrowdJump(this.intensity, this.random);
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putFloat("intensity", this.intensity);
        data.putBool("random", this.random);

        return data;
    }

    public static CrowdJump fromData(BaseType data)
    {
        CrowdJump value = new CrowdJump();

        if (data == null)
        {
            return value;
        }

        /* Films written before this was a compound stored a bare rate in jumps per member per
         * second. Read it back as the share of the crowd that rate actually kept in the air, so
         * an old film plays at the density it was authored at rather than at whatever the number
         * happens to mean now. */
        if (data.isNumeric())
        {
            value.intensity = (float) dutyForLegacyRate(data.asNumeric().doubleValue());
            value.random = false;

            return value;
        }

        if (!data.isMap())
        {
            return value;
        }

        MapType map = data.asMap();

        value.intensity = MathHelper.clamp(map.getFloat("intensity"), 0F, 1F);
        value.random = !map.has("random") || map.getBool("random");

        return value;
    }

    /** The old rate's chance per tick, turned into the share of time a member spent airborne. */
    private static double dutyForLegacyRate(double rate)
    {
        double chance = rate <= 0D ? 0D : 1D - Math.exp(-Math.min(10D, rate) / 20D);

        return chance <= 0D ? 0D : chance * DURATION / (chance * DURATION + 1D);
    }

    /**
     * The per-tick chance a member who is standing starts a jump.
     *
     * <p>Derived from the share wanted rather than picked by feel: a member waits {@code 1/chance}
     * ticks on average and then spends {@link #DURATION} in the air, so the share airborne is
     * {@code DURATION / (DURATION + 1/chance)}. Solved for chance, that is what is below - which
     * is what makes 0.5 mean half the crowd instead of some number of jumps.</p>
     */
    public double chance()
    {
        double duty = MathHelper.clamp(this.intensity, 0F, 1F);

        if (duty <= 0D)
        {
            return 0D;
        }

        if (duty >= 1D)
        {
            return 1D;
        }

        return Math.min(1D, duty / ((1D - duty) * DURATION));
    }
}
