package mchorse.bbs_mod.actions.types.crowd;

/**
 * A living entity whose body yaw the crowd behaviour clip has written this tick.
 *
 * <p>Mixed into {@code LivingEntity} so the flag lives on the entity and dies with it - a global
 * table of driven entities would have to be swept, and at crowd scale sweeping is the expensive
 * part. It is consumed rather than cleared: vanilla's turn takes the flag when it skips, so one
 * write buys exactly one skipped turn and a clip that stops driving hands the body straight back
 * without anyone having to say so.</p>
 */
public interface CrowdBodyYawOwner
{
    /** Claim the body yaw for the tick about to happen. */
    void bbs$driveBodyYaw();

    /** Whether a claim is outstanding, taking it if so. */
    boolean bbs$takeBodyYawDrive();
}
