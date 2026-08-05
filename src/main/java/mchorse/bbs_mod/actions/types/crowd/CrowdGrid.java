package mchorse.bbs_mod.actions.types.crowd;

import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A flat XZ bucket index over one crowd, rebuilt each applied tick.
 *
 * <p>Every member used to compare itself against every other member to work out which
 * neighbours to push away from, which costs the square of the crowd size per tick and is what
 * put a ceiling on how many mobs a shot could hold. Bucketing by cell means a member only
 * looks at the nine cells it can actually reach, so the cost grows with the crowd rather than
 * with its square and stays flat as the crowd spreads out.</p>
 */
public class CrowdGrid
{
    private final double cell;
    private final Map<Long, List<LivingEntity>> buckets = new HashMap<>();

    public CrowdGrid(List<LivingEntity> crowd, double cell)
    {
        this.cell = Math.max(1D, cell);

        for (LivingEntity entity : crowd)
        {
            if (entity.isAlive() && !entity.isRemoved())
            {
                this.buckets.computeIfAbsent(this.key(entity.getX(), entity.getZ()), (k) -> new ArrayList<>()).add(entity);
            }
        }
    }

    /** Members in the nine cells around this point - a superset of everything within one cell. */
    public List<LivingEntity> neighbours(double x, double z)
    {
        List<LivingEntity> found = new ArrayList<>();

        this.forEachNeighbour(x, z, found::add);

        return found;
    }

    /**
     * The same nine cells, handed over one at a time.
     *
     * <p>Steering runs this once per member per tick, so the list {@link #neighbours} builds is
     * pure garbage at crowd scale - a five-figure crowd throws away tens of thousands of lists a
     * tick. Callers that only walk the result should take it this way.</p>
     */
    public void forEachNeighbour(double x, double z, Consumer<LivingEntity> consumer)
    {
        int cx = (int) Math.floor(x / this.cell);
        int cz = (int) Math.floor(z / this.cell);

        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                List<LivingEntity> bucket = this.buckets.get(pack(cx + dx, cz + dz));

                if (bucket != null)
                {
                    for (int i = 0; i < bucket.size(); i++)
                    {
                        consumer.accept(bucket.get(i));
                    }
                }
            }
        }
    }

    private long key(double x, double z)
    {
        return pack((int) Math.floor(x / this.cell), (int) Math.floor(z / this.cell));
    }

    private static long pack(int x, int z)
    {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }
}
