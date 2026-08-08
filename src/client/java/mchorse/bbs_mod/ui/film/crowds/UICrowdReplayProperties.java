package mchorse.bbs_mod.ui.film.crowds;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

/**
 * A crowd replay's whole editing surface, in the parameters area beside the timeline.
 *
 * <p>Everything about the crowd is reachable without leaving the shot: which crowd the replay
 * drives, making a new one, and the crowd's own settings and paint brush. It used to take
 * opening the form editor to reach any of it, which is several steps away from the timeline the
 * crowd is being animated on.</p>
 *
 * <p>Only shown while a crowd form is the replay's form, since there is nothing here that means
 * anything otherwise.</p>
 */
public class UICrowdReplayProperties extends UIScrollView
{
    private final UIButton crowd;
    private final UIButton addCrowd;
    private final UIToggle keepBehavior;
    private final UICrowdSettings settings;

    private CrowdForm form;

    public UICrowdReplayProperties(Runnable onEdit)
    {
        super(ScrollDirection.VERTICAL);

        this.scroll.cancelScrolling();

        this.crowd = new UIButton(IKey.EMPTY, (b) -> this.openCrowdMenu());
        this.crowd.tooltip(IKey.constant("Which of the film's crowds this replay's keyframes drive."));
        this.addCrowd = new UIButton(IKey.constant("New crowd"), (b) -> this.addCrowd());
        this.keepBehavior = new UIToggle(IKey.constant("Keep behaviour clips"), (b) ->
        {
            if (this.form != null)
            {
                this.form.keepBehavior.set(b.getValue());
            }
        });
        this.keepBehavior.tooltip(IKey.constant("On, keyframes override the crowd's behaviour clips only where they have keys, so a stretch with no walk keyframes leaves the behaviour steering.\n\nOff, the keyframes are the whole story."));
        this.settings = new UICrowdSettings(onEdit);

        this.column(UIConstants.MARGIN).scroll().vertical().stretch().padding(UIConstants.SCROLL_PADDING);

        this.add(UI.label(IKey.constant("Driven crowd")), this.crowd, this.addCrowd, this.keepBehavior);
        this.add(this.settings.marginTop(UIConstants.SECTION_GAP));
    }

    /**
     * @return whether this replay has anything for the panel to show, so the caller knows
     *         whether to give it the area at all.
     */
    public boolean setReplay(Replay replay)
    {
        this.form = replay != null && replay.form.get() instanceof CrowdForm crowdForm ? crowdForm : null;

        if (this.form == null)
        {
            this.settings.setCrowd(null);
            CrowdSelection.set(null);

            return false;
        }

        this.keepBehavior.setValue(this.form.keepBehavior.get());
        this.bindCrowd();
        this.refresh();

        return true;
    }

    /**
     * Make sure the form is driving something, so the settings are simply there.
     *
     * <p>Giving a replay a crowd form says plainly enough that it wants a crowd, so asking which
     * one before showing anything was a step that only ever had one answer the first time. A
     * form with nothing to drive takes the film's first crowd, or a new one when the film has
     * none. Picking only matters once there is more than one to pick from.</p>
     */
    private void bindCrowd()
    {
        Film film = UIFilmPanel.getEditedFilm();

        if (film == null || film.crowds.byTag(this.form.crowd.get()) != null)
        {
            return;
        }

        List<Crowd> crowds = film.crowds.getList();

        this.form.crowd.set(crowds.isEmpty() ? this.createCrowd(film).crowdTag.get() : crowds.get(0).crowdTag.get());
    }

    private Crowd createCrowd(Film film)
    {
        Crowd crowd = film.crowds.addCrowd();

        crowd.name.set("Crowd " + film.crowds.getList().size());
        

        return crowd;
    }

    private void addCrowd()
    {
        Film film = UIFilmPanel.getEditedFilm();

        if (film != null && this.form != null)
        {
            this.form.crowd.set(this.createCrowd(film).crowdTag.get());
            this.refresh();
        }
    }

    private void openCrowdMenu()
    {
        Film film = UIFilmPanel.getEditedFilm();

        if (film == null || this.form == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            for (Crowd crowd : film.crowds.getList())
            {
                menu.action(Icons.CHICKEN, IKey.constant(crowd.getDisplayName()), () ->
                {
                    this.form.crowd.set(crowd.crowdTag.get());
                    this.refresh();
                });
            }
        });
    }

    private void refresh()
    {
        Film film = UIFilmPanel.getEditedFilm();
        String tag = this.form.crowd.get();
        Crowd crowd = film == null ? null : film.crowds.byTag(tag);

        /* A form pointing at a crowd the film no longer has says so, rather than looking settled
         * while driving nobody. */
        this.crowd.label = IKey.constant(crowd != null
            ? crowd.getDisplayName()
            : (tag == null || tag.isEmpty() ? "(pick a crowd)" : "(missing: " + tag + ")"));

        this.settings.setCrowd(crowd);

        /* The viewport draws the painted outline and the formation ring for whichever crowd is
         * being edited, and this is now the usual place to edit one. */
        if (crowd != null)
        {
            CrowdSelection.set(crowd);
        }
    }
}
