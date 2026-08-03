package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.crowd.CrowdMemberSource;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UICrowdFormPanel extends UIFormPanel<CrowdForm>
{
    private final UIButton source;
    private final UIButton addSource;
    private final UIButton removeSource;
    private final UIButton previousSource;
    private final UIButton nextSource;
    private final UIButton formation;
    private final UITrackpad sourceWeight;
    private final UITrackpad minimumScale;
    private final UITrackpad maximumScale;
    private final UITrackpad count;
    private final UITrackpad spacing;
    private final UITrackpad radius;
    private final UITrackpad hollow;
    private final UIElement hollowRow;
    private final UITrackpad seed;
    private final UITrackpad variation;
    private final UITrackpad renderBudget;
    private final UIToggle perBlock;

    private int selectedSource;

    public UICrowdFormPanel(UIForm editor)
    {
        super(editor);

        this.source = new UIButton(IKey.constant("Choose member form"), (b) -> this.pickSource(false));
        this.addSource = new UIButton(IKey.constant("Add member source"), (b) -> this.pickSource(true));
        this.removeSource = new UIButton(IKey.constant("Remove selected source"), (b) -> this.removeSelectedSource());
        this.previousSource = new UIButton(IKey.constant("Previous source"), (b) -> this.changeSource(-1));
        this.nextSource = new UIButton(IKey.constant("Next source"), (b) -> this.changeSource(1));
        this.formation = new UIButton(IKey.EMPTY, (b) -> this.openFormationPicker());
        this.sourceWeight = new UITrackpad((v) ->
        {
            CrowdMemberSource source = this.getSelectedSource();
            if (source != null) source.weight.set(v.intValue());
        }).limit(1, 10_000, true);
        this.minimumScale = new UITrackpad((v) ->
        {
            CrowdMemberSource source = this.getSelectedSource();
            if (source != null) source.minimumScale.set(v.floatValue());
        }).limit(0.01, 10);
        this.maximumScale = new UITrackpad((v) ->
        {
            CrowdMemberSource source = this.getSelectedSource();
            if (source != null) source.maximumScale.set(v.floatValue());
        }).limit(0.01, 10);
        this.count = new UITrackpad((v) -> this.form.count.set(v.intValue())).limit(1, 1_000_000, true);
        this.spacing = new UITrackpad((v) -> this.form.spacing.set(v.floatValue())).limit(0.1, 32);
        this.radius = new UITrackpad((v) -> this.form.radius.set(v.floatValue())).limit(0.1, 64);
        this.hollow = new UITrackpad((v) -> this.form.hollow.set(v.floatValue()))
            .limit(0, 0.95).increment(0.05).values(0.05, 0.01, 0.1);
        this.hollowRow = UI.labelRow(IKey.constant("Center hole"), this.hollow);
        this.seed = new UITrackpad((v) -> this.form.seed.set(v.intValue())).integer();
        this.variation = new UITrackpad((v) -> this.form.variation.set(v.floatValue())).limit(0, 180);
        this.renderBudget = new UITrackpad((v) -> this.form.renderBudget.set(v.intValue()))
            .limit(1, CrowdForm.MAX_RENDER_BUDGET, true);
        this.perBlock = new UIToggle(IKey.constant("Per block"), false, (b) -> this.form.perBlock.set(b.getValue()));

        this.options.add(
            UI.labelRow(IKey.constant("Member source"), this.source),
            this.previousSource,
            this.nextSource,
            this.addSource,
            this.removeSource,
            UI.labelRow(IKey.constant("Source weight"), this.sourceWeight),
            UI.labelRow(IKey.constant("Minimum scale"), this.minimumScale),
            UI.labelRow(IKey.constant("Maximum scale"), this.maximumScale),
            UI.labelRow(IKey.constant("Formation"), this.formation),
            this.perBlock,
            UI.labelRow(IKey.constant("Count"), this.count),
            UI.labelRow(IKey.constant("Spacing"), this.spacing),
            UI.labelRow(IKey.constant("Radius"), this.radius),
            this.hollowRow,
            UI.labelRow(IKey.constant("Seed"), this.seed),
            UI.labelRow(IKey.constant("Yaw variation"), this.variation),
            UI.labelRow(IKey.constant("Render budget"), this.renderBudget)
        );
    }

    private void pickSource(boolean add)
    {
        CrowdMemberSource selected = this.getSelectedSource();
        Form current = add || selected == null ? null : selected.getForm();
        UIFormPalette palette = UIFormPalette.open(this, true, current, true, (form) ->
        {
            if (form instanceof CrowdForm)
            {
                return;
            }

            Form copy = FormUtils.copy(form);

            if (add || selected == null)
            {
                this.form.sources.addSource(copy);
                this.selectedSource = this.form.sources.getAllTyped().size() - 1;
            }
            else
            {
                selected.form.set(copy);
            }

            if (this.selectedSource == 0)
            {
                this.form.memberForm.set(FormUtils.copy(form));
            }

            this.updateSourceControls();
        });

        if (palette != null)
        {
            palette.updatable();
        }
    }

    private void removeSelectedSource()
    {
        CrowdMemberSource source = this.getSelectedSource();

        if (source != null)
        {
            this.form.sources.removeSource(source);
            this.selectedSource = Math.max(0, Math.min(this.selectedSource, this.form.sources.getAllTyped().size() - 1));
            this.updateSourceControls();
        }
    }

    private void changeSource(int offset)
    {
        int size = this.form.sources.getAllTyped().size();

        if (size > 0)
        {
            this.selectedSource = Math.floorMod(this.selectedSource + offset, size);
            this.updateSourceControls();
        }
    }

    private CrowdMemberSource getSelectedSource()
    {
        return this.form == null ? null : this.form.sources.getSource(this.selectedSource);
    }

    @Override
    public void startEdit(CrowdForm form)
    {
        super.startEdit(form);

        this.selectedSource = Math.max(0, Math.min(this.selectedSource, form.sources.getAllTyped().size() - 1));
        this.count.setValue(form.count.get());
        this.spacing.setValue(form.spacing.get());
        this.radius.setValue(form.radius.get());
        this.hollow.setValue(form.hollow.get());
        this.seed.setValue(form.seed.get());
        this.variation.setValue(form.variation.get());
        this.renderBudget.setValue(form.renderBudget.get());
        this.perBlock.setValue(form.perBlock.get());
        this.updateLabels();
        this.updateSourceControls();
    }

    private void updateLabels()
    {
        CrowdFormation formation = CrowdFormation.get(this.form.formation.get());

        this.formation.label = IKey.constant(formation.title);
        this.hollowRow.setVisible(formation == CrowdFormation.CIRCLE);
        this.options.resize();
    }

    private void openFormationPicker()
    {
        UIContext context = this.getContext();

        if (context == null || this.form == null)
        {
            return;
        }

        context.replaceContextMenu((menu) ->
        {
            menu.autoKeys();

            for (CrowdFormation formation : CrowdFormation.selectableValues())
            {
                menu.action(Icons.CIRCLE, IKey.constant(formation.title),
                    formation.ordinal() == this.form.formation.get(), () ->
                    {
                        this.form.formation.set(formation.ordinal());
                        this.updateLabels();
                    });
            }
        });
    }

    private void updateSourceControls()
    {
        CrowdMemberSource source = this.getSelectedSource();
        int size = this.form.sources.getAllTyped().size();

        this.source.label = IKey.constant(source == null || source.getForm() == null
            ? "Choose member form"
            : "[" + (this.selectedSource + 1) + "/" + size + "] " + source.getForm().getDisplayName());

        if (source != null)
        {
            this.sourceWeight.setValue(source.weight.get());
            this.minimumScale.setValue(source.minimumScale.get());
            this.maximumScale.setValue(source.maximumScale.get());
        }
        else
        {
            this.sourceWeight.setValue(1);
            this.minimumScale.setValue(1);
            this.maximumScale.setValue(1);
        }
    }
}
