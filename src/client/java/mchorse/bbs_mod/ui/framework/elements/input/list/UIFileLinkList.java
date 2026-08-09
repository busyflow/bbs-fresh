package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class UIFileLinkList extends UIList<UIFileLinkList.FileLink>
{
    /** Row height that fits a name and nothing else - the list as it has always looked. */
    public static final int TEXT_ROW = 16;
    /** Biggest thumbnail worth showing; past this a folder holds too few rows to browse. */
    public static final int MAX_ROW = 128;
    private static final int ZOOM_STEP = 8;

    public Consumer<Link> fileCallback;
    public Link path = new Link("", "");
    public Predicate<Link> filter;

    public UIFileLinkList(Consumer<Link> fileCallback)
    {
        super(null);

        this.context((menu) ->
        {
            int index = this.getIndexAtCursor(this.getContext());

            if (!this.exists(index))
            {
                return;
            }

            FileLink hovered = this.list.get(index);

            if (!hovered.folder || hovered.title.equals(".."))
            {
                return;
            }

            if (isPinned(hovered.link))
            {
                menu.action(Icons.CLOSE, UIKeys.FILES_UNPIN, () -> this.pin(hovered.link, false));
            }
            else
            {
                menu.action(Icons.BOOKMARK, UIKeys.FILES_PIN, () -> this.pin(hovered.link, true));
            }
        });

        this.callback = (list) ->
        {
            FileLink fileLink = list.get(0);

            if (!fileLink.folder)
            {
                if (this.fileCallback != null)
                {
                    this.fileCallback.accept(fileLink.link);
                }
            }
            else
            {
                this.setPath(fileLink.link, !fileLink.title.equals(".."));
            }
        };
        this.fileCallback = fileCallback;
        this.scroll.scrollItemSize = 16;
        this.scroll.scrollSpeed = 16;
    }

    public UIFileLinkList filter(Predicate<Link> filter)
    {
        this.filter = filter;

        return this;
    }

    /**
     * Ctrl + wheel grows the rows into thumbnails, so a folder of skins can be read by eye
     * rather than by filename. Plain wheel still scrolls.
     */
    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (Window.isCtrlPressed() && context.mouseWheel != 0D)
        {
            this.setRowSize(this.scroll.scrollItemSize + (context.mouseWheel > 0D ? ZOOM_STEP : -ZOOM_STEP));

            return true;
        }

        return super.subMouseScrolled(context);
    }

    public void setRowSize(int size)
    {
        size = MathUtils.clamp(size, TEXT_ROW, MAX_ROW);

        if (size == this.scroll.scrollItemSize)
        {
            return;
        }

        this.scroll.scrollItemSize = size;
        /* Wheel steps stay one row, the way they do at any other size. */
        this.scroll.scrollSpeed = size;

        this.update();
    }

    public void setPath(Link link)
    {
        this.setPath(link, true);
    }

    /**
     * Set current link
     */
    public void setPath(Link link, boolean fastForward)
    {
        if (link == null || link.source.isEmpty())
        {
            this.clear();

            this.path = new Link("", "");

            for (String source : BBSMod.getProvider().getSourceKeys())
            {
                this.add(new FileLink(source, new Link(source, ""), true));
            }

            this.addPins();
            this.sort();
        }
        else
        {
            Collection<Link> links = BBSMod.getProvider().getLinksFromPath(link, false);

            if (fastForward && links.size() == 1)
            {
                Link first = links.iterator().next();

                if (first.path.endsWith("/"))
                {
                    this.setPath(first);

                    return;
                }
            }

            this.path = link;

            FileLink parent = link.path.isEmpty()
                ? new FileLink("..", new Link("", ""), true)
                : new FileLink("..", new Link(link.source, StringUtils.parentPath(link.path)), true);

            this.clear();
            this.add(parent);
            this.addPins();

            for (Link l : links)
            {
                if (this.filter == null || this.filter.test(l))
                {
                    this.add(new FileLink(StringUtils.fileName(l.path).replaceAll("/", ""), l, l.path.endsWith("/")));
                }
            }

            this.sort();
        }
    }

    /* Pinned folders */

    private static String pinKey(Link link)
    {
        return link.source + ":" + link.path;
    }

    public static boolean isPinned(Link link)
    {
        return link != null && BBSSettings.pinnedFolders.get().contains(pinKey(link));
    }

    private void pin(Link link, boolean pinned)
    {
        Set<String> pins = new HashSet<>(BBSSettings.pinnedFolders.get());

        if (pinned)
        {
            pins.add(pinKey(link));
        }
        else
        {
            pins.remove(pinKey(link));
        }

        /* set() rather than mutating what get() returns - that is what marks the setting dirty
         * and gets it written back to disk. */
        BBSSettings.pinnedFolders.set(pins);

        this.setPath(this.path, false);
    }

    /**
     * Add every pinned folder to the current view, so a pin is one click away from wherever you
     * are rather than only from the root.
     *
     * <p>The folder you are standing in is left out - a shortcut to here is a row that does
     * nothing, and the point of the pins is to not have to read past rows that do nothing.</p>
     */
    private void addPins()
    {
        for (String key : BBSSettings.pinnedFolders.get())
        {
            int colon = key.indexOf(':');

            if (colon < 0)
            {
                continue;
            }

            Link link = new Link(key.substring(0, colon), key.substring(colon + 1));

            if (link.equals(this.path))
            {
                continue;
            }

            FileLink pin = new FileLink(StringUtils.fileName(link.path).replaceAll("/", ""), link, true);

            pin.pinned = true;

            this.add(pin);
        }
    }

    public void setCurrent(Link link)
    {
        this.setCurrent(link, false);
    }

    public void setCurrent(Link link, boolean scroll)
    {
        this.deselect();

        if (link == null)
        {
            return;
        }

        for (FileLink entry : this.list)
        {
            if (entry.link.equals(link))
            {
                if (scroll) this.setCurrentScroll(entry);
                else this.setCurrent(entry);

                return;
            }
        }
    }

    @Override
    protected boolean sortElements()
    {
        this.list.sort((a, b) ->
        {
            /* ".." keeps the top, then the pins, then the folder's own contents. */
            boolean upA = a.folder && a.title.equals("..");
            boolean upB = b.folder && b.title.equals("..");

            if (upA != upB)
            {
                return upA ? -1 : 1;
            }

            if (a.pinned != b.pinned)
            {
                return a.pinned ? -1 : 1;
            }

            if (a.folder != b.folder)
            {
                return a.folder ? -1 : 1;
            }

            return NaturalOrderComparator.compare(true, a.title, b.title);
        });

        return true;
    }

    @Override
    protected void renderElementPart(UIContext context, FileLink element, int i, int x, int y, boolean hover, boolean selected)
    {
        int size = this.scroll.scrollItemSize;
        int color = hover ? Colors.HIGHLIGHT : Colors.WHITE;

        if (size <= TEXT_ROW || element.folder)
        {
            /* Folders have nothing to show, so they keep the icon and only follow the row height. */
            int iconY = y + (size - TEXT_ROW) / 2;
            Icon icon = element.pinned ? Icons.BOOKMARK : (element.folder ? Icons.FOLDER : Icons.IMAGE);

            context.batcher.icon(icon, Colors.setA(Colors.WHITE, hover ? 0.75F : 0.6F), x + 2, iconY);
            context.batcher.textShadow(element.title, x + 20, iconY + 4, color);

            return;
        }

        int box = size - 4;
        Texture texture = context.render.getTextures().getTexture(element.link);
        int w = box;
        int h = box;

        /* Fit rather than stretch - skins are twice as wide as they are tall, and a stretched
         * one is harder to tell from its neighbour than the filename was. */
        if (texture.width > texture.height)
        {
            h = Math.max(1, (int) (texture.height / (float) texture.width * box));
        }
        else if (texture.height > texture.width)
        {
            w = Math.max(1, (int) (texture.width / (float) texture.height * box));
        }

        int tx = x + 2 + (box - w) / 2;
        int ty = y + 2 + (box - h) / 2;

        context.batcher.iconArea(Icons.CHECKBOARD, tx, ty, w, h);
        context.batcher.fullTexturedBox(texture, tx, ty, w, h);
        context.batcher.textShadow(element.title, x + box + 8, y + (size - context.batcher.getFont().getHeight()) / 2, color);
    }

    public static class FileLink
    {
        public String title;
        public Link link;
        public boolean folder;
        /** A shortcut row rather than something living in this folder. */
        public boolean pinned;

        public FileLink(String title, Link link, boolean folder)
        {
            this.title = title;
            this.link = link;
            this.folder = folder;
        }
    }
}