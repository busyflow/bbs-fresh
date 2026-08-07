package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdSpawnActionClip;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.area.AreaBrush;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UICrowdSpawnActionClip extends UIActionClip<CrowdSpawnActionClip>
{
    private static final List<String> MOB_IDS = new ArrayList<>();

    private UITextbox crowdTag;
    private UIButton mobType;
    private UIToggle useActorForm;
    private UINestedEdit actorForm;
    private UIToggle randomTextures;
    private UIButton randomTextureFolder;
    private UITrackpad count;
    private UITrackpad previewCount;
    private UITrackpad seed;
    private UITrackpad spacing;
    private UIButton formation;
    private UITrackpad holeRadius;
    private UIToggle disableAi;
    private UIToggle randomYaw;
    private UIToggle spawnOnBlock;
    private UIToggle skipUnsafe;
    private UIToggle replaceExisting;
    private UIElement paintSection;
    private UITrackpad brushSize;
    private UIButton paint;
    private UIButton erase;
    private UIButton removeSelection;
    private UILabel paintInfo;

    static
    {
        for (RegistryKey<EntityType<?>> key : Registries.ENTITY_TYPE.getKeys())
        {
            MOB_IDS.add(key.getValue().toString());
        }

        MOB_IDS.sort(Comparator.comparing((a) -> a));
    }

    public UICrowdSpawnActionClip(CrowdSpawnActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.crowdTag = new UITextbox(128, (text) -> this.editor.editMultiple(this.clip.crowdTag, (value) -> value.set(text)));
        this.mobType = new UIButton(IKey.EMPTY, (b) -> this.openMobPicker());
        this.useActorForm = new UIToggle(IKey.constant("BBS model"), (b) -> this.editor.editMultiple(this.clip.useActorForm, (value) -> value.set(b.getValue())));
        this.actorForm = new UINestedEdit((edit) -> this.openFormPicker(this.clip.actorForm, this.actorForm, edit)).keybinds();
        this.randomTextures = new UIToggle(IKey.constant("Random textures"), (b) -> this.editor.editMultiple(this.clip.randomTextures, (value) -> value.set(b.getValue())));
        this.randomTextureFolder = new UIButton(IKey.EMPTY, (b) -> this.openTextureFolderPicker());
        this.count = new UITrackpad((value) -> this.editor.editMultiple(this.clip.count, (v) -> v.set(value.intValue())));
        this.count.limit(this.clip.count).integer();
        this.previewCount = new UITrackpad((value) -> this.editor.editMultiple(this.clip.previewCount, (v) -> v.set(value.intValue())));
        this.previewCount.limit(this.clip.previewCount).integer();
        this.previewCount.tooltip(IKey.constant("How many of the crowd to spawn while editing. An export always spawns the full count.\n\nThe preview is spread evenly over the whole area rather than filling one corner, so it still shows where the crowd stands - including anyone dropped into a hole or pressed against a wall.\n\n0 spawns all of them, always."));
        this.seed = new UITrackpad((value) -> this.editor.editMultiple(this.clip.seed, (v) -> v.set(value.intValue())));
        this.seed.integer();
        this.spacing = new UITrackpad((value) -> this.editor.editMultiple(this.clip.spacing, (v) -> v.set(value.floatValue())));
        this.spacing.limit(this.clip.spacing).values(0.1D);
        this.formation = new UIButton(IKey.EMPTY, (b) -> this.openFormationMenu());
        this.holeRadius = new UITrackpad((value) -> this.editor.editMultiple(this.clip.holeRadius, (v) -> v.set(value.floatValue())));
        this.holeRadius.limit(this.clip.holeRadius).values(0.1D, 0.01D, 0.5D);
        this.disableAi = new UIToggle(IKey.constant("No mob AI"), (b) -> this.editor.editMultiple(this.clip.disableAi, (value) -> value.set(b.getValue())));
        this.randomYaw = new UIToggle(IKey.constant("Random yaw"), (b) -> this.editor.editMultiple(this.clip.randomYaw, (value) -> value.set(b.getValue())));
        this.spawnOnBlock = new UIToggle(IKey.constant("Surface"), (b) -> this.editor.editMultiple(this.clip.spawnOnBlock, (value) -> value.set(b.getValue())));
        this.skipUnsafe = new UIToggle(IKey.constant("Skip blocked"), (b) -> this.editor.editMultiple(this.clip.skipUnsafe, (value) -> value.set(b.getValue())));
        this.replaceExisting = new UIToggle(IKey.constant("Replace same tag"), (b) -> this.editor.editMultiple(this.clip.replaceExisting, (value) -> value.set(b.getValue())));

        this.brushSize = new UITrackpad((value) -> this.editor.editMultiple(this.clip.brushSize, (v) -> v.set(value.intValue())));
        this.brushSize.limit(this.clip.brushSize).integer();
        this.paint = new UIButton(IKey.constant("Paint"), (b) -> this.toggleBrush(false));
        this.erase = new UIButton(IKey.constant("Erase"), (b) -> this.toggleBrush(true));
        this.removeSelection = new UIButton(IKey.constant("Remove selection"), (b) ->
            this.editor.editMultiple(this.clip.cells, (value) -> value.get().clear()));
        this.removeSelection.color(Colors.NEGATIVE);
        this.paintInfo = UI.label(IKey.EMPTY);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.paintSection = this.section("Painted area",
            this.row("Brush size", this.brushSize),
            UI.row(2, this.paint, this.erase),
            this.removeSelection,
            this.paintInfo
        );

        this.panels.add(
            this.section("Crowd",
                this.row("Tag", this.crowdTag),
                this.row("Count", this.count),
                this.row("Preview count", this.previewCount),
                this.row("Seed", this.seed),
                this.row("Spacing", this.spacing),
                this.row("Formation", this.formation),
                this.row("Donut hole", this.holeRadius)
            ),
            this.paintSection,
            this.section("Placement",
                UI.row(1, this.spawnOnBlock, this.skipUnsafe),
                UI.row(1, this.randomYaw, this.replaceExisting),
                this.disableAi
            ),
            this.section("NPC Model",
                this.row("Mob", this.mobType),
                this.useActorForm,
                this.row("Model", this.actorForm),
                this.row("Textures", this.randomTextureFolder),
                this.randomTextures
            )
        );
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.crowdTag.setText(this.clip.crowdTag.get());
        this.mobType.label = IKey.constant(this.clip.mobType.get());
        this.useActorForm.setValue(this.clip.useActorForm.get());
        this.actorForm.setForm(this.clip.actorForm.get());
        this.randomTextures.setValue(this.clip.randomTextures.get());
        this.count.setValue(this.clip.count.get());
        this.previewCount.setValue(this.clip.previewCount.get());
        this.seed.setValue(this.clip.seed.get());
        this.spacing.setValue(this.clip.spacing.get());
        this.refreshFormationLabel();
        this.holeRadius.setValue(this.clip.holeRadius.get());
        this.disableAi.setValue(this.clip.disableAi.get());
        this.randomYaw.setValue(this.clip.randomYaw.get());
        this.spawnOnBlock.setValue(this.clip.spawnOnBlock.get());
        this.skipUnsafe.setValue(this.clip.skipUnsafe.get());
        this.replaceExisting.setValue(this.clip.replaceExisting.get());
        this.brushSize.setValue(this.clip.brushSize.get());
        this.refreshTextureFolderLabel();
    }

    @Override
    public void render(UIContext context)
    {
        /* The brush belongs to whichever crowd clip is open, and only while it is asking to be
         * painted — switching formation away from Paint puts left-drag back where it was. */
        boolean painting = CrowdFormation.get(this.clip.formation.get()) == CrowdFormation.PAINT;

        AreaBrush.disarmUnless(this.clip);

        if (!painting)
        {
            AreaBrush.disarm();
        }

        this.paintSection.setVisible(painting);

        boolean armed = AreaBrush.getClip() == this.clip;

        this.paint.custom = armed && !AreaBrush.isErasing();
        this.paint.customColor = Colors.A100 | Colors.ACTIVE;
        this.erase.custom = armed && AreaBrush.isErasing();
        this.erase.customColor = Colors.A100 | Colors.ACTIVE;
        this.paintInfo.label = IKey.constant(this.clip.getCells().size() + " blocks painted");

        super.render(context);
    }

    private void toggleBrush(boolean erasing)
    {
        if (AreaBrush.getClip() == this.clip && AreaBrush.isErasing() == erasing)
        {
            AreaBrush.disarm();

            return;
        }

        AreaBrush.arm(this.clip, erasing);
    }

    private UIElement row(String label, UIElement element)
    {
        return UI.row(4, this.label(label, 74), element);
    }

    private UIElement triple(String a, UIElement aElement, String b, UIElement bElement, String c, UIElement cElement)
    {
        return UI.row(2, this.compact(a, aElement, 18), this.compact(b, bElement, 18), this.compact(c, cElement, 18));
    }

    private UIElement compact(String label, UIElement element, int width)
    {
        return UI.row(2, this.label(label, width), element);
    }

    private UIElement section(String label, UIElement... elements)
    {
        return UI.column(3, 0, UI.label(IKey.constant(label)), UI.column(3, 0, elements)).marginTop(4);
    }

    private UIElement label(String label, int width)
    {
        return UI.label(IKey.constant(label)).w(width);
    }

    private void openFormPicker(ValueForm value, UINestedEdit element, boolean editing)
    {
        UIFormPalette.open(this, editing, value.get(), (form) ->
        {
            this.editor.editMultiple(value, (v) -> v.set(FormUtils.copy(form)));
            element.setForm(form);
        });
    }

    private void openMobPicker()
    {
        UIListOverlayPanel panel = new UIListOverlayPanel(IKey.constant("Mob type"), (id) ->
        {
            this.editor.editMultiple(this.clip.mobType, (value) -> value.set(id));
            this.mobType.label = IKey.constant(id);
        });

        panel.addValues(MOB_IDS).setValue(this.clip.mobType.get());
        UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
    }

    private void openTextureFolderPicker()
    {
        UIFolderOverlayPanel panel = new UIFolderOverlayPanel(IKey.constant("Random texture folder"), IKey.constant("Pick a folder containing PNG textures."), (folder) ->
        {
            this.editor.editMultiple(this.clip.randomTextureFolder, (value) -> value.set(folder));
            this.refreshTextureFolderLabel();
        }).confirmLabel(IKey.constant("Use folder"));

        panel.list.setPath(this.clip.randomTextureFolder.get());

        UIOverlay.addOverlay(this.getContext(), panel, 320, 0.8F);
    }

    private void openFormationMenu()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            for (CrowdFormation formation : CrowdFormation.values())
            {
                menu.action(Icons.SHAPES, IKey.constant(formation.title), () ->
                {
                    this.editor.editMultiple(this.clip.formation, (value) -> value.set(formation.ordinal()));
                    this.refreshFormationLabel();
                });
            }
        });
    }

    private void refreshFormationLabel()
    {
        this.formation.label = IKey.constant(CrowdFormation.get(this.clip.formation.get()).title);
    }

    private void refreshTextureFolderLabel()
    {
        Link folder = this.clip.randomTextureFolder.get();

        this.randomTextureFolder.label = IKey.constant(folder == null ? "(none)" : folder.toString());
    }
}
