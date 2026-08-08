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
    public static final int DURATION = CrowdJump.DURATION;
    public static final double HEIGHT = CrowdJump.HEIGHT;

    /* Salts, so a member's height and jump length are drawn from the same generator as its
     * timing without being the same number as any tick's roll. */
    private static final int HEIGHT_SALT = Integer.MIN_VALUE + 1;
    private static final int LENGTH_SALT = Integer.MIN_VALUE + 2;

    private CrowdJumpEvaluator()
    {}

    public static Frame frame(Replay replay, float filmTick)
    {
        if (replay == null || replay.keyframes.crowdJump.isEmpty())
        {
            return null;
        }

        int currentTick = MathHelper.floor(filmTick);
        int firstTick = currentTick - CrowdJump.MAX_DURATION;
        double[] chances = new double[CrowdJump.MAX_DURATION + 1];
        boolean potential = false;
        boolean random = false;

        for (int i = 0; i < chances.length; i++)
        {
            float localTick = localTick(replay, firstTick + i);
            CrowdJump jump = replay.keyframes.crowdJump.interpolate(localTick);

            if (jump == null)
            {
                continue;
            }

            double chance = jump.chance();

            chances[i] = chance;
            potential |= chance > 0D;
            /* Whether the crowd is varied is read from the tick being drawn, not from the whole
             * window - the older entries are only there to find jumps already under way. */
            random = jump.random;
        }

        return new Frame(replay.getId().hashCode(), replay.looping.get(), firstTick,
            filmTick, chances, potential, random);
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
        private final boolean random;

        private Frame(int replayHash, int loop, int firstTick, float filmTick,
            double[] chances, boolean potential, boolean random)
        {
            this.replayHash = replayHash;
            this.loop = loop;
            this.firstTick = firstTick;
            this.filmTick = filmTick;
            this.chances = chances;
            this.potential = potential;
            this.random = random;
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

            double scale = 1D;
            int duration = DURATION;

            if (this.random)
            {
                scale = 0.7D + randomFor(this.replayHash, memberIndex, HEIGHT_SALT) * 0.6D;
                duration = (int) Math.round(DURATION
                    * (0.8D + randomFor(this.replayHash, memberIndex, LENGTH_SALT) * 0.55D));
            }

            int start = Integer.MIN_VALUE;

            for (int i = 0; i < this.chances.length; i++)
            {
                int tick = this.firstTick + i;

                /* Landed, so this member is free to leave the ground again. */
                if (start != Integer.MIN_VALUE && tick - start >= duration)
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

            if (elapsed < 0D || elapsed >= duration)
            {
                return 0D;
            }

            double progress = elapsed / duration;
            double wave = Math.sin(Math.PI * progress);

            /* sin squared has zero vertical velocity at take-off and landing. It keeps both the
             * live tier and the visual LOD tier on the exact same smooth, deterministic arc
             * without the parabola's abrupt endpoint snap. */
            return HEIGHT * scale * wave * wave;
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
