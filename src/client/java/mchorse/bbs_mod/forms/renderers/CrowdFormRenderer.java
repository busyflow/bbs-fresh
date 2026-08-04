package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * A crowd has no geometry of its own.
 *
 * <p>Every member is a real spawned actor owned by {@code CrowdFormRuntimeManager}, so the
 * world already draws the crowd through the ordinary entity renderer. Drawing member copies
 * here as well would render the same formation twice: once as actors and once as client-side
 * duplicates standing in exactly the same places.</p>
 *
 * <p>What remains is the palette thumbnail, which shows the member form so a crowd is still
 * recognisable in the form picker.</p>
 */
public class CrowdFormRenderer extends FormRenderer<CrowdForm>
{
    public CrowdFormRenderer(CrowdForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Form member = this.form.getMemberForm();

        if (member != null && member != this.form)
        {
            FormUtilsClient.renderUI(member, context, x1, y1, x2, y2);
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {}
}
