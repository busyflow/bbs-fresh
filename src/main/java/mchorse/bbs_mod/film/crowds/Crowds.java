package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.utils.CollectionUtils;

public class Crowds extends ValueList<Crowd>
{
    public Crowds(String id)
    {
        super(id);
    }

    public Crowd addCrowd()
    {
        Crowd crowd = new Crowd(String.valueOf(this.list.size()));

        crowd.crowdTag.set(this.freeTag());

        this.preNotify();
        this.add(crowd);
        this.postNotify();

        return crowd;
    }

    public void remove(Crowd crowd)
    {
        int index = CollectionUtils.getIndex(this.list, crowd);

        if (CollectionUtils.inRange(this.list, index))
        {
            this.preNotify();
            this.list.remove(index);
            this.sync();
            this.postNotify();
        }
    }

    public Crowd byTag(String tag)
    {
        if (tag == null)
        {
            return null;
        }

        for (Crowd crowd : this.list)
        {
            if (crowd.crowdTag.get().equals(tag))
            {
                return crowd;
            }
        }

        return null;
    }

    /**
     * A tag no crowd in this film is using.
     *
     * <p>Tags are what members are addressed and identified by, so two crowds sharing one would
     * have each removing the other's members. Nobody types these any more, so nothing stops them
     * simply being made unique here.</p>
     */
    public String freeTag()
    {
        for (int i = 1; ; i++)
        {
            String tag = "crowd_" + i;

            if (this.byTag(tag) == null)
            {
                return tag;
            }
        }
    }

    @Override
    protected Crowd create(String id)
    {
        return new Crowd(id);
    }
}
