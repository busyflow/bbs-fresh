package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorMode;
import mchorse.bbs_mod.actions.types.crowd.CrowdSpawnActionClip;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.crowd.CrowdMemberSource;
import mchorse.bbs_mod.forms.forms.crowd.CrowdMemberSources;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * A crowd of real, spawned actors driven entirely from one form.
 *
 * <p>Everything the separate crowd spawn and crowd behaviour action clips used to author is
 * exposed here, so a crowd is placed, shaped, dressed and given a behaviour in a single
 * editor rather than by hand-wiring two clips in the action timeline. Movement, jumping and
 * texture changes are additionally driven by the replay's crowd keyframe channels, which
 * override the behaviour whenever they carry keyframes.</p>
 */
public class CrowdForm extends Form
{
    public static final int CURRENT_SCHEMA = 6;
    public static final float MAX_RADIUS = CrowdSpawnActionClip.MAX_RADIUS;
    /**
     * Members are real living entities with collision, damage and per-tick steering. Past a
     * couple of thousand a server tick cannot keep up, so this is a deliberate ceiling rather
     * than the million-member cap the old fake-geometry crowd used.
     */
    public static final int MAX_MEMBERS = 2_000;
    /**
     * Members per square block at density 100. Four puts neighbours half a block apart,
     * which is tighter than a villager is wide, so the ground behind them is covered.
     */
    public static final float PACKED_MEMBERS_PER_BLOCK = 4F;
    public static final float MAX_DENSITY = 100F;

    public final ValueInt crowdSchema = new ValueInt("crowd_schema", CURRENT_SCHEMA);
    public final ValueForm memberForm = new ValueForm("member_form");
    public final CrowdMemberSources sources = new CrowdMemberSources("sources");
    /**
     * Name this crowd answers to. Another crowd form points its Enemy group at this name to
     * fight it. Blank means the crowd gets a private, automatically derived tag.
     */
    public final ValueString groupName = new ValueString("group_name", "");
    public final ValueInt count = new ValueInt("count", 20, 1, MAX_MEMBERS);
    public final ValueInt formation = new ValueInt("formation", CrowdFormation.GRID.ordinal(), 0, CrowdFormation.values().length - 1);
    public final ValueBoolean perBlock = new ValueBoolean("per_block", false);
    /**
     * How tightly the authored footprint is filled, 0 to 100. Above zero the crowd's Count
     * (and, for spacing-driven shapes, its Spacing) is derived from the radius instead of
     * typed in. Zero means Count is authored by hand.
     */
    public final ValueFloat density = new ValueFloat("density", 0F, 0F, MAX_DENSITY);
    public final ValueFloat spacing = new ValueFloat("spacing", 1.5F, 0.1F, 64F);
    public final ValueFloat radius = new ValueFloat("radius", 4F, 0.1F, MAX_RADIUS);
    public final ValueInt seed = new ValueInt("seed", 0);
    public final ValueFloat variation = new ValueFloat("variation", 0F, 0F, 180F);
    public final ValueLink textureFolder = new ValueLink("texture_folder", null);
    public final ValueBoolean recursiveTextures = new ValueBoolean("recursive_textures", false);
    public final ValueInt textureRevision = new ValueInt("texture_revision", 0);
    public final ValueLink textureOverride = new ValueLink("texture_override", null);

