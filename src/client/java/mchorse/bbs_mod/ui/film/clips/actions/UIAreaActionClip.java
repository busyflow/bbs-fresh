package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.area.AreaActionClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.area.AreaBrush;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIAreaActionClip extends UIActionClip<AreaActionClip>
{
    private UITextbox areaTag;
    private UITrackpad brushSize;
    private UIToggle showFill;
    private UIButton paint;
    private UIButton erase;
    private UIButton removeSelection;
    private UILabel info;

    public UIAreaActionClip(AreaActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.areaTag = new UITextbox(64, (text) -> this.editor.editMultiple(this.clip.areaTag, (value) -> value.set(text)));
        this.brushSize = new UITrackpad((value) -> this.editor.editMultiple(this.clip.brushSize, (v) -> v.set(value.intValue())));
        this.brushSize.limit(this.clip.brushSize).integer();
        this.showFill = new UIToggle(IKey.constant("Shade the area in"), (b) -> this.editor.editMultiple(this.clip.showFill, (value) -> value.set(b.getValue())));

        this.paint = new UIButton(IKey.constant("Paint"), (b) -> this.toggleBrush(false));
        this.erase = new UIButton(IKey.constant("Erase"), (b) -> this.toggleBrush(true));
        this.removeSelection = new UIButton(IKey.constant("Remove selection"), (b) ->
            this.editor.editMultiple(this.clip.cells, (value) -> value.get().clear()));
        this.removeSelection.color(Colors.NEGATIVE);

        this.info = UI.label(IKey.EMPTY);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(
            UI.column(3, 0,
                UI.label(IKey.constant("Area")),
                UI.column(3, 0,
                    UI.row(4, UI.label(IKey.constant("Tag")).w(74), this.areaTag),
                    UI.row(4, UI.label(IKey.constant("Brush size")).w(74), this.brushSize),
                    this.showFill,
                    UI.row(2, this.paint, this.erase),
                    this.removeSelection,
                    this.info
                )
            ).marginTop(4)
        );
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.areaTag.setText(this.clip.areaTag.get());
        this.brushSize.setValue(this.clip.brushSize.get());
        this.showFill.setValue(this.clip.showFill.get());
    }

    @Override
    public void render(UIContext context)
    {
        /* The brush belongs to whichever area clip is open; it can't outlive that. */
        AreaBrush.disarmUnless(this.clip);

        boolean armed = AreaBrush.getClip() == this.clip;

        this.paint.custom = armed && !AreaBrush.isErasing();
        this.paint.customColor = Colors.A100 | Colors.ACTIVE;
        this.erase.custom = armed && AreaBrush.isErasing();
        this.erase.customColor = Colors.A100 | Colors.ACTIVE;

        this.info.label = IKey.constant(this.clip.getCells().size() + " blocks painted — drag in the preview to paint, right-drag to erase");

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
}
