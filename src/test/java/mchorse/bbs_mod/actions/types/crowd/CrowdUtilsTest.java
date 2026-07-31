package mchorse.bbs_mod.actions.types.crowd;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrowdUtilsTest
{
    @Test
    public void filmOwnershipIsStableAcrossReloads()
    {
        String first = CrowdUtils.filmTag("films/example");
        String reloaded = CrowdUtils.filmTag("films/example");

        assertEquals(first, reloaded);
        assertTrue(first.startsWith(CrowdUtils.FILM_TAG_PREFIX));
        assertNotEquals(first, CrowdUtils.filmTag("films/other"));
    }

    @Test
    public void visualTierNeverDuplicatesLiveFormationSlots()
    {
        assertEquals(0, CrowdUtils.visualFormationIndices(1, 4096, 2000).length);

        int count = 3000;
        int liveCount = 2000;
        int[] visual = CrowdUtils.visualFormationIndices(count, 4096, liveCount);
        boolean[] live = new boolean[count];

        for (int i = 0; i < liveCount; i++)
        {
            live[CrowdSpawnActionClip.liveFormationIndex(i, liveCount, count)] = true;
        }

        assertEquals(1000, visual.length);

        for (int index : visual)
        {
            assertTrue(!live[index], "visual member duplicated live slot " + index);
        }

        assertEquals(2096, CrowdUtils.visualFormationIndices(1_000_000, 4096, 2000).length);
    }

    @Test
    public void tenThousandMemberPartitionIsCompleteAndUnique()
    {
        int count = 10_000;
        int liveCount = CrowdSpawnActionClip.MAX_LIVE_MEMBERS;
        int[] visual = CrowdUtils.visualFormationIndices(count, count, liveCount);
        boolean[] seen = new boolean[count];

        for (int i = 0; i < liveCount; i++)
        {
            int index = CrowdSpawnActionClip.liveFormationIndex(i, liveCount, count);

            assertTrue(!seen[index], "duplicate live slot " + index);
            seen[index] = true;
        }

        assertEquals(count - liveCount, visual.length);

        for (int index : visual)
        {
            assertTrue(!seen[index], "visual member duplicated slot " + index);
            seen[index] = true;
        }

        for (int i = 0; i < seen.length; i++)
        {
            assertTrue(seen[i], "logical crowd slot was omitted " + i);
        }
    }

    @Test
    public void circleHoleOpensCenterWithoutCompressingMembers()
    {
        int count = 400;
        double filledMin = Double.MAX_VALUE;
        double filledMax = 0D;
        double hollowMin = Double.MAX_VALUE;
        double hollowMax = 0D;

        for (int i = 0; i < count; i++)
        {
            var filled = CrowdUtils.formationPoint(CrowdFormation.CIRCLE, i, count, 1.5D, 1D, 1D, 1D, 0D);
            var hollow = CrowdUtils.formationPoint(CrowdFormation.CIRCLE, i, count, 1.5D, 1D, 1D, 1D, 0.75D);
            double filledRadius = Math.hypot(filled.x, filled.z);
            double hollowRadius = Math.hypot(hollow.x, hollow.z);

            filledMin = Math.min(filledMin, filledRadius);
            filledMax = Math.max(filledMax, filledRadius);
            hollowMin = Math.min(hollowMin, hollowRadius);
            hollowMax = Math.max(hollowMax, hollowRadius);
        }

        assertTrue(hollowMin > filledMin + 10D, "the center hole did not open");
        assertTrue(hollowMax > filledMax, "members were compressed into the original outer radius");
    }

    @Test
    public void circleHasDeterministicExactBoundaryAndRespectsHole()
    {
        int count = 400;
        double spacing = 1.5D;
        double hole = 0.55D;
        double outer = CrowdUtils.hollowOuterRadius(count, spacing, hole);
        double inner = CrowdUtils.hollowInnerRadius(count, spacing, hole);
        double maximum = 0D;

        for (int i = 0; i < count; i++)
        {
            var first = CrowdUtils.formationPoint(CrowdFormation.CIRCLE, i, count, spacing, 1D, 1D, 1D, hole);
            var second = CrowdUtils.formationPoint(CrowdFormation.CIRCLE, i, count, spacing, 1D, 1D, 1D, hole);
            double radius = Math.hypot(first.x, first.z);

            assertEquals(first, second);
            assertTrue(radius >= inner - 1.0E-9D, "member entered the center hole");
            assertTrue(radius <= outer + 1.0E-9D, "member escaped the circle");
            maximum = Math.max(maximum, radius);
        }

        assertEquals(outer, maximum, 1.0E-9D, "the outer silhouette is not an exact circle");
    }

    @Test
    public void oneMemberFilledCircleStartsAtItsCenter()
    {
        assertEquals(
            net.minecraft.util.math.Vec3d.ZERO,
            CrowdUtils.formationPoint(CrowdFormation.CIRCLE, 0, 1, 1.5D, 1D, 1D, 1D, 0D)
        );
    }

    @Test
    public void authoredRadiusDoesNotGrowWhenCountIncreases()
    {
        assertEquals(10D, maximumRadius(35, 10D, 2.5D, 0D), 1.0E-9D);
        assertEquals(10D, maximumRadius(350, 10D, 2.5D, 0D), 1.0E-9D);
    }

    @Test
    public void screenshotHoleProducesAnEvenSingleRing()
    {
        int count = 35;
        double previous = Double.NaN;
        double expectedGap = Math.PI * 2D / count;

        for (int i = 0; i < count; i++)
        {
            var point = CrowdUtils.circlePoint(i, count, 10D, 2.5D, 0.95D);
            double radius = Math.hypot(point.x, point.z);
            double angle = Math.atan2(point.z, point.x);

            assertEquals(10D, radius, 1.0E-9D);

            if (!Double.isNaN(previous))
            {
                double gap = angle - previous;

                if (gap < 0D)
                {
                    gap += Math.PI * 2D;
                }

                assertEquals(expectedGap, gap, 1.0E-9D);
            }

            previous = angle;
        }
    }

    @Test
    public void filledAndHollowCirclesKeepNearestNeighborSpacingEven()
    {
        assertTrue(nearestNeighborRatio(35, 10D, 0D) < 1.2D);
        assertTrue(nearestNeighborRatio(100, 10D, 0D) < 1.2D);
        assertTrue(nearestNeighborRatio(400, 10D, 0D) < 1.2D);
        assertTrue(nearestNeighborRatio(35, 10D, 0.55D) < 1.1D);
        assertTrue(nearestNeighborRatio(400, 10D, 0.55D) < 1.1D);
    }

    @Test
    public void lodSubsetsStayBalancedAcrossTheWholeCircle()
    {
        int count = 10_000;

        for (int stride : new int[] {1, 2, 4, 8})
        {
            int[] quadrants = new int[4];

            for (int i = 0; i < count; i += stride)
            {
                var point = CrowdUtils.circlePoint(i, count, 10D, 1.5D, 0D);
                int quadrant = (point.x < 0D ? 1 : 0) + (point.z < 0D ? 2 : 0);

                quadrants[quadrant]++;
            }

            int minimum = Math.min(Math.min(quadrants[0], quadrants[1]), Math.min(quadrants[2], quadrants[3]));
            int maximum = Math.max(Math.max(quadrants[0], quadrants[1]), Math.max(quadrants[2], quadrants[3]));

            assertTrue(maximum - minimum <= 12, "LOD stride " + stride + " distorted one side of the circle");
        }
    }

    @Test
    public void perBlockCircleKeepsTypicalMembersOnUniqueBlocks()
    {
        Set<String> blocks = new HashSet<>();

        for (int i = 0; i < 35; i++)
        {
            var point = CrowdUtils.circlePoint(i, 35, 10D, 2.5D, 0D);

            blocks.add((int) Math.floor(point.x) + ":" + (int) Math.floor(point.z));
        }

        assertEquals(35, blocks.size());
    }

    private static double maximumRadius(int count, double radius, double spacing, double hole)
    {
        double maximum = 0D;

        for (int i = 0; i < count; i++)
        {
            var point = CrowdUtils.circlePoint(i, count, radius, spacing, hole);

            maximum = Math.max(maximum, Math.hypot(point.x, point.z));
        }

        return maximum;
    }

    private static double nearestNeighborRatio(int count, double radius, double hole)
    {
        double minimum = Double.MAX_VALUE;
        double maximum = 0D;

        for (int i = 0; i < count; i++)
        {
            var point = CrowdUtils.circlePoint(i, count, radius, 1.5D, hole);
            double nearest = Double.MAX_VALUE;

            for (int j = 0; j < count; j++)
            {
                if (i == j)
                {
                    continue;
                }

                var other = CrowdUtils.circlePoint(j, count, radius, 1.5D, hole);

                nearest = Math.min(nearest, point.distanceTo(other));
            }

            minimum = Math.min(minimum, nearest);
            maximum = Math.max(maximum, nearest);
        }

        return maximum / minimum;
    }
}
