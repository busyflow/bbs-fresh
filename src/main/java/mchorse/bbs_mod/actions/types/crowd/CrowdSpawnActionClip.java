package mchorse.bbs_mod.actions.types.crowd;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import mchorse.bbs_mod.actions.types.area.ValueAreaCells;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrowdSpawnActionClip extends ActionClip
{
    public final ValueString crowdTag = new ValueString("crowd_tag", "crowd_1");
    public final ValueString mobType = new ValueString("mob_type", "minecraft:villager");
    public final ValueBoolean useActorForm = new ValueBoolean("use_actor_form", false);
    public final ValueForm actorForm = new ValueForm("actor_form");
    public final ValueBoolean randomTextures = new ValueBoolean("random_textures", false);
    public final ValueLink randomTextureFolder = new ValueLink("random_texture_folder", null);
    /* Neighbour work is bucketed rather than all-pairs (see CrowdGrid), so the ceiling is the
     * entity tick itself rather than the crowd logic. The high end of this range is meant for
     * rendering out a shot, not for editing one live - vanilla's own entity ticking will not
     * hold 20 TPS there. */
    public final ValueInt count = new ValueInt("count", 20, 1, 100000);
    public final ValueInt seed = new ValueInt("seed", 1);
    public final ValueFloat spacing = new ValueFloat("spacing", 1.0F, 0.1F, 64F);
    public final ValueInt formation = new ValueInt("formation", CrowdFormation.CIRCLE.ordinal(), 0, CrowdFormation.values().length - 1);
    public final ValueFloat holeRadius = new ValueFloat("hole_radius", 4F, 0F, 128F);
    /* The crowd behaviour clip drives movement by setting velocity directly and stops the
     * navigator anyway, so vanilla AI contributes nothing but cost - and for villagers that
     * cost is the brain, comfortably the most expensive thing they do per tick. Turning it off
     * also stops them wandering off on their own errands, out of the loaded chunks. */
    public final ValueBoolean disableAi = new ValueBoolean("disable_ai", false);
    public final ValueBoolean randomYaw = new ValueBoolean("random_yaw", true);
    public final ValueBoolean spawnOnBlock = new ValueBoolean("spawn_on_block", true);
    public final ValueBoolean skipUnsafe = new ValueBoolean("skip_unsafe", true);
    public final ValueBoolean replaceExisting = new ValueBoolean("replace_existing", true);

    /* The {@link CrowdFormation#PAINT} formation's shape: ground painted by hand in the editor,
     * as the surface height of every column the brush covered. Unlike every other formation this
     * one is absolute world space, not an offset from the replay - it is drawn onto the world. */
    public final ValueAreaCells cells = new ValueAreaCells("cells");
    public final ValueInt brushSize = new ValueInt("brush_size", 4, 1, 64);

    public CrowdSpawnActionClip()
    {
        super();

        this.add(this.crowdTag);
        this.add(this.mobType);
        this.add(this.useActorForm);
        this.add(this.actorForm);
        this.add(this.randomTextures);
        this.add(this.randomTextureFolder);
        this.add(this.count);
        this.add(this.seed);
        this.add(this.spacing);
        this.add(this.formation);
        this.add(this.holeRadius);
        this.add(this.disableAi);
        this.add(this.randomYaw);
        this.add(this.spawnOnBlock);
        this.add(this.skipUnsafe);
        this.add(this.replaceExisting);
        this.add(this.cells);
        this.add(this.brushSize);
    }

    /* Painting, from the editor's brush. */

    public Long2IntOpenHashMap getCells()
    {
        return this.cells.get();
    }

    public void paint(int x, int y, int z)
    {
        this.getCells().put(ValueAreaCells.key(x, z), y);
    }

    public void erase(int x, int z)
    {
        this.getCells().remove(ValueAreaCells.key(x, z));
    }

    public void clearCells()
    {
        this.getCells().clear();
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        if (player == null || !CrowdUtils.isServerWorld(player.getWorld()) || film == null || replay == null)
        {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        String tag = CrowdUtils.crowdTag(this.crowdTag.get());

        if (this.replaceExisting.get())
        {
            CrowdUtils.removeCrowd(world, film, tag);
        }

        boolean useActor = this.useActorForm.get();
        EntityType<?> type = null;

        if (!useActor)
        {
            try
            {
                type = Registries.ENTITY_TYPE.get(new Identifier(this.mobType.get()));
            }
            catch (Exception e)
            {
                return;
            }

            if (type == null)
            {
                return;
            }
        }
        else if (this.actorForm.get() == null)
        {
            return;
        }

        Vec3d center = CrowdUtils.replayPosition(replay, tick);
        CrowdFormation formation = CrowdFormation.get(this.formation.get());
        int count = this.count.get();
        double spacing = this.spacing.get();
        List<Link> textures = this.randomTextures.get() ? this.collectTextures(this.randomTextureFolder.get()) : List.of();
        Map<Long, Double> surfaceCache = this.spawnOnBlock.get() ? new HashMap<>() : null;
        EntityData entityData = null;
        CrowdPaintArea area = null;

        if (formation == CrowdFormation.PAINT)
        {
            area = new CrowdPaintArea(this.getCells());

            /* Nothing painted means nothing was asked for. Falling back to a circle here would
             * put a crowd somewhere the shot never called for. */
            if (area.isEmpty())
            {
                return;
            }
        }

        IntList spawned = new IntArrayList(count);

        for (int i = 0; i < count; i++)
        {
            LivingEntity entity;

            if (useActor)
            {
                Form form = this.createActorForm(textures, i);

                if (form == null)
                {
                    continue;
                }

                ActorEntity actorEntity = new ActorEntity(BBSMod.ACTOR_ENTITY, world);

                actorEntity.setForm(form);
                entity = actorEntity;
            }
            else
            {
                Entity created = type.create(world);

                if (!(created instanceof MobEntity mob))
                {
                    if (created != null)
                    {
                        created.discard();
                    }

                    continue;
                }

                BlockPos initial = BlockPos.ofFloored(center);

                entityData = mob.initialize(world, world.getLocalDifficulty(initial), SpawnReason.COMMAND, entityData, null);
                mob.setPersistent();
                mob.setAiDisabled(this.disableAi.get());
                mob.setSilent(false);
                mob.setCustomNameVisible(false);
                entity = mob;
            }

            entity.setUuid(CrowdUtils.deterministicUuid(film, tag, this.seed.get(), i));

            Vec3d spawn = this.findSpawnPoint(world, entity, center, formation, area, i, count, spacing, surfaceCache);

            if (spawn == null)
            {
                continue;
            }

            float yaw = this.randomYaw.get() ? (float) ((i * 137.507764D) % 360D) : 0F;

            entity.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, yaw, 0F);
            entity.prevX = spawn.x;
            entity.prevY = spawn.y;
            entity.prevZ = spawn.z;
            entity.lastRenderX = spawn.x;
            entity.lastRenderY = spawn.y;
            entity.lastRenderZ = spawn.z;
            entity.prevYaw = yaw;
            entity.prevPitch = 0F;
            entity.setHeadYaw(yaw);
            entity.prevHeadYaw = yaw;
            entity.setBodyYaw(yaw);
            entity.prevBodyYaw = yaw;
            entity.setVelocity(Vec3d.ZERO);
            entity.fallDistance = 0F;
            entity.calculateDimensions();
            entity.setSprinting(false);
            entity.setSneaking(false);
            CrowdUtils.tag(entity, film, tag, i);

            world.spawnEntity(entity);
            spawned.add(entity.getId());
        }

        /* Tell the clients who these are as soon as they exist, rather than leaving it to the
         * behaviour clip: a crowd is allowed to have no behaviour at all, and one that never
         * announced itself is one the client cannot thin out of the preview or hold the bodies
         * of - it would be drawn ten thousand strong, facing wherever it spawned. */
        if (!spawned.isEmpty())
        {
            ServerNetwork.sendCrowdMembers(world, spawned);
        }
    }

    private Form createActorForm(List<Link> textures, int index)
    {
        Form form = FormUtils.copy(this.actorForm.get());

        if (form == null)
        {
            return null;
        }

        if (!textures.isEmpty())
        {
            BaseValue property = FormUtils.getProperty(form, "texture");

            if (property instanceof ValueLink valueLink)
            {
                valueLink.set(textures.get(Math.floorMod(index * 31 + form.hashCode(), textures.size())));
            }
        }

        return form;
    }

    private List<Link> collectTextures(Link folder)
    {
        List<Link> textures = new ArrayList<>();

        if (folder == null || folder.source.isEmpty())
        {
            return textures;
        }

        try
        {
            for (Link link : BBSMod.getProvider().getLinksFromPath(folder, false))
            {
                if (!link.path.endsWith("/") && link.path.endsWith(".png"))
                {
                    textures.add(link);
                }
            }
        }
        catch (Exception e)
        {}

        return textures;
    }

    private Vec3d findSpawnPoint(ServerWorld world, LivingEntity entity, Vec3d center, CrowdFormation formation, CrowdPaintArea area, int index, int count, double spacing, Map<Long, Double> surfaceCache)
    {
        Vec3d fallback = null;
        boolean skipUnsafe = this.skipUnsafe.get();

        for (int attempt = 0; attempt < 16; attempt++)
        {
            double x;
            double z;
            double sampledY;

            if (area != null)
            {
                /* Painted ground is drawn onto the world, so its points are already absolute -
                 * the replay's position doesn't move them. */
                Vec3d painted = area.point(index, count, attempt);

                x = painted.x;
                z = painted.z;
                sampledY = painted.y;
            }
            else
            {
                Vec3d offset = this.getOffset(formation, index, count, spacing, attempt);

                x = center.x + offset.x;
                z = center.z + offset.z;
                sampledY = center.y + offset.y;
            }

            Double y;

            if (area != null)
            {
                /* The brush already found this column's surface, and it did it against the world
                 * the shot was composed in. Re-deriving it here would cost a block read per
                 * member - forty thousand of them - to answer a question already answered. */
                y = sampledY;
            }
            else if (this.spawnOnBlock.get())
            {
                y = this.findSurfaceY(world, center, x, z, surfaceCache);
            }
            else
            {
                y = sampledY;
            }

            fallback = new Vec3d(x, sampledY, z);

            if (y == null)
            {
                continue;
            }

            Vec3d candidate = new Vec3d(x, y, z);

            fallback = candidate;

            if (this.isSpawnClear(world, entity, candidate))
            {
                return candidate;
            }
        }

        return skipUnsafe ? null : fallback;
    }

    private Vec3d getOffset(CrowdFormation formation, int index, int count, double spacing, int attempt)
    {
        int sampleIndex = index + attempt * Math.max(1, count) * 9973;
        Vec3d offset = CrowdUtils.formationPoint(formation, sampleIndex, count, spacing, this.holeRadius.get());

        if ((formation == CrowdFormation.BOX || formation == CrowdFormation.BOX_OUTLINE) || attempt == 0)
        {
            return offset;
        }

        Vec3d nudge = CrowdUtils.formationPoint(CrowdFormation.CIRCLE, attempt - 1, 15, Math.max(0.6D, spacing * 0.35D));

        return offset.add(nudge);
    }

    private boolean isSpawnClear(ServerWorld world, LivingEntity entity, Vec3d spawn)
    {
        double halfWidth = Math.max(0.2D, entity.getWidth() * 0.5D - 0.01D);

        /* Same reasoning as findSurfaceY - the footprint can straddle a chunk border, and the
         * probe must not be what pulls an ungenerated chunk in on the server thread. */
        for (int cx = (int) Math.floor(spawn.x - halfWidth) >> 4; cx <= (int) Math.floor(spawn.x + halfWidth) >> 4; cx++)
        {
            for (int cz = (int) Math.floor(spawn.z - halfWidth) >> 4; cz <= (int) Math.floor(spawn.z + halfWidth) >> 4; cz++)
            {
                if (world.getChunk(cx, cz, ChunkStatus.FULL, false) == null)
                {
                    return false;
                }
            }
        }

        int height = Math.max(1, (int) Math.ceil(entity.getHeight()));
        double[] xs = new double[] {spawn.x, spawn.x - halfWidth, spawn.x + halfWidth};
        double[] zs = new double[] {spawn.z, spawn.z - halfWidth, spawn.z + halfWidth};

        for (int dy = 0; dy < height; dy++)
        {
            double y = spawn.y + dy;

            for (double px : xs)
            {
                for (double pz : zs)
                {
                    BlockPos pos = BlockPos.ofFloored(px, y, pz);
                    BlockState state = world.getBlockState(pos);

                    if (!state.getCollisionShape(world, pos).isEmpty())
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private Double findSurfaceY(ServerWorld world, Vec3d center, double x, double z, Map<Long, Double> surfaceCache)
    {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        long key = columnKey(bx, bz);

        if (surfaceCache != null && surfaceCache.containsKey(key))
        {
            return surfaceCache.get(key);
        }

        Double surface = this.findSurfaceY(world, center, bx, bz);

        if (surfaceCache != null)
        {
            surfaceCache.put(key, surface);
        }

        return surface;
    }

    /**
     * Find the standable Y in this column. The search spans {@link #VERTICAL_RANGE} blocks
     * both above <em>and</em> below the origin, so a crowd laid over sloping ground keeps its
     * shape when the terrain drops away instead of losing every member on the low side.
     */
    private Double findSurfaceY(ServerWorld world, Vec3d center, int bx, int bz)
    {
        /* Never touch a column whose chunk is not already finished. Reading a block out there
         * would force a synchronous generate on the server thread, and a wide crowd has enough
         * rim members to chain those into a freeze that also takes the world save down with it.
         * Skipping the member instead simply leaves the crowd's edge at the loaded boundary.
         * A chunk part-way through generation counts as loaded but is not free to read, so ask
         * for a finished one and accept its absence rather than waiting for it. */
        if (world.getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false) == null)
        {
            return null;
        }

        int origin = (int) Math.floor(center.y);
        int minY = Math.max(world.getBottomY() + 1, origin - VERTICAL_RANGE);
        int maxY = Math.min(world.getTopY() - 2, origin + VERTICAL_RANGE);

        if (maxY < minY)
        {
            return null;
        }

        /* Start from the heightmap when it falls inside the band - that skips the whole
         * scan for open sky, which is the common case. */
        int start = Math.min(maxY, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz));

        for (int y = start; y >= minY; y--)
        {
            BlockPos feet = new BlockPos(bx, y, bz);
            BlockPos below = feet.down();
            BlockState belowState = world.getBlockState(below);

            if (belowState.isSideSolidFullSquare(world, below, Direction.UP) && world.isAir(feet) && world.isAir(feet.up()))
            {
                return (double) y;
            }
        }

        return null;
    }

    private static final int VERTICAL_RANGE = 32;

    private static long columnKey(int x, int z)
    {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    @Override
    protected Clip create()
    {
        return new CrowdSpawnActionClip();
    }
}