    public final ValueFloat health = new ValueFloat("health", 20F, 0F, 1024F);
    public final ValueBoolean randomArmor = new ValueBoolean("random_armor", false);
    public final ValueFloat randomArmorCoverage = new ValueFloat("random_armor_coverage", 0.55F, 0F, 1F);
    public final ValueFloat randomArmorRichness = new ValueFloat("random_armor_richness", 0.45F, 0F, 1F);
    public final ValueBoolean randomArmorHead = new ValueBoolean("random_armor_head", true);
    public final ValueBoolean randomArmorChest = new ValueBoolean("random_armor_chest", true);
    public final ValueBoolean randomArmorLegs = new ValueBoolean("random_armor_legs", true);
    public final ValueBoolean randomArmorFeet = new ValueBoolean("random_armor_feet", true);
    public final ValueInt randomArmorHeadMaterials = new ValueInt("random_armor_head_materials", CrowdSpawnActionClip.ARMOR_ALL, 0, CrowdSpawnActionClip.ARMOR_ALL);
    public final ValueInt randomArmorChestMaterials = new ValueInt("random_armor_chest_materials", CrowdSpawnActionClip.ARMOR_ALL, 0, CrowdSpawnActionClip.ARMOR_ALL);
    public final ValueInt randomArmorLegsMaterials = new ValueInt("random_armor_legs_materials", CrowdSpawnActionClip.ARMOR_ALL, 0, CrowdSpawnActionClip.ARMOR_ALL);
    public final ValueInt randomArmorFeetMaterials = new ValueInt("random_armor_feet_materials", CrowdSpawnActionClip.ARMOR_ALL, 0, CrowdSpawnActionClip.ARMOR_ALL);
    public final ValueBoolean randomTextures = new ValueBoolean("random_textures", false);
    public final ValueLink randomTextureFolder = new ValueLink("random_texture_folder", null);
    public final ValueFloat hollow = new ValueFloat("hollow", 0F, 0F, 0.95F);
    public final ValueBoolean useVolume = new ValueBoolean("use_volume", false);
    public final ValueTransform volume = new ValueTransform("volume", createVolume());

    public final ValueBoolean behaviorEnabled = new ValueBoolean("behavior_enabled", false);
    public final ValueInt behaviorTarget = new ValueInt("behavior_target", CrowdBehaviorActionClip.TARGET_NONE);
    public final ValueInt behaviorMode = new ValueInt("behavior_mode", CrowdBehaviorMode.WANDER.ordinal(), 0, CrowdBehaviorMode.values().length - 1);
    public final ValueBoolean behaviorPause = new ValueBoolean("behavior_pause", false);
    public final ValueFloat behaviorSpeed = new ValueFloat("behavior_speed", 1F, 0F, 8F);
    public final ValueBoolean behaviorSprint = new ValueBoolean("behavior_sprint", false);
    public final ValueInt behaviorMoveEase = new ValueInt("behavior_move_ease", 10, 0, 200);
    public final ValueFloat behaviorStopDistance = new ValueFloat("behavior_stop_distance", 1.5F, 0F, 32F);
    public final ValueFloat behaviorTargetSpread = new ValueFloat("behavior_target_spread", 2F, 0F, 64F);
    public final ValueFloat behaviorDisperseRadius = new ValueFloat("behavior_disperse_radius", 12F, 0F, 128F);
    public final ValueInt behaviorWanderInterval = new ValueInt("behavior_wander_interval", 80, 20, 400);
    public final ValueInt behaviorLookAroundTicks = new ValueInt("behavior_look_around_ticks", 25, 0, 200);
    public final ValueFloat behaviorAreaX = new ValueFloat("behavior_area_x", 12F, 0.1F, 256F);
    public final ValueFloat behaviorAreaY = new ValueFloat("behavior_area_y", 2F, 0F, 128F);
    public final ValueFloat behaviorAreaZ = new ValueFloat("behavior_area_z", 12F, 0.1F, 256F);
    public final ValueFloat behaviorSeparation = new ValueFloat("behavior_separation", 0.85F, 0F, 6F);
    public final ValueFloat behaviorMaxStepHeight = new ValueFloat("behavior_max_step_height", 0.55F, 0F, 0.75F);
    public final ValueBoolean behaviorCrouch = new ValueBoolean("behavior_crouch", false);
    public final ValueBoolean behaviorZigZag = new ValueBoolean("behavior_zig_zag", false);
    public final ValueBoolean behaviorRandomJump = new ValueBoolean("behavior_random_jump", false);
    public final ValueFloat behaviorJumpRate = new ValueFloat("behavior_jump_rate", 0.25F, 0F, 10F);
    public final ValueBoolean behaviorArmSwing = new ValueBoolean("behavior_arm_swing", false);
    public final ValueFloat behaviorArmSwingRate = new ValueFloat("behavior_arm_swing_rate", 1F, 0F, 20F);
    public final ValueBoolean behaviorHeadMotion = new ValueBoolean("behavior_head_motion", true);
    public final ValueFloat behaviorEnergy = new ValueFloat("behavior_energy", 1F, 0F, 2F);
    public final ValueBoolean behaviorLookAtTarget = new ValueBoolean("behavior_look_at_target", true);
    public final ValueInt behaviorLookEase = new ValueInt("behavior_look_ease", 10, 0, 200);
    public final ValueFloat behaviorHeadYawLimit = new ValueFloat("behavior_head_yaw_limit", 70F, 0F, 120F);
    public final ValueBoolean behaviorLookBodyYaw = new ValueBoolean("behavior_look_body_yaw", false);
    public final ValueBoolean behaviorLookHeadYaw = new ValueBoolean("behavior_look_head_yaw", true);
    public final ValueBoolean behaviorLookHeadPitch = new ValueBoolean("behavior_look_head_pitch", true);

