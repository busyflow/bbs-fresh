package mchorse.bbs_mod.actions.types.crowd;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.VideoRecorder;
import net.minecraft.entity.Entity;

/**
 * Which of a crowd is worth drawing while the shot is being built.
 *
 * <p>A five-figure crowd is drawn in full every frame, near or far, on screen or behind the
 * camera, and that - not the simulation, which was made to hold twenty ticks a second - is what
 * takes the editor down to single-figure frame rates. Almost all of that work lands on members
 * covering a pixel or two.</p>
 *
 * <p>So beyond a radius the crowd thins with distance: everyone close by is drawn, and further
 * out a falling share of them is, never below a floor that keeps a distant crowd a crowd rather
 * than an empty field. The shape and mass of it survive, which is what the editor is for. An
 * export draws every one of them.</p>
 */
public final class CrowdPreview
{
    private CrowdPreview()
    {
    }

    /**
     * Whether this entity is a crowd member the preview is leaving out this frame.
     *
     * <p>Cheap first: the great majority of entities are not crowd members at all, and the ones
     * that are get an answer from their id and their distance, with nothing kept between frames.
     * The id is what makes the answer stable - a member thinned out this frame is thinned out
     * the next, so the crowd stands still instead of flickering.</p>
     */
    public static boolean isThinnedOut(Entity entity, double x, double y, double z)
    {
        if (!BBSSettings.crowdPreviewThinning.get() || !CrowdClientMembers.isMember(entity.getId()))
        {
            return false;
        }

        VideoRecorder recorder = BBSModClient.getVideoRecorder();

        if (recorder != null && recorder.isRecording())
        {
            return false;
        }

        /* The coordinates an entity is drawn at are already relative to the camera. */
        double radius = BBSSettings.crowdPreviewRadius.get();
        double distanceSq = x * x + y * y + z * z;
        double radiusSq = radius * radius;

        if (distanceSq <= radiusSq)
        {
            return false;
        }

        /* Squared falloff, so the share kept drops the way apparent size does: at twice the
         * radius a quarter of them are drawn, at four times a sixteenth. Density on screen stays
         * roughly even as the crowd recedes, rather than the near edge staying solid and the far
         * edge emptying out all at once. */
        float keep = (float) (radiusSq / distanceSq);
        float floor = BBSSettings.crowdPreviewFloor.get();

        return hash(entity.getId()) >= Math.max(keep, floor);
    }

    /** A settled value in [0, 1) from an entity id - the same one every frame, for a given id. */
    private static float hash(int id)
    {
        int h = id * 0x9E3779B9;

        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;

        return (h >>> 8) / (float) (1 << 24);
    }
}
