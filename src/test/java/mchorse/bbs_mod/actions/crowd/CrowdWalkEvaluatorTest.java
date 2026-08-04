package mchorse.bbs_mod.actions.crowd;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrowdWalkEvaluatorTest
{
    private static CrowdWalkEvaluator.Frame frame(float progress, float stagger, float spread, float ease)
    {
        CrowdWalk from = new CrowdWalk();
        CrowdWalk to = new CrowdWalk();

        from.stagger = stagger;
        from.spread = spread;
        from.ease = ease;
        to.x = 100F;

        return new CrowdWalkEvaluator.Frame(from, to, from.position(), to.position(),
            new Vec3d(1D, 0D, 0D), progress, progress > 0F && progress < 1F, 0F, 20F);
    }

    @Test
    public void everyMemberArrivesExactlyWhenTheCrowdDoes()
    {
        CrowdWalkEvaluator.Frame frame = frame(1F, 1F, 0F, 0F);

        for (int index = 0; index < 500; index++)
        {
            assertEquals(1D, CrowdWalkEvaluator.memberProgress(frame, index), 1.0E-9D,
                "member " + index + " must land on the target waypoint");
        }
    }

    @Test
    public void noMemberLeavesBeforeTheCrowdStarts()
    {
        CrowdWalkEvaluator.Frame frame = frame(0F, 1F, 0F, 0F);

        for (int index = 0; index < 500; index++)
        {
            assertEquals(0D, CrowdWalkEvaluator.memberProgress(frame, index), 1.0E-9D);
        }
    }

    @Test
    public void staggerSpreadsDeparturesButStaysDeterministic()
    {
        CrowdWalkEvaluator.Frame frame = frame(0.5F, 0.8F, 0F, 0F);
        double first = CrowdWalkEvaluator.memberProgress(frame, 7);
        double lowest = 1D;
        double highest = 0D;

        for (int index = 0; index < 500; index++)
        {
            double progress = CrowdWalkEvaluator.memberProgress(frame, index);

            lowest = Math.min(lowest, progress);
            highest = Math.max(highest, progress);
        }

        assertEquals(first, CrowdWalkEvaluator.memberProgress(frame, 7), 0D, "same member, same departure");
        assertTrue(highest - lowest > 0.1D, "stagger must actually spread members out");
    }

    @Test
    public void zeroStaggerMovesTheCrowdAsOneBlock()
    {
        CrowdWalkEvaluator.Frame frame = frame(0.5F, 0F, 0F, 0F);

        assertEquals(CrowdWalkEvaluator.memberProgress(frame, 0),
            CrowdWalkEvaluator.memberProgress(frame, 431), 0D);
    }

    @Test
    public void formationIsExactAtBothEndsAndLoosensBetween()
    {
        double[] output = new double[3];

        CrowdWalkEvaluator.memberPosition(frame(0F, 0F, 1F, 0F), 0, 4D, 0D, 0D, output);
        assertEquals(4D, output[0], 1.0E-9D, "shape is exact on departure");

        CrowdWalkEvaluator.memberPosition(frame(1F, 0F, 1F, 0F), 0, 4D, 0D, 0D, output);
        assertEquals(104D, output[0], 1.0E-9D, "shape is exact on arrival");

        CrowdWalkEvaluator.memberPosition(frame(0.5F, 0F, 1F, 0F), 0, 4D, 0D, 0D, output);
        assertTrue(output[0] > 54D, "shape loosens halfway");
    }
}
