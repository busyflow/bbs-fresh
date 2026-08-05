package mchorse.bbs_mod.actions.types.area;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * A hand-painted patch of ground.
 *
 * <p>The clip does nothing when it plays — it is a shape, drawn in the editor with a brush over
 * the world's surface and stored as the columns it covered. Other clips read it by tag: a crowd
 * spawner pointed at this area can only place mobs on the columns painted here, which is a thing
 * a radius and a formation cannot express (a plaza with a river through it, a road, a courtyard).</p>
 */
public class AreaActionClip extends ActionClip
{
    /** The name other clips look this area up by. */
    public final ValueString areaTag = new ValueString("area_tag", "area");

    /** Brush radius in blocks — how wide a stroke paints. */
    public final ValueInt brushSize = new ValueInt("brush_size", 3, 1, 64);

    /** Whether the painted ground is shaded in, on top of its outline. */
    public final ValueBoolean showFill = new ValueBoolean("show_fill", true);

    public final ValueAreaCells cells = new ValueAreaCells("cells");

    public AreaActionClip()
    {
        this.add(this.areaTag);
        this.add(this.brushSize);
        this.add(this.showFill);
        this.add(this.cells);
    }

    @Override
    protected Clip create()
    {
        return new AreaActionClip();
    }

    public Long2IntOpenHashMap getCells()
    {
        return this.cells.get();
    }

    /** Paint one column, remembering the surface height the brush found there. */
    public void paint(int x, int y, int z)
    {
        this.getCells().put(ValueAreaCells.key(x, z), y);
    }

    public void erase(int x, int z)
    {
        this.getCells().remove(ValueAreaCells.key(x, z));
    }

    public void clear()
    {
        this.getCells().clear();
    }

    /** Whether a position stands on painted ground. An empty area covers everything, not nothing. */
    public boolean contains(double x, double z)
    {
        Long2IntOpenHashMap cells = this.getCells();

        return cells.isEmpty() || cells.containsKey(ValueAreaCells.key((int) Math.floor(x), (int) Math.floor(z)));
    }
}
