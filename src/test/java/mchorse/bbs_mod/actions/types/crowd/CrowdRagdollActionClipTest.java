package mchorse.bbs_mod.actions.types.crowd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrowdRagdollActionClipTest
{
    @Test
    public void rampsInHoldsAndRecovers()
    {
        assertEquals(0F, CrowdRagdollActionClip.progress(-1, 40, 5, 5));
        assertTrue(CrowdRagdollActionClip.progress(0, 40, 5, 5) > 0F);
        assertEquals(1F, CrowdRagdollActionClip.progress(10, 40, 5, 5));
        assertTrue(CrowdRagdollActionClip.progress(39, 40, 5, 5) < 1F);
        assertEquals(0F, CrowdRagdollActionClip.progress(40, 40, 5, 5));
    }
}
