package mchorse.bbs_mod.ui.film.crowds;

import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.area.AreaBrush;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The film's crowds: one list of them, and the settings of whichever is picked.
 *
 * <p>Sits beside the camera and replay editors rather than in an overlay, because everything
 * here is judged against the world - the painted ground, the formation ring, where the members
 * land - and none of that can be seen through a panel covering the viewport.</p>
 *
 * <p>Crowds used to be spawn clips on some actor's timeline, so editing one meant finding the
 * actor it happened to have been dropped on. A crowd belongs to the film, so this is where it
 * lives.</p>
 */
public class UICrowdsEditor extends UIElement
{
    private static final List<String> MOB_IDS = new ArrayList<>();

    static
    {
        for (RegistryKey<EntityType<?>> key : Registries.ENTITY_TYPE.getKeys())
        {
            MOB_IDS.add(key.getValue().toString());
        }

        MOB_IDS.sort(Comparator.comparing((a) -> a));
    }

    /**
     * The crowd being edited, for the viewport overlays to draw.
     *
     * <p>Static because the world rendering that wants it runs far from here and there is only
     * ever one film editor open.</p>
     */
    private static Crowd selected;

    public static Crowd getSelected()
    {
        return selected;
    }

    private final UIFilmPanel filmPanel;

    private final UIStringList list;
    private final UIScrollView settings;

    private final UITextbox name;
    private final UIToggle enabled;
    private final UITrackpad start;
    private final UITrackpad duration;
    private final UIButton anchor;

    private final UIButton mobType;
    private final UIToggle useActorForm;
    private final UINestedEdit actorForm;
    private final UIToggle randomTextures;
    private final UIButton randomTextureFolder;
    private final UITrackpad count;
    private final UITrackpad seed;
    private final UITrackpad spacing;
    private final UIButton formation;
    private final UITrackpad holeRadius;
    private final UIToggle disableAi;
    private final UIToggle randomYaw;
    private final UIToggle spawnOnBlock;
    private final UIToggle skipUnsafe;

    private final UIElement paintSection;
    private final UITrackpad brushSize;
    private final UIButton paint;
    private final UIButton erase;
    private final UIButton removeSelection;
    private final UILabel paintInfo;