    public final ValueString behaviorEnemyGroup = new ValueString("behavior_enemy_group", "");
    public final ValueFloat behaviorFightDamage = new ValueFloat("behavior_fight_damage", 2F, 0F, 1024F);
    public final ValueFloat behaviorAttackRate = new ValueFloat("behavior_attack_rate", 0.8F, 0F, 20F);
    public final ValueFloat behaviorEngagementDistance = new ValueFloat("behavior_engagement_distance", 1.6F, 0.25F, 16F);
    public final ValueFloat behaviorFightRadius = new ValueFloat("behavior_fight_radius", 24F, 1F, 256F);
    public final ValueInt behaviorRetargetTicks = new ValueInt("behavior_retarget_ticks", 30, 1, 400);
    public final ValueFloat behaviorFightRandomness = new ValueFloat("behavior_fight_randomness", 1F, 0F, 8F);

    public final ValueBoolean behaviorShoot = new ValueBoolean("behavior_shoot", false);
    public final ValueFloat behaviorShootRate = new ValueFloat("behavior_shoot_rate", 0.5F, 0F, 20F);
    public final ValueForm behaviorProjectileModel = new ValueForm("behavior_projectile_model");
    public final ValueFloat behaviorProjectileSpeed = new ValueFloat("behavior_projectile_speed", 1.6F, 0F, 16F);
    public final ValueInt behaviorProjectileLifeSpan = new ValueInt("behavior_projectile_life_span", 100, 1, 1200);
    public final ValueForm behaviorImpactModel = new ValueForm("behavior_impact_model");
    public final ValueInt behaviorImpactBounces = new ValueInt("behavior_impact_bounces", 0, 0, 64);
    public final ValueFloat behaviorImpactBounceDamping = new ValueFloat("behavior_impact_bounce_damping", 0.5F, 0F, 1F);
    public final ValueBoolean behaviorImpactVanish = new ValueBoolean("behavior_impact_vanish", true);
    public final ValueFloat behaviorImpactDamage = new ValueFloat("behavior_impact_damage", 0F, 0F, 1024F);
    public final ValueFloat behaviorImpactKnockback = new ValueFloat("behavior_impact_knockback", 0F, 0F, 64F);
    public final ValueBoolean behaviorImpactCollideBlocks = new ValueBoolean("behavior_impact_collide_blocks", true);
    public final ValueBoolean behaviorImpactCollideEntities = new ValueBoolean("behavior_impact_collide_entities", true);

