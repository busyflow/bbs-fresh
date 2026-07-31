package mchorse.bbs_mod.actions.crowd;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CrowdMotionEvaluatorTest
{
    @Test
    public void allocationFreeVisualSampleMatchesRuntimeSampleThroughGate()
    {
        CrowdMotionPath gate = new CrowdMotionPath();

        gate.gate = true;
        gate.yaw = 37F;
        gate.width = 6.5F;
        gate.depth = 3.25F;
        gate.scatter = 0.72F;
        gate.formationPreservation = 0.83F;

        CrowdMotionEvaluator.Frame frame = new CrowdMotionEvaluator.Frame(
            gate, gate, null, null,
            new Vec3d(2D, 1D, -3D),
            new Vec3d(12D, 0.5D, 7D),
            new Vec3d(0.6D, 0D, 0.8D),
            0.4F, 0.65F, gate, true,
            20D, 14D, 0F, 40F, 40F, 24F
        );
        Vec3d start = new Vec3d(8.25D, 2.5D, -6.75D);
        Vec3d expected = CrowdMotionEvaluator.member(frame, 0, 1, start).position();
        double[] actual = new double[3];

        CrowdMotionEvaluator.memberPosition(frame, start.x, start.y, start.z, actual);

        assertEquals(expected.x, actual[0], 1.0E-9D);
        assertEquals(expected.y, actual[1], 1.0E-9D);
        assertEquals(expected.z, actual[2], 1.0E-9D);
    }
}
