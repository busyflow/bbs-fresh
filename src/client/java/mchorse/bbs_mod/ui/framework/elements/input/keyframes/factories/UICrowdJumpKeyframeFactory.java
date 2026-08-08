package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdJump;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/** How much of the crowd is jumping at this keyframe. */
public class UICrowdJumpKeyframeFactory extends UIKeyframeFactory<CrowdJump>
{
    private final UITrackpad intensity;
    private final UIToggle random;

    public UICrowdJumpKeyframeFactory(Keyframe<CrowdJump> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        if (keyframe.getValue() == null)
        {
            keyframe.setValue(new CrowdJump());
        }

        this.intensity = new UITrackpad((value) -> this.edit((jump) -> jump.intensity = value.floatValue()));
        this.intensity.limit(0D, 1D).increment(0.05D).values(0.05D, 0.01D, 0.2D);
        this.intensity.tooltip(IKey.constant("How much of the crowd is off the ground.\n\n0 is nobody. 1 is everybody, jumping as fast as they can land and go again. 0.5 is half the crowd in the air at any moment."));

        this.random = new UIToggle(IKey.constant("Random"), (b) -> this.edit((jump) -> jump.random = b.getValue()));
        this.random.tooltip(IKey.constant("Vary each member's jump height and how long it takes.\n\nOff, everyone jumps exactly the same height at the same speed - they start at their own times, but identical arcs read as a machine rather than a crowd."));

        UIElement content = UI.column(
            UI.label(IKey.constant("Crowd Jump")),
            UI.labelRow(IKey.constant("How many"), this.intensity).marginTop(UIConstants.SECTION_GAP),
            this.random.marginTop(UIConstants.SECTION_GAP)
        );

        this.scroll.add(content);
        this.display();
    }

    private void edit(Consumer<CrowdJump> consumer)
    {
        CrowdJump jump = this.keyframe.getValue();

        if (jump == null)
        {
            jump = new CrowdJump();

            this.keyframe.setValue(jump);
        }

        this.keyframe.preNotify();
        consumer.accept(jump);
        this.keyframe.postNotify();
    }

    private void display()
    {
        CrowdJump jump = this.keyframe.getValue();

        if (jump == null)
        {
            return;
        }

        this.intensity.setValue(jump.intensity);
        this.random.setValue(jump.random);
    }
}
