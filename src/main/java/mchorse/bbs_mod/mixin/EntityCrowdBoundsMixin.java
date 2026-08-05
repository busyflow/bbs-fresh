package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps crowd members from walking out of the loaded world.
 *
 * <p>Moving an entity reads the blocks it is about to occupy, and reading a block in a chunk
 * that does not exist yet makes the server thread block until that chunk is generated. A wide
 * crowd puts hundreds of members on the rim of the loaded area, so those stalls chain into a
 * freeze that takes the world save down with it - the symptom is frozen mobs and a hung save
 * while the client still renders at full speed.</p>
 *
 * <p>Cancelling the horizontal part of such a move leaves the member standing at the boundary
 * instead. This sits on movement rather than on the crowd's own steering because vanilla AI
 * moves these mobs too, and it is the read that has to be prevented, whatever caused it.</p>
 */
@Mixin(Entity.class)
public class EntityCrowdBoundsMixin
{
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3d bbs$clampToLoadedChunks(Vec3d movement)
    {
        Entity self = (Entity) (Object) this;

        if (movement == null || (movement.x == 0D && movement.z == 0D))
        {
            return movement;
        }

        if (!(self.getWorld() instanceof ServerWorld world) || !self.getCommandTags().contains(CrowdUtils.INTERNAL_TAG))
        {
            return movement;
        }

        /* A step that isn't a number puts the member at a position that isn't one either, and
         * that is a wound the world carries: the member is written into the save, and every
         * later load of that chunk throws building a stack trace before discarding it. A crowd
         * worth of those turns opening the world into a several-minute grind. Refuse the step
         * instead - a member that stands still for a tick is not something a shot can show. */
        if (!isFinite(movement.x) || !isFinite(movement.y) || !isFinite(movement.z))
        {
            return Vec3d.ZERO;
        }

        /* Moving reads every block the entity's box touches after the step, not just the block
         * under its feet, and collision widens that box by another block on each side. Testing
         * only the destination column let a member standing near a chunk edge still read across
         * the border and block the server thread on chunk generation, so test the whole
         * footprint the move is about to touch. */
        Box box = self.getBoundingBox().offset(movement.x, 0D, movement.z).expand(1D, 0D, 1D);
        int minX = ((int) Math.floor(box.minX)) >> 4;
        int maxX = ((int) Math.floor(box.maxX)) >> 4;
        int minZ = ((int) Math.floor(box.minZ)) >> 4;
        int maxZ = ((int) Math.floor(box.maxZ)) >> 4;

        for (int cx = minX; cx <= maxX; cx++)
        {
            for (int cz = minZ; cz <= maxZ; cz++)
            {
                if (!world.getChunkManager().isChunkLoaded(cx, cz))
                {
                    return new Vec3d(0D, movement.y, 0D);
                }
            }
        }

        return movement;
    }

    /**
     * Keep crowd members out of the save file.
     *
     * <p>They are props for a shot, respawned by the clip whenever it plays, so writing them to
     * disk gains nothing and costs plenty: quitting mid-scene bakes a five-figure crowd into the
     * world, and every later load has to read all of it back before the world opens.</p>
     */
    @Inject(method = "shouldSave", at = @At("HEAD"), cancellable = true)
    private void bbs$doNotSaveCrowd(CallbackInfoReturnable<Boolean> info)
    {
        if (((Entity) (Object) this).getCommandTags().contains(CrowdUtils.INTERNAL_TAG))
        {
            info.setReturnValue(false);
        }
    }

    private static boolean isFinite(double value)
    {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
