package mchorse.bbs_mod.actions.types.crowd;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import mchorse.bbs_mod.actions.types.area.ValueAreaCells;
import net.minecraft.util.math.Vec3d;

/**
 * Spreads a crowd evenly over hand-painted ground.
 *
 * <p>Even means even by area, not by patch: every painted column receives the same share of the
 * crowd, so two patches of different sizes end up at the same density rather than with the same
 * head count, and a crowd of forty thousand over a plaza has no thin spots or piles. Each column's
 * share is then laid out on its own little grid inside the block, so the members inside a column
 * don't stack either.</p>
 *
 * <p>The columns are walked in Z-order (Morton) rather than row by row. The share per column is
 * {@code count / columns} rounded off, and which columns get the extra one is decided by that
 * ordering — row-major would hand the extras out in stripes, visible as ranks across the crowd,
 * while Z-order scatters them. It matters most when the crowd is smaller than the area, where the
 * ordering IS the pattern: every n-th column in row-major order is a lattice of lines, in Z-order
 * it's an even scatter.</p>
 */
public class CrowdPaintArea
{
    private final long[] keys;
    private final int[] surfaces;

    public CrowdPaintArea(Long2IntOpenHashMap cells)
    {
        this.keys = cells.keySet().toLongArray();

        LongArrays.quickSort(this.keys, (a, b) -> Long.compare(
            morton(ValueAreaCells.keyX(a), ValueAreaCells.keyZ(a)),
            morton(ValueAreaCells.keyX(b), ValueAreaCells.keyZ(b))
        ));

        this.surfaces = new int[this.keys.length];

        for (int i = 0; i < this.keys.length; i++)
        {
            this.surfaces[i] = cells.get(this.keys[i]);
        }
    }

    public int size()
    {
        return this.keys.length;
    }

    public boolean isEmpty()
    {
        return this.keys.length == 0;
    }

    /**
     * Where crowd member {@code index} of {@code count} stands, in world space.
     *
     * @param attempt retry number — the spawner walks these when a spot is blocked, so each one
     * shifts the member around inside its own column rather than to a different column, which
     * keeps the density even even where the ground is cluttered.
     */
    public Vec3d point(int index, int count, int attempt)
    {
        int columns = this.keys.length;

        if (columns == 0)
        {
            return null;
        }

        count = Math.max(1, count);
        index = Math.max(0, Math.min(index, count - 1));

        int column = (int) ((long) index * columns / count);

        if (column >= columns)
        {
            column = columns - 1;
        }

        /* The inverse of the mapping above: the run of members that landed in this column. */
        long first = ((long) column * count + columns - 1) / columns;
        long next = ((long) (column + 1) * count + columns - 1) / columns;
        int share = (int) Math.max(1L, next - first);
        int slot = (int) Math.max(0L, Math.min(index - first, share - 1));

        /* A square-ish grid inside the block, with the last (short) row spread across the full
         * width instead of bunched at one edge. */
        int side = (int) Math.ceil(Math.sqrt(share));
        int rows = (side <= 0) ? 1 : (share + side - 1) / side;
        int row = side <= 0 ? 0 : slot / side;
        int inRow = side <= 0 ? 0 : slot % side;
        int rowCount = Math.min(side, share - row * side);

        double px = (inRow + 0.5D) / Math.max(1, rowCount);
        double pz = (row + 0.5D) / Math.max(1, rows);

        if (attempt > 0)
        {
            /* Deterministic wobble, so a retry is a different spot in the same column and the
             * same member lands in the same place on every playback. */
            px += (hash(index * 31 + attempt, 0x9E3779B9) - 0.5D) * 0.8D;
            pz += (hash(index * 31 + attempt, 0x85EBCA6B) - 0.5D) * 0.8D;
            px = Math.max(0.05D, Math.min(0.95D, px));
            pz = Math.max(0.05D, Math.min(0.95D, pz));
        }

        long key = this.keys[column];

        return new Vec3d(
            ValueAreaCells.keyX(key) + px,
            this.surfaces[column] + 1,
            ValueAreaCells.keyZ(key) + pz
        );
    }

    /** Interleave the bits of a column's coordinates; biased so negatives order below positives. */
    private static long morton(int x, int z)
    {
        return (spread(x + 0x40000000) << 1) | spread(z + 0x40000000);
    }

    private static long spread(int value)
    {
        long v = value & 0x7FFFFFFFL;

        v = (v | (v << 16)) & 0x0000FFFF0000FFFFL;
        v = (v | (v << 8)) & 0x00FF00FF00FF00FFL;
        v = (v | (v << 4)) & 0x0F0F0F0F0F0F0F0FL;
        v = (v | (v << 2)) & 0x3333333333333333L;
        v = (v | (v << 1)) & 0x5555555555555555L;

        return v;
    }

    private static double hash(int a, int b)
    {
        int h = a * 0x27D4EB2D ^ b;

        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;

        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
