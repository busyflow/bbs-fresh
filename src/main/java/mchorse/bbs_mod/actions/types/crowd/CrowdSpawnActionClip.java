package mchorse.bbs_mod.actions.types.crowd;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ServerNetwork;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class CrowdSpawnActionClip extends ActionClip
{
	/** Logical cinematic crowd size. Mega crowds are visualized with client-side LOD. */
	public static final int MAX_MEMBERS = 1_000_000;
	/**
	 * Full living entities are the interactive damage/combat tier. The remaining
	 * logical members are rendered by the client-side crowd tier.
	 */
	public static final int MAX_LIVE_MEMBERS = 512;
    public static final int ARMOR_SALT_HEAD = 0x45d9f3b;
    public static final int ARMOR_SALT_CHEST = 0x119de1f3;
    public static final int ARMOR_SALT_LEGS = 0x3449a2d7;
    public static final int ARMOR_SALT_FEET = 0x68931c7;
    /** Bit flags deliberately keep material selection compact and serialization-friendly. */
    public static final int ARMOR_LEATHER = 1;
    public static final int ARMOR_CHAINMAIL = 1 << 1;
    public static final int ARMOR_IRON = 1 << 2;
    public static final int ARMOR_GOLD = 1 << 3;
    public static final int ARMOR_DIAMOND = 1 << 4;
    public static final int ARMOR_NETHERITE = 1 << 5;
    public static final int ARMOR_ALL = ARMOR_LEATHER | ARMOR_CHAINMAIL | ARMOR_IRON | ARMOR_GOLD | ARMOR_DIAMOND | ARMOR_NETHERITE;

    public final ValueString crowdTag = new ValueString("crowd_tag", "crowd_1");
    public final ValueString mobType = new ValueString("mob_type", "minecraft:villager");
    public final ValueBoolean useActorForm = new ValueBoolean("use_actor_form", false);
    public final ValueForm actorForm = new ValueForm("actor_form");
    public final ValueBoolean randomTextures = new ValueBoolean("random_textures", false);
    public final ValueLink textureOverride = new ValueLink("texture_override", null);
    public final ValueLink randomTextureFolder = new ValueLink("random_texture_folder", null);
    public final ValueBoolean recursiveTextures = new ValueBoolean("recursive_textures", false);
    public final ValueInt count = new ValueInt("count", 20, 1, MAX_MEMBERS);
    public final ValueInt liveLimit = new ValueInt("live_limit", MAX_LIVE_MEMBERS, 0, MAX_LIVE_MEMBERS);
    public final ValueFloat spacing = new ValueFloat("spacing", 1.0F, 0.1F, 64F);
    public final ValueFloat health = new ValueFloat("health", 20F, 0F, 1024F);
    public final ValueBoolean randomArmor = new ValueBoolean("random_armor", false);
    public final ValueFloat randomArmorCoverage = new ValueFloat("random_armor_coverage", 0.55F, 0F, 1F);
    public final ValueFloat randomArmorRichness = new ValueFloat("random_armor_richness", 0.45F, 0F, 1F);
    public final ValueBoolean randomArmorHead = new ValueBoolean("random_armor_head", true);
    public final ValueBoolean randomArmorChest = new ValueBoolean("random_armor_chest", true);
    public final ValueBoolean randomArmorLegs = new ValueBoolean("random_armor_legs", true);
    public final ValueBoolean randomArmorFeet = new ValueBoolean("random_armor_feet", true);
    /** Exact material pools per slot. The legacy richness value remains for old scenes. */
    public final ValueInt randomArmorHeadMaterials = new ValueInt("random_armor_head_materials", ARMOR_ALL, 0, ARMOR_ALL);
    public final ValueInt randomArmorChestMaterials = new ValueInt("random_armor_chest_materials", ARMOR_ALL, 0, ARMOR_ALL);
    public final ValueInt randomArmorLegsMaterials = new ValueInt("random_armor_legs_materials", ARMOR_ALL, 0, ARMOR_ALL);
    public final ValueInt randomArmorFeetMaterials = new ValueInt("random_armor_feet_materials", ARMOR_ALL, 0, ARMOR_ALL);
    public final ValueInt formation = new ValueInt("formation", CrowdFormation.CIRCLE.ordinal(), 0, CrowdFormation.values().length - 1);
    public final ValueFloat hollow = new ValueFloat("hollow", 0F, 0F, 0.95F);
    public final ValueFloat radius = new ValueFloat("radius", 0F, 0F, 512F);
    public final ValueInt seed = new ValueInt("seed", 0);
    public final ValueFloat yawVariation = new ValueFloat("yaw_variation", 0F, 0F, 180F);
    public final ValueFloat boxX = new ValueFloat("box_x", 8F, 0.1F, 256F);
    public final ValueFloat boxY = new ValueFloat("box_y", 2F, 0F, 128F);
    public final ValueFloat boxZ = new ValueFloat("box_z", 8F, 0.1F, 256F);
    public final ValueBoolean randomYaw = new ValueBoolean("random_yaw", true);
    public final ValueBoolean spawnOnBlock = new ValueBoolean("spawn_on_block", true);
    public final ValueBoolean snapToBlock = new ValueBoolean("snap_to_block", false);
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
        this.add(this.textureOverride);
        this.add(this.randomTextureFolder);
        this.add(this.recursiveTextures);
        this.add(this.count);
        this.add(this.liveLimit.invisible());
        this.add(this.spacing);
        this.add(this.health);
        this.add(this.randomArmor);
        this.add(this.randomArmorCoverage);
        this.add(this.randomArmorRichness);
        this.add(this.randomArmorHead);
        this.add(this.randomArmorChest);
        this.add(this.randomArmorLegs);
        this.add(this.randomArmorFeet);
        this.add(this.randomArmorHeadMaterials);
        this.add(this.randomArmorChestMaterials);
        this.add(this.randomArmorLegsMaterials);
        this.add(this.randomArmorFeetMaterials);
        this.add(this.formation);
        this.add(this.hollow);
        this.add(this.radius);
        this.add(this.seed);
        this.add(this.yawVariation);
        this.add(this.boxX);
        this.add(this.boxY);
        this.add(this.boxZ);
        this.add(this.randomYaw);
        this.add(this.spawnOnBlock);
        this.add(this.snapToBlock);
        this.add(this.skipUnsafe);
        this.add(this.replaceExisting);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        if (player == null || !CrowdUtils.isServerLevel(player.getWorld()) || film == null || replay == null)
        {
            return;
        }

        this.spawn((ServerWorld) player.getWorld(), film, replay, tick);
    }

    /** Spawn this crowd directly on the server. Used by the Crowd replay form. */
    public void spawn(ServerWorld world, Film film, Replay replay, int tick)
    {
        this.spawn(world, film, replay, tick, Vec3d.ZERO, false);
    }

    /** Spawn this crowd directly on the server around an offset from its replay position. */
    public void spawn(ServerWorld world, Film film, Replay replay, int tick, Vec3d offset)
    {
        this.spawn(world, film, replay, tick, offset, false);
    }

    /** Spawn directly with an optional hard X/Y/Z boundary around the replay position. */
    public List<LivingEntity> spawn(ServerWorld world, Film film, Replay replay, int tick, Vec3d offset, boolean constrainToBox)
    {
        if (world == null || film == null || replay == null)
        {
            return List.of();
        }

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
                return List.of();
            }

            if (type == null)
            {
                return List.of();
            }
        }
        else if (this.actorForm.get() == null)
        {
            return List.of();
        }

        Vec3d center = CrowdUtils.replayPosition(replay, tick).add(offset == null ? Vec3d.ZERO : offset);
        CrowdFormation formation = CrowdFormation.get(this.formation.get());
        int count = this.count.get();
        int liveCount = Math.min(count, Math.min(MAX_LIVE_MEMBERS, this.liveLimit.get()));
        double spacing = this.spacing.get();
        List<Link> textures = this.randomTextures.get()
            ? this.collectTextures(this.randomTextureFolder.get(), this.recursiveTextures.get())
            : List.of();
        byte[] sharedActorFormData = useActor && textures.isEmpty()
            ? DataStorageUtils.writeToBytes(FormUtils.toData(this.actorForm.get()))
            : null;
        Map<Long, Double> surfaceCache = this.spawnOnBlock.get() ? new HashMap<>() : null;
        Set<Long> occupiedColumns = this.snapToBlock.get() ? new HashSet<>() : null;
        EntityData entityData = null;
        List<LivingEntity> spawnedEntities = new ArrayList<>(liveCount);

        for (int i = 0; i < liveCount; i++)
        {
            /* Spread the bounded live tier evenly across the WHOLE logical formation instead of
             * taking the first liveCount indices. For area-uniform formations (circle, hollow
             * circle, grid, square) the low indices all fall near the centre/inner edge, so the
             * unspread mapping made the real crowd bunch into a dense inner ring. Sampling one in
             * every count/liveCount slots keeps the live members deterministic and uniformly
             * distributed over the entire area, matching the visual LOD tier with no seam. */
            int fi = liveFormationIndex(i, liveCount, count);
            Vec3d primaryOffset = constrainToBox
                ? CrowdUtils.formationPointInBox(formation, fi, count, spacing,
                    this.boxX.get(), this.boxY.get(), this.boxZ.get(), this.hollow.get())
                : this.getOffset(formation, fi, count, spacing, 0);
            Vec3d primaryPoint = center.add(primaryOffset);

            /* Avoid allocating/finalizing an entity (and copying a potentially large custom form)
             * when its logical slot is outside the server's loaded simulation area. */
            if (!world.isChunkLoaded(BlockPos.ofFloored(primaryPoint)))
            {
                continue;
            }

            LivingEntity entity;

            if (useActor)
            {
                Form form = this.createActorForm(textures, this.seededIndex(fi));

                if (form == null)
                {
                    continue;
                }

                ActorEntity actorEntity = new ActorEntity(BBSMod.ACTOR_ENTITY, world);

                actorEntity.setForm(form);
                // 1.20.1 actors do not expose the newer crowd ownership metadata.
                // Actor form synchronization still happens through setForm().
                actorEntity.setNoGravity(true);
                actorEntity.setCrowdControlled(true);
                actorEntity.setInvulnerable(false);
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

            /* Preserve the exact logical formation slots for the live simulation tier. All terrain
             * access below is loaded-chunk-only, so a million-member footprint can no longer force
             * distant synchronous chunk generation or trip the server watchdog. */
            Vec3d spawn = this.findSpawnPoint(world, entity, center, formation, fi, count, spacing, surfaceCache, occupiedColumns, constrainToBox);

            if (spawn == null)
            {
                entity.discard();

                continue;
            }

            float yaw = this.spawnYaw(formation, fi, count);

            entity.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, yaw, 0F);
            entity.setHeadYaw(yaw);
            entity.setBodyYaw(yaw);
            entity.setSprinting(false);
            entity.setSneaking(false);
            if (entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null)
            {
                entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(Math.max(1F, this.health.get()));
            }
            entity.setHealth(Math.max(0F, Math.min(this.health.get(), entity.getMaxHealth())));
            this.applyRandomArmor(entity, this.seededIndex(fi));
            CrowdUtils.tag(entity, film, tag);

            if (world.spawnEntity(entity))
            {
                spawnedEntities.add(entity);
            }
            else
            {
                entity.discard();
            }
        }

        /* startSeenByPlayer sends each actor form exactly once when vanilla begins tracking it.
         * The old extra queued pass duplicated every form packet for every player. */
        return spawnedEntities;
    }

    private void applyRandomArmor(LivingEntity entity, int index)
    {
        if (!this.randomArmor.get() || entity == null)
        {
            return;
        }

        this.applyRandomArmorSlot(entity, EquipmentSlot.HEAD, this.randomArmorHead.get(), this.randomArmorHeadMaterials.get(), index, ARMOR_SALT_HEAD);
        this.applyRandomArmorSlot(entity, EquipmentSlot.CHEST, this.randomArmorChest.get(), this.randomArmorChestMaterials.get(), index, ARMOR_SALT_CHEST);
        this.applyRandomArmorSlot(entity, EquipmentSlot.LEGS, this.randomArmorLegs.get(), this.randomArmorLegsMaterials.get(), index, ARMOR_SALT_LEGS);
        this.applyRandomArmorSlot(entity, EquipmentSlot.FEET, this.randomArmorFeet.get(), this.randomArmorFeetMaterials.get(), index, ARMOR_SALT_FEET);
    }

    private void applyRandomArmorSlot(LivingEntity entity, EquipmentSlot slot, boolean enabled, int materials, int index, int salt)
    {
        if (!enabled)
        {
            return;
        }

        ItemStack stack = createRandomArmorStack(slot, index, this.randomArmorCoverage.get(), materials, this.randomArmorRichness.get(), salt);

        if (!stack.isEmpty())
        {
            entity.equipStack(slot, stack);
        }
    }

    public static ItemStack createRandomArmorStack(EquipmentSlot slot, int index, float coverage, float richness, int salt)
    {
        return createRandomArmorStack(slot, index, coverage, ARMOR_ALL, richness, salt);
    }

    /** Creates deterministic armour from exactly the materials selected for this slot. */
    public static ItemStack createRandomArmorStack(EquipmentSlot slot, int index, float coverage, int materialMask, float richness, int salt)
    {
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET)
        {
            return ItemStack.EMPTY;
        }

        if (unitHash(index, salt) > MathHelper.clamp(coverage, 0F, 1F))
        {
            return ItemStack.EMPTY;
        }

        String suffix = switch (slot)
        {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> "";
        };
        String[] materials = new String[] {"leather", "chainmail", "iron", "golden", "diamond", "netherite"};
        int[] selected = new int[materials.length];
        int selectedCount = 0;

        for (int i = 0; i < materials.length; i++)
        {
            if ((materialMask & (1 << i)) != 0)
            {
                selected[selectedCount++] = i;
            }
        }

        if (selectedCount == 0)
        {
            return ItemStack.EMPTY;
        }

        int materialIndex;

        if (materialMask == ARMOR_ALL)
        {
            double noise = unitHash(index, salt ^ 0x7f4a7c15) - 0.5D;
            double score = MathHelper.clamp(richness + noise * 0.75D, 0D, 1D);
            materialIndex = MathHelper.clamp((int) Math.round(score * (materials.length - 1)), 0, materials.length - 1);
        }
        else
        {
            materialIndex = selected[Math.min(selectedCount - 1, (int) (unitHash(index, salt ^ 0x7f4a7c15) * selectedCount))];
        }
        Identifier id = new Identifier("minecraft:" + materials[materialIndex] + "_" + suffix);
        var item = Registries.ITEM.get(id);

        if (item == null || item == Items.AIR)
        {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private static double unitHash(int index, int salt)
    {
        int value = index * 0x9e3779b9 + salt;

        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;

        return (value & 0x7fffffff) / (double) 0x7fffffff;
    }

    private Form createActorForm(List<Link> textures, int index)
    {
        Form form = FormUtils.copy(this.actorForm.get());

        if (form == null)
        {
            return null;
        }

        if (this.textureOverride.get() != null)
        {
            BaseValue property = FormUtils.getProperty(form, "texture");

            if (property instanceof ValueLink valueLink)
            {
                valueLink.set(this.textureOverride.get());
            }
        }
        else if (!textures.isEmpty())
        {
            BaseValue property = FormUtils.getProperty(form, "texture");

            if (property instanceof ValueLink valueLink)
            {
                valueLink.set(textures.get(Math.floorMod(index * 31 + form.hashCode(), textures.size())));
            }
        }

        return form;
    }

    /** Shared, fault-tolerant PNG folder scan for actor and visual crowd tiers. */
    public static List<Link> collectTextures(Link folder)
    {
        return collectTextures(folder, false);
    }

    public static List<Link> collectTextures(Link folder, boolean recursive)
    {
        List<Link> textures = new ArrayList<>();

        if (folder == null || folder.source.isEmpty())
        {
            return textures;
        }

        try
        {
            for (Link link : BBSMod.getProvider().getLinksFromPath(folder, recursive))
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

    private Vec3d findSpawnPoint(ServerWorld world, LivingEntity entity, Vec3d center, CrowdFormation formation, int index, int count, double spacing, Map<Long, Double> surfaceCache, Set<Long> occupiedColumns, boolean constrainToBox)
    {
        /* A bounded crowd must never be displaced by collision retries or ground snapping.
         * Crowd actors do not use gravity, so this exact point remains inside the box. */
        if (constrainToBox)
        {
            Vec3d point = center.add(CrowdUtils.formationPointInBox(formation, index, count, spacing, this.boxX.get(), this.boxY.get(), this.boxZ.get(), this.hollow.get()));

            if (this.snapToBlock.get())
            {
                point = new Vec3d(Math.floor(point.x) + 0.5D, Math.floor(point.y), Math.floor(point.z) + 0.5D);
                long column = columnKey(MathHelper.floor(point.x), MathHelper.floor(point.z));

                if (occupiedColumns != null && !occupiedColumns.add(column))
                {
                    return null;
                }
            }

            return world.isChunkLoaded(BlockPos.ofFloored(point)) ? point : null;
        }

        Vec3d fallback = null;
        boolean skipUnsafe = this.skipUnsafe.get();

        for (int attempt = 0; attempt < 16; attempt++)
        {
            Vec3d offset = this.getOffset(formation, index, count, spacing, attempt);
            double x = center.x + offset.x;
            double z = center.z + offset.z;

            if (this.snapToBlock.get())
            {
                x = Math.floor(x) + 0.5D;
                z = Math.floor(z) + 0.5D;
            }

            long column = columnKey(MathHelper.floor(x), MathHelper.floor(z));

            if (occupiedColumns != null && occupiedColumns.contains(column))
            {
                continue;
            }

            double sampledY = center.y + offset.y;
            Double y;

            /* Crowd playback must never synchronously load/generate terrain. If a formation reaches
             * outside the server's already-loaded area, leave that member to the visual LOD tier. */
            if (!world.isChunkLoaded(BlockPos.ofFloored(x, center.y, z)))
            {
                continue;
            }

            /* Box formations deliberately use the vertical range of their spawn volume.
             * Other formations retain their ground-snapping behaviour. */
            if (this.spawnOnBlock.get() && formation != CrowdFormation.BOX && formation != CrowdFormation.BOX_OUTLINE)
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
                if (occupiedColumns != null)
                {
                    occupiedColumns.add(column);
                }

                return candidate;
            }
        }

        return skipUnsafe ? null : fallback;
    }

    private Vec3d getOffset(CrowdFormation formation, int index, int count, double spacing, int attempt)
    {
        if (formation == CrowdFormation.CIRCLE && this.radius.get() > 0F)
        {
            Vec3d point = CrowdUtils.circlePoint(index, count, this.radius.get(), spacing, this.hollow.get());

            if (attempt == 0)
            {
                return point;
            }

            double angle = attempt * Math.PI / 24D;
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);

            return new Vec3d(point.x * cosine - point.z * sine, point.y, point.x * sine + point.z * cosine);
        }

        int sampleIndex = index + attempt * Math.max(1, count) * 9973;
        Vec3d offset = attempt == 0
            ? this.authoredOffset(formation, index, count, spacing)
            : CrowdUtils.formationPoint(formation, sampleIndex, count, spacing, this.boxX.get(), this.boxY.get(), this.boxZ.get(), this.hollow.get());

        if (formation == CrowdFormation.BOX || formation == CrowdFormation.BOX_OUTLINE
            || formation == CrowdFormation.HOLLOW_CIRCLE || attempt == 0)
        {
            return offset;
        }

        Vec3d nudge = CrowdUtils.formationPoint(CrowdFormation.CIRCLE, attempt - 1, 15, Math.max(0.6D, spacing * 0.35D));

        return offset.add(nudge);
    }

    private Vec3d authoredOffset(CrowdFormation formation, int index, int count, double spacing)
    {
        double radius = this.radius.get();

        if (radius <= 0D || formation == CrowdFormation.LINE || formation == CrowdFormation.GRID)
        {
            return CrowdUtils.formationPoint(formation, index, count, spacing, this.boxX.get(), this.boxY.get(), this.boxZ.get(), this.hollow.get());
        }

        if (formation == CrowdFormation.CIRCLE_OUTLINE || formation == CrowdFormation.HOLLOW_CIRCLE)
        {
            double angle = Math.PI * 2D * index / Math.max(1, count);

            return new Vec3d(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
        }

        if (formation == CrowdFormation.CIRCLE)
        {
            return CrowdUtils.circlePoint(index, count, radius, spacing, this.hollow.get());
        }

        if (formation == CrowdFormation.SQUARE_OUTLINE)
        {
            return CrowdUtils.formationPoint(formation, index, count, radius * 4D / Math.max(1, count));
        }

        if (formation == CrowdFormation.SQUARE)
        {
            int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
            int rows = Math.max(1, (int) Math.ceil(count / (double) columns));
            double x = columns == 1 ? 0D : -radius + (index % columns) * radius * 2D / (columns - 1);
            double z = rows == 1 ? 0D : -radius + (index / columns) * radius * 2D / (rows - 1);

            return new Vec3d(x, 0D, z);
        }

        return CrowdUtils.formationPoint(formation, index, count, spacing, this.boxX.get(), this.boxY.get(), this.boxZ.get(), this.hollow.get());
    }

    private float spawnYaw(CrowdFormation formation, int index, int count)
    {
        if (!this.randomYaw.get())
        {
            return 0F;
        }

        if (this.radius.get() <= 0F && this.yawVariation.get() <= 0F && this.seed.get() == 0)
        {
            return (float) ((index * 137.507764D) % 360D);
        }

        float yaw = (float) ((this.seededUnitHash(index, 11) - 0.5D) * this.yawVariation.get());

        if (formation == CrowdFormation.CIRCLE_OUTLINE || formation == CrowdFormation.HOLLOW_CIRCLE)
        {
            double angle = Math.PI * 2D * index / Math.max(1, count);

            yaw += (float) Math.toDegrees(-angle) + 90F;
        }

        return yaw;
    }

    private int seededIndex(int index)
    {
        return index ^ Integer.rotateLeft(this.seed.get() * 0x9e3779b9, 13);
    }

    private double seededUnitHash(int index, int salt)
    {
        int value = this.seededIndex(index) * 0x9e3779b9 + salt;

        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;

        return (value & 0x7fffffff) / (double) 0x7fffffff;
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

                    if (!world.isChunkLoaded(pos))
                    {
                        return false;
                    }

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

    private Double findSurfaceY(ServerWorld world, Vec3d center, int bx, int bz)
    {
        if (!world.isChunkLoaded(new BlockPos(bx, MathHelper.floor(center.y), bz)))
        {
            return null;
        }

        int feetY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);

        /* boxY is the actual crowd-volume height. Surface lookup still needs a generous range,
         * because replays may be authored above or below the terrain they are previewed on. */
        double verticalRange = Math.max(256D, this.boxY.get());

        if (feetY < center.y - verticalRange || feetY > center.y + verticalRange)
        {
            return null;
        }

        BlockPos feet = new BlockPos(bx, feetY, bz);
        BlockPos below = feet.down();
        BlockState belowState = world.getBlockState(below);

        if (belowState.isSideSolidFullSquare(world, below, Direction.UP) && world.getBlockState(feet).isAir() && world.getBlockState(feet.up()).isAir())
        {
            return (double) feetY;
        }

        return null;
    }

    private static long columnKey(int x, int z)
    {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Maps a live-tier slot (0..liveCount-1) to a logical formation index spread evenly across the
     * full crowd (0..count-1). Because every filled formation orders its members by increasing area,
     * an even stride over the index range yields an even spatial distribution over the whole area,
     * so the bounded live crowd covers the entire formation instead of a dense inner core/ring.
     */
    public static int liveFormationIndex(int slot, int liveCount, int count)
    {
        if (liveCount <= 0 || count <= liveCount)
        {
            return Math.min(slot, Math.max(0, count - 1));
        }

        long index = ((2L * slot + 1L) * count) / (2L * liveCount);

        return (int) Math.min((long) count - 1L, Math.max(0L, index));
    }

    @Override
    protected Clip create()
    {
        return new CrowdSpawnActionClip();
    }
}
