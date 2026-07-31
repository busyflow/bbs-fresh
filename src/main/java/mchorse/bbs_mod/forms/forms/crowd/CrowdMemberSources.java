package mchorse.bbs_mod.forms.forms.crowd;

import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueList;

public class CrowdMemberSources extends ValueList<CrowdMemberSource>
{
    public CrowdMemberSources(String id)
    {
        super(id);
    }

    public CrowdMemberSource addSource(Form form)
    {
        CrowdMemberSource source = new CrowdMemberSource(String.valueOf(this.list.size()));

        source.form.set(form);
        this.preNotify();
        this.add(source);
        this.sync();
        this.postNotify();

        return source;
    }

    public void removeSource(CrowdMemberSource source)
    {
        this.preNotify();
        this.list.remove(source);
        this.sync();
        this.postNotify();
    }

    public CrowdMemberSource getSource(int index)
    {
        return index < 0 || index >= this.list.size() ? null : this.list.get(index);
    }

    public int selectSource(long memberId)
    {
        long total = 0L;

        for (CrowdMemberSource source : this.list)
        {
            if (source.getForm() != null && !(source.getForm() instanceof CrowdForm))
            {
                total += Math.max(1, source.weight.get());
            }
        }

        if (total <= 0L)
        {
            return -1;
        }

        long selection = Long.remainderUnsigned(mix(memberId), total);

        for (int i = 0; i < this.list.size(); i++)
        {
            CrowdMemberSource source = this.list.get(i);

            if (source.getForm() == null || source.getForm() instanceof CrowdForm)
            {
                continue;
            }

            selection -= Math.max(1, source.weight.get());

            if (selection < 0L)
            {
                return i;
            }
        }

        return -1;
    }

    public long signature()
    {
        long signature = this.list.size();

        for (CrowdMemberSource source : this.list)
        {
            signature = signature * 31L + source.weight.get();
            signature = signature * 31L + Float.floatToIntBits(source.minimumScale.get());
            signature = signature * 31L + Float.floatToIntBits(source.maximumScale.get());
            signature = signature * 31L + System.identityHashCode(source.getForm());
        }

        return signature;
    }

    public void validate()
    {
        for (CrowdMemberSource source : this.list)
        {
            source.validate();
        }
    }

    @Override
    protected CrowdMemberSource create(String id)
    {
        return new CrowdMemberSource(id);
    }

    private static long mix(long value)
    {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;

        return value;
    }
}
