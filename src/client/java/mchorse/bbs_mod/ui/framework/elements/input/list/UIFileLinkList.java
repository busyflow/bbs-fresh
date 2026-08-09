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
    /** Room under a thumbnail for its name. */
    private static final int LABEL_BAND = 14;
    private static final int CELL_GAP = 8;

    /** Thumbnail edge. {@link #TEXT_ROW} means the plain one-per-row name list. */
    private int thumb = TEXT_ROW;

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

        if (size == this.thumb)
        {
            return;
        }

        this.thumb = size;

        /* The list's own scrolling and hit testing are in rows of scrollItemSize, so a grid is
         * expressed to it as taller rows - one row of cells - and the columns are handled here. */
        this.scroll.scrollItemSize = this.cellHeight();
        this.scroll.scrollSpeed = this.cellHeight();

        this.update();
    }

    /** Zoomed in, files are laid out as a grid of thumbnails with their names underneath. */
    private boolean isGrid()
    {
        return this.thumb > TEXT_ROW;
    }

    private int cellHeight()
    {
        return this.isGrid() ? this.thumb + LABEL_BAND : TEXT_ROW;
    }

    private int cellWidth()
    {
        return this.isGrid() ? this.thumb + CELL_GAP : Math.max(1, this.area.w);
    }

    public int columns()
    {
        return this.isGrid() ? Math.max(1, this.area.w / this.cellWidth()) : 1;
    }

    private int count()
    {
        return this.isFiltering() ? this.filtered.size() : this.list.size();
    }

    @Override
    public void update()
    {
        int columns = this.columns();

        /* Scroll extent is in rows, and a grid row holds several files. */
        this.scroll.setSize((this.count() + columns - 1) / columns);
        this.scroll.clamp();
    }

    @Override
    public int getHoveredIndex(UIContext context)
    {
        int columns = this.columns();

        if (columns <= 1)
        {
            return super.getHoveredIndex(context);
        }

        if (!this.area.isInside(context))
        {
            return -1;
        }

        int column = (context.mouseX - this.area.x) / this.cellWidth();

        if (column < 0 || column >= columns)
        {
            return -1;
        }

        int row = (context.mouseY - this.area.y + (int) this.scroll.getScroll()) / this.cellHeight();

        return row * columns + column;
    }

    @Override
    public void renderList(UIContext context)
    {
        int columns = this.columns();

        if (columns <= 1)
        {
            super.renderList(context);

            return;
        }

        int cellW = this.cellWidth();
        int cellH = this.cellHeight();
        int count = this.count();

        for (int i = 0; i < count; i++)
        {
            int y = this.area.y + (i / columns) * cellH - (int) this.scroll.getScroll();

            if (y + cellH < this.area.y)
            {
                /* Skip the whole row rather than each of its cells. */
                i += columns - 1 - (i % columns);

                continue;
            }

            if (y >= this.area.ey())
            {
                break;
            }

            int x = this.area.x + (i % columns) * cellW;
            int index = this.isFiltering() ? this.filtered.get(i).b : i;
            FileLink element = this.isFiltering() ? this.filtered.get(i).a : this.list.get(i);
            boolean hover = context.mouseX >= x && context.mouseY >= y
                && context.mouseX < x + cellW && context.mouseY < y + cellH;

            this.renderCell(context, element, x, y, cellW, cellH, hover, this.current.contains(index));
        }
    }

    private void renderCell(UIContext context, FileLink element, int x, int y, int cellW, int cellH, boolean hover, boolean selected)
    {
        if (selected)
        {
            context.batcher.box(x, y, x + cellW, y + cellH, Colors.A50 | BBSSettings.primaryColor.get());
        }

        int box = this.thumb;
        int tx = x + (cellW - box) / 2;

        if (element.folder)
        {
            Icon icon = element.pinned ? Icons.BOOKMARK : Icons.FOLDER;

            context.batcher.icon(icon, Colors.setA(Colors.WHITE, hover ? 0.75F : 0.6F), x + (cellW - 16) / 2, y + (box - 16) / 2);
        }
        else
        {
            Texture texture = context.render.getTextures().getTexture(element.link);
            int w = box;
            int h = box;

            if (texture.width > texture.height)
            {
                h = Math.max(1, (int) (texture.height / (float) texture.width * box));
            }
            else if (texture.height > texture.width)
            {
                w = Math.max(1, (int) (texture.width / (float) texture.height * box));
            }

            int ix = x + (cellW - w) / 2;
            int iy = y + (box - h) / 2;

            context.batcher.iconArea(Icons.CHECKBOARD, ix, iy, w, h);
            context.batcher.fullTexturedBox(texture, ix, iy, w, h);
        }

        String title = context.batcher.getFont().limitToWidth(element.title, "...", cellW - 4);
        int textX = x + (cellW - context.batcher.getFont().getWidth(title)) / 2;

        context.batcher.textShadow(title, textX, y + box + 3, hover ? Colors.HIGHLIGHT : Colors.WHITE);
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

    /** The unzoomed list. Zoomed in, {@link #renderCell} draws the grid instead. */
    @Override
    protected void renderElementPart(UIContext context, FileLink element, int i, int x, int y, boolean hover, boolean selected)
    {
        Icon icon = element.pinned ? Icons.BOOKMARK : (element.folder ? Icons.FOLDER : Icons.IMAGE);

        context.batcher.icon(icon, Colors.setA(Colors.WHITE, hover ? 0.75F : 0.6F), x + 2, y);
        context.batcher.textShadow(element.title, x + 20, y + 4, hover ? Colors.HIGHLIGHT : Colors.WHITE);
    }

    /** Columns come from the width, so a resized picker has to re-measure its scroll extent. */
    @Override
    public void resize()
    {
        super.resize();

        if (this.isGrid())
        {
            this.update();
        }
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