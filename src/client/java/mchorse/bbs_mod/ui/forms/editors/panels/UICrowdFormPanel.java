package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

/**
 * Says which of the film's crowds this replay drives.
 *
 * <p>Short on purpose. The crowd itself - who they are, how many, where they stand - is edited
 * in the Crowds editor, because that is where it lives; this only joins a replay's timeline to
 * one so its keyframes have something to move.</p>
 */
public class UICrowdFormPanel extends UIFormPanel<CrowdForm>
{
    public UIButton crowd;
    public UIToggle keepBehavior;
    public UILabel hint;

    public UICrowdFormPanel(UIForm editor)
    {
        super(editor);

        this.crowd = new UIButton(IKey.EMPTY, (b) -> this.openCrowdMenu());
        this.crowd.tooltip(IKey.constant("Which of the film's crowds this replay's keyframes drive.\n\nCrowds are made in the Crowds editor, in the top bar."));
        this.keepBehavior = new UIToggle(IKey.constant("Keep behaviour clips"), (b) -> this.form.keepBehavior.set(b.getValue()));
        this.keepBehavior.tooltip(IKey.constant("On, keyframes override the crowd's behaviour clips only where they have keys, so a stretch with no walk keyframes leaves the behaviour steering.\n\nOff, the keyframes are the whole story."));
        this.hint = UI.label(IKey.constant("Add crowd keyframe channels from the replay's channel list: walk, look, jump, texture and colour."));

        this.options.add(UI.label(IKey.constant("Crowd")).marginTop(UIConstants.SECTION_GAP), this.crowd, this.keepBehavior);
        this.options.add(this.hint.marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    public void startEdit(CrowdForm form)
    {
        super.startEdit(form);

        this.keepBehavior.setValue(form.keepBehavior.get());
        this.refreshCrowdLabel();
    }

    private void openCrowdMenu()
    {
        Film film = this.getFilm();

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
                    this.refreshCrowdLabel();
                });
            }
        });
    }

    private void refreshCrowdLabel()
    {
        Film film = this.getFilm();
        String tag = this.form.crowd.get();
        Crowd crowd = film == null ? null : film.crowds.byTag(tag);

        /* A form pointing at a crowd the film no longer has says so, rather than looking settled
         * while driving nobody. */
        this.crowd.label = IKey.constant(crowd != null
            ? crowd.getDisplayName()
            : (tag == null || tag.isEmpty() ? "(pick a crowd)" : "(missing: " + tag + ")"));
    }

    private Film getFilm()
    {
        return UIFilmPanel.getEditedFilm();
    }
}
