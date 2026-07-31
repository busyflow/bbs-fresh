package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.item.ItemStack;

public class UIItemStackKeyframeFactory extends UIKeyframeFactory<ItemStack>
{
    private UIItemStack itemEditor;

    public UIItemStackKeyframeFactory(Keyframe<ItemStack> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.itemEditor = new UIItemStack(this::setItemValue);
        this.itemEditor.setStack(keyframe.getValue());

        this.scroll.add(this.itemEditor);
    }

    private void setItemValue(ItemStack stack)
    {
        this.setValue(stack);
        UIReplaysEditorUtils.setItemForSelectedReplays(this.editor, this.keyframe, stack);
    }
}
