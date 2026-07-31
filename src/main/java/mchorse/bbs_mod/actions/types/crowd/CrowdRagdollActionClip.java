package mchorse.bbs_mod.actions.types.crowd;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

public class CrowdRagdollActionClip extends ActionClip
{
    public final ValueInt fallTicks = new ValueInt("fall_ticks", 6, 1, 40);
    public final ValueInt recoverTicks = new ValueInt("recover_ticks", 8, 0, 40);
    public final ValueFloat tilt = new ValueFloat("tilt", 90F, 0F, 120F);
    public final ValueBoolean randomDirection = new ValueBoolean("random_direction", true);
    public final ValueFloat direction = new ValueFloat("direction", 0F, -180F, 180F);

    public CrowdRagdollActionClip()
    {
        this.frequency.set(1);
        this.duration.set(40);
        this.title.set("Crowd Ragdoll");

        this.add(this.fallTicks);
        this.add(this.recoverTicks);
        this.add(this.tilt);
        this.add(this.randomDirection);
        this.add(this.direction);
    }

    public float progress(int tick)
    {
        return progress(tick - this.tick.get(), this.duration.get(), this.fallTicks.get(), this.recoverTicks.get());
    }

    public static float progress(int localTick, int duration, int fallTicks, int recoverTicks)
    {
        if (localTick < 0 || localTick >= duration)
        {
            return 0F;
        }

        float fall = Math.min(1F, (localTick + 1F) / Math.max(1, fallTicks));
        float recover = recoverTicks <= 0
            ? 1F
            : Math.min(1F, (duration - localTick) / (float) recoverTicks);

        return fall * recover;
    }

    @Override
    protected Clip create()
    {
        return new CrowdRagdollActionClip();
    }
}
