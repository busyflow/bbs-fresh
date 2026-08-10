package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UICrowdWalkKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIKeyframeEditor extends UIElement
{
    public static final int[] COLORS = {Colors.RED, Colors.GREEN, Colors.BLUE, Colors.CYAN, Colors.MAGENTA, Colors.YELLOW, Colors.LIGHTEST_GRAY & 0xffffff, Colors.DEEP_PINK};

    public UIKeyframes view;
    public UIKeyframeFactory editor;

    /** Width of the properties panel when it sits beside the sheet. */
    private static final int SIDE_WIDTH = 140;
    /** Height of the properties panel when it sits under the sheet, from the Fresh settings. */
    private static int belowHeight()
    {
        return BBSSettings.keyframePropertiesHeight.get();
    }

    private UIElement target;
    private boolean timelineVisible = true;
    private boolean propertiesVisible = true;
    private java.util.function.BooleanSupplier propertiesBelowSupplier = () -> false;
    private boolean propertiesBelow;
    private int lastContentHeight = -1;

    public UIKeyframeEditor(Function<Consumer<Keyframe>, UIKeyframes> factory)
    {
        this.view = factory.apply(this::pickKeyframe);
        this.view.changed(() ->
        {
            if (this.editor != null)
            {
                this.editor.update();
            }
        });

        this.add(this.view.full(this).w(1F, -SIDE_WIDTH));
    }

    /**
     * Put the keyframe's properties under the sheet rather than beside it.
     *
     * <p>Beside it, the panel takes a fixed column out of the width for as long as the editor is
     * open, and the sheet - the thing actually being read across - gets what is left. Underneath,
     * it spans the full width instead, and the channel names go with the sheet's right edge into
     * the space the column had.</p>
     */
    public UIKeyframeEditor propertiesBelow(boolean below)
    {
        return this.propertiesBelow(() -> below);
    }

    /**
     * Read live, so toggling the setting rearranges an open editor without reopening it. The
     * layout is re-applied in {@link #render(UIContext)} when the answer changes.
     */
    public UIKeyframeEditor propertiesBelow(java.util.function.BooleanSupplier below)
    {
        this.propertiesBelowSupplier = below;
        this.propertiesBelow = below.getAsBoolean();

        this.applyViewFlex();

        return this;
    }

    private void applyViewFlex()
    {
        if (this.target != null)
        {
            this.view.resetFlex().full(this).w(1F);
        }
        else if (this.propertiesBelow)
        {
            this.view.resetFlex().full(this).w(1F).h(this.sheetHeight());
        }
        else
        {
            this.view.resetFlex().full(this).w(1F, -SIDE_WIDTH);
        }

        this.resize();
    }

    /**
     * How tall to make the sheet when the properties go under it: as tall as its tracks, so the
     * properties sit directly beneath the last one rather than at the far bottom of the panel with
     * an empty gap between. Falls back to filling the space when the graph cannot measure itself.
     */
    private int sheetHeight()
    {
        int content = this.view.getGraph().getContentHeight();

        this.lastContentHeight = content;
        int available = Math.max(0, this.area.h - belowHeight());

        if (content <= 0)
        {
            return available;
        }

        return available <= 0 ? content : Math.min(content, available);
    }

    /**
     * The parameters panel is parented to {@link #target}, not to this editor, so nothing would take
     * it down when this editor is dropped &mdash; it would stay in the edit area, clickable, and the
     * next editor would stack its own panel on top of it.
     */
    @Override
    public void removeFromParent()
    {
        super.removeFromParent();

        if (this.editor != null)
        {
            this.editor.removeFromParent();
        }
    }

    public UIKeyframeEditor target(UIElement target)
    {
        this.target = target;

        this.applyViewFlex();

        return this;
    }

    private void pickKeyframe(Keyframe keyframe)
    {
        /* Nothing picked: keep the panel where it is and empty it, rather than taking it down and
         * letting everything around it jump. With nothing ever picked there is no panel yet, so
         * one is built off any keyframe in the sheets purely to stand there, values hidden. */
        boolean empty = keyframe == null;

        if (empty)
        {
            if (this.editor != null)
            {
                this.editor.setValuesVisible(false);

                return;
            }

            keyframe = this.firstKeyframe();

            if (keyframe == null)
            {
                return;
            }
        }

        UIKeyframeFactory.saveScroll(this.editor);

        if (this.editor != null)
        {
            this.editor.removeFromParent();
            this.editor = null;
        }

        {
            this.editor = UIKeyframeFactory.createPanel(keyframe, this.view);

            if (this.editor == null)
            {
                return;
            }

            this.editor.setValuesVisible(!empty);

            if (this.target != null)
            {
                this.editor.relative(this.target).x(0).y(0).w(1F).h(1F);
            }
            else if (this.propertiesBelow)
            {
                /* Against the sheet's bottom edge rather than the panel's, so it follows the last
                 * track instead of sitting at the foot of the panel with a gap above it. Full
                 * width, and the same place whichever keyframe is picked. */
                this.editor.relative(this.view).x(0).y(1F).w(1F).h(belowHeight());
            }
            else
            {
                this.editor.relative(this).x(1F, -SIDE_WIDTH).w(SIDE_WIDTH).h(1F);
            }

            /* The panel lives in whichever element it is laid out over, so it stays visible when
             * the timeline is hidden behind another dock tab. */
            (this.target == null ? this : this.target).add(this.editor);
            this.editor.setVisible(this.propertiesVisible);
            this.resize();

            if (this.target != null)
            {
                this.target.resize();
            }
        }

        this.resize();

        if (this.editor != null)
        {
            this.editor.restoreScroll();
        }
    }

    /** Any keyframe at all, to build an empty panel off when none has been picked yet. */
    private Keyframe firstKeyframe()
    {
        for (UIKeyframeSheet sheet : this.view.getGraph().getSheets())
        {
            List<Keyframe> keyframes = sheet.channel.getKeyframes();

            if (!keyframes.isEmpty())
            {
                return keyframes.get(0);
            }
        }

        return null;
    }

    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;
        this.view.setVisible(visible);
    }

    public void setPropertiesVisible(boolean visible)
    {
        this.propertiesVisible = visible;

        if (this.editor != null)
        {
            this.editor.setVisible(visible);
        }
    }

    public void setChannel(KeyframeChannel channel, int color)
    {
        this.view.removeAllSheets();
        this.view.addSheet(new UIKeyframeSheet(color, false, channel, null));

        this.pickKeyframe(null);
    }

    public void setClip(KeyframeClip clip)
    {
        this.view.removeAllSheets();

        for (int i = 0; i < clip.channels.length; i++)
        {
            KeyframeChannel channel = clip.channels[i];

            this.view.addSheet(new UIKeyframeSheet(COLORS[i], false, channel, null));
        }

        /* Re-measure now the tracks exist - the height set before them had nothing to measure. */
        if (this.propertiesBelow && this.target == null)
        {
            this.applyViewFlex();
        }

        this.pickKeyframe(null);
    }

    public UIKeyframeSheet getSheet(Keyframe keyframe)
    {
        if (keyframe == null)
        {
            return null;
        }

        for (UIKeyframeSheet sheet : this.view.getGraph().getSheets())
        {
            if (sheet.channel == keyframe.getParent())
            {
                return sheet;
            }
        }

        return null;
    }

    public Pair<String, Boolean> getBone()
    {
        UIKeyframeFactory editor = this.editor;
        String bone = null;
        boolean local = false;

        if (editor instanceof UIPoseKeyframeFactory pose)
        {
            UIKeyframeSheet sheet = this.getSheet(editor.getKeyframe());
            String currentFirst = pose.poseEditor.groups.list.getCurrentFirst();

            if (sheet != null)
            {
                String id = StringUtils.fileName(sheet.id);

                if (id.startsWith("pose"))
                {
                    PerLimbService.PoseBonePath path = PerLimbService.parsePoseBonePath(sheet.id);
                    if (path != null)
                        bone = path.formPath().isEmpty() ? currentFirst : path.formPath() + "/" + currentFirst;
                    else
                    {
                        int i = sheet.id.lastIndexOf('/');
                        bone = i >= 0 ? sheet.id.substring(0, i + 1) + currentFirst : currentFirst;
                    }
                    local = pose.poseEditor.transform.isLocal();
                }
            }
        }
        else if (editor instanceof UITransformKeyframeFactory transform)
        {
            UIKeyframeSheet sheet = this.getSheet(editor.getKeyframe());

            if (sheet != null)
            {
                String id = StringUtils.fileName(sheet.id);

                PerLimbService.PoseBonePath poseBonePath = PerLimbService.parsePoseBonePath(sheet.id);

                if (poseBonePath != null)
                {
                    bone = poseBonePath.formPath().isEmpty() ? poseBonePath.bone() : poseBonePath.formPath() + "/" + poseBonePath.bone();
                    local = transform.transform.isLocal();
                }
                else if (ReplayKeyframes.RIGHT_HAND_POSE.equals(id) || ReplayKeyframes.LEFT_HAND_POSE.equals(id))
                {
                    bone = ReplayKeyframes.RIGHT_HAND_POSE.equals(id)
                        ? ModelFormRenderer.MAIN_HAND_ITEM_BONE
                        : ModelFormRenderer.OFF_HAND_ITEM_BONE;
                    local = true;
                }
                else if (id.startsWith("transform"))
                {
                    int i = sheet.id.lastIndexOf('/');

                    bone = i >= 0 ? sheet.id.substring(0, i) : "";
                    local = transform.transform.isLocal();
                }
            }
        }
        else if (editor instanceof UIPoseTransformKeyframeFactory poseTransform)
        {
            UIKeyframeSheet sheet = this.getSheet(editor.getKeyframe());

            if (sheet != null)
            {
                PerLimbService.PoseBonePath poseBonePath = PerLimbService.parsePoseBonePath(sheet.id);

                if (poseBonePath != null)
                {
                    bone = poseBonePath.formPath().isEmpty() ? poseBonePath.bone() : poseBonePath.formPath() + "/" + poseBonePath.bone();
                    local = poseTransform.transform.isLocal();
                }
            }
        }

        if (bone != null)
        {
            return new Pair<>(bone, local);
        }

        return null;
    }

    /** The space of the active editable transform (mirrors
     *  {@code UIReplaysEditorUtils.getEditableTransform}'s dispatch — the bone
     *  tracks AND the form anchor), so the film gizmo is drawn in the very space
     *  its drag operates in. */
    public TransformSpace getBoneSpace()
    {
        UIKeyframeFactory editor = this.editor;

        if (editor instanceof UIPoseKeyframeFactory pose)
        {
            return pose.poseEditor.transform.getSpace();
        }
        else if (editor instanceof UITransformKeyframeFactory transform)
        {
            return transform.transform.getSpace();
        }
        else if (editor instanceof UIPoseTransformKeyframeFactory poseTransform)
        {
            return poseTransform.transform.getSpace();
        }
        else if (editor instanceof UIAnchorKeyframeFactory anchor)
        {
            return anchor.transform.getSpace();
        }

        return TransformSpace.LOCAL;
    }

    /**
     * Whether the active editor is the form's "anchor" property track — the one
     * that re-parents the whole form to another replay's attachment and carries
     * a {@link mchorse.bbs_mod.utils.pose.Transform} offset the gizmo can edit.
     * The IK/pole/physics target tracks reuse the {@code Anchor} value type but
     * are created without a backing property, so the {@code property != null}
     * test excludes them; the {@code "anchor"} id keeps it to the root form's
     * track, whose placement {@link mchorse.bbs_mod.film.BaseFilmController}
     * resolves from the entity's own {@code form.anchor}.
     */
    public boolean isFormAnchorTrack()
    {
        if (!(this.editor instanceof UIAnchorKeyframeFactory))
        {
            return false;
        }

        UIKeyframeSheet sheet = this.getSheet(this.editor.getKeyframe());

        return sheet != null && sheet.property != null && "anchor".equals(sheet.id);
    }

    public UICrowdWalkKeyframeFactory getCrowdMotionEditor()
    {
        return this.editor instanceof UICrowdWalkKeyframeFactory factory ? factory : null;
    }

    public boolean isCrowdWalkTrack()
    {
        UICrowdWalkKeyframeFactory factory = this.getCrowdMotionEditor();
        UIKeyframeSheet sheet = factory == null ? null : this.getSheet(factory.getMotionKeyframe());

        return sheet != null && "crowd_motion_path".equals(sheet.id);
    }

    /** Whether the anchor gizmo should be oriented in the bone's local space (mirrors {@link #getBone()}'s flag). */
    public boolean getAnchorLocal()
    {
        return this.editor instanceof UIAnchorKeyframeFactory factory && factory.transform.isLocal();
    }

    /**
     * Follow the tracks as they grow.
     *
     * <p>Alt and the wheel change how thick the tracks are drawn, which changes how much room they
     * need. Measured once, the sheet kept the height it had and the properties under it stayed
     * put, so thickening the tracks only bought a scrollbar. The height is re-taken whenever the
     * tracks report a different one.</p>
     */
    @Override
    public void render(UIContext context)
    {
        if (this.target == null)
        {
            /* Toggled in settings while open: rearrange side to below (or back) without a reopen. */
            if (this.propertiesBelowSupplier.getAsBoolean() != this.propertiesBelow)
            {
                this.propertiesBelow = !this.propertiesBelow;
                this.lastContentHeight = -1;

                if (this.editor != null)
                {
                    this.editor.removeFromParent();
                    this.editor = null;
                }

                this.applyViewFlex();
                this.pickKeyframe(null);
            }
            else if (this.propertiesBelow
                && this.view.getGraph().getContentHeight() != this.lastContentHeight)
            {
                this.applyViewFlex();
            }
        }

        super.render(context);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        KeyframeState state = new KeyframeState();

        state.extra = data.getMap("extra");

        for (BaseType type : data.getList("selection"))
        {
            state.selected.add(DataStorageUtils.intListFromData(type));
        }

        this.view.applyState(state);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        KeyframeState keyframeState = this.view.cacheState();
        ListType selection = new ListType();

        for (List<Integer> integers : keyframeState.selected)
        {
            selection.add(DataStorageUtils.intListToData(integers));
        }

        data.put("extra", keyframeState.extra);
        data.put("selection", selection);
    }
}
