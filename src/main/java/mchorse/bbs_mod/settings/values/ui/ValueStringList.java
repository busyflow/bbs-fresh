package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered, de-duplicated list of strings persisted as a {@link ListType}.
 *
 * <p>Unlike {@link ValueStringKeys} (a {@code Set}, unordered) this keeps
 * insertion order, which matters for things like the texture picker's pinned
 * quick-access folders where the user expects their pins to stay where they
 * put them.</p>
 */
public class ValueStringList extends BaseValueBasic<List<String>>
{
    public ValueStringList(String id)
    {
        super(id, new ArrayList<>());
    }

    public List<String> getList()
    {
        return this.get();
    }

    public boolean contains(String value)
    {
        return this.get().contains(value);
    }

    /** Add a value to the end if it isn't already present. Returns true if added. */
    public boolean add(String value)
    {
        if (value == null || value.isEmpty() || this.get().contains(value))
        {
            return false;
        }

        List<String> list = new ArrayList<>(this.get());

        list.add(value);
        this.set(list);

        return true;
    }

    public boolean remove(String value)
    {
        if (!this.get().contains(value))
        {
            return false;
        }

        List<String> list = new ArrayList<>(this.get());

        list.remove(value);
        this.set(list);

        return true;
    }

    /** Add the value if missing, remove it if present. Returns true when it ends up pinned. */
    public boolean toggle(String value)
    {
        if (this.contains(value))
        {
            this.remove(value);

            return false;
        }

        this.add(value);

        return true;
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (String s : this.value)
        {
            list.addString(s);
        }

        return list;
    }

    @Override
    public void fromData(BaseType data)
    {
        List<String> list = new ArrayList<>();

        if (data.isList())
        {
            for (BaseType type : data.asList())
            {
                if (type.isString())
                {
                    list.add(type.asString());
                }
            }
        }

        this.value = list;
    }
}
