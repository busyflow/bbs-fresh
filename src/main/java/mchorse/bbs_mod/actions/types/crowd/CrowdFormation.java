package mchorse.bbs_mod.actions.types.crowd;

public enum CrowdFormation
{
    CIRCLE("Circle"),
    CIRCLE_OUTLINE("Circle outline"),
    LINE("Line"),
    GRID("Grid"),
    SQUARE("Square"),
    SQUARE_OUTLINE("Square outline"),
    BOX("Box"),
    BOX_OUTLINE("Box outline");

    public final String title;

    CrowdFormation(String title)
    {
        this.title = title;
    }

    public static CrowdFormation get(int index)
    {
        CrowdFormation[] values = values();

        if (index < 0 || index >= values.length)
        {
            return CIRCLE;
        }

        return values[index];
    }
}
