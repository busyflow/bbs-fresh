package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIActionsFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelConstraintsFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelIKFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelPhysicsFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class UIModelForm extends UIForm<ModelForm>
{
    public UIModelFormPanel modelPanel;

    public UIModelForm()
    {
        this.modelPanel = new UIModelFormPanel(this);
        this.modelPanel.poseEditor.transform.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(this.modelPanel.poseEditor.transform));
        this.modelPanel.poseEditor.transform.worldTransform(new FormBoneWorldProvider(this));
        this.modelPanel.poseEditor.transform.rotationConstrained(() ->
        {
            ModelForm form = this.form;
            ModelInstance instance = form == null ? null : ModelFormRenderer.getModel(form);

            return instance != null && ModelIKRuntime.isRotationConstrained(instance.model, form, this.modelPanel.poseEditor.groups.list.getCurrentFirst());
        });
        this.defaultPanel = this.modelPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MODEL_POSE, Icons.POSE);
        this.registerPanel(new UIModelIKFormPanel(this), UIKeys.FORMS_EDITORS_MODEL_IK, Icons.LIMB);
        this.registerPanel(new UIModelPhysicsFormPanel(this), UIKeys.FORMS_EDITORS_MODEL_PHYSICS_TITLE, Icons.DROP);
        this.registerPanel(new UIModelConstraintsFormPanel(this), UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_TITLE, Icons.LOCKED);
        this.registerPanel(new UIActionsFormPanel(this), UIKeys.FORMS_EDITORS_ACTIONS_TITLE, Icons.MORE);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.modelPanel)
            {
                this.setPanel(this.modelPanel);
            }

            this.modelPanel.pick.clickItself();
        });
    }

    @Override
    public UIPropTransform getEditableTransform()
    {
        return this.modelPanel.poseEditor.transform;
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.put("bones", DataStorageUtils.stringListToData(this.modelPanel.poseEditor.groups.list.getCurrent()));
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.has("bones"))
        {
            this.modelPanel.poseEditor.restoreSelection(DataStorageUtils.stringListFromData(data.get("bones")));
        }
    }

    @Override
    public Matrix4f getOrigin(float transition)
    {
        return this.withAnchorOffset(this.getOrigin(transition, this.bonePath(), this.activeEditor().transform.isLocal()));
    }

    @Override
    public Matrix4f getOriginMatrix(float transition)
    {
        return this.withAnchorOffset(this.getOrigin(transition, this.bonePath(), true));
    }

    @Override
    public TransformSpace getGizmoSpace()
    {
        return this.activeEditor().transform.getSpace();
    }

    /**
     * Which of the two bone lists the gizmo answers to. They clear each other's selection, so at most
     * one is ever picked; the anchors list wins while it holds the pick, otherwise it is the pose.
     */
    private UIPoseEditor activeEditor()
    {
        boolean anchors = !this.modelPanel.anchorsEditor.groups.list.getCurrent().isEmpty();

        return anchors ? this.modelPanel.anchorsEditor : this.modelPanel.poseEditor;
    }

    /**
     * Slide the gizmo out to where the anchor being edited actually sits, so the handles mark the point
     * the bone would rotate about rather than the bone's own origin - which is the only way to see an
     * anchor, it having no geometry of its own. Model units are sixteenths of a block, and the X axis
     * runs the other way in this space (see ICubicRenderer#moveToGroupPivot).
     */
    private Matrix4f withAnchorOffset(Matrix4f matrix)
    {
        if (matrix == null || this.modelPanel.anchorsEditor.groups.list.getCurrent().isEmpty())
        {
            return matrix;
        }

        PoseTransform anchor = this.form.secondaryAnchors.get().transforms.get(this.modelPanel.anchorsEditor.groups.list.getCurrentFirst());

        if (anchor == null)
        {
            return matrix;
        }

        Vector3f offset = anchor.translate;

        return new Matrix4f(matrix).translate(-offset.x / 16F, offset.y / 16F, offset.z / 16F);
    }

    private String bonePath()
    {
        return StringUtils.combinePaths(FormUtils.getPath(this.form), this.activeEditor().groups.list.getCurrentFirst());
    }

    /**
     * The additive euler base under the pose editor's channels for the picked
     * bone ({@link FormUtils#additivePoseRotationBase}): the total comes from
     * the bone's EVALUATED channels in the capture (rest + actions + the whole
     * pose stack) with the pose track's own contribution subtracted, so gizmo
     * deltas compose at the bone's effective angles. {@code null} for any other
     * transform editor — only the pose panel edits a pose-stacked track.
     */
    public Vector3f poseRotationBase(UIPropTransform transform, float transition)
    {
        if (transform != this.modelPanel.poseEditor.transform)
        {
            return null;
        }

        String bone = this.modelPanel.poseEditor.groups.list.getCurrentFirst();

        if (bone == null)
        {
            return null;
        }

        return FormUtils.additivePoseRotationBase(this.form.pose, bone, this.getEvaluatedRotation(transition, this.bonePath()));
    }

    @Override
    public boolean toggleBoneSelection(String bone)
    {
        if (!this.modelPanel.poseEditor.hasBone(bone))
        {
            return false;
        }

        this.modelPanel.poseEditor.selectBone(bone, true);

        return true;
    }
}
