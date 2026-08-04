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

import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class CrowdUtils
{
    public static final String INTERNAL_TAG = "bbs_crowd";
    public static final String RUN_TAG_PREFIX = "bbs_crowd_run_";
    public static final String FILM_TAG_PREFIX = "bbs_crowd_film_";

    public static String crowdTag(String raw)
    {
        String tag = raw == null ? "" : raw.trim();

        return tag.isEmpty() ? "crowd_1" : tag;
    }

    public static String getRunTag(Film film)
    {
        return filmTag(film == null ? "" : film.getId());
    }

    static String filmTag(String filmId)
    {
        String stableId = filmId == null ? "" : filmId;
        UUID uuid = UUID.nameUUIDFromBytes(("bbs-crowd:" + stableId).getBytes(StandardCharsets.UTF_8));

        return FILM_TAG_PREFIX + uuid.toString().replace("-", "");
    }

    public static void tag(Entity entity, Film film, String crowdTag)
    {
        entity.addCommandTag(INTERNAL_TAG);
        entity.addCommandTag(getRunTag(film));
        entity.addCommandTag(crowdTag(crowdTag));
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
        Box box = new Box(center.x - r, center.y - Math.max(8D, r / 2D), center.z - r,
            center.x + r, center.y + Math.max(8D, r / 2D), center.z + r);
        List<LivingEntity> result = new ArrayList<>();

        for (Entity entity : world.iterateEntities())
        {
            if (entity instanceof LivingEntity living && box.contains(living.getPos()) && hasTags(living, film, crowdTag))
            {
                result.add(living);
            }
        }

        return result;
    }

    public static void removeCrowd(ServerWorld world, Film film, String crowdTag)
    {
        String tag = crowdTag(crowdTag);
        String runTag = getRunTag(film);
        double radius = 30000000D;
        /* Discarding changes Minecraft's linked entity map. Always iterate a snapshot:
         * large crowds otherwise corrupt its live iterator and crash the server tick. */
        for (Entity entity : entitySnapshot(world))
        {
            if (entity instanceof LivingEntity mob && mob.getCommandTags().contains(INTERNAL_TAG)
                && mob.getCommandTags().contains(runTag) && mob.getCommandTags().contains(tag))
            {
                mob.discard();
            }
        }
    }

    public static void removeAllForFilm(ServerWorld world, Film film)
    {
        String runTag = getRunTag(film);
        double radius = 30000000D;
        /* See removeCrowd: cleanup must never mutate the collection being iterated. */
        for (Entity entity : entitySnapshot(world))
        {
            if (entity instanceof LivingEntity mob && mob.getCommandTags().contains(INTERNAL_TAG) && mob.getCommandTags().contains(runTag))
            {
                mob.discard();
            }
        }
    }

    /**
     * v0.22 and earlier used an ephemeral Java-object run tag. After a live film reload there
     * was no way for the replacement manager to identify those actors. Remove only matching
     * pre-stable-owner members; current crowds always carry a FILM_TAG_PREFIX tag.
     */
    public static void removeLegacyCrowd(ServerWorld world, String crowdTag)
    {
        String tag = crowdTag(crowdTag);

        for (Entity entity : entitySnapshot(world))
        {
            if (!(entity instanceof LivingEntity mob)
                || !mob.getCommandTags().contains(INTERNAL_TAG)
                || !mob.getCommandTags().contains(tag))
            {
                continue;
            }

            boolean hasStableOwner = mob.getCommandTags().stream()
                .anyMatch((value) -> value.startsWith(FILM_TAG_PREFIX));

            if (!hasStableOwner)
            {
                mob.discard();
            }
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

    /**
     * Selects client-only logical slots after reserving the evenly distributed live actor slots.
     * The combined live and visual tiers never exceed renderBudget and never draw the same member.
     */
    public static int[] visualFormationIndices(int count, int renderBudget, int maxLive)
    {
        int safeCount = Math.max(0, count);
        int totalBudget = Math.min(safeCount, Math.max(0, renderBudget));
        int liveCount = Math.min(totalBudget, Math.max(0, maxLive));
        int visualCount = totalBudget - liveCount;

        if (visualCount <= 0)
        {
            return new int[0];
        }

        int[] liveIndices = new int[liveCount];

        for (int i = 0; i < liveCount; i++)
        {
            liveIndices[i] = CrowdSpawnActionClip.liveFormationIndex(i, liveCount, safeCount);
        }

        int remaining = safeCount - liveCount;
        int[] visualIndices = new int[visualCount];

        for (int i = 0; i < visualCount; i++)
        {
            int rank = (int) Math.min(remaining - 1L, ((2L * i + 1L) * remaining) / (2L * visualCount));

            visualIndices[i] = logicalIndexForRemainingRank(rank, liveIndices, safeCount);
        }

        return visualIndices;
    }

    private static int logicalIndexForRemainingRank(int rank, int[] excluded, int count)
    {
        int low = 0;
        int high = count - 1;

        while (low < high)
        {
            int middle = (low + high) >>> 1;
            int availableThroughMiddle = middle + 1 - upperBound(excluded, middle);

            if (availableThroughMiddle > rank)
            {
                high = middle;
            }
            else
            {
                low = middle + 1;
            }
        }

        return low;
    }

    private static int upperBound(int[] sorted, int value)
    {
        int low = 0;
        int high = sorted.length;

        while (low < high)
        {
            int middle = (low + high) >>> 1;

            if (sorted[middle] <= value)
            {
                low = middle + 1;
            }
            else
            {
                high = middle;
            }
        }

        return low;
    }

    public static Vec3d formationPoint(CrowdFormation formation, int index, int count, double spacing, double boxX, double boxY, double boxZ)
    {
        return formationPoint(formation, index, count, spacing, boxX, boxY, boxZ, 0D);
    }

    public static Vec3d formationPoint(CrowdFormation formation, int index, int count, double spacing, double boxX, double boxY, double boxZ, double hollow)
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
                /* The old implementation was only a flat rectangle. Split the samples
                 * across the two horizontal rectangles and four vertical edges so the
                 * Box outline follows every edge of the visible spawn volume. */
                double horizontalPerimeter = 2D * (boxX + boxZ);
                double horizontalLength = horizontalPerimeter * 2D;
                double totalLength = horizontalLength + boxY * 4D;
                double distance = count <= 1 ? 0D : (index / (double) count) * totalLength;

                if (distance < horizontalPerimeter)
                {
                    Vec3d point = rectangleOutlinePoint(distance, boxX, boxZ);

                    return new Vec3d(point.x, 0D, point.z);
                }

                if (distance < horizontalLength)
                {
                    Vec3d point = rectangleOutlinePoint(distance - horizontalPerimeter, boxX, boxZ);

                    return new Vec3d(point.x, boxY, point.z);
                }

                if (boxY <= 0D)
                {
                    Vec3d point = rectangleOutlinePoint(distance % horizontalPerimeter, boxX, boxZ);

                    return new Vec3d(point.x, 0D, point.z);
                }

                double verticalDistance = distance - horizontalLength;
                int edge = Math.min(3, (int) (verticalDistance / boxY));
                double y = verticalDistance - edge * boxY;
                double halfX = boxX * 0.5D;
                double halfZ = boxZ * 0.5D;

                return switch (edge)
                {
                    case 0 -> new Vec3d(-halfX, y, -halfZ);
                    case 1 -> new Vec3d(halfX, y, -halfZ);
                    case 2 -> new Vec3d(halfX, y, halfZ);
                    default -> new Vec3d(-halfX, y, halfZ);
                };
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
            case HOLLOW_CIRCLE:
            default:
            {
                return circlePoint(index, count, hollowOuterRadius(count, spacing, hollow), spacing, hollow);
            }
        }
    }

    /**
     * Deterministic circumference-balanced ring packing. A golden-angle sunflower is
     * area-uniform, but sparse/live-tier samples expose its sequence as stars and spirals.
     * Rings keep the authored silhouette circular at every count, while independent phase
     * offsets prevent neighboring rings from forming radial spokes.
     */
    public static Vec3d circlePoint(int index, int count, double outerRadius, double spacing, double hollow)
    {
        count = Math.max(1, count);
        index = Math.floorMod(index, count);
        outerRadius = Math.max(0.1D, outerRadius);

        double hole = MathHelper.clamp(hollow, 0D, 0.95D);
        if (count == 1)
        {
            return hole <= 1.0E-8D ? Vec3d.ZERO : new Vec3d(outerRadius, 0D, 0D);
        }

        if (hole <= 1.0E-8D)
        {
            if (index == 0)
            {
                return Vec3d.ZERO;
            }

            int rings = Math.max(1, (int) Math.floor(Math.sqrt(count / Math.PI)));
            int ring = filledCircleRing(index, count, rings);
            int start = filledCircleRingBoundary(ring - 1, count, rings);
            int end = filledCircleRingBoundary(ring, count, rings);
            int ringCount = Math.max(1, end - start);
            double radius = outerRadius * ring / rings;
            double angle = circleRingPhase(ring) + (index - start) * Math.PI * 2D / ringCount;

            return new Vec3d(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
        }

        int rings = Math.max(1, (int) Math.round(
            (1D - hole) * Math.sqrt(count / (Math.PI * (1D - hole * hole)))
        ));
        int ring = hollowCircleRing(index, count, rings, hole);
        int start = hollowCircleRingBoundary(ring, count, rings, hole);
        int end = hollowCircleRingBoundary(ring + 1, count, rings, hole);
        int ringCount = Math.max(1, end - start);
        double radiusFactor = rings == 1
            ? 1D
            : hole + (1D - hole) * ring / (rings - 1D);
        double radius = outerRadius * radiusFactor;
        double angle = circleRingPhase(ring) + (index - start) * Math.PI * 2D / ringCount;

        return new Vec3d(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
    }

    private static int filledCircleRing(int index, int count, int rings)
    {
        double rank = index - 1D;
        double scaled = rank * rings * (rings + 1D) / Math.max(1D, count - 1D);
        int ring = Math.max(1, Math.min(rings,
            (int) Math.floor((Math.sqrt(1D + 4D * scaled) - 1D) * 0.5D) + 1));

        while (ring > 1 && index < filledCircleRingBoundary(ring - 1, count, rings))
        {
            ring--;
        }

        while (ring < rings && index >= filledCircleRingBoundary(ring, count, rings))
        {
            ring++;
        }

        return ring;
    }

    private static int filledCircleRingBoundary(int ring, int count, int rings)
    {
        if (ring <= 0)
        {
            return 1;
        }

        long numerator = (long) (count - 1) * ring * (ring + 1L);
        long denominator = (long) rings * (rings + 1L);

        return 1 + (int) Math.min(count - 1L, Math.round(numerator / (double) denominator));
    }

    private static int hollowCircleRing(int index, int count, int rings, double hole)
    {
        if (rings <= 1)
        {
            return 0;
        }

        double step = (1D - hole) / (rings - 1D);
        double totalWeight = rings * (hole + 1D) * 0.5D;
        double targetWeight = (index + 0.5D) * totalWeight / count;
        double linear = hole - step * 0.5D;
        double discriminant = linear * linear + 2D * step * targetWeight;
        int ring = step <= 1.0E-12D
            ? 0
            : (int) Math.floor((-linear + Math.sqrt(Math.max(0D, discriminant))) / step);

        ring = Math.max(0, Math.min(rings - 1, ring));

        while (ring > 0 && index < hollowCircleRingBoundary(ring, count, rings, hole))
        {
            ring--;
        }

        while (ring < rings - 1 && index >= hollowCircleRingBoundary(ring + 1, count, rings, hole))
        {
            ring++;
        }

        return ring;
    }

    private static int hollowCircleRingBoundary(int ring, int count, int rings, double hole)
    {
        if (ring <= 0)
        {
            return 0;
        }

        if (ring >= rings)
        {
            return count;
        }

        double step = (1D - hole) / (rings - 1D);
        double cumulativeWeight = ring * hole + step * ring * (ring - 1D) * 0.5D;
        double totalWeight = rings * (hole + 1D) * 0.5D;

        return (int) Math.round(count * cumulativeWeight / totalWeight);
    }

    private static double circleRingPhase(int ring)
    {
        return randomUnit(ring, 0x6a09e667) * Math.PI * 2D;
    }

    /** Exact authored footprint used to make the route's first gate match the spawn formation. */
    public static Vec3d formationSize(CrowdFormation formation, int count, double spacing, double boxX, double boxY, double boxZ, boolean constrained)
    {
        return formationSize(formation, count, spacing, boxX, boxY, boxZ, constrained, 0D);
    }

    public static Vec3d formationSize(CrowdFormation formation, int count, double spacing, double boxX, double boxY, double boxZ, boolean constrained, double hollow)
    {
        formation = formation == null ? CrowdFormation.CIRCLE : formation;
        count = Math.max(1, count);
        spacing = Math.max(0.1D, spacing);
        boxX = Math.max(0.1D, boxX);
        boxY = Math.max(0D, boxY);
        boxZ = Math.max(0.1D, boxZ);

        if (constrained || formation == CrowdFormation.BOX || formation == CrowdFormation.BOX_OUTLINE)
        {
            return new Vec3d(boxX, boxY, boxZ);
        }

        return switch (formation)
        {
            case LINE -> new Vec3d(Math.max(spacing, (count - 1D) * spacing), 0D, spacing);
            case GRID, SQUARE ->
            {
                int side = (int) Math.ceil(Math.sqrt(count));
                double size = Math.max(spacing, (side - 1D) * spacing);

                yield new Vec3d(size, 0D, size);
            }
            case SQUARE_OUTLINE ->
            {
                double size = Math.max(spacing, count * spacing / 4D);

                yield new Vec3d(size, 0D, size);
            }
            case CIRCLE_OUTLINE ->
            {
                double diameter = count <= 1 ? spacing : count * spacing / Math.PI;

                yield new Vec3d(diameter, 0D, diameter);
            }
            case CIRCLE, HOLLOW_CIRCLE ->
            {
                double diameter = Math.max(spacing, hollowOuterRadius(count, spacing, hollow) * 2D);

                yield new Vec3d(diameter, 0D, diameter);
            }
            default -> new Vec3d(boxX, boxY, boxZ);
        };
    }

    /**
     * True when a formation fills a 2D ground area (as opposed to a 1D outline or a
     * volumetric box). Filled formations share the same local density of 1/spacing^2,
     * which lets the cinematic mega-crowd layer reproduce them with an area-based fill
     * instead of a sparse index sample.
     */
    public static boolean isFilledArea(CrowdFormation formation)
    {
        return formation == CrowdFormation.CIRCLE
            || formation == CrowdFormation.HOLLOW_CIRCLE
            || formation == CrowdFormation.GRID
            || formation == CrowdFormation.SQUARE;
    }

    /** Outer radius of a filled CIRCLE, matching {@link #formationPoint}'s maximum radius. */
    public static double filledCircleRadius(int count, double spacing)
    {
        return Math.sqrt(Math.max(1, count) / Math.PI) * Math.max(0.1D, spacing);
    }

    /** Outer radius of a Circle with an optional center hole, matching {@link #formationPoint}. */
    public static double hollowOuterRadius(int count, double spacing, double hollow)
    {
        double amount = MathHelper.clamp(hollow, 0D, 0.95D);

        return filledCircleRadius(count, spacing) / Math.sqrt(Math.max(0.0975D, 1D - amount * amount));
    }

    /** Inner empty radius of a Circle with a hole, matching {@link #formationPoint}. */
    public static double hollowInnerRadius(int count, double spacing, double hollow)
    {
        double amount = MathHelper.clamp(hollow, 0D, 0.95D);

        return hollowOuterRadius(count, spacing, hollow) * amount;
    }

    /** Half extent (centre to edge) of a filled GRID/SQUARE, matching {@link #formationPoint}. */
    public static double squareHalfExtent(int count, double spacing)
    {
        int side = (int) Math.ceil(Math.sqrt(Math.max(1, count)));

        return Math.max(0D, (side - 1) * Math.max(0.1D, spacing) * 0.5D);
    }

    /** Stable pseudo-random 32-bit hash of a 2D grid cell for jitter and yaw. */
    public static int cellHash(int gx, int gz)
    {
        int hash = gx * 0x27d4eb2d ^ gz * 0x165667b1;

        hash ^= hash >>> 15;
        hash *= 0x2c1b3c6d;
        hash ^= hash >>> 12;
        hash *= 0x297a2d39;
        hash ^= hash >>> 15;

        return hash;
    }

    private static List<Entity> entitySnapshot(ServerWorld world)
    {
        List<Entity> entities = new ArrayList<>();

        for (Entity entity : world.iterateEntities())
        {
            entities.add(entity);
        }

        return entities;
    }

    /**
     * Return a deterministic formation point fitted entirely inside an X/Y/Z box.
     * The spacing defines the formation's proportions, then the result is squeezed
     * to the box so even a large crowd cannot spill outside its boundary.
     */
    public static Vec3d formationPointInBox(CrowdFormation formation, int index, int count, double spacing, double boxX, double boxY, double boxZ)
    {
        return formationPointInBox(formation, index, count, spacing, boxX, boxY, boxZ, 0D);
    }

    public static Vec3d formationPointInBox(CrowdFormation formation, int index, int count, double spacing, double boxX, double boxY, double boxZ, double hollow)
    {
        count = Math.max(1, count);
        spacing = Math.max(0.1D, spacing);
        boxX = Math.max(0.1D, boxX);
        boxY = Math.max(0D, boxY);
        boxZ = Math.max(0.1D, boxZ);
        formation = formation == null ? CrowdFormation.CIRCLE : formation;

        if (formation == CrowdFormation.BOX || formation == CrowdFormation.BOX_OUTLINE)
        {
            return formationPoint(formation, index, count, spacing, boxX, boxY, boxZ, hollow);
        }

        Vec3d point = formationPoint(formation, index, count, spacing, boxX, boxY, boxZ, hollow);
        double halfX;
        double halfZ;

        switch (formation)
        {
            case LINE:
                halfX = Math.max(0D, (count - 1) * spacing * 0.5D);
                halfZ = 0D;
                break;
            case GRID:
            case SQUARE:
            {
                int side = (int) Math.ceil(Math.sqrt(count));
                halfX = halfZ = Math.max(0D, (side - 1) * spacing * 0.5D);
                break;
            }
            case SQUARE_OUTLINE:
                halfX = halfZ = Math.max(0D, count * spacing / 8D);
                break;
            case CIRCLE_OUTLINE:
                halfX = halfZ = count <= 1 ? 0D : count * spacing / (Math.PI * 2D);
                break;
            case CIRCLE:
            case HOLLOW_CIRCLE:
            default:
                halfX = halfZ = hollowOuterRadius(count, spacing, hollow);
                break;
        }

        return new Vec3d(fitAxis(point.x, halfX, boxX), 0D, fitAxis(point.z, halfZ, boxZ));
    }

    private static double fitAxis(double value, double sourceHalf, double boxSize)
    {
        if (sourceHalf <= 1.0E-8D)
        {
            return 0D;
        }

        double halfBox = boxSize * 0.5D;

        return Math.max(-halfBox, Math.min(halfBox, value / sourceHalf * halfBox));
    }

    private static Vec3d rectangleOutlinePoint(double distance, double boxX, double boxZ)
    {
        double perimeter = 2D * (boxX + boxZ);
        double d = perimeter <= 0D ? 0D : distance % perimeter;
        double halfX = boxX * 0.5D;
        double halfZ = boxZ * 0.5D;

        if (d < boxX) return new Vec3d(-halfX + d, 0D, -halfZ);
        if (d < boxX + boxZ) return new Vec3d(halfX, 0D, -halfZ + d - boxX);
        if (d < boxX * 2D + boxZ) return new Vec3d(halfX - (d - boxX - boxZ), 0D, halfZ);

        return new Vec3d(-halfX, 0D, halfZ - (d - boxX * 2D - boxZ));
    }

    public static Vec3d personalOffset(LivingEntity mob, double spacing)
    {
        if (spacing <= 0D)
        {
            return Vec3d.ZERO;
        }

        int hash = mob.getUuid().hashCode();
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

    public static boolean isServerLevel(World world)
    {
        return world instanceof ServerWorld;
    }
}
