package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * How high above the ground each crowd member is at a given tick.
 *
 * <p>A pure function of the tick and the member's index: nothing is remembered between ticks, so
 * scrubbing backwards lands on exactly the pose playing forwards would have, and a jump can never
 * accumulate. It used to add its height to wherever the member already was, which on a crowd with
 * no walk keyframes to reset the position meant every tick added another lift and the crowd
 * climbed away into the sky.</p>
 */
public final class CrowdJumpEvaluator
{
    public static final int DURATION = CrowdJump.DURATION;
    public static final double HEIGHT = CrowdJump.HEIGHT;

    /* Salts, so a member's choice, height and jump length are drawn from the same generator
     * without being the same number as each other. */
    private static final int CHOICE_SALT = Integer.MIN_VALUE + 1;
    private static final int HEIGHT_SALT = Integer.MIN_VALUE + 2;
    private static final int LENGTH_SALT = Integer.MIN_VALUE + 3;
    private static final int PHASE_SALT = Integer.MIN_VALUE + 4;

    /** At the slowest rate that still repeats, roughly this long standing between jumps. */
    private static final double MAX_GAP = DURATION * 12D;

    private CrowdJumpEvaluator()
    {}

    public static Frame frame(Replay replay, float filmTick)
    {
        if (replay == null || replay.keyframes.crowdJump.isEmpty())
        {
            return null;
        }

        float tick = localTick(replay, filmTick);
        CrowdJump jump = replay.keyframes.crowdJump.interpolate(tick);

        if (jump == null || jump.amount <= 0F)
        {
            return null;
        }

        return new Frame(replay.getId().hashCode(), tick, anchor(replay, tick),
            jump.amount, jump.rate, jump.random);
    }

    /**
     * The tick the keyframe in effect sits on, which is where a jump is counted from.
     *
     * <p>It matters at rate 0, where each member jumps exactly once: the jump belongs to the
     * keyframe that asked for it, so dropping a keyframe on the timeline is one jump there.</p>
     */
    private static float anchor(Replay replay, float tick)
    {
        List<Keyframe<CrowdJump>> list = replay.keyframes.crowdJump.getKeyframes();
        float anchor = list.get(0).getTick();

        for (Keyframe<CrowdJump> keyframe : list)
        {
            if (keyframe.getTick() > tick)
            {
                break;
            }

            anchor = keyframe.getTick();
        }

        return anchor;
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

    public record Frame(int replayHash, float tick, float anchor, float amount, float rate,
                        boolean random)
    {
        public boolean hasPotential()
        {
            return this.amount > 0F;
        }

        /** How high this member is right now. */
        public double height(int memberIndex)
        {
            return this.heightAt(memberIndex, this.tick);
        }

        /**
         * How high this member was a tick ago.
         *
         * <p>Wanted by the caller that can only nudge a member up or down rather than place it
         * outright, so it can undo exactly as much as it added.</p>
         */
        public double previousHeight(int memberIndex)
        {
            return this.heightAt(memberIndex, this.tick - 1F);
        }

        public double heightAt(int memberIndex, float at)
        {
            /* Who jumps is a fixed draw against the share wanted, not a roll per tick. The same
             * members jump throughout, and raising the share adds to them rather than choosing a
             * different crowd. */
            if (randomFor(this.replayHash, memberIndex, CHOICE_SALT) >= this.amount)
            {
                return 0D;
            }

            double scale = 1D;
            double duration = DURATION;

            if (this.random)
            {
                scale = 0.7D + randomFor(this.replayHash, memberIndex, HEIGHT_SALT) * 0.6D;
                duration = DURATION * (0.8D + randomFor(this.replayHash, memberIndex, LENGTH_SALT) * 0.55D);
            }

            double phase = randomFor(this.replayHash, memberIndex, PHASE_SALT) * duration;
            double since = at - this.anchor;
            double elapsed;

            if (this.rate <= 0F)
            {
                /* One jump, and the phase only keeps the crowd from leaving the ground in
                 * perfect unison. */
                elapsed = since - phase;
            }
            else
            {
                /* The gap is the standing about between jumps, so rate 1 leaves none of it and a
                 * member is back up the tick after it lands. */
                double gap = MAX_GAP * (1D - this.rate) / this.rate;
                double period = duration + Math.min(gap, MAX_GAP);

                elapsed = Math.floorMod((long) Math.floor(since + phase), (long) Math.max(1D, period));
            }

            if (elapsed < 0D || elapsed >= duration)
            {
                return 0D;
            }

            double progress = elapsed / duration;
            double wave = Math.sin(Math.PI * progress);

            /* sin squared has zero vertical velocity at take-off and landing, so a member settles
             * onto the ground rather than arriving at it still moving. */
            return HEIGHT * scale * wave * wave;
        }
    }

    private static double randomFor(int replayHash, int memberIndex, int salt)
    {
        long value = replayHash * 0x9E3779B97F4A7C15L;

        value ^= (long) memberIndex * 0xBF58476D1CE4E5B9L;
        value ^= (long) salt * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;

        return (value >>> 11) * 0x1.0p-53;
    }
}
