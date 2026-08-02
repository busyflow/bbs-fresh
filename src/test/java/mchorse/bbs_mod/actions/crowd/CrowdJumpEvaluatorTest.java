package mchorse.bbs_mod.actions.crowd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrowdJumpEvaluatorTest
{
    @Test
    public void rateScaleHasExplicitOffRareAndSpamEndpoints()
    {
        assertEquals(0D, CrowdJumpEvaluator.chanceForRate(0D));
        assertTrue(CrowdJumpEvaluator.chanceForRate(0.1D) < 0.006D);
        assertTrue(CrowdJumpEvaluator.chanceForRate(10D) > 0.39D);
        assertEquals(CrowdJumpEvaluator.chanceForRate(10D),
            CrowdJumpEvaluator.chanceForRate(100D));
    }
}
