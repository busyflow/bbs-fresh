package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.pose.Transform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplayHandItemPoseTest
{
    @Test
    public void interpolatesAndKeepsHandsIndependent()
    {
        ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
        Transform rightStart = new Transform();
        Transform rightEnd = new Transform();
        Transform left = new Transform();

        rightEnd.translate.set(10F, 4F, -2F);
        rightEnd.scale.set(3F, 2F, 0.5F);
        rightEnd.rotate.set(0F, (float) Math.PI / 2F, 0F);
        left.translate.set(-7F, 1F, 9F);
        left.scale.set(0.5F, 1.5F, 2.5F);

        keyframes.rightHandPose.insert(0, rightStart);
        keyframes.rightHandPose.insert(10, rightEnd);
        keyframes.rightHandPose.getKeyframes().get(0).getInterpolation().setInterp(Interpolations.LINEAR);
        keyframes.leftHandPose.insert(0, left);

        /* The transform factory reuses an interpolation buffer, just like runtime. Copying
         * here mirrors IEntity#setEquipmentTransform and proves each hand remains isolated. */
        Transform rightResult = keyframes.rightHandPose.interpolate(5).copy();
        Transform leftResult = keyframes.leftHandPose.interpolate(5).copy();

        assertEquals(5F, rightResult.translate.x, 0.0001F);
        assertEquals(2F, rightResult.translate.y, 0.0001F);
        assertEquals(-1F, rightResult.translate.z, 0.0001F);
        assertEquals(2F, rightResult.scale.x, 0.0001F);
        assertEquals((float) Math.PI / 4F, rightResult.rotate.y, 0.0001F);
        assertEquals(-7F, leftResult.translate.x, 0.0001F);
        assertEquals(0.5F, leftResult.scale.x, 0.0001F);

        rightResult.translate.x = 100F;
        assertEquals(-7F, leftResult.translate.x, 0.0001F);
        assertEquals(10F, rightEnd.translate.x, 0.0001F);
    }
}
