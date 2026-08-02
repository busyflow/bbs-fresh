package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.film.replays.Replay;
import net.minecraft.util.math.MathHelper;

/**
 * Deterministic crowd jump timeline shared by live actors and the visual crowd tier.
 *
 * <p>Only the short history capable of contributing to the current jump arc is evaluated.
 * This makes seeking and scrubbing exact without storing one state object per logical member.</p>
 */
public final class CrowdJumpEvaluator
{
    public static final int DURATION = 12;
    public static final double HEIGHT = 0.9D;

    private CrowdJumpEvaluator()
    {}

    public static Frame frame(Replay replay, float filmTick)
    {
        return frame(replay, filmTick, 0D);
    }

    public static Frame frame(Replay replay, float filmTick, double ambientRate)
    {
        if (replay == null)
        {
            return null;
        }

        boolean keyframed = !replay.keyframes.crowdJump.isEmpty();
        double fallbackRate = MathHelper.clamp(ambientRate, 0D, 10D);

        if (!keyframed && fallbackRate <= 0D)
        {
            return null;
        }

        int currentTick = MathHelper.floor(filmTick);
        int firstTick = currentTick - DURATION;
        double[] chances = new double[DURATION + 1];
        boolean potential = false;

        for (int i = 0; i < chances.length; i++)
        {
            int sampleTick = firstTick + i;
            float localTick = localTick(replay, sampleTick);
            double rate = keyframed
                ? MathHelper.clamp(replay.keyframes.crowdJump.interpolate(localTick), 0D, 10D)
                : fallbackRate;
            double chance = chanceForRate(rate);

            chances[i] = chance;
            potential |= chance > 0D;
        }

        return new Frame(replay.getId().hashCode(), replay.looping.get(), firstTick,
            filmTick, chances, potential);
    }

    static double chanceForRate(double rate)
    {
        return rate <= 0D ? 0D : 1D - Math.exp(-Math.min(10D, rate) / 20D);
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

    public static final class Frame
    {
        private final int replayHash;
        private final int loop;
        private final int firstTick;
        private final float filmTick;
        private final double[] chances;
        private final boolean potential;

        private Frame(int replayHash, int loop, int firstTick, float filmTick,
            double[] chances, boolean potential)
        {
            this.replayHash = replayHash;
            this.loop = loop;
            this.firstTick = firstTick;
            this.filmTick = filmTick;
            this.chances = chances;
            this.potential = potential;
        }

        public boolean hasPotential()
        {
            return this.potential;
        }

        public double height(int memberIndex)
        {
            if (!this.potential)
            {
                return 0D;
            }

            int start = Integer.MIN_VALUE;

            for (int i = 0; i < this.chances.length; i++)
            {
                int tick = this.firstTick + i;

                if (start != Integer.MIN_VALUE && tick - start >= DURATION)
                {
                    start = Integer.MIN_VALUE;
                }

                if (start == Integer.MIN_VALUE && this.chances[i] > 0D
                    && randomFor(this.replayHash, memberIndex, this.randomTick(tick)) < this.chances[i])
                {
                    start = tick;
                }
            }

            if (start == Integer.MIN_VALUE)
            {
                return 0D;
            }

            double elapsed = this.filmTick - start;

            if (elapsed < 0D || elapsed >= DURATION)
            {
                return 0D;
            }

            double progress = elapsed / DURATION;
            double wave = Math.sin(Math.PI * progress);

            /* sinÃ‚Â² has zero vertical velocity at take-off and landing. It keeps
             * both the live tier and visual LOD tier on the exact same smooth,
             * deterministic arc without the parabola's abrupt endpoint snap. */
            return HEIGHT * wave * wave;
        }

        private int randomTick(int tick)
        {
            if (this.loop <= 0)
            {
                return tick;
            }

            int wrapped = tick % this.loop;

            return wrapped < 0 ? wrapped + this.loop : wrapped;
        }
    }

    private static double randomFor(int replayHash, int memberIndex, int tick)
    {
        long value = replayHash * 0x9E3779B97F4A7C15L;
        value ^= (long) memberIndex * 0xBF58476D1CE4E5B9L;
        value ^= (long) tick * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;

        return (value >>> 11) * 0x1.0p-53;
    }
}