    public CrowdForm()
    {
        for (mchorse.bbs_mod.settings.values.base.BaseValue value : new mchorse.bbs_mod.settings.values.base.BaseValue[] {
            this.crowdSchema, this.memberForm, this.sources, this.groupName, this.count, this.formation,
            this.perBlock, this.density, this.spacing, this.radius, this.seed, this.variation,
            this.textureFolder, this.recursiveTextures, this.textureRevision, this.textureOverride,
            this.health, this.randomArmor, this.randomArmorCoverage, this.randomArmorRichness,
            this.randomArmorHead, this.randomArmorChest, this.randomArmorLegs, this.randomArmorFeet,
            this.randomArmorHeadMaterials, this.randomArmorChestMaterials, this.randomArmorLegsMaterials,
            this.randomArmorFeetMaterials, this.randomTextures, this.randomTextureFolder, this.hollow,
            this.useVolume, this.volume,
            this.behaviorEnabled, this.behaviorTarget, this.behaviorMode, this.behaviorPause,
            this.behaviorSpeed, this.behaviorSprint, this.behaviorMoveEase, this.behaviorStopDistance,
            this.behaviorTargetSpread, this.behaviorDisperseRadius, this.behaviorWanderInterval,
            this.behaviorLookAroundTicks, this.behaviorAreaX, this.behaviorAreaY, this.behaviorAreaZ,
            this.behaviorSeparation, this.behaviorMaxStepHeight, this.behaviorCrouch, this.behaviorZigZag,
            this.behaviorRandomJump, this.behaviorJumpRate, this.behaviorArmSwing, this.behaviorArmSwingRate,
            this.behaviorHeadMotion, this.behaviorEnergy, this.behaviorLookAtTarget, this.behaviorLookEase,
            this.behaviorHeadYawLimit, this.behaviorLookBodyYaw, this.behaviorLookHeadYaw,
            this.behaviorLookHeadPitch, this.behaviorEnemyGroup, this.behaviorFightDamage,
            this.behaviorAttackRate, this.behaviorEngagementDistance, this.behaviorFightRadius,
            this.behaviorRetargetTicks, this.behaviorFightRandomness, this.behaviorShoot,
            this.behaviorShootRate, this.behaviorProjectileModel,
            this.behaviorProjectileSpeed, this.behaviorProjectileLifeSpan, this.behaviorImpactModel,
            this.behaviorImpactBounces, this.behaviorImpactBounceDamping, this.behaviorImpactVanish,
            this.behaviorImpactDamage, this.behaviorImpactKnockback, this.behaviorImpactCollideBlocks,
            this.behaviorImpactCollideEntities
        })
        {
            /* Crowd setup is authored in the form editor, never scrubbed on the timeline:
             * a mid-take respawn would rebuild every actor. The keyframeable parts of a
             * crowd are its walk, jumps and textures, which live on the replay instead. */
            value.invisible();
            this.add(value);
        }
    }

    public Form getMemberForm()
    {
        if (this.memberForm.get() != null)
        {
            return this.memberForm.get();
        }

        CrowdMemberSource source = this.sources.getSource(0);

        return source == null ? null : source.getForm();
    }

    @Override
    protected String getDefaultDisplayName()
    {
        Form form = this.getMemberForm();

        return form == null ? "Crowd" : "Crowd: " + form.getDisplayName();
    }

    /**
     * Ground area the authored shape covers, in square blocks. Density multiplies this to
     * get a member count, so the radius stays exactly as authored while population changes.
     */
    public double getFootprintArea()
    {
        double radius = Math.max(0.1F, this.radius.get());
        double hole = Math.max(0F, Math.min(0.95F, this.hollow.get()));
        double side = Math.max(radius, Math.max(0.1F, this.spacing.get()));

        return switch (CrowdFormation.get(this.formation.get()))
        {
            case CIRCLE -> Math.PI * radius * radius * Math.max(0.01D, 1D - hole * hole);
            case CIRCLE_OUTLINE, HOLLOW_CIRCLE -> Math.PI * 2D * radius;
            case LINE -> radius * 2D;
            case SQUARE_OUTLINE, BOX_OUTLINE -> side * 8D;
            case SQUARE, BOX -> side * side * 4D;
            default -> Math.PI * radius * radius;
        };
    }

    /**
     * Recompute Count (and, for shapes laid out by spacing rather than radius, Spacing) from
     * the density slider. A no-op at density 0, where Count is authored by hand.
     */
    public void applyDensity()
    {
        float density = this.density.get();

        if (density <= 0F)
        {
            return;
        }

        double perBlock = density / MAX_DENSITY * PACKED_MEMBERS_PER_BLOCK;
        CrowdFormation formation = CrowdFormation.get(this.formation.get());

        if (formation == CrowdFormation.GRID || formation == CrowdFormation.LINE)
        {
            /* These place members by spacing, so density has to tighten the step or the
             * shape would grow past the authored radius instead of packing inside it. */
            this.spacing.set((float) Math.max(0.1D, 1D / Math.sqrt(Math.max(1.0E-4D, perBlock))));
        }

        int count = (int) Math.round(this.getFootprintArea() * perBlock);

        this.count.set(Math.max(1, Math.min(MAX_MEMBERS, count)));
    }

