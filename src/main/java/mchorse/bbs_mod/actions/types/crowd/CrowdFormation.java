package mchorse.bbs_mod.actions.types.crowd;

public enum CrowdFormation
{
    CIRCLE("Circle"),
    CIRCLE_OUTLINE("Circle outline"),
    LINE("Line"),
    GRID("Grid"),
    SQUARE("Square"),
    SQUARE_OUTLINE("Square outline"),
    BOX("AABB"),
    BOX_OUTLINE("AABB outline"),
    /** Legacy saved ordinal. The UI and runtime normalize this to CIRCLE. */
    HOLLOW_CIRCLE("Hollow Circle");

    private static final CrowdFormation[] SELECTABLE = {
        CIRCLE,
        CIRCLE_OUTLINE,
        LINE,
        GRID,
        SQUARE,
        SQUARE_OUTLINE,
        BOX,
        BOX_OUTLINE
    };

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

        CrowdFormation formation = values[index];

        return formation == HOLLOW_CIRCLE ? CIRCLE : formation;
    }

    public static CrowdFormation[] selectableValues()
    {
        return SELECTABLE.clone();
    }
}
