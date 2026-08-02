package mchorse.bbs_mod.actions.types.crowd;

public enum CrowdBehaviorMode
{
    FOLLOW("Follow"),
    DISPERSE("Disperse"),
    CHEER("Cheer"),
    SAD_WALK("Sad walk"),
    TALK("Talk"),
    HOLD("Hold"),
    WANDER("Wander"),
    WANDER_LOOK("Wander + look");

    public final String title;

    CrowdBehaviorMode(String title)
    {
        this.title = title;
    }

    public static CrowdBehaviorMode get(int index)
    {
        CrowdBehaviorMode[] values = values();

        if (index < 0 || index >= values.length)
        {
            return FOLLOW;
        }

        return values[index];
    }
}
