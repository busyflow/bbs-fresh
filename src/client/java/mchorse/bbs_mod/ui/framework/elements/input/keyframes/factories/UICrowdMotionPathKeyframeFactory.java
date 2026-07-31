package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdMotionPath;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;

/** Editor for one timeline motion point. There are no internal checkpoints. */
public class UICrowdMotionPathKeyframeFactory extends UIKeyframeFactory<CrowdMotionPath>
{
    public final UIPropTransform transform = new UIPropTransform();

    private final UITrackpad x;
    private final UITrackpad y;
    private final UITrackpad z;
    private final UITrackpad width;
    private final UITrackpad height;
    private final UITrackpad depth;
    private final UITrackpad rotation;
    private final UITrackpad scatter;
    private final UITrackpad formationPreservation;
    private final UIToggle gate;
    private final UIToggle terrain;
    private final UIToggle faceTravel;
    private final UIToggle run;
    private final UIToggle showPathLine;
    private final UIToggle showPoint;
    private final UIElement widthRow;
    private final UIElement heightRow;
    private final UIElement depthRow;
    private final UIElement rotationRow;
    private final UIElement scatterRow;
    private final UIElement preservationRow;
    private final UIElement content;
    private final Transform edited = new Transform();
    private boolean syncing;

    public UICrowdMotionPathKeyframeFactory(Keyframe<CrowdMotionPath> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        if (keyframe.getValue() == null)
        {
            keyframe.setValue(new CrowdMotionPath());
        }

        keyframe.setDuration(0F);
        this.duration.setVisible(false);

        this.x = trackpad(-10000D, 10000D, 0.25D, v -> this.edit(p -> p.x = v.floatValue()));
        this.y = trackpad(-10000D, 10000D, 0.25D, v -> this.edit(p -> p.y = v.floatValue()));
        this.z = trackpad(-10000D, 10000D, 0.25D, v -> this.edit(p -> p.z = v.floatValue()));
        this.width = trackpad(0.25D, 256D, 0.25D, v -> this.edit(p -> p.width = Math.max(0.25F, v.floatValue())));
        this.height = trackpad(0.25D, 256D, 0.25D, v -> this.edit(p -> p.height = Math.max(0.25F, v.floatValue())));
        this.depth = trackpad(0.25D, 256D, 0.25D, v -> this.edit(p -> p.depth = Math.max(0.25F, v.floatValue())));
        this.rotation = trackpad(-180D, 180D, 5D, v -> this.edit(p -> p.yaw = v.floatValue()));
        this.scatter = trackpad(0D, 1D, 0.05D, v -> this.edit(p -> p.scatter = v.floatValue()));
        this.formationPreservation = trackpad(0D, 1D, 0.05D, v -> this.edit(p -> p.formationPreservation = v.floatValue()));

        this.widthRow = UI.labelRow(IKey.constant("Gate width"), this.width);
        this.heightRow = UI.labelRow(IKey.constant("Gate height"), this.height);
        this.depthRow = UI.labelRow(IKey.constant("Gate thickness"), this.depth);
        this.rotationRow = UI.labelRow(IKey.constant("Gate rotation"), this.rotation);
        this.scatterRow = UI.labelRow(IKey.constant("Gate scatter"), this.scatter);
        this.preservationRow = UI.labelRow(IKey.constant("Formation preservation"), this.formationPreservation);
        this.gate = new UIToggle(IKey.constant("Gate"), b ->
        {
            this.edit(p -> p.gate = b.getValue());
            this.display(false);
        });
        this.terrain = new UIToggle(IKey.constant("Follow terrain (uphill/downhill)"), b -> this.edit(p -> p.terrainFollow = b.getValue()));
        this.faceTravel = new UIToggle(IKey.constant("Face direction of travel"), b -> this.edit(p -> p.faceTravel = b.getValue()));
        this.run = new UIToggle(IKey.constant("Run"), b -> this.edit(p -> p.run = b.getValue()));
        this.showPathLine = new UIToggle(IKey.constant("Show motion path"), b -> this.edit(p -> p.showPathLine = b.getValue()));
        this.showPoint = new UIToggle(IKey.constant("Show timeline points"), b -> this.edit(p -> p.showPoint = b.getValue()));

        this.transform.callbacks(
            () -> this.keyframe.preNotify(),
            () ->
            {
                this.syncFromTransform();
                this.keyframe.postNotify();
                this.display(false);
            },
            () -> this.keyframe.preNotify(IValueListener.FLAG_UNMERGEABLE)
        );
        this.transform.enableTranslateHotkeys();
        Gizmo.INSTANCE.setMode(Gizmo.Mode.TRANSLATE_AXES);

        this.content = UI.column(
            UI.label(IKey.constant("Crowd Motion Point")),
            UI.label(IKey.constant("Timing is controlled by this keyframe's position on the timeline.")),
            UI.labelRow(IKey.constant("Position X"), this.x).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(IKey.constant("Position Y"), this.y),
            UI.labelRow(IKey.constant("Position Z"), this.z),
            this.gate.marginTop(UIConstants.SECTION_GAP),
            this.widthRow,
            this.heightRow,
            this.depthRow,
            this.rotationRow,
            this.scatterRow,
            this.preservationRow,
            this.showPathLine.marginTop(UIConstants.SECTION_GAP),
            this.showPoint,
            this.terrain.marginTop(UIConstants.SECTION_GAP),
            this.faceTravel,
            this.run
        );
        this.scroll.add(this.content);

        /* Keep the gizmo controller attached to the editor but outside the scroll layout. */
        this.transform.setVisible(false);
        this.add(this.transform);
        this.display(true);
    }

