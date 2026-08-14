package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

/**
 * The pose editor bound to a form's secondary anchors rather than to a pose. Only each entry's
 * position is read, so it drops the secondary-anchor toggle the pose editors carry - inside the
 * anchor list itself that flag would have nothing to act on.
 */
public class UIModelAnchorsEditor extends UIModelPoseEditor
{
    @Override
    protected boolean hasSecondaryAnchorToggle()
    {
        return false;
    }
}
