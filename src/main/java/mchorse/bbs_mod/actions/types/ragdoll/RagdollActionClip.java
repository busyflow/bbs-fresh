package mchorse.bbs_mod.actions.types.ragdoll;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.cubic.physics.RagdollControl;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Drops a model's skeleton for the length of the clip: the limbs stop being animated, hang from
 * wherever the body still holds together, and fall against the world.
 *
 * <p>Everything here is settings for a simulation that runs on the client, because that is where
 * the model and its bones are. The clip carries no per-model setup - the rig is read off whatever
 * skeleton the shot uses - so dropping it on a track is the whole of it.</p>
 */
public class RagdollActionClip extends ActionClip
{
    /** Where the blow came from, as a compass bearing and an elevation, in degrees. */
    public final ValueFloat impactYaw = new ValueFloat("impact_yaw", 0F, -180F, 180F);
    public final ValueFloat impactPitch = new ValueFloat("impact_pitch", 0F, -90F, 90F);

    /** How hard, in blocks per tick handed to the limbs on the first frame. */
    public final ValueFloat impactStrength = new ValueFloat("impact_strength", 0.5F, 0F, 20F);

    public final ValueFloat gravity = new ValueFloat("gravity", 1F, 0F, 8F);

    /**
     * How much speed a limb keeps from one tick to the next. High damping is what separates a
     * body from a bouncy toy: an arm that hits the ground should knock about and stop, so most of
     * the speed the ground takes away must not come back.
     */
    public final ValueFloat damping = new ValueFloat("damping", 0.4F, 0F, 1F);

    /** How hard a limb pulls back towards its animated pose. Zero is fully limp. */
    public final ValueFloat stiffness = new ValueFloat("stiffness", 0.02F, 0F, 1F);

    /** How thick the limbs are for collision, in blocks. */
    public final ValueFloat radius = new ValueFloat("radius", 0.12F, 0.01F, 1F);

    public final ValueBoolean collisions = new ValueBoolean("collisions", true);

    /** Whether the body itself goes over, or only the limbs go slack while it stands. */
    public final ValueBoolean topple = new ValueBoolean("topple", true);

    /**
     * How far the body sinks as it goes over, in model units.
     *
     * <p>It turns about its root bone's pivot, and where that pivot sits is the model's business -
     * at the feet on one rig, at the waist on another. A rig pivoted at the waist swings its feet
     * through the floor unless the body also comes down as it falls, and only the person looking
     * at the model knows by how much.</p>
     */
    public final ValueFloat toppleDrop = new ValueFloat("topple_drop", 0F, -32F, 32F);

    public RagdollActionClip()
    {
        super();

        this.add(this.impactYaw);
        this.add(this.impactPitch);
        this.add(this.impactStrength);
        this.add(this.gravity);
        this.add(this.damping);
        this.add(this.stiffness);
        this.add(this.radius);
        this.add(this.collisions);
        this.add(this.topple);
        this.add(this.toppleDrop);
    }

    /** Fills in the settings the physics runtime reads, including the impulse this clip stands for. */
    public void fill(RagdollControl control)
    {
        double yaw = Math.toRadians(this.impactYaw.get());
        double pitch = Math.toRadians(this.impactPitch.get());
        double flat = Math.cos(pitch);

        control.x = (float) (-Math.sin(yaw) * flat);
        control.y = (float) Math.sin(pitch);
        control.z = (float) (Math.cos(yaw) * flat);
        control.strength = this.impactStrength.get();
        control.gravity = this.gravity.get();
        control.damping = this.damping.get();
        control.stiffness = this.stiffness.get();
        control.radius = this.radius.get();
        control.collisions = this.collisions.get();
        control.topple = this.topple.get();
        control.toppleDrop = this.toppleDrop.get();

        /* The blow is this clip starting, so the tick it starts on names it. Scrub back over the
         * start and it lands again; sit inside the clip and it does not land twice. */
        control.impulse = this.tick.get() + 1;
    }

    @Override
    protected Clip create()
    {
        return new RagdollActionClip();
    }
}
