package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorMode;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdSpawnActionClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.crowd.CrowdMemberSource;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Consumer;

public class UIReplayPropertiesPanel extends UIElement
{
    private final UIFilmPanel filmPanel;

    public UIElement properties;
    public UINestedEdit pickEdit;
    public UIToggle enabled;
    public UITextbox label;
    public UITextbox nameTag;
    public UIToggle shadow;
    public UITrackpad shadowSize;
    public UIToggle shadowFollow;
    public UITrackpad shadowOffsetX;
    public UITrackpad shadowOffsetY;
    public UITrackpad shadowOffsetZ;
    public UITrackpad looping;
    public UIToggle actor;
    public UIToggle fp;
    public UIToggle relative;
    public UITrackpad relativeOffsetX;
    public UITrackpad relativeOffsetY;
    public UITrackpad relativeOffsetZ;
    public UIToggle axesPreview;
    public UIButton pickAxesPreviewBone;
    public UISection crowdSection;
    private UISection shadowSection;
    public UINestedEdit crowdMember;
    public UIButton crowdTextureFolder;
    public UIButton crowdClearTextures;
    public UIButton crowdRefreshTextures;
    public UIButton crowdFormation;
    public UIToggle crowdRecursiveTextures;
    public UIToggle crowdPerBlock;
    public UITrackpad crowdCount;
    public UITrackpad crowdDensity;
    public UITrackpad crowdSpacing;
    public UITrackpad crowdRadius;
    public UITrackpad crowdHollow;
    public UIElement crowdHollowField;
    public UITrackpad crowdSeed;
    public UITrackpad crowdVariation;
    public UITextbox crowdGroupName;
    public UITrackpad crowdHealth;
    public UIToggle crowdRandomArmor;
    public UITrackpad crowdArmorCoverage;
    public UIToggle crowdUseVolume;
    public UITrackpad crowdVolumeX;
    public UITrackpad crowdVolumeY;
    public UITrackpad crowdVolumeZ;
    public UIToggle crowdBehaviorEnabled;
    public UIButton crowdBehaviorMode;
    public UITrackpad crowdBehaviorSpeed;
    public UIToggle crowdBehaviorSprint;
    public UIToggle crowdBehaviorJump;
    public UITrackpad crowdBehaviorJumpRate;
    public UIToggle crowdBehaviorShoot;
    public UITrackpad crowdBehaviorShootRate;
    public UIToggle crowdBehaviorPause;
    public UITrackpad crowdBehaviorMoveEase;
    public UITrackpad crowdBehaviorStopDistance;
    public UITrackpad crowdBehaviorSpread;
    public UITrackpad crowdBehaviorDisperseRadius;
    public UITrackpad crowdBehaviorWanderInterval;
    public UITrackpad crowdBehaviorLookAroundTicks;
    public UITrackpad crowdBehaviorAreaX;
    public UITrackpad crowdBehaviorAreaY;
    public UITrackpad crowdBehaviorAreaZ;
    public UITrackpad crowdBehaviorSeparation;
    public UITrackpad crowdBehaviorStepHeight;
    public UIToggle crowdBehaviorCrouch;
    public UIToggle crowdBehaviorZigZag;
    public UIToggle crowdBehaviorArmSwing;
    public UITrackpad crowdBehaviorArmSwingRate;
    public UIToggle crowdBehaviorHeadMotion;
    public UITrackpad crowdBehaviorEnergy;
    public UIToggle crowdBehaviorLookAtTarget;
    public UITrackpad crowdBehaviorLookEase;
    public UITrackpad crowdBehaviorHeadYawLimit;
    public UIToggle crowdBehaviorLookBodyYaw;
    public UIToggle crowdBehaviorLookHeadYaw;
    public UIToggle crowdBehaviorLookHeadPitch;
    public UITextbox crowdBehaviorEnemyGroup;
    public UITrackpad crowdBehaviorFightDamage;
    public UITrackpad crowdBehaviorAttackRate;
    public UITrackpad crowdBehaviorEngagementDistance;
    public UITrackpad crowdBehaviorFightRadius;
    public UITrackpad crowdBehaviorRetargetTicks;
    public UITrackpad crowdBehaviorFightRandomness;
    public UITrackpad crowdBehaviorProjectileSpeed;
    public UITrackpad crowdBehaviorProjectileLifeSpan;
    public UITrackpad crowdBehaviorImpactDamage;
    public UITrackpad crowdBehaviorImpactKnockback;
    public UITrackpad crowdBehaviorImpactBounces;
    public UIToggle crowdBehaviorImpactVanish;
    public UIToggle crowdBehaviorImpactCollideBlocks;
    public UIToggle crowdBehaviorImpactCollideEntities;

    private UIReplayList list;

