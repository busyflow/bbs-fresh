package mchorse.bbs_mod.actions.types.crowd;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CrowdUtils
{
    public static final String INTERNAL_TAG = "bbs_crowd";
    public static final String RUN_TAG_PREFIX = "bbs_crowd_run_";
    public static final String INDEX_TAG_PREFIX = "bbs_crowd_index_";

    private static final AtomicInteger RUN_COUNTER = new AtomicInteger();
    private static final Map<Film, String> RUN_TAGS = new WeakHashMap<>();

    public static String crowdTag(String raw)
    {
        String tag = raw == null ? "" : raw.trim();

        return tag.isEmpty() ? "crowd_1" : tag;
    }

    public static String getRunTag(Film film)
    {
        if (film == null)
        {
            return RUN_TAG_PREFIX + "unknown";
        }

        synchronized (RUN_TAGS)
        {
            return RUN_TAGS.computeIfAbsent(film, (f) -> RUN_TAG_PREFIX + Integer.toHexString(RUN_COUNTER.incrementAndGet()));
        }
    }

    public static void tag(Entity entity, Film film, String crowdTag)
    {
        tag(entity, film, crowdTag, -1);
    }

    public static void tag(Entity entity, Film film, String crowdTag, int index)
    {
        entity.addCommandTag(INTERNAL_TAG);
        entity.addCommandTag(getRunTag(film));
        entity.addCommandTag(crowdTag(crowdTag));

        if (index >= 0)
        {
            entity.addCommandTag(INDEX_TAG_PREFIX + index);
        }
    }

    public static boolean hasTags(Entity entity, Film film, String crowdTag)
    {
        return entity.getCommandTags().contains(INTERNAL_TAG)
            && entity.getCommandTags().contains(getRunTag(film))
            && entity.getCommandTags().contains(crowdTag(crowdTag));
    }

    public static List<LivingEntity> getCrowd(ServerWorld world, Film film, String crowdTag, Vec3d center, double range)
    {
        double r = Math.max(8D, range);
        Box box = Box.of(center, r * 2D, Math.max(16D, r), r * 2D);

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box, (entity) -> hasTags(entity, film, crowdTag));

        entities.sort(Comparator
            .comparingInt(CrowdUtils::entityIndex)
            .thenComparingInt(Entity::getId));

        return entities;
    }

    public static void removeCrowd(ServerWorld world, Film film, String crowdTag)
    {
        String tag = crowdTag(crowdTag);
        String runTag = getRunTag(film);
        double radius = 30000000D;
        Box box = new Box(-radius, world.getBottomY(), -radius, radius, world.getTopY(), radius);

        for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, box, (entity) ->
            entity.getCommandTags().contains(INTERNAL_TAG)
                && entity.getCommandTags().contains(runTag)
                && entity.getCommandTags().contains(tag)))
        {
            mob.discard();
        }
    }

    public static void removeAllForFilm(ServerWorld world, Film film)
    {
        String runTag = getRunTag(film);
        double radius = 30000000D;
        Box box = new Box(-radius, world.getBottomY(), -radius, radius, world.getTopY(), radius);

        for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, box, (entity) ->
            entity.getCommandTags().contains(INTERNAL_TAG) && entity.getCommandTags().contains(runTag)))
        {
            mob.discard();
        }
    }

    public static Replay getReplay(Film film, int index)
    {
        if (film == null || index < 0 || index >= film.replays.getList().size())
        {
            return null;
        }

        return film.replays.getList().get(index);
    }

    public static Vec3d replayPosition(Replay replay, int tick)
    {
        int replayTick = replay.getTick(tick);

        return new Vec3d(
            replay.keyframes.x.interpolate(replayTick),
            replay.keyframes.y.interpolate(replayTick),
            replay.keyframes.z.interpolate(replayTick)
        );
    }

    public static Vec3d formationPoint(CrowdFormation formation, int index, int count, double spacing)
    {
        double side = Math.max(spacing, Math.ceil(Math.sqrt(Math.max(1, count))) * Math.max(0.1D, spacing));

        return formationPoint(formation, index, count, spacing, side, 2D, side);
    }

    public static Vec3d formationPoint(CrowdFormation formation, int index, int count, double spacing, double boxX, double boxY, double boxZ)
    {
        spacing = Math.max(0.1D, spacing);
        count = Math.max(1, count);
        boxX = Math.max(0.1D, boxX);
        boxY = Math.max(0D, boxY);
        boxZ = Math.max(0.1D, boxZ);

        if (formation == null)
        {
            formation = CrowdFormation.CIRCLE;
        }

        switch (formation)
        {
            case BOX:
            {
                double x = randomSigned(index, 0x45d9f3b) * boxX * 0.5D;
                double y = boxY <= 0D ? 0D : randomUnit(index, 0x119de1f3) * boxY;
                double z = randomSigned(index, 0x27d4eb2d) * boxZ * 0.5D;

                return new Vec3d(x, y, z);
            }
            case BOX_OUTLINE:
            {
                double perimeter = boxX * 2D + boxZ * 2D;
                double distance = count <= 1 ? 0D : (index / (double) count) * perimeter;
                double halfX = boxX * 0.5D;
                double halfZ = boxZ * 0.5D;
                double x;
                double z;

                if (distance < boxX)
                {
                    x = -halfX + distance;
                    z = -halfZ;
                }
                else if (distance < boxX + boxZ)
                {
                    x = halfX;
                    z = -halfZ + distance - boxX;
                }
                else if (distance < boxX * 2D + boxZ)
                {
                    x = halfX - (distance - boxX - boxZ);
                    z = halfZ;
                }
                else
                {
                    x = -halfX;
                    z = halfZ - (distance - boxX * 2D - boxZ);
                }

                return new Vec3d(x, 0D, z);
            }
            case LINE:
            {
                double half = (count - 1) / 2D;

                return new Vec3d((index - half) * spacing, 0D, 0D);
            }
            case GRID:
            case SQUARE:
            {
                int side = (int) Math.ceil(Math.sqrt(count));
                int row = index / side;
                int col = index % side;
                double half = (side - 1) / 2D;

                return new Vec3d((col - half) * spacing, 0D, (row - half) * spacing);
            }
            case SQUARE_OUTLINE:
            {
                double side = Math.max(spacing, count * spacing / 4D);
                double half = side / 2D;
                double t = count <= 1 ? 0D : (index / (double) count) * 4D;
                double x;
                double z;

                if (t < 1D)
                {
                    x = -half + side * t;
                    z = -half;
                }
                else if (t < 2D)
                {
                    x = half;
                    z = -half + side * (t - 1D);
                }
                else if (t < 3D)
                {
                    x = half - side * (t - 2D);
                    z = half;
                }
                else
                {
                    x = -half;
                    z = half - side * (t - 3D);
                }

                return new Vec3d(x, 0D, z);
            }
            case CIRCLE_OUTLINE:
            {
                double radius = count <= 1 ? 0D : count * spacing / (Math.PI * 2D);
                double angle = count <= 1 ? 0D : index / (double) count * Math.PI * 2D;

                return new Vec3d(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
            }
            case CIRCLE:
            default:
            {
                double radius = Math.sqrt(count / Math.PI) * spacing;
                double goldenAngle = Math.PI * (3D - Math.sqrt(5D));
                double t = (index + 0.5D) / count;
                double r = Math.sqrt(t) * radius;
                double angle = index * goldenAngle;

                return new Vec3d(Math.cos(angle) * r, 0D, Math.sin(angle) * r);
            }
        }
    }

    public static int entityIndex(Entity entity)
    {
        for (String tag : entity.getCommandTags())
        {
            if (tag.startsWith(INDEX_TAG_PREFIX))
            {
                try
                {
                    return Integer.parseInt(tag.substring(INDEX_TAG_PREFIX.length()));
                }
                catch (NumberFormatException e)
                {}
            }
        }

        return Math.floorMod(entity.getUuid().hashCode(), 1000000);
    }

    public static UUID deterministicUuid(Film film, String crowdTag, int seed, int index)
    {
        String filmId = film == null ? "unknown" : film.getId();
        String key = filmId + "|" + crowdTag(crowdTag) + "|" + seed + "|" + index;

        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public static int entitySeed(Entity entity, int seed, int salt)
    {
        return hash(seed ^ entityIndex(entity) * 0x9e3779b9 ^ salt);
    }

    public static double randomUnit(int seed, int step, int salt)
    {
        return (hash(seed ^ step * 0x632be5ab ^ salt) & 0x00ffffff) / (double) 0x01000000;
    }

    public static double randomSigned(int seed, int step, int salt)
    {
        return randomUnit(seed, step, salt) * 2D - 1D;
    }

    public static int hash(int value)
    {
        int hash = value;

        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;

        return hash;
    }

    public static Vec3d personalOffset(LivingEntity mob, double spacing)
    {
        if (spacing <= 0D)
        {
            return Vec3d.ZERO;
        }

        int hash = entitySeed(mob, 0, 0x51f15e);
        double angle = (hash & 0xffff) / 65535D * Math.PI * 2D;
        double ring = 0.4D + ((hash >>> 16) & 0x7) / 7D;
        double radius = spacing * ring;

        return new Vec3d(MathHelper.cos((float) angle) * radius, 0D, MathHelper.sin((float) angle) * radius);
    }

    private static double randomUnit(int index, int salt)
    {
        int hash = index;

        hash ^= salt;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;

        return (hash & 0x00ffffff) / (double) 0x01000000;
    }

    private static double randomSigned(int index, int salt)
    {
        return randomUnit(index, salt) * 2D - 1D;
    }

    public static boolean isServerWorld(World world)
    {
        return world instanceof ServerWorld;
    }
}
