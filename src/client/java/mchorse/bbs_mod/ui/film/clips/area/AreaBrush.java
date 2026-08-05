package mchorse.bbs_mod.ui.film.clips.area;

import mchorse.bbs_mod.actions.types.crowd.CrowdSpawnActionClip;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * The free-hand brush that paints an {@link CrowdSpawnActionClip}'s ground.
 *
 * <p>One brush exists at a time and belongs to whichever area clip is open in the editor — arming
 * it takes over left-drag in the viewport, which is why it disarms itself the moment the clip
 * stops being edited. It paints columns, not blocks: a stroke finds the surface under the cursor
 * and stamps a disc of surface columns around it, so a hillside gets painted as ground rather
 * than as the one block the ray happened to land on.</p>
 */
public class AreaBrush
{
    /** How far above and below the brushed surface a neighbouring column may follow it. */
    private static final int SURFACE_SPAN = 8;

    private static CrowdSpawnActionClip clip;
    private static boolean erasing;
    private static boolean painting;
    private static boolean strokeErase;

    /** The column the cursor is over this frame, for the brush's own preview ring. */
    private static BlockPos hovered;
    private static int hoveredRadius;

    public static CrowdSpawnActionClip getClip()
    {
        return clip;
    }

    public static boolean isArmed()
    {
        return clip != null;
    }

    public static boolean isErasing()
    {
        return erasing;
    }

    public static boolean isPainting()
    {
        return painting;
    }

    public static BlockPos getHovered()
    {
        return hovered;
    }

    public static int getHoveredRadius()
    {
        return hoveredRadius;
    }

    public static void arm(CrowdSpawnActionClip target, boolean erase)
    {
        clip = target;
        erasing = erase;
        painting = false;
    }

    public static void disarm()
    {
        clip = null;
        painting = false;
        hovered = null;
    }

    /** Drop the brush when the clip it belongs to is no longer the one being edited. */
    public static void disarmUnless(CrowdSpawnActionClip target)
    {
        if (clip != null && clip != target)
        {
            disarm();
        }
    }

    public static void stopPainting()
    {
        painting = false;
    }

    /**
     * Start a stroke, if the brush is armed and the click is a paint click.
     *
     * @return whether the click was consumed — true keeps it away from form picking and the orbit
     * camera, which both also want left-drag in the viewport.
     */
    public static boolean click(UIContext context, Area area, Camera camera)
    {
        if (clip == null || context.mouseButton > 1)
        {
            return false;
        }

        /* Right-drag erases whatever the current mode is, so correcting a stroke doesn't mean
         * going back to the panel to flip a toggle and back. */
        held(context, area, camera, context.mouseButton == 1);

        return true;
    }

    /**
     * Paint from the raw button state, once per frame, for as long as a button is held.
     *
     * <p>The viewport's click event is not a reliable place to start a stroke — it is contested by
     * the orbit camera, the gizmos and form picking, and whichever of them the editor hands the
     * press to, the brush never hears about it. Polling the button instead means the brush works
     * the same whoever else wanted that click, and a stroke is naturally continuous: it is simply
     * "the button is down and the cursor is here", every frame.</p>
     */
    public static void held(UIContext context, Area area, Camera camera, boolean right)
    {
        if (clip == null)
        {
            return;
        }

        if (!painting)
        {
            /* First frame of the stroke: fix paint-or-erase now so it can't change halfway. */
            strokeErase = erasing || right;
            painting = true;
        }

        stamp(context, area, camera, strokeErase);
    }

    /** Track the column under the cursor so the viewport can show where a stroke would land. */
    public static void hover(UIContext context, Area area, Camera camera)
    {
        if (clip == null)
        {
            hovered = null;

            return;
        }

        hovered = trace(context, area, camera);
        hoveredRadius = clip.brushSize.get();
    }

    private static void stamp(UIContext context, Area area, Camera camera, boolean erase)
    {
        BlockPos hit = trace(context, area, camera);

        if (hit == null)
        {
            return;
        }

        World world = MinecraftClient.getInstance().world;
        int radius = clip.brushSize.get();
        int radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                if (dx * dx + dz * dz > radiusSquared)
                {
                    continue;
                }

                int x = hit.getX() + dx;
                int z = hit.getZ() + dz;

                if (erase)
                {
                    clip.erase(x, z);

                    continue;
                }

                int y = surfaceY(world, x, z, hit.getY());

                if (y != Integer.MIN_VALUE)
                {
                    clip.paint(x, y, z);
                }
            }
        }
    }

    /**
     * The surface of one column near the height the stroke started at.
     *
     * <p>Searched around the brushed height rather than from the sky down, so a stroke inside a
     * building paints its floor instead of its roof, and a stroke on a hillside follows the slope
     * only as far as the brush can reasonably reach.</p>
     */
    private static int surfaceY(World world, int x, int z, int around)
    {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int y = around + SURFACE_SPAN; y >= around - SURFACE_SPAN; y--)
        {
            pos.set(x, y, z);

            if (world.getBlockState(pos).isAir())
            {
                continue;
            }

            pos.set(x, y + 1, z);

            if (world.getBlockState(pos).isAir())
            {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    /** The block the mouse points at, through the editor's own camera rather than the player's. */
    private static BlockPos trace(UIContext context, Area area, Camera camera)
    {
        World world = MinecraftClient.getInstance().world;

        if (world == null || camera == null)
        {
            return null;
        }

        Vector3f rayOffset = new Vector3f();
        Vector3f rayDirection = CameraUtils.getMouseRay(camera.projection, camera.view, context.mouseX, context.mouseY, area.x, area.y, area.w, area.h, rayOffset);

        BlockHitResult result = RayTracing.rayTrace(
            world,
            RayTracing.fromVector3d(new Vector3d(camera.position).add(rayOffset.x, rayOffset.y, rayOffset.z)),
            RayTracing.fromVector3f(rayDirection),
            512F
        );

        return result.getType() == HitResult.Type.MISS ? null : result.getBlockPos();
    }
}
