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
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * A deterministic crowd that provides a lightweight editor preview and live actor simulation.
 */
public class CrowdForm extends Form
{
    public static final int CURRENT_SCHEMA = 4;
    public static final int MAX_RENDER_BUDGET = 100_000;

    public final ValueInt crowdSchema = new ValueInt("crowd_schema", CURRENT_SCHEMA);
    public final ValueForm memberForm = new ValueForm("member_form");
    public final CrowdMemberSources sources = new CrowdMemberSources("sources");
    public final ValueInt count = new ValueInt("count", 20, 1, CrowdSpawnActionClip.MAX_MEMBERS);
    public final ValueInt formation = new ValueInt("formation", CrowdFormation.GRID.ordinal(), 0, CrowdFormation.values().length - 1);
    public final ValueBoolean perBlock = new ValueBoolean("per_block", false);
    public final ValueFloat spacing = new ValueFloat("spacing", 1.5F, 0.1F, 64F);
    public final ValueFloat radius = new ValueFloat("radius", 4F, 0.1F, 512F);
    public final ValueInt seed = new ValueInt("seed", 0);
    public final ValueFloat variation = new ValueFloat("variation", 0F, 0F, 180F);
    public final ValueInt renderBudget = new ValueInt("render_budget", 4096, 1, MAX_RENDER_BUDGET);
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
    public final ValueFloat behaviorSpeed = new ValueFloat("behavior_speed", 1F, 0F, 8F);
    public final ValueBoolean behaviorSprint = new ValueBoolean("behavior_sprint", false);
    public final ValueFloat behaviorStopDistance = new ValueFloat("behavior_stop_distance", 1.5F, 0F, 32F);
    public final ValueFloat behaviorTargetSpread = new ValueFloat("behavior_target_spread", 2F, 0F, 64F);
    public final ValueInt behaviorWanderInterval = new ValueInt("behavior_wander_interval", 80, 20, 400);
    public final ValueBoolean behaviorRandomJump = new ValueBoolean("behavior_random_jump", false);
    public final ValueFloat behaviorJumpRate = new ValueFloat("behavior_jump_rate", 0.25F, 0F, 10F);
    public final ValueBoolean behaviorLookAtTarget = new ValueBoolean("behavior_look_at_target", true);
    public final ValueFloat behaviorHeadYawLimit = new ValueFloat("behavior_head_yaw_limit", 70F, 0F, 120F);
    public final ValueBoolean behaviorShoot = new ValueBoolean("behavior_shoot", false);
    public final ValueFloat behaviorShootRate = new ValueFloat("behavior_shoot_rate", 0.5F, 0F, 20F);

    public CrowdForm()
    {
        this.crowdSchema.invisible();
        this.sources.invisible();
        this.memberForm.invisible();
        this.count.invisible();
        this.formation.invisible();
        this.perBlock.invisible();
        this.spacing.invisible();
        this.radius.invisible();
        this.seed.invisible();
        this.variation.invisible();
        this.renderBudget.invisible();
        this.textureFolder.invisible();
        this.recursiveTextures.invisible();
        this.textureRevision.invisible();
        this.textureOverride.invisible();
        this.health.invisible();
        this.randomArmor.invisible();
        this.randomArmorCoverage.invisible();
        this.randomArmorRichness.invisible();
        this.randomArmorHead.invisible();
        this.randomArmorChest.invisible();
        this.randomArmorLegs.invisible();
        this.randomArmorFeet.invisible();
        this.randomArmorHeadMaterials.invisible();
        this.randomArmorChestMaterials.invisible();
        this.randomArmorLegsMaterials.invisible();
        this.randomArmorFeetMaterials.invisible();
        this.randomTextures.invisible();
        this.randomTextureFolder.invisible();
        this.hollow.invisible();
        this.useVolume.invisible();
        this.volume.invisible();
        this.behaviorEnabled.invisible();
        this.behaviorTarget.invisible();
        this.behaviorMode.invisible();
        this.behaviorSpeed.invisible();
        this.behaviorSprint.invisible();
        this.behaviorStopDistance.invisible();
        this.behaviorTargetSpread.invisible();
        this.behaviorWanderInterval.invisible();
        this.behaviorRandomJump.invisible();
        this.behaviorJumpRate.invisible();
        this.behaviorLookAtTarget.invisible();
        this.behaviorHeadYawLimit.invisible();
        this.behaviorShoot.invisible();
        this.behaviorShootRate.invisible();

        this.add(this.crowdSchema);
        this.add(this.memberForm);
        this.add(this.sources);
        this.add(this.count);
        this.add(this.formation);
        this.add(this.perBlock);
        this.add(this.spacing);
        this.add(this.radius);
        this.add(this.seed);
        this.add(this.variation);
        this.add(this.renderBudget);
        this.add(this.textureFolder);
        this.add(this.recursiveTextures);
        this.add(this.textureRevision);
        this.add(this.textureOverride);
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
        this.add(this.randomTextures);
        this.add(this.randomTextureFolder);
        this.add(this.hollow);
        this.add(this.useVolume);
        this.add(this.volume);
        this.add(this.behaviorEnabled);
        this.add(this.behaviorTarget);
        this.add(this.behaviorMode);
        this.add(this.behaviorSpeed);
        this.add(this.behaviorSprint);
        this.add(this.behaviorStopDistance);
        this.add(this.behaviorTargetSpread);
        this.add(this.behaviorWanderInterval);
        this.add(this.behaviorRandomJump);
        this.add(this.behaviorJumpRate);
        this.add(this.behaviorLookAtTarget);
        this.add(this.behaviorHeadYawLimit);
        this.add(this.behaviorShoot);
        this.add(this.behaviorShootRate);
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
        this.count.set(Math.max(1, Math.min(CrowdSpawnActionClip.MAX_MEMBERS, this.count.get())));
        this.formation.set(Math.max(0, Math.min(CrowdFormation.values().length - 1, this.formation.get())));

        if (this.formation.get() == CrowdFormation.HOLLOW_CIRCLE.ordinal())
        {
            this.formation.set(CrowdFormation.CIRCLE.ordinal());
        }

        this.spacing.set(Math.max(0.1F, this.spacing.get()));
        this.radius.set(Math.max(0.1F, this.radius.get()));
        this.renderBudget.set(Math.max(1, Math.min(MAX_RENDER_BUDGET, this.renderBudget.get())));
        this.health.set(Math.max(0F, Math.min(1024F, this.health.get())));
        this.hollow.set(Math.max(0F, Math.min(0.95F, this.hollow.get())));
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
