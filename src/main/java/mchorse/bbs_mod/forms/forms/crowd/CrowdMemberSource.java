package mchorse.bbs_mod.forms.forms.crowd;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

public class CrowdMemberSource extends ValueGroup
{
    public final ValueForm form = new ValueForm("form");
    public final ValueInt weight = new ValueInt("weight", 1, 1, 10_000);
    public final ValueFloat minimumScale = new ValueFloat("minimum_scale", 1F, 0.01F, 10F);
    public final ValueFloat maximumScale = new ValueFloat("maximum_scale", 1F, 0.01F, 10F);

    public CrowdMemberSource(String id)
    {
        super(id);

        this.add(this.form);
        this.add(this.weight);
        this.add(this.minimumScale);
        this.add(this.maximumScale);
    }

    public Form getForm()
    {
        return this.form.get();
    }

    public float getScale(float random)
    {
        float minimum = Math.min(this.minimumScale.get(), this.maximumScale.get());
        float maximum = Math.max(this.minimumScale.get(), this.maximumScale.get());

        return minimum + (maximum - minimum) * random;
    }

    public void validate()
    {
        this.weight.set(Math.max(1, this.weight.get()));
        this.minimumScale.set(Math.max(0.01F, this.minimumScale.get()));
        this.maximumScale.set(Math.max(0.01F, this.maximumScale.get()));
    }
}
