package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.ragdoll.RagdollActionClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

public class UIRagdollActionClip extends UIActionClip<RagdollActionClip>
{
    private UITrackpad impactYaw;
    private UITrackpad impactPitch;
    private UITrackpad impactStrength;
    private UITrackpad gravity;
    private UITrackpad damping;
    private UITrackpad stiffness;
    private UITrackpad radius;
    private UITrackpad flail;
    private UIToggle collisions;
    private UIToggle topple;

    public UIRagdollActionClip(RagdollActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.impactYaw = new UITrackpad((value) -> this.editor.editMultiple(this.clip.impactYaw, (v) -> v.set(value.floatValue())));
        this.impactYaw.limit(this.clip.impactYaw).values(1D, 0.1D, 15D);
        this.impactPitch = new UITrackpad((value) -> this.editor.editMultiple(this.clip.impactPitch, (v) -> v.set(value.floatValue())));
        this.impactPitch.limit(this.clip.impactPitch).values(1D, 0.1D, 15D);
        this.impactStrength = new UITrackpad((value) -> this.editor.editMultiple(this.clip.impactStrength, (v) -> v.set(value.floatValue())));
        this.impactStrength.limit(this.clip.impactStrength).values(0.05D, 0.01D, 0.5D);

        this.gravity = new UITrackpad((value) -> this.editor.editMultiple(this.clip.gravity, (v) -> v.set(value.floatValue())));
        this.gravity.limit(this.clip.gravity).values(0.05D, 0.01D, 0.5D);
        this.damping = new UITrackpad((value) -> this.editor.editMultiple(this.clip.damping, (v) -> v.set(value.floatValue())));
        this.damping.limit(this.clip.damping).values(0.02D, 0.01D, 0.1D);
        this.damping.tooltip(IKey.constant("How much speed a limb keeps per tick. Low values land like a body, high values bounce like a toy."));
        this.stiffness = new UITrackpad((value) -> this.editor.editMultiple(this.clip.stiffness, (v) -> v.set(value.floatValue())));
        this.stiffness.limit(this.clip.stiffness).values(0.01D, 0.001D, 0.05D);
        this.stiffness.tooltip(IKey.constant("How hard limbs pull back to the animated pose. 0 is fully limp."));
        this.radius = new UITrackpad((value) -> this.editor.editMultiple(this.clip.radius, (v) -> v.set(value.floatValue())));
        this.radius.limit(this.clip.radius).values(0.01D, 0.005D, 0.05D);
        this.radius.tooltip(IKey.constant("Limb thickness for hitting the world."));
        this.flail = new UITrackpad((value) -> this.editor.editMultiple(this.clip.flail, (v) -> v.set(value.floatValue())));
        this.flail.limit(this.clip.flail).values(0.05D, 0.01D, 0.25D);
        this.flail.tooltip(IKey.constant("How much uneven spin and independent limb motion the impact creates. 0 keeps the hit completely directed."));

        this.collisions = new UIToggle(IKey.constant("Collide with world"), (b) -> this.editor.editMultiple(this.clip.collisions, (value) -> value.set(b.getValue())));
        this.topple = new UIToggle(IKey.constant("Body falls over"), (b) -> this.editor.editMultiple(this.clip.topple, (value) -> value.set(b.getValue())));
        this.topple.tooltip(IKey.constant("Off leaves the body standing while only the limbs go slack."));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(
            this.section("Impact",
                this.row("Yaw", this.impactYaw),
                this.row("Pitch", this.impactPitch),
                this.row("Strength", this.impactStrength)
            ),
            this.section("Body",
                this.row("Gravity", this.gravity),
                this.row("Damping", this.damping),
                this.row("Stiffness", this.stiffness),
                this.row("Limb radius", this.radius),
                this.row("Flail", this.flail),
                this.collisions,
                this.topple
            )
        );
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.impactYaw.setValue(this.clip.impactYaw.get());
        this.impactPitch.setValue(this.clip.impactPitch.get());
        this.impactStrength.setValue(this.clip.impactStrength.get());
        this.gravity.setValue(this.clip.gravity.get());
        this.damping.setValue(this.clip.damping.get());
        this.stiffness.setValue(this.clip.stiffness.get());
        this.radius.setValue(this.clip.radius.get());
        this.flail.setValue(this.clip.flail.get());
        this.collisions.setValue(this.clip.collisions.get());
        this.topple.setValue(this.clip.topple.get());
    }

    private UIElement row(String label, UIElement element)
    {
        return UI.row(4, UI.label(IKey.constant(label)).w(74), element);
    }

    private UIElement section(String label, UIElement... elements)
    {
        return UI.column(3, 0, UI.label(IKey.constant(label)), UI.column(3, 0, elements)).marginTop(4);
    }
}
