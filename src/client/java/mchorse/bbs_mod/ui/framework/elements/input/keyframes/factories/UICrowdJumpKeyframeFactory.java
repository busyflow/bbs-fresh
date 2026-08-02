package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** Numeric editor for average jumps per crowd member per second. */
public class UICrowdJumpKeyframeFactory extends UIDoubleKeyframeFactory
{
    public UICrowdJumpKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.value.limit(0D, 20D).increment(0.1D).values(0.1D, 0.05D, 0.5D);
        this.value.tooltip(IKey.constant("Jump rate (average jumps per member per second)"));
    }
}