    private UITrackpad trackpad(double min, double max, double increment, java.util.function.Consumer<Double> callback)
    {
        return new UITrackpad(callback).limit(min, max).increment(increment).values(increment, increment * 0.2D, increment * 4D);
    }

    public CrowdMotionPath getPath()
    {
        return this.keyframe.getValue();
    }

    public Keyframe<CrowdMotionPath> getMotionKeyframe()
    {
        return this.keyframe;
    }

    private void edit(java.util.function.Consumer<CrowdMotionPath> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        this.keyframe.preNotify();
        consumer.accept(this.getPath());
        this.keyframe.postNotify();
        this.display(true);
    }

    private void syncFromTransform()
    {
        CrowdMotionPath point = this.getPath();
        Transform value = this.transform.getTransform();

        if (this.syncing || point == null || value == null)
        {
            return;
        }

        point.x = value.translate.x;
        point.y = value.translate.y;
        point.z = value.translate.z;
        point.yaw = MathUtils.toDeg(value.rotate.y);
    }

    private void display(boolean transformToo)
    {
        CrowdMotionPath point = this.getPath();

        if (point == null)
        {
            return;
        }

        this.syncing = true;

        try
        {
            this.x.setValue(point.x);
            this.y.setValue(point.y);
            this.z.setValue(point.z);
            this.width.setValue(point.width);
            this.height.setValue(point.height);
            this.depth.setValue(point.depth);
            this.rotation.setValue(point.yaw);
            this.scatter.setValue(point.scatter);
            this.formationPreservation.setValue(point.formationPreservation);
            this.gate.setValue(point.gate);
            this.terrain.setValue(point.terrainFollow);
            this.faceTravel.setValue(point.faceTravel);
            this.run.setValue(point.run);
            this.showPathLine.setValue(point.showPathLine);
            this.showPoint.setValue(point.showPoint);
            this.widthRow.setVisible(point.gate);
            this.heightRow.setVisible(point.gate);
            this.depthRow.setVisible(point.gate);
            this.rotationRow.setVisible(point.gate);
            this.scatterRow.setVisible(point.gate);
            this.preservationRow.setVisible(point.gate);
            this.content.resize();
            this.scroll.resize();

            if (transformToo)
            {
                this.edited.identity();
                this.edited.translate.set(point.x, point.y, point.z);
                this.edited.rotate.y = MathUtils.toRad(point.yaw);
                this.transform.setTransform(this.edited);
            }
        }
        finally
        {
            this.syncing = false;
        }
    }

    @Override
    public void update()
    {
        super.update();
        /* Never reload the edited transform during a live gizmo gesture. Doing so
         * races the overlay handler and makes the handle appear pinned in place. */
        this.display(!this.transform.isEditing());
    }
}
