package mchorse.bbs_mod.actions.crowd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrowdLookEvaluatorTest
{
    @Test
    public void rotationFacesResolvedTarget()
    {
        CrowdLookEvaluator.Sample sample = new CrowdLookEvaluator.Sample(
            new net.minecraft.util.math.Vec3d(10D, 1.62D, 0D),
            new net.minecraft.util.math.Vec3d(10D, 1.62D, 0D),
            0D,
            CrowdLookTarget.parse("target")
        );
        float[] rotation = new float[2];

        assertTrue(CrowdLookEvaluator.rotation(0D, 1.62D, 0D, sample, rotation));
        assertEquals(-90F, rotation[0], 0.0001F);
        assertEquals(0F, rotation[1], 0.0001F);
    }

    @Test
    public void lookControlsRoundTripAndPlainIdsStayCompatible()
    {
        CrowdLookTarget legacy = CrowdLookTarget.parse("player/steve");

        assertEquals("player/steve", legacy.replayId());
        assertTrue(legacy.yaw());
        assertTrue(legacy.pitch());
        assertTrue(legacy.bodyYaw());
        assertTrue(legacy.headYaw());

        CrowdLookTarget custom = new CrowdLookTarget("player/alex", true, false, true, false);

        assertEquals(custom, CrowdLookTarget.parse(custom.encode()));
    }
}