    public UICrowdsEditor(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;

        this.list = new UIStringList((values) -> this.pickByLabel(values.isEmpty() ? null : values.get(0)));
        this.list.background();

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addCrowd());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeCrowd());

        add.tooltip(IKey.constant("Add a crowd"));
        remove.tooltip(IKey.constant("Remove the selected crowd"));

        this.name = new UITextbox(64, (text) -> this.edit((crowd) -> crowd.name.set(text)));
        this.enabled = new UIToggle(IKey.constant("Enabled"), (b) -> this.edit((crowd) -> crowd.enabled.set(b.getValue())));
        this.start = new UITrackpad((value) -> this.edit((crowd) -> crowd.start.set(value.intValue())));
        this.start.integer().tooltip(IKey.constant("The tick the crowd appears on."));
        this.duration = new UITrackpad((value) -> this.edit((crowd) -> crowd.duration.set(Math.max(1, value.intValue()))));
        this.duration.integer().tooltip(IKey.constant("How many ticks the crowd stays for."));
        this.anchor = new UIButton(IKey.EMPTY, (b) -> this.openAnchorMenu());
        this.anchor.tooltip(IKey.constant("Which replay the crowd is placed around.\n\nPainted crowds ignore this - painted ground is drawn onto the world, so it already knows where it is."));

        this.mobType = new UIButton(IKey.EMPTY, (b) -> this.openMobPicker());
        this.useActorForm = new UIToggle(IKey.constant("BBS model"), (b) -> this.edit((crowd) -> crowd.useActorForm.set(b.getValue())));
        this.actorForm = new UINestedEdit((edit) -> this.openFormPicker(edit)).keybinds();
        this.randomTextures = new UIToggle(IKey.constant("Random textures"), (b) -> this.edit((crowd) -> crowd.randomTextures.set(b.getValue())));
        this.randomTextureFolder = new UIButton(IKey.EMPTY, (b) -> this.openTextureFolderPicker());
        this.count = new UITrackpad((value) -> this.edit((crowd) -> crowd.count.set(value.intValue())));
        this.count.integer();
        this.seed = new UITrackpad((value) -> this.edit((crowd) -> crowd.seed.set(value.intValue())));
        this.seed.integer();
        this.spacing = new UITrackpad((value) -> this.edit((crowd) -> crowd.spacing.set(value.floatValue())));
        this.spacing.values(0.1D);
        this.formation = new UIButton(IKey.EMPTY, (b) -> this.openFormationMenu());
        this.holeRadius = new UITrackpad((value) -> this.edit((crowd) -> crowd.holeRadius.set(value.floatValue())));
        this.holeRadius.values(0.1D, 0.01D, 0.5D);
        this.disableAi = new UIToggle(IKey.constant("No mob AI"), (b) -> this.edit((crowd) -> crowd.disableAi.set(b.getValue())));
        this.randomYaw = new UIToggle(IKey.constant("Random yaw"), (b) -> this.edit((crowd) -> crowd.randomYaw.set(b.getValue())));
        this.spawnOnBlock = new UIToggle(IKey.constant("Surface"), (b) -> this.edit((crowd) -> crowd.spawnOnBlock.set(b.getValue())));
        this.skipUnsafe = new UIToggle(IKey.constant("Skip blocked"), (b) -> this.edit((crowd) -> crowd.skipUnsafe.set(b.getValue())));

        this.brushSize = new UITrackpad((value) -> this.edit((crowd) -> crowd.brushSize.set(value.intValue())));
        this.brushSize.integer();
        this.paint = new UIButton(IKey.constant("Paint"), (b) -> this.toggleBrush(false));
        this.erase = new UIButton(IKey.constant("Erase"), (b) -> this.toggleBrush(true));
        this.removeSelection = new UIButton(IKey.constant("Remove selection"), (b) -> this.edit((crowd) -> crowd.clearCells()));
        this.removeSelection.color(Colors.NEGATIVE);
        this.paintInfo = UI.label(IKey.EMPTY);

        this.paintSection = this.section("Painted area",
            this.row("Brush size", this.brushSize),
            UI.row(2, this.paint, this.erase),
            this.removeSelection,
            this.paintInfo
        );

        /* Each group is its own child of the scroll view rather than one nested column, so the
         * scroll view's vertical stretch reaches every row. Wrapping them in a plain column put
         * a child between the two that had no size of its own, and everything under it drew at
         * zero height on top of itself. */
        this.settings = new UIScrollView(ScrollDirection.VERTICAL);
        this.settings.scroll.cancelScrolling();
        this.settings.relative(this).x(94).w(1F, -94).h(1F).column(UIConstants.MARGIN).scroll().vertical().stretch().padding(UIConstants.SCROLL_PADDING);

        this.settings.add(
            this.section("Crowd",
                this.row("Name", this.name),
                this.enabled,
                this.row("Count", this.count),
                this.row("Seed", this.seed)
            ),
            this.section("When",
                this.row("Start", this.start),
                this.row("Duration", this.duration),
                this.row("Anchor", this.anchor)
            ),
            this.section("Where",
                this.row("Formation", this.formation),
                this.row("Spacing", this.spacing),
                this.row("Donut hole", this.holeRadius)
            ),
            this.paintSection,
            this.section("Placement",
                UI.row(1, this.spawnOnBlock, this.skipUnsafe),
                UI.row(1, this.randomYaw, this.disableAi)
            ),
            this.section("NPC Model",
                this.row("Mob", this.mobType),
                this.useActorForm,
                this.row("Model", this.actorForm),
                this.row("Textures", this.randomTextureFolder),
                this.randomTextures
            )
        );

        UIElement buttons = UI.row(2, add, remove);

        buttons.h(20);

        UIElement sidebar = new UIElement();

        /* The list takes what the buttons leave rather than a share of the column, so it does
         * not shrink to a couple of rows. */
        sidebar.relative(this).w(90).h(1F).column(UIConstants.MARGIN).vertical().stretch().padding(UIConstants.SCROLL_PADDING);
        sidebar.add(buttons, this.list);

        this.list.h(1F, -26);

        this.add(sidebar, this.settings);
    }

    /* Editing */

    private void edit(java.util.function.Consumer<Crowd> consumer)
    {
        if (selected != null)
        {
            consumer.accept(selected);
            this.filmPanel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        }
    }

    private void addCrowd()
    {
        Film film = this.filmPanel.getData();

        if (film == null)
        {
            return;
        }

        Crowd crowd = film.crowds.addCrowd();

        crowd.name.set("Crowd " + film.crowds.getList().size());
        crowd.duration.set(Math.max(1, film.camera.calculateDuration()));

        this.select(crowd);
        this.refreshList();
    }

    private void removeCrowd()
    {
        Film film = this.filmPanel.getData();

        if (film == null || selected == null)
        {
            return;
        }

        film.crowds.remove(selected);
        this.select(film.crowds.getList().isEmpty() ? null : film.crowds.getList().get(0));
        this.refreshList();
    }

    private void pickByLabel(String label)
    {
        Film film = this.filmPanel.getData();

        if (film == null || label == null)
        {
            return;
        }

        int index = this.list.getList().indexOf(label);
        List<Crowd> crowds = film.crowds.getList();

        this.select(index >= 0 && index < crowds.size() ? crowds.get(index) : null);
    }

    private void select(Crowd crowd)
    {
        selected = crowd;

        /* The brush belongs to whichever crowd is open; leaving it armed on the last one would
         * paint into a crowd nobody is looking at. */
        AreaBrush.disarm();

        this.settings.setVisible(crowd != null);

        if (crowd != null)
        {
            this.fillData();
        }
    }

    /** Reopen on whatever film is now in the tab. */
    public void setFilm(Film film)
    {
        Crowd first = film == null || film.crowds.getList().isEmpty() ? null : film.crowds.getList().get(0);

        this.select(first);
        this.refreshList();
    }

    private void refreshList()
    {
        Film film = this.filmPanel.getData();
        List<String> labels = new ArrayList<>();

        if (film != null)
        {
            for (Crowd crowd : film.crowds.getList())
            {
                labels.add(crowd.getDisplayName());
            }
        }

        this.list.clear();
        this.list.add(labels);

        if (selected != null && film != null)
        {
            int index = film.crowds.getList().indexOf(selected);

            if (index >= 0)
            {
                this.list.setIndex(index);
            }
        }
    }

    private void fillData()
    {
        Crowd crowd = selected;

        if (crowd == null)
        {
            return;
        }

        this.name.setText(crowd.name.get());
        this.enabled.setValue(crowd.enabled.get());
        this.start.setValue(crowd.start.get());
        this.duration.setValue(crowd.duration.get());
        this.refreshAnchorLabel();

        this.mobType.label = IKey.constant(crowd.mobType.get());
        this.useActorForm.setValue(crowd.useActorForm.get());
        this.actorForm.setForm(crowd.actorForm.get());
        this.randomTextures.setValue(crowd.randomTextures.get());
        this.count.setValue(crowd.count.get());
        this.seed.setValue(crowd.seed.get());
        this.spacing.setValue(crowd.spacing.get());
        this.refreshFormationLabel();
        this.holeRadius.setValue(crowd.holeRadius.get());
        this.disableAi.setValue(crowd.disableAi.get());
        this.randomYaw.setValue(crowd.randomYaw.get());
        this.spawnOnBlock.setValue(crowd.spawnOnBlock.get());
        this.skipUnsafe.setValue(crowd.skipUnsafe.get());
        this.brushSize.setValue(crowd.brushSize.get());
        this.refreshTextureFolderLabel();
    }

    @Override
    public void render(UIContext context)
    {
        Crowd crowd = selected;

        if (crowd != null)
        {
            boolean painting = crowd.getFormation() == CrowdFormation.PAINT;

            if (!painting)
            {
                AreaBrush.disarm();
            }

            this.paintSection.setVisible(painting);

            boolean armed = AreaBrush.getCrowd() == crowd;

            this.paint.custom = armed && !AreaBrush.isErasing();
            this.paint.customColor = Colors.A100 | Colors.ACTIVE;
            this.erase.custom = armed && AreaBrush.isErasing();
            this.erase.customColor = Colors.A100 | Colors.ACTIVE;
            this.paintInfo.label = IKey.constant(crowd.getCells().size() + " blocks painted");
        }

        super.render(context);
    }

    private void toggleBrush(boolean erasing)
    {
        if (AreaBrush.getCrowd() == selected && AreaBrush.isErasing() == erasing)
        {
            AreaBrush.disarm();

            return;
        }

        AreaBrush.arm(selected, erasing);
    }

    /* Pickers */

    private void openFormPicker(boolean editing)
    {
        Crowd crowd = selected;

        if (crowd == null)
        {
            return;
        }

        ValueForm value = crowd.actorForm;

        UIFormPalette.open(this, editing, value.get(), (form) ->
        {
            this.edit((c) -> c.actorForm.set(FormUtils.copy(form)));
            this.actorForm.setForm(form);
        });
    }

    private void openMobPicker()
    {
        if (selected == null)
        {
            return;
        }

        UIListOverlayPanel panel = new UIListOverlayPanel(IKey.constant("Mob type"), (id) ->
        {
            this.edit((crowd) -> crowd.mobType.set(id));
            this.mobType.label = IKey.constant(id);
        });

        panel.addValues(MOB_IDS).setValue(selected.mobType.get());
        UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
    }

    private void openTextureFolderPicker()
    {
        if (selected == null)
        {
            return;
        }

        UIFolderOverlayPanel panel = new UIFolderOverlayPanel(IKey.constant("Random texture folder"), IKey.constant("Pick a folder containing PNG textures."), (folder) ->
        {
            this.edit((crowd) -> crowd.randomTextureFolder.set(folder));
            this.refreshTextureFolderLabel();
        }).confirmLabel(IKey.constant("Use folder"));

        panel.list.setPath(selected.randomTextureFolder.get());

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
                    this.edit((crowd) -> crowd.formation.set(formation.ordinal()));
                    this.refreshFormationLabel();
                });
            }
        });
    }

    private void openAnchorMenu()
    {
        Film film = this.filmPanel.getData();

        if (film == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, IKey.constant("(none)"), () ->
            {
                this.edit((crowd) -> crowd.anchor.set(-1));
                this.refreshAnchorLabel();
            });

            List<mchorse.bbs_mod.film.replays.Replay> replays = film.replays.getList();

            for (int i = 0; i < replays.size(); i++)
            {
                int index = i;

                menu.action(Icons.SCENE, IKey.constant(replays.get(i).getName()), () ->
                {
                    this.edit((crowd) -> crowd.anchor.set(index));
                    this.refreshAnchorLabel();
                });
            }
        });
    }

    private void refreshAnchorLabel()
    {
        Film film = this.filmPanel.getData();
        int index = selected == null ? -1 : selected.anchor.get();
        List<mchorse.bbs_mod.film.replays.Replay> replays = film == null ? List.of() : film.replays.getList();

        this.anchor.label = IKey.constant(index < 0 || index >= replays.size() ? "(none)" : replays.get(index).getName());
    }

    private void refreshFormationLabel()
    {
        this.formation.label = IKey.constant(selected == null ? "" : selected.getFormation().title);
    }

    private void refreshTextureFolderLabel()
    {
        Link folder = selected == null ? null : selected.randomTextureFolder.get();

        this.randomTextureFolder.label = IKey.constant(folder == null ? "(none)" : folder.toString());
    }

    /* Layout helpers, matching the clip editors' spacing so the panel doesn't read as foreign. */

    private UIElement row(String label, UIElement element)
    {
        return UI.row(4, UI.label(IKey.constant(label)).w(74), element);
    }

    /** The same collapsible section the clip editors group their fields into. */
    private UIElement section(String label, UIElement... elements)
    {
        UISection section = new UISection(IKey.constant(label));

        section.fields.add(elements);

        return section;
    }
}
