package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * One point on the crowd motion track. Timing and connectivity belong to the
 * ordinary {@code KeyframeChannel}; a value never contains private checkpoints.
 */
public class CrowdMotionPath
{
    public float x;
    public float y;
    public float z;

    public boolean gate;
    public float width = 4F;
    public float height = 2.5F;
    public float depth = 2F;
    public float yaw;

    public boolean terrainFollow = true;
    public boolean faceTravel = true;
    public boolean run = true;
    public boolean showPathLine = true;
    public boolean showPoint = true;
    public float scatter = 0.85F;
    public float formationPreservation = 1F;

    public CrowdMotionPath copy()
    {
        CrowdMotionPath point = new CrowdMotionPath();

        point.x = this.x;
        point.y = this.y;
        point.z = this.z;
        point.gate = this.gate;
        point.width = this.width;
        point.height = this.height;
        point.depth = this.depth;
        point.yaw = this.yaw;
        point.terrainFollow = this.terrainFollow;
        point.faceTravel = this.faceTravel;
        point.run = this.run;
        point.showPathLine = this.showPathLine;
        point.showPoint = this.showPoint;
        point.scatter = this.scatter;
        point.formationPreservation = this.formationPreservation;

        return point;
    }

    public Vec3d position()
    {
        return new Vec3d(this.x, this.y, this.z);
    }

    public Vec3d right()
    {
        double radians = Math.toRadians(this.yaw);

        return new Vec3d(Math.cos(radians), 0D, -Math.sin(radians));
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putInt("version", 2);
        data.putFloat("x", this.x);
        data.putFloat("y", this.y);
        data.putFloat("z", this.z);
        data.putBool("gate", this.gate);
        data.putFloat("width", this.width);
        data.putFloat("height", this.height);
        data.putFloat("depth", this.depth);
        data.putFloat("yaw", this.yaw);
        data.putBool("terrain", this.terrainFollow);
        data.putBool("face", this.faceTravel);
        data.putBool("run", this.run);
        data.putBool("show_path_line", this.showPathLine);
        data.putBool("show_point", this.showPoint);
        data.putFloat("scatter", this.scatter);
        data.putFloat("formation_preservation", this.formationPreservation);

        return data;
    }

    public static CrowdMotionPath fromData(BaseType data)
    {
        CrowdMotionPath point = new CrowdMotionPath();

        if (data == null || !data.isMap())
        {
            return point;
        }

        MapType map = data.asMap();

        /* One-way compatibility with the old single-key/private-checkpoint format.
         * The first old point is the only safe conversion: it preserves the crowd's
         * authored starting location without unexpectedly launching it down a preset. */
        if (!map.has("x") && map.has("points") && map.get("points").isList())
        {
            ListType points = map.getList("points");

            if (points.size() > 0 && points.get(0) != null && points.get(0).isMap())
            {
                MapType old = points.get(0).asMap();

                point.x = finite(old.getFloat("x"), 0F, -1_000_000F, 1_000_000F);
                point.y = finite(old.getFloat("y"), 0F, -1_000_000F, 1_000_000F);
                point.z = finite(old.getFloat("z"), 0F, -1_000_000F, 1_000_000F);
            }
        }
        else
        {
            point.x = finite(map.getFloat("x"), 0F, -1_000_000F, 1_000_000F);
            point.y = finite(map.getFloat("y"), 0F, -1_000_000F, 1_000_000F);
            point.z = finite(map.getFloat("z"), 0F, -1_000_000F, 1_000_000F);
        }

        point.gate = map.getBool("gate", false);
        point.width = finite(map.getFloat("width", 4F), 4F, 0.25F, 256F);
        point.height = finite(map.getFloat("height", 2.5F), 2.5F, 0.25F, 256F);
        point.depth = finite(map.getFloat("depth", 2F), 2F, 0.25F, 256F);
        point.yaw = finite(map.getFloat("yaw"), 0F, -180F, 180F);
        point.terrainFollow = map.getBool("terrain", true);
        point.faceTravel = map.getBool("face", true);
        point.run = map.getBool("run", true);
        point.showPathLine = map.getBool("show_path_line", true);
        point.showPoint = map.getBool("show_point", map.getBool("show_checkpoints", true));
        point.scatter = finite(map.getFloat("scatter", 0.85F), 0.85F, 0F, 1F);
        point.formationPreservation = finite(map.getFloat("formation_preservation", 1F), 1F, 0F, 1F);

        return point;
    }

    private static float finite(float value, float fallback, float min, float max)
    {
        return Float.isFinite(value) ? MathHelper.clamp(value, min, max) : fallback;
    }
}
