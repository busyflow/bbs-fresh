package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorMode;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
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
    public UITrackpad crowdSpacing;
    public UITrackpad crowdRadius;
    public UITrackpad crowdHollow;
    public UIElement crowdHollowField;
    public UITrackpad crowdSeed;
    public UITrackpad crowdVariation;
    public UITrackpad crowdRenderBudget;
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
        this.crowdCount = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.count.set(v.intValue())))
            .limit(1, 1_000_000, true);
        this.crowdSpacing = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.spacing.set(v.floatValue())))
            .limit(0.1, 32);
        this.crowdRadius = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.radius.set(v.floatValue())))
            .limit(0.1, 64);
        this.crowdHollow = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.hollow.set(v.floatValue())))
            .limit(0, 0.95).increment(0.05).values(0.05, 0.01, 0.1);
        this.crowdHollowField = this.compactCrowdField("Hole", this.crowdHollow);
        this.crowdSeed = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.seed.set(v.intValue()))).integer();
        this.crowdVariation = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.variation.set(v.floatValue())))
            .limit(0, 180);
        this.crowdRenderBudget = new UITrackpad((v) -> this.editCrowd((crowd) -> crowd.renderBudget.set(v.intValue())))
            .limit(1, 20_000, true);
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
                this.compactCrowdField("Count", this.crowdCount),
                this.compactCrowdField("Spacing", this.crowdSpacing),
                this.crowdPerBlock
            ),
            UI.row(4,
                this.compactCrowdField("Radius", this.crowdRadius),
                this.crowdHollowField,
                this.compactCrowdField("Seed", this.crowdSeed),
                this.compactCrowdField("Yaw", this.crowdVariation),
                this.compactCrowdField("Budget", this.crowdRenderBudget)
            ),
            UI.row(4,
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
                this.crowdBehaviorJump,
                this.compactCrowdField("Rate", this.crowdBehaviorJumpRate),
                this.crowdBehaviorShoot,
                this.compactCrowdField("Rate", this.crowdBehaviorShootRate)
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
        this.refreshEditPanelOffset();

        this.add(this.properties);
        this.setReplay(null);
    }

    public void refreshEditPanelOffset()
    {
        int top = this.filmPanel.getEditPanelTopOffsetPx();
        this.properties.relative(this).x(0).y(0, top).w(1F).h(1F, -top);
        this.resize();
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

    private void editCrowd(Consumer<CrowdForm> consumer)
    {
        if (consumer == null || this.list == null)
        {
            return;
        }

        for (Replay replay : this.list.getSelectedReplays())
        {
            if (replay.form.get() instanceof CrowdForm crowd)
            {
                /* ValueForm deliberately treats its Form as opaque serialized data, so mutations
                 * inside CrowdForm do not bubble into the film's undo/sync callbacks. Notify the
                 * replay form itself around every crowd edit; the existing 100 ms sync debounce
                 * then sends one complete, server-resolvable form update while a control is dragged. */
                BaseValue.edit(replay.form, (value) -> consumer.accept(crowd));
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
        this.crowdSpacing.setValue(crowd.spacing.get());
        this.crowdRadius.setValue(crowd.radius.get());
        this.crowdHollow.setValue(crowd.hollow.get());
        this.crowdHollowField.setVisible(CrowdFormation.get(crowd.formation.get()) == CrowdFormation.CIRCLE);
        this.crowdSeed.setValue(crowd.seed.get());
        this.crowdVariation.setValue(crowd.variation.get());
        this.crowdRenderBudget.setValue(crowd.renderBudget.get());
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
