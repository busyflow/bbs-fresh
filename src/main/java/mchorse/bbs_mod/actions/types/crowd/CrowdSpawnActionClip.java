package mchorse.bbs_mod.actions.types.crowd;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

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
    public final ValueInt count = new ValueInt("count", 20, 1, 10000);
    public final ValueInt seed = new ValueInt("seed", 1);
    public final ValueFloat spacing = new ValueFloat("spacing", 1.0F, 0.1F, 64F);
    public final ValueInt formation = new ValueInt("formation", CrowdFormation.CIRCLE.ordinal(), 0, CrowdFormation.values().length - 1);
    public final ValueFloat holeRadius = new ValueFloat("hole_radius", 4F, 0F, 128F);
    public final ValueBoolean randomYaw = new ValueBoolean("random_yaw", true);
    public final ValueBoolean spawnOnBlock = new ValueBoolean("spawn_on_block", true);
    public final ValueBoolean skipUnsafe = new ValueBoolean("skip_unsafe", true);
    public final ValueBoolean replaceExisting = new ValueBoolean("replace_existing", true);

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
        this.add(this.randomYaw);
        this.add(this.spawnOnBlock);
        this.add(this.skipUnsafe);
        this.add(this.replaceExisting);
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
                mob.setAiDisabled(false);
                mob.setSilent(false);
                mob.setCustomNameVisible(false);
                entity = mob;
            }

            entity.setUuid(CrowdUtils.deterministicUuid(film, tag, this.seed.get(), i));

            Vec3d spawn = this.findSpawnPoint(world, entity, center, formation, i, count, spacing, surfaceCache);

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

    private Vec3d findSpawnPoint(ServerWorld world, LivingEntity entity, Vec3d center, CrowdFormation formation, int index, int count, double spacing, Map<Long, Double> surfaceCache)
    {
        Vec3d fallback = null;
        boolean skipUnsafe = this.skipUnsafe.get();

        for (int attempt = 0; attempt < 16; attempt++)
        {
            Vec3d offset = this.getOffset(formation, index, count, spacing, attempt);
            double x = center.x + offset.x;
            double z = center.z + offset.z;
            double sampledY = center.y + offset.y;
            Double y;

            if (this.spawnOnBlock.get())
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
