package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.panels.UICrowdFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UICrowdForm extends UIForm<CrowdForm>
{
    public UICrowdForm()
    {
        this.defaultPanel = new UICrowdFormPanel(this);
        this.registerPanel(this.defaultPanel, IKey.constant("Crowd"), Icons.CHICKEN);
        this.registerDefaultPanels();
    }
}
