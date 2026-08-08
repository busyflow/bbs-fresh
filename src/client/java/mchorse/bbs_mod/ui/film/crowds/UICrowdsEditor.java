package mchorse.bbs_mod.ui.film.crowds;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.List;

/**
 * The film's crowds: one list of them, and the settings of whichever is picked.
 *
 * <p>This is where crowds are made and removed. Editing one is equally possible from the crowd
 * form's own panel, which is where a shot is usually built - both show the same
 * {@link UICrowdSettings} over the same crowd, because there is only one crowd and it belongs to
 * the film.</p>
 */
public class UICrowdsEditor extends UIElement
{
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

    public static void setSelected(Crowd crowd)
    {
        selected = crowd;
    }

    private final UIFilmPanel filmPanel;

    private final UIStringList list;
    private final UICrowdSettings settings;
    private final UIScrollView form;

    public UICrowdsEditor(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;

        this.list = new UIStringList((values) -> this.pickByLabel(values.isEmpty() ? null : values.get(0)));
        this.list.background();

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addCrowd());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeCrowd());

        add.tooltip(IKey.constant("Add a crowd"));
        remove.tooltip(IKey.constant("Remove the selected crowd"));

        this.settings = new UICrowdSettings(() -> this.filmPanel.getUndoHandler().getUndoManager().markLastUndoNoMerging());

        this.form = new UIScrollView(ScrollDirection.VERTICAL);
        this.form.scroll.cancelScrolling();
        this.form.relative(this).x(94).w(1F, -94).h(1F).column(UIConstants.MARGIN).scroll().vertical().stretch().padding(UIConstants.SCROLL_PADDING);
        this.form.add(this.settings);

        UIElement buttons = UI.row(2, add, remove);

        buttons.h(20);

        UIElement sidebar = new UIElement();

        /* The list takes what the buttons leave rather than a share of the column, so it does
         * not shrink to a couple of rows. */
        sidebar.relative(this).w(90).h(1F).column(UIConstants.MARGIN).vertical().stretch().padding(UIConstants.SCROLL_PADDING);
        sidebar.add(buttons, this.list);

        this.list.h(1F, -26);

        this.add(sidebar, this.form);
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

        this.settings.setCrowd(crowd);
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
}
