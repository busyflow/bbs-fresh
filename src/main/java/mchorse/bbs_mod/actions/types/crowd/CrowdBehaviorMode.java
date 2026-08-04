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
    WANDER_LOOK("Wander + look"),
    IDLE_CROWD("Idle crowd"),
    GATHER("Gather"),
    WATCH("Watch event"),
    FLEE("Flee"),
    MARKET("Marketplace"),
    MEETING("Meeting circle"),
    PANIC("Crowd panic"),
    GUARD_PATROL("Guard patrol"),
    WORKERS("Workers"),
    FIGHT("Crowd fight");

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