    public UIReplayPropertiesPanel(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;
        /* This dock owns its blank area too, so clicks cannot reach timelines below it. */
        this.eventPropagataion(EventPropagation.BLOCK_INSIDE);

        this.pickEdit = new UINestedEdit((editing) ->
        {
            if (this.list == null)
            {
                return;
            }

            Replay r = this.list.getSelectedReplayFirst();

            if (r != null)
            {
                this.list.openFormEditor(r.form, editing, (form) ->
                {
                    this.pickEdit.setForm(form);
                    this.updateCrowdControls();
                });
            }
        });
        this.pickEdit.pick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PICK_FORM);
        this.pickEdit.edit.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_EDIT_FORM);
        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            this.edit((replay) -> replay.enabled.set(b.getValue()));
            filmPanel.getController().createEntities();
        });
        this.label = new UITextbox(1000, (s) -> this.edit((replay) -> replay.label.set(s)));
        this.label.textbox.setPlaceholder(UIKeys.FILM_REPLAY_LABEL);
        this.nameTag = new UITextbox(1000, (s) -> this.edit((replay) -> replay.nameTag.set(s)));
        this.nameTag.textbox.setPlaceholder(UIKeys.FILM_REPLAY_NAME_TAG);
        this.shadow = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> this.edit((replay) -> replay.shadow.set(b.getValue())));
        this.shadowSize = new UITrackpad((v) -> this.edit((replay) -> replay.shadowSize.set(v.floatValue())));
        this.shadowSize.tooltip(UIKeys.FILM_REPLAY_SHADOW_SIZE);
        this.shadowFollow = new UIToggle(UIKeys.FILM_REPLAY_SHADOW_FOLLOW, (b) -> this.edit((replay) -> replay.shadowFollow.set(b.getValue())));
        this.shadowFollow.tooltip(UIKeys.FILM_REPLAY_SHADOW_FOLLOW_TOOLTIP);
        this.shadowOffsetX = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().x = v)));
        this.shadowOffsetX.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.shadowOffsetY = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().y = v)));
        this.shadowOffsetY.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.shadowOffsetZ = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().z = v)));
        this.shadowOffsetZ.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.looping = new UITrackpad((v) -> this.edit((replay) -> replay.looping.set(v.intValue())));
        this.looping.limit(0).integer().tooltip(UIKeys.FILM_REPLAY_LOOPING_TOOLTIP);
        this.actor = new UIToggle(UIKeys.FILM_REPLAY_ACTOR, (b) -> this.edit((replay) -> replay.actor.set(b.getValue())));
        this.actor.tooltip(UIKeys.FILM_REPLAY_ACTOR_TOOLTIP);
        this.fp = new UIToggle(UIKeys.FILM_REPLAY_FP, (b) ->
        {
            if (filmPanel.getData() != null)
            {
                for (Replay replay : filmPanel.getData().replays.getList())
                {
                    if (replay.fp.get())
                    {
                        replay.fp.set(false);
                    }
                }
            }

            Replay first = this.list == null ? null : this.list.getSelectedReplayFirst();

            if (first != null)
            {
                first.fp.set(b.getValue());
            }
        });
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, (b) -> this.edit((replay) -> replay.relative.set(b.getValue())));
        this.relative.tooltip(UIKeys.FILM_REPLAY_RELATIVE_TOOLTIP);
        this.relativeOffsetX = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().x = v)));
        this.relativeOffsetY = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().y = v)));
        this.relativeOffsetZ = new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().z = v)));
        this.axesPreview = new UIToggle(UIKeys.FILM_REPLAY_AXES_PREVIEW, (b) -> this.edit((replay) -> replay.axesPreview.set(b.getValue())));
        this.pickAxesPreviewBone = new UIButton(UIKeys.FILM_REPLAY_PICK_AXES_PREVIEW, (b) ->
        {
            Replay replay = filmPanel.replayEditor.getReplay();

            if (replay != null && filmPanel.getData() != null)
            {
                UIAnchorKeyframeFactory.displayAttachments(filmPanel, filmPanel.getData().replays.getList().indexOf(replay), replay.axesPreviewBone.get(), (s) ->
                {
                    this.edit((r) -> r.axesPreviewBone.set(s));
                });
            }
        });
        this.crowdMember = new UINestedEdit(this::openCrowdMember);
        this.crowdTextureFolder = new UIButton(IKey.constant("Choose random texture folder"), (b) -> this.openCrowdTextureFolder());
        this.crowdClearTextures = new UIButton(IKey.constant("Clear textures"), (b) ->
        {
            this.editCrowd((crowd) ->
            {
                crowd.textureFolder.set(null);
                crowd.randomTextureFolder.set(null);
                crowd.randomTextures.set(false);
                crowd.textureRevision.set(crowd.textureRevision.get() + 1);
            });
            this.updateCrowdControls();
        });
        this.crowdRefreshTextures = new UIButton(IKey.constant("Refresh folder"), (b) ->
        {
            this.editCrowd((crowd) -> crowd.textureRevision.set(crowd.textureRevision.get() + 1));
            this.updateCrowdControls();
        });
        this.crowdFormation = new UIButton(IKey.EMPTY, (b) -> this.openCrowdFormationPicker());
        this.crowdRecursiveTextures = new UIToggle(IKey.constant("Include texture subfolders"), (b) ->
        {
            this.editCrowd((crowd) ->
            {
                crowd.recursiveTextures.set(b.getValue());
                crowd.textureRevision.set(crowd.textureRevision.get() + 1);
            });
        });
        this.crowdPerBlock = new UIToggle(IKey.constant("Spawn one member per block"), (b) ->
            this.editCrowd((crowd) -> crowd.perBlock.set(b.getValue())));
        this.crowdCount = new UITrackpad((v) -> this.editCrowd((crowd) ->
        {
            int count = v.intValue();

            /* Typing a Count by hand hands authorship back to the user. */
            crowd.density.set(0F);
            crowd.count.set(count);
        })).limit(1, CrowdForm.MAX_MEMBERS, true);
        this.crowdDensity = new UITrackpad((v) ->
        {
            this.editCrowd((crowd) ->
            {
                crowd.density.set(v.floatValue());
                crowd.applyDensity();
            });
            this.syncCrowdDerived();
        }).limit(0, CrowdForm.MAX_DENSITY).increment(1);
        this.crowdSpacing = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.spacing.set(v.floatValue())))
            .limit(0.1, 32);
        this.crowdRadius = new UITrackpad((v) ->
        {
            this.editCrowd((crowd) ->
            {
                crowd.radius.set(v.floatValue());
                crowd.applyDensity();
            });
            this.syncCrowdDerived();
        }).limit(0.1, CrowdForm.MAX_RADIUS);
        this.crowdHollow = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.hollow.set(v.floatValue())))
            .limit(0, 0.95).increment(0.05).values(0.05, 0.01, 0.1);
        this.crowdHollowField = this.compactCrowdField("Hole", this.crowdHollow);
        this.crowdSeed = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.seed.set(v.intValue()))).integer();
        this.crowdVariation = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.variation.set(v.floatValue())))
            .limit(0, 180);
        this.crowdGroupName = new UITextbox(120, (s) -> this.editCrowd((crowd) -> crowd.groupName.set(s)));
        this.crowdGroupName.tooltip(IKey.constant("Name other crowds use to fight this one. Blank keeps it private."));
        this.crowdHealth = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.health.set(v.floatValue())))
            .limit(0, 1024);
        this.crowdRandomArmor = new UIToggle(IKey.constant("Randomize armor"), (b) ->
            this.editCrowd((crowd) -> crowd.randomArmor.set(b.getValue())));
        this.crowdArmorCoverage = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.randomArmorCoverage.set(v.floatValue())))
            .limit(0, 1);
        this.crowdUseVolume = new UIToggle(IKey.constant("Limit formation to 3D box"), (b) ->
            this.editCrowd((crowd) -> crowd.useVolume.set(b.getValue())));
        this.crowdVolumeX = new UITrackpad((v) -> this.editCrowd((crowd) ->
            BaseValue.edit(crowd.volume, (value) -> value.get().scale.x = Math.max(0.1F, v.floatValue())))).limit(0.1, 512);
        this.crowdVolumeY = new UITrackpad((v) -> this.editCrowd((crowd) ->
            BaseValue.edit(crowd.volume, (value) -> value.get().scale.y = Math.max(0.1F, v.floatValue())))).limit(0.1, 512);
        this.crowdVolumeZ = new UITrackpad((v) -> this.editCrowd((crowd) ->
            BaseValue.edit(crowd.volume, (value) -> value.get().scale.z = Math.max(0.1F, v.floatValue())))).limit(0.1, 512);
        this.crowdBehaviorEnabled = new UIToggle(IKey.constant("Enable autonomous behavior"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorEnabled.set(b.getValue())));
        this.crowdBehaviorMode = new UIButton(IKey.EMPTY, (b) ->
        {
            CrowdForm crowd = this.getSelectedCrowd();

            if (crowd != null)
            {
                int mode = (crowd.behaviorMode.get() + 1) % CrowdBehaviorMode.values().length;
                this.editCrowd((other) -> other.behaviorMode.set(mode));
                this.updateCrowdControls();
            }
        });
        this.crowdBehaviorSpeed = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorSpeed.set(v.floatValue())))
            .limit(0, 8);
        this.crowdBehaviorSprint = new UIToggle(IKey.constant("Sprint with particles"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorSprint.set(b.getValue())));
        this.crowdBehaviorJump = new UIToggle(IKey.constant("Random cheering jumps"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorRandomJump.set(b.getValue())));
        this.crowdBehaviorJumpRate = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorJumpRate.set(v.floatValue())))
            .limit(0, 10);
        this.crowdBehaviorShoot = new UIToggle(IKey.constant("Enable ranged attacks"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorShoot.set(b.getValue())));
        this.crowdBehaviorShootRate = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorShootRate.set(v.floatValue())))
            .limit(0, 20);
        this.crowdBehaviorPause = new UIToggle(IKey.constant("Freeze in place"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorPause.set(b.getValue())));
        this.crowdBehaviorMoveEase = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorMoveEase.set(v.intValue())))
            .limit(0, 200, true);
        this.crowdBehaviorStopDistance = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorStopDistance.set(v.floatValue())))
            .limit(0, 32);
        this.crowdBehaviorSpread = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorTargetSpread.set(v.floatValue())))
            .limit(0, 64);
        this.crowdBehaviorDisperseRadius = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorDisperseRadius.set(v.floatValue())))
            .limit(0, 128);
        this.crowdBehaviorWanderInterval = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorWanderInterval.set(v.intValue())))
            .limit(20, 400, true);
        this.crowdBehaviorLookAroundTicks = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorLookAroundTicks.set(v.intValue())))
            .limit(0, 200, true);
        this.crowdBehaviorAreaX = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorAreaX.set(v.floatValue())))
            .limit(0.1, 256);
        this.crowdBehaviorAreaY = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorAreaY.set(v.floatValue())))
            .limit(0, 128);
        this.crowdBehaviorAreaZ = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorAreaZ.set(v.floatValue())))
            .limit(0.1, 256);
        this.crowdBehaviorSeparation = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorSeparation.set(v.floatValue())))
            .limit(0, 6);
        this.crowdBehaviorStepHeight = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorMaxStepHeight.set(v.floatValue())))
            .limit(0, 0.75).increment(0.05);
        this.crowdBehaviorCrouch = new UIToggle(IKey.constant("Crouch"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorCrouch.set(b.getValue())));
        this.crowdBehaviorZigZag = new UIToggle(IKey.constant("Zig zag"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorZigZag.set(b.getValue())));
        this.crowdBehaviorArmSwing = new UIToggle(IKey.constant("Swing arms"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorArmSwing.set(b.getValue())));
        this.crowdBehaviorArmSwingRate = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorArmSwingRate.set(v.floatValue())))
            .limit(0, 20);
        this.crowdBehaviorHeadMotion = new UIToggle(IKey.constant("Idle head motion"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorHeadMotion.set(b.getValue())));
        this.crowdBehaviorEnergy = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorEnergy.set(v.floatValue())))
            .limit(0, 2);
        this.crowdBehaviorLookAtTarget = new UIToggle(IKey.constant("Look at target"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorLookAtTarget.set(b.getValue())));
        this.crowdBehaviorLookEase = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorLookEase.set(v.intValue())))
            .limit(0, 200, true);
        this.crowdBehaviorHeadYawLimit = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorHeadYawLimit.set(v.floatValue())))
            .limit(0, 120);
        this.crowdBehaviorLookBodyYaw = new UIToggle(IKey.constant("Turn body"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorLookBodyYaw.set(b.getValue())));
        this.crowdBehaviorLookHeadYaw = new UIToggle(IKey.constant("Turn head"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorLookHeadYaw.set(b.getValue())));
        this.crowdBehaviorLookHeadPitch = new UIToggle(IKey.constant("Tilt head"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorLookHeadPitch.set(b.getValue())));
        this.crowdBehaviorEnemyGroup = new UITextbox(120, (s) -> this.editCrowd((crowd) -> crowd.behaviorEnemyGroup.set(s)));
        this.crowdBehaviorEnemyGroup.tooltip(IKey.constant("Group name of the crowd this one fights, in Crowd fight mode."));
        this.crowdBehaviorFightDamage = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorFightDamage.set(v.floatValue())))
            .limit(0, 1024);
        this.crowdBehaviorAttackRate = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorAttackRate.set(v.floatValue())))
            .limit(0, 20);
        this.crowdBehaviorEngagementDistance = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorEngagementDistance.set(v.floatValue())))
            .limit(0.25, 16);
        this.crowdBehaviorFightRadius = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorFightRadius.set(v.floatValue())))
            .limit(1, 256);
        this.crowdBehaviorRetargetTicks = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorRetargetTicks.set(v.intValue())))
            .limit(1, 400, true);
        this.crowdBehaviorFightRandomness = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorFightRandomness.set(v.floatValue())))
            .limit(0, 8);
        this.crowdBehaviorProjectileSpeed = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorProjectileSpeed.set(v.floatValue())))
            .limit(0, 16);
        this.crowdBehaviorProjectileLifeSpan = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorProjectileLifeSpan.set(v.intValue())))
            .limit(1, 1200, true);
        this.crowdBehaviorImpactDamage = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorImpactDamage.set(v.floatValue())))
            .limit(0, 1024);
        this.crowdBehaviorImpactKnockback = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorImpactKnockback.set(v.floatValue())))
            .limit(0, 64);
        this.crowdBehaviorImpactBounces = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.behaviorImpactBounces.set(v.intValue())))
            .limit(0, 64, true);
        this.crowdBehaviorImpactVanish = new UIToggle(IKey.constant("Vanish on hit"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorImpactVanish.set(b.getValue())));
        this.crowdBehaviorImpactCollideBlocks = new UIToggle(IKey.constant("Hit blocks"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorImpactCollideBlocks.set(b.getValue())));
        this.crowdBehaviorImpactCollideEntities = new UIToggle(IKey.constant("Hit entities"), (b) ->
            this.editCrowd((crowd) -> crowd.behaviorImpactCollideEntities.set(b.getValue())));

        this.crowdSection = new UISection(IKey.constant("Crowd"));
        this.crowdSection.fields.column(2);
        this.crowdSection.fields.add(
            UI.labelRow(IKey.constant("Member"), 180, this.crowdMember),
            UI.row(2,
                this.crowdTextureFolder,
                this.crowdClearTextures.w(90),
                this.crowdRefreshTextures.w(90),
                this.crowdRecursiveTextures
            ),
            UI.row(4,
                this.compactCrowdField("Shape", this.crowdFormation),
                this.compactCrowdField("Density", this.crowdDensity),
                this.compactCrowdField("Count", this.crowdCount),
                this.compactCrowdField("Spacing", this.crowdSpacing),
                this.crowdPerBlock
            ),
            UI.row(4,
                this.compactCrowdField("Radius", this.crowdRadius),
                this.crowdHollowField,
                this.compactCrowdField("Seed", this.crowdSeed),
                this.compactCrowdField("Yaw", this.crowdVariation)
            ),
            UI.row(4,
                this.compactCrowdField("Group", this.crowdGroupName),
                this.compactCrowdField("Health", this.crowdHealth),
                this.crowdRandomArmor,
                this.compactCrowdField("Coverage", this.crowdArmorCoverage)
            ),
            UI.row(4,
                this.crowdUseVolume,
                this.compactCrowdField("X", this.crowdVolumeX),
                this.compactCrowdField("Y", this.crowdVolumeY),
                this.compactCrowdField("Z", this.crowdVolumeZ)
            ),
            UI.row(4,
                this.crowdBehaviorEnabled,
                this.compactCrowdField("Mode", this.crowdBehaviorMode),
                this.compactCrowdField("Speed", this.crowdBehaviorSpeed),
                this.crowdBehaviorSprint
            ),
            UI.row(4,
                this.crowdBehaviorPause,
                this.compactCrowdField("Ease in", this.crowdBehaviorMoveEase),
                this.compactCrowdField("Stop at", this.crowdBehaviorStopDistance),
                this.compactCrowdField("Spread", this.crowdBehaviorSpread)
            ),
            UI.row(4,
                this.compactCrowdField("Roam", this.crowdBehaviorDisperseRadius),
                this.compactCrowdField("Every", this.crowdBehaviorWanderInterval),
                this.compactCrowdField("Pause", this.crowdBehaviorLookAroundTicks),
                this.compactCrowdField("Gap", this.crowdBehaviorSeparation)
            ),
            UI.row(4,
                this.compactCrowdField("Area X", this.crowdBehaviorAreaX),
                this.compactCrowdField("Area Y", this.crowdBehaviorAreaY),
                this.compactCrowdField("Area Z", this.crowdBehaviorAreaZ),
                this.compactCrowdField("Step up", this.crowdBehaviorStepHeight)
            ),
            UI.row(4,
                this.crowdBehaviorCrouch,
                this.crowdBehaviorZigZag,
                this.crowdBehaviorHeadMotion,
                this.compactCrowdField("Energy", this.crowdBehaviorEnergy)
            ),
            UI.row(4,
                this.crowdBehaviorJump,
                this.compactCrowdField("Rate", this.crowdBehaviorJumpRate),
                this.crowdBehaviorArmSwing,
                this.compactCrowdField("Rate", this.crowdBehaviorArmSwingRate)
            ),
            UI.row(4,
                this.crowdBehaviorLookAtTarget,
                this.compactCrowdField("Ease", this.crowdBehaviorLookEase),
                this.compactCrowdField("Yaw cap", this.crowdBehaviorHeadYawLimit),
                this.crowdBehaviorLookBodyYaw
            ),
            UI.row(4,
                this.crowdBehaviorLookHeadYaw,
                this.crowdBehaviorLookHeadPitch,
                this.compactCrowdField("Enemy", this.crowdBehaviorEnemyGroup),
                this.compactCrowdField("Damage", this.crowdBehaviorFightDamage)
            ),
            UI.row(4,
                this.compactCrowdField("Swings", this.crowdBehaviorAttackRate),
                this.compactCrowdField("Reach", this.crowdBehaviorEngagementDistance),
                this.compactCrowdField("Search", this.crowdBehaviorFightRadius),
                this.compactCrowdField("Switch", this.crowdBehaviorRetargetTicks)
            ),
            UI.row(4,
                this.compactCrowdField("Chaos", this.crowdBehaviorFightRandomness),
                this.crowdBehaviorShoot,
                this.compactCrowdField("Rate", this.crowdBehaviorShootRate),
                this.compactCrowdField("Speed", this.crowdBehaviorProjectileSpeed)
            ),
            UI.row(4,
                this.compactCrowdField("Life", this.crowdBehaviorProjectileLifeSpan),
                this.compactCrowdField("Impact", this.crowdBehaviorImpactDamage),
                this.compactCrowdField("Knock", this.crowdBehaviorImpactKnockback),
                this.compactCrowdField("Bounces", this.crowdBehaviorImpactBounces)
            ),
            UI.row(4,
                this.crowdBehaviorImpactVanish,
                this.crowdBehaviorImpactCollideBlocks,
                this.crowdBehaviorImpactCollideEntities
            )
        );

        this.shadowSection = new UISection(UIKeys.FILM_REPLAY_SHADOW);

        this.shadowSection.fields.add(
            this.shadow, this.shadowSize,
            this.shadowFollow, UI.row(this.shadowOffsetX, this.shadowOffsetY, this.shadowOffsetZ)
        );

        UISection other = new UISection(UIKeys.FILM_REPLAY_SECTION_OTHER);

        other.fields.add(
            this.looping, this.actor, this.fp,
            this.relative, UI.row(this.relativeOffsetX, this.relativeOffsetY, this.relativeOffsetZ),
            this.axesPreview, this.pickAxesPreviewBone
        );

        this.crowdSection.setExpanded(false);
        this.crowdSection.setVisible(false);
        this.shadowSection.setExpanded(false);
        other.setExpanded(false);

        this.properties = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING,
            this.pickEdit, this.enabled, this.label, this.nameTag,
            this.crowdSection,
            this.shadowSection,
            other
        );
        this.properties.relative(this).x(0).y(0).w(1F).h(1F);

        this.add(this.properties);
        this.setReplay(null);
    }

    public void attachReplayList(UIReplayList list)
    {
        this.list = list;
    }

    public Consumer<Form> getFormConsumer()
    {
        return this.pickEdit::setForm;
    }

    private UIElement compactCrowdField(String label, UIElement control)
    {
        return UI.labelRow(IKey.constant(label), 68, control);
    }

    private void edit(Consumer<Replay> consumer)
    {
        if (consumer != null && this.list != null)
        {
            for (Replay replay : this.list.getSelectedReplays())
            {
                consumer.accept(replay);
            }
        }
    }

    private CrowdForm getSelectedCrowd()
    {
        Replay replay = this.list == null ? null : this.list.getSelectedReplayFirst();
        Form form = replay == null ? null : replay.form.get();

        return form instanceof CrowdForm crowd ? crowd : null;
    }

    /** Push values density just recomputed back into their controls. */
    private void syncCrowdDerived()
    {
        Replay replay = this.list == null ? null : this.list.getSelectedReplayFirst();

        if (replay != null && replay.form.get() instanceof CrowdForm crowd)
        {
            this.crowdCount.setValue(crowd.count.get());
            this.crowdSpacing.setValue(crowd.spacing.get());
        }
    }

    private void editCrowd(Consumer<CrowdForm> consumer)
    {
        if (consumer == null || this.list == null)
        {
            return;
        }

        var film = this.filmPanel.getData();

        for (Replay replay : this.list.getSelectedReplays())
        {
            if (replay.form.get() instanceof CrowdForm crowd)
            {
                /* ValueForm deliberately treats its Form as opaque serialized data, so mutations
                 * inside CrowdForm do not bubble into the film's undo/sync callbacks. Notify the
                 * replay form itself around every crowd edit; the existing 100 ms sync debounce
                 * then sends one complete, server-resolvable form update while a control is dragged. */
                BaseValue.edit(replay.form, (value) -> consumer.accept(crowd));

                /* BaseFilmController renders a copy of replay.form. Mirror the same edit into
                 * that persistent preview copy so sliders update this frame without rebuilding
                 * entities (which used to leave the visual crowd stuck at its old count). */
                if (film != null)
                {
                    int index = film.replays.getList().indexOf(replay);
                    IEntity entity = index < 0 ? null : this.filmPanel.getController().getEntities().get(index);

                    if (entity != null && entity.getForm() instanceof CrowdForm preview && preview != crowd)
                    {
                        consumer.accept(preview);
                    }
                }
            }
        }
    }

    private void openCrowdMember(boolean editing)
    {
        CrowdForm crowd = this.getSelectedCrowd();

        if (crowd == null)
        {
            return;
        }

        Form current = crowd.getMemberForm();
        UIFormPalette palette = UIFormPalette.open(this, editing, current, true, (form) ->
        {
            if (form == null || form instanceof CrowdForm)
            {
                return;
            }

            this.editCrowd((other) ->
            {
                CrowdMemberSource primary = other.sources.getSource(0);
                Form copy = FormUtils.copy(form);

                if (primary == null)
                {
                    other.sources.addSource(copy);
                }
                else
                {
                    primary.form.set(copy);
                }

                other.memberForm.set(FormUtils.copy(form));
            });
            this.updateCrowdControls();
        });

        if (palette != null)
        {
            palette.updatable();
        }
    }

    private void openCrowdTextureFolder()
    {
        CrowdForm crowd = this.getSelectedCrowd();

        if (crowd == null)
        {
            return;
        }

        UIFolderOverlayPanel panel = new UIFolderOverlayPanel(
            IKey.constant("Crowd texture folder"),
            IKey.constant("Pick a folder containing PNG textures. Each crowd member receives a stable random texture."),
            (folder) ->
            {
                if (folder != null)
                {
                    this.editCrowd((other) ->
                    {
                        other.textureFolder.set(folder);
                        other.randomTextureFolder.set(folder);
                        other.randomTextures.set(true);
                        other.textureRevision.set(other.textureRevision.get() + 1);
                    });
                    this.updateCrowdControls();
                }
            }
        );
        Link current = crowd.textureFolder.get();

        if (current != null)
        {
            panel.list.setPath(current);
        }

        UIOverlay.addOverlay(this.getContext(), panel, 320, 0.8F);
    }

    private void openCrowdFormationPicker()
    {
        CrowdForm crowd = this.getSelectedCrowd();

        if (crowd == null || this.getContext() == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.autoKeys();

            for (CrowdFormation formation : CrowdFormation.selectableValues())
            {
                menu.action(Icons.CIRCLE, IKey.constant(formation.title),
                    formation.ordinal() == crowd.formation.get(), () ->
                    {
                        this.editCrowd((other) -> other.formation.set(formation.ordinal()));
                        this.updateCrowdControls();
                    });
            }
        });
    }

    private void updateCrowdControls()
    {
        CrowdForm crowd = this.getSelectedCrowd();
        boolean visible = crowd != null;

        this.crowdSection.setVisible(visible);

        if (visible)
        {
            if (!this.crowdSection.hasParent())
            {
                this.properties.addBefore(this.shadowSection, this.crowdSection);
            }
        }
        else if (this.crowdSection.hasParent())
        {
            this.crowdSection.removeFromParent();
        }

        this.properties.resize();

        if (!visible)
        {
            return;
        }

        Form member = crowd.getMemberForm();
        Link folder = crowd.randomTextureFolder.get() == null ? crowd.textureFolder.get() : crowd.randomTextureFolder.get();

        this.crowdMember.setForm(member);
        this.crowdTextureFolder.label = IKey.constant(folder == null ? "Choose random texture folder" : folder.toString());
        this.crowdClearTextures.setEnabled(folder != null);
        this.crowdRefreshTextures.setEnabled(folder != null);
        this.crowdRecursiveTextures.setValue(crowd.recursiveTextures.get());
        this.crowdFormation.label = IKey.constant(CrowdFormation.get(crowd.formation.get()).title);
        this.crowdPerBlock.setValue(crowd.perBlock.get());
        this.crowdCount.setValue(crowd.count.get());
        this.crowdDensity.setValue(crowd.density.get());
        this.crowdSpacing.setValue(crowd.spacing.get());
        this.crowdRadius.setValue(crowd.radius.get());
        this.crowdHollow.setValue(crowd.hollow.get());
        this.crowdHollowField.setVisible(CrowdFormation.get(crowd.formation.get()) == CrowdFormation.CIRCLE);
        this.crowdSeed.setValue(crowd.seed.get());
        this.crowdVariation.setValue(crowd.variation.get());
        this.crowdGroupName.setText(crowd.groupName.get());
        this.crowdHealth.setValue(crowd.health.get());
        this.crowdRandomArmor.setValue(crowd.randomArmor.get());
        this.crowdArmorCoverage.setValue(crowd.randomArmorCoverage.get());
        this.crowdUseVolume.setValue(crowd.useVolume.get());
        this.crowdVolumeX.setValue(crowd.volume.get().scale.x);
        this.crowdVolumeY.setValue(crowd.volume.get().scale.y);
        this.crowdVolumeZ.setValue(crowd.volume.get().scale.z);
        this.crowdBehaviorEnabled.setValue(crowd.behaviorEnabled.get());
        this.crowdBehaviorMode.label = IKey.constant(CrowdBehaviorMode.get(crowd.behaviorMode.get()).title);
        this.crowdBehaviorSpeed.setValue(crowd.behaviorSpeed.get());
        this.crowdBehaviorSprint.setValue(crowd.behaviorSprint.get());
        this.crowdBehaviorJump.setValue(crowd.behaviorRandomJump.get());
        this.crowdBehaviorJumpRate.setValue(crowd.behaviorJumpRate.get());
        this.crowdBehaviorShoot.setValue(crowd.behaviorShoot.get());
        this.crowdBehaviorShootRate.setValue(crowd.behaviorShootRate.get());
        this.crowdBehaviorPause.setValue(crowd.behaviorPause.get());
        this.crowdBehaviorMoveEase.setValue(crowd.behaviorMoveEase.get());
        this.crowdBehaviorStopDistance.setValue(crowd.behaviorStopDistance.get());
        this.crowdBehaviorSpread.setValue(crowd.behaviorTargetSpread.get());
        this.crowdBehaviorDisperseRadius.setValue(crowd.behaviorDisperseRadius.get());
        this.crowdBehaviorWanderInterval.setValue(crowd.behaviorWanderInterval.get());
        this.crowdBehaviorLookAroundTicks.setValue(crowd.behaviorLookAroundTicks.get());
        this.crowdBehaviorAreaX.setValue(crowd.behaviorAreaX.get());
        this.crowdBehaviorAreaY.setValue(crowd.behaviorAreaY.get());
        this.crowdBehaviorAreaZ.setValue(crowd.behaviorAreaZ.get());
        this.crowdBehaviorSeparation.setValue(crowd.behaviorSeparation.get());
        this.crowdBehaviorStepHeight.setValue(crowd.behaviorMaxStepHeight.get());
        this.crowdBehaviorCrouch.setValue(crowd.behaviorCrouch.get());
        this.crowdBehaviorZigZag.setValue(crowd.behaviorZigZag.get());
        this.crowdBehaviorArmSwing.setValue(crowd.behaviorArmSwing.get());
        this.crowdBehaviorArmSwingRate.setValue(crowd.behaviorArmSwingRate.get());
        this.crowdBehaviorHeadMotion.setValue(crowd.behaviorHeadMotion.get());
        this.crowdBehaviorEnergy.setValue(crowd.behaviorEnergy.get());
        this.crowdBehaviorLookAtTarget.setValue(crowd.behaviorLookAtTarget.get());
        this.crowdBehaviorLookEase.setValue(crowd.behaviorLookEase.get());
        this.crowdBehaviorHeadYawLimit.setValue(crowd.behaviorHeadYawLimit.get());
        this.crowdBehaviorLookBodyYaw.setValue(crowd.behaviorLookBodyYaw.get());
        this.crowdBehaviorLookHeadYaw.setValue(crowd.behaviorLookHeadYaw.get());
        this.crowdBehaviorLookHeadPitch.setValue(crowd.behaviorLookHeadPitch.get());
        this.crowdBehaviorEnemyGroup.setText(crowd.behaviorEnemyGroup.get());
        this.crowdBehaviorFightDamage.setValue(crowd.behaviorFightDamage.get());
        this.crowdBehaviorAttackRate.setValue(crowd.behaviorAttackRate.get());
        this.crowdBehaviorEngagementDistance.setValue(crowd.behaviorEngagementDistance.get());
        this.crowdBehaviorFightRadius.setValue(crowd.behaviorFightRadius.get());
        this.crowdBehaviorRetargetTicks.setValue(crowd.behaviorRetargetTicks.get());
        this.crowdBehaviorFightRandomness.setValue(crowd.behaviorFightRandomness.get());
        this.crowdBehaviorProjectileSpeed.setValue(crowd.behaviorProjectileSpeed.get());
        this.crowdBehaviorProjectileLifeSpan.setValue(crowd.behaviorProjectileLifeSpan.get());
        this.crowdBehaviorImpactDamage.setValue(crowd.behaviorImpactDamage.get());
        this.crowdBehaviorImpactKnockback.setValue(crowd.behaviorImpactKnockback.get());
        this.crowdBehaviorImpactBounces.setValue(crowd.behaviorImpactBounces.get());
        this.crowdBehaviorImpactVanish.setValue(crowd.behaviorImpactVanish.get());
        this.crowdBehaviorImpactCollideBlocks.setValue(crowd.behaviorImpactCollideBlocks.get());
        this.crowdBehaviorImpactCollideEntities.setValue(crowd.behaviorImpactCollideEntities.get());
        this.crowdSection.resizeParent();
    }

    public void setReplay(Replay replay)
    {
        this.properties.setVisible(replay != null);

        if (replay != null)
        {
            this.pickEdit.setForm(replay.form.get());
            this.enabled.setValue(replay.enabled.get());
            this.label.setText(replay.label.get());
            this.nameTag.setText(replay.nameTag.get());
            this.shadow.setValue(replay.shadow.get());
            this.shadowSize.setValue(replay.shadowSize.get());
            this.shadowFollow.setValue(replay.shadowFollow.get());
            this.shadowOffsetX.setValue(replay.shadowOffset.get().x);
            this.shadowOffsetY.setValue(replay.shadowOffset.get().y);
            this.shadowOffsetZ.setValue(replay.shadowOffset.get().z);
            this.looping.setValue(replay.looping.get());
            this.actor.setValue(replay.actor.get());
            this.fp.setValue(replay.fp.get());
            this.relative.setValue(replay.relative.get());
            this.relativeOffsetX.setValue(replay.relativeOffset.get().x);
            this.relativeOffsetY.setValue(replay.relativeOffset.get().y);
            this.relativeOffsetZ.setValue(replay.relativeOffset.get().z);
            this.axesPreview.setValue(replay.axesPreview.get());
        }

        this.updateCrowdControls();
    }
}