    public long getStableMemberId(int index)
    {
        long value = ((long) this.seed.get() << 32) ^ Integer.toUnsignedLong(index);

        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;

        return value ^ value >>> 31;
    }

    public int getStableTextureIndex(int index, int textureCount)
    {
        if (textureCount <= 0)
        {
            return -1;
        }

        long value = this.getStableMemberId(index) ^ 0xd1b54a32d192ed03L;

        value ^= value >>> 29;
        value *= 0x94d049bb133111ebL;
        value ^= value >>> 31;

        return (int) Long.remainderUnsigned(value, textureCount);
    }

    public void validateCrowd()
    {
        this.crowdSchema.set(CURRENT_SCHEMA);
        this.count.set(Math.max(1, Math.min(MAX_MEMBERS, this.count.get())));
        this.formation.set(Math.max(0, Math.min(CrowdFormation.values().length - 1, this.formation.get())));

        if (this.formation.get() == CrowdFormation.HOLLOW_CIRCLE.ordinal())
        {
            this.formation.set(CrowdFormation.CIRCLE.ordinal());
        }

        this.spacing.set(Math.max(0.1F, this.spacing.get()));
        this.radius.set(Math.max(0.1F, Math.min(MAX_RADIUS, this.radius.get())));
        this.health.set(Math.max(0F, Math.min(1024F, this.health.get())));
        this.hollow.set(Math.max(0F, Math.min(0.95F, this.hollow.get())));
        this.density.set(Math.max(0F, Math.min(MAX_DENSITY, this.density.get())));
        this.applyDensity();
        this.sources.validate();

        if (this.memberForm.get() == null)
        {
            CrowdMemberSource source = this.sources.getSource(0);

            if (source != null && !(source.getForm() instanceof CrowdForm))
            {
                this.memberForm.set(source.getForm());
            }
        }

        if (this.randomTextureFolder.get() == null && this.textureFolder.get() != null)
        {
            this.randomTextureFolder.set(this.textureFolder.get());
            this.randomTextures.set(true);
        }
    }

    @Override
    public void fromData(BaseType data)
    {
        int schema = data instanceof MapType map ? map.getInt("crowd_schema", 0) : 0;
        int oldFormation = data instanceof MapType map ? map.getInt("formation", 0) : 0;

        super.fromData(data);

        if (schema > 0 && schema < 3)
        {
            this.formation.set(switch (oldFormation)
            {
                case 0 -> CrowdFormation.GRID.ordinal();
                case 1 -> CrowdFormation.CIRCLE.ordinal();
                case 2 -> CrowdFormation.HOLLOW_CIRCLE.ordinal();
                case 3 -> CrowdFormation.LINE.ordinal();
                case 5 -> CrowdFormation.BOX.ordinal();
                case 6 -> CrowdFormation.BOX_OUTLINE.ordinal();
                default -> CrowdFormation.CIRCLE.ordinal();
            });
        }

        if (schema < 4)
        {
            if (this.formation.get() == CrowdFormation.HOLLOW_CIRCLE.ordinal())
            {
                this.formation.set(CrowdFormation.CIRCLE.ordinal());
            }
            else if (this.formation.get() == CrowdFormation.CIRCLE.ordinal())
            {
                /* Circle did not expose the hole control before schema 4. */
                this.hollow.set(0F);
            }
        }

        if (schema > 0 && schema < CURRENT_SCHEMA)
        {
            /* Crowds before schema 6 were drawn as fake instanced geometry and could hold a
             * million members. They are now real spawned actors, so anything authored above
             * the live ceiling is clamped rather than silently spawning a server-killing
             * population. */
            this.count.set(Math.max(1, Math.min(MAX_MEMBERS, this.count.get())));
        }

        this.validateCrowd();
    }

    @Override
    public BaseType toData()
    {
        this.validateCrowd();

        return super.toData();
    }

    private static Transform createVolume()
    {
        Transform volume = new Transform();

        volume.scale.set(8F, 2F, 8F);

        return volume;
    }
}
