package mchorse.bbs_mod.cubic.physics;

/**
 * A model's ragdoll, while one is running.
 *
 * <p>Set on the form each frame by the film's playback and read by the physics runtime, the same
 * way wind and the per-chain controls are handed over. It is a runtime override rather than a
 * saved setting because a ragdoll is an event: the model's own physics config describes cloth and
 * hair that are always on, while this replaces the whole skeleton for as long as the clip runs and
 * leaves nothing behind when it ends.</p>
 */
public class RagdollControl
{
    /** Direction of the blow, normalised, in world space. */
    public float x;
    public float y = 1F;
    public float z;

    /** How hard, in blocks per tick given to the struck limb. */
    public float strength = 1F;

    public float gravity = 1F;
    public float damping = 0.4F;
    public float stiffness = 0.02F;
    public float radius = 0.12F;
    public float flail = 0.75F;
    public boolean collisions = true;

    /** Whether the body itself goes over, or only the limbs go slack. */
    public boolean topple = true;

    /**
     * Identifies the blow. The runtime pushes the limbs once per value it has not seen, so the
     * hit lands on the first frame of the clip and the rest of it is the body falling - not a
     * shove re-applied every frame, which is a jet rather than an impact.
     */
    public int impulse;

    /** Replay-local tick used to spot loops and scrubs independently of entity age. */
    public int playbackTick = Integer.MIN_VALUE;

    /** True only until the runtime consumes a newly entered clip. */
    public boolean fresh = true;

    public void copy(RagdollControl other)
    {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.strength = other.strength;
        this.gravity = other.gravity;
        this.damping = other.damping;
        this.stiffness = other.stiffness;
        this.radius = other.radius;
        this.flail = other.flail;
        this.collisions = other.collisions;
        this.topple = other.topple;
        this.impulse = other.impulse;
        this.playbackTick = other.playbackTick;
        this.fresh = other.fresh;
    }
}
