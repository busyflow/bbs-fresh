package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.crowds.UICrowdSettings;
import mchorse.bbs_mod.ui.film.crowds.UICrowdsEditor;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The crowd a replay drives, and that crowd's own settings.
 *
 * <p>The settings are here rather than only in the Crowds editor because this is where a shot is
 * built. How many there are and where they are painted are judged against the world in front of
 * you, so having to leave the shot to change them was the wrong shape. It is the same crowd and
 * the same controls either way - the film owns the crowd, and both places are views of it.</p>
 */
public class UICrowdFormPanel extends UIFormPanel<CrowdForm>
{
    public UIButton crowd;
    public UIButton addCrowd;
    public UIToggle keepBehavior;
    public UICrowdSettings settings;

    public UICrowdFormPanel(UIForm editor)
    {
        super(editor);

        this.crowd = new UIButton(IKey.EMPTY, (b) -> this.openCrowdMenu());
        this.crowd.tooltip(IKey.constant("Which of the film's crowds this replay's keyframes drive."));
        this.addCrowd = new UIButton(IKey.constant("New crowd"), (b) -> this.createCrowd());
        this.keepBehavior = new UIToggle(IKey.constant("Keep behaviour clips"), (b) -> this.form.keepBehavior.set(b.getValue()));
        this.keepBehavior.tooltip(IKey.constant("On, keyframes override the crowd's behaviour clips only where they have keys, so a stretch with no walk keyframes leaves the behaviour steering.\n\nOff, the keyframes are the whole story."));
        this.settings = new UICrowdSettings(null);

        this.options.add(UI.label(IKey.constant("Driven crowd")).marginTop(UIConstants.SECTION_GAP), this.crowd, this.addCrowd, this.keepBehavior);
        this.options.add(this.settings.marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    public void startEdit(CrowdForm form)
    {
        super.startEdit(form);

        this.keepBehavior.setValue(form.keepBehavior.get());
        this.refresh();
    }

    private void createCrowd()
    {
        Film film = UIFilmPanel.getEditedFilm();

        if (film == null)
        {
            return;
        }

        Crowd crowd = film.crowds.addCrowd();

        crowd.name.set("Crowd " + film.crowds.getList().size());
        crowd.duration.set(Math.max(1, film.camera.calculateDuration()));

        this.form.crowd.set(crowd.crowdTag.get());
        this.refresh();
    }

    private void openCrowdMenu()
    {
        Film film = UIFilmPanel.getEditedFilm();

        if (film == null)
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
         * being edited, and editing one here is as much "being edited" as picking it in the
         * Crowds editor. */
        if (crowd != null)
        {
            UICrowdsEditor.setSelected(crowd);
        }
    }
}
