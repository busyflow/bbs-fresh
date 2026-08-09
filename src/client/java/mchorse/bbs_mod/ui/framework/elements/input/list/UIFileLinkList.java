package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class UIFileLinkList extends UIList<UIFileLinkList.FileLink>
{
    /** Row height that fits a name and nothing else - the list as it has always looked. */
    public static final int TEXT_ROW = 16;
    /** Biggest cell worth showing; past this a folder holds too few rows to browse. */
    public static final int MAX_ROW = 128;
    /**
     * Cell size at which the list stops being rows and becomes a grid.
     *
     * <p>Below it, zooming grows the row and puts a thumbnail beside the name, which is the
     * readable shape while the picture is still small. Above it the picture carries the row on
     * its own and one-per-row is mostly empty width.</p>
     */
    public static final int GRID_MIN = 40;
    private static final int ZOOM_STEP = 8;
    /** Kept clear on the right so a thumbnail never sits under the scrollbar. */
    private static final int SCROLLBAR_ROOM = 6;

    public Consumer<Link> fileCallback;
    public Link path = new Link("", "");
    public Predicate<Link> filter;

    public UIFileLinkList(Consumer<Link> fileCallback)
    {
        super(null);

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

        /* Come back at the zoom that was last dialled in - the size you browse skins at is a
         * preference, not something to set again every time a picker opens. */
        this.applyCellSize(BBSSettings.texturePickerThumbnailSize.get());
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
        if (this.area.isInside(context) && Window.isCtrlPressed() && context.mouseWheel != 0D)
        {
            this.setCellSize(this.scroll.scrollItemSize + (int) Math.copySign(ZOOM_STEP, context.mouseWheel));

            return true;
        }

        return super.subMouseScrolled(context);
    }

    /** Resize the cells without remembering it - for restoring the remembered size itself. */
    public void applyCellSize(int size)
    {
        this.scroll.scrollItemSize = MathUtils.clamp(size, TEXT_ROW, MAX_ROW);
        this.scroll.scrollSpeed = this.scroll.scrollItemSize;

        this.update();
    }

    public void setCellSize(int size)
    {
        size = MathUtils.clamp(size, TEXT_ROW, MAX_ROW);

        if (size == this.scroll.scrollItemSize)
        {
            return;
        }

        this.applyCellSize(size);
        BBSSettings.texturePickerThumbnailSize.set(size);
    }

    /** Zoomed past {@link #GRID_MIN}, files tile across the width instead of stacking as rows. */
    public boolean isGrid()
    {
        return this.scroll.scrollItemSize >= GRID_MIN;
    }

    public int columns()
    {
        if (!this.isGrid())
        {
            return 1;
        }

        return Math.max(1, (this.area.w - SCROLLBAR_ROOM) / Math.max(1, this.scroll.scrollItemSize));
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

        int cell = this.scroll.scrollItemSize;
        int column = (context.mouseX - this.area.x) / cell;

        if (column < 0 || column >= columns)
        {
            return -1;
        }

        int row = (context.mouseY - this.area.y + (int) this.scroll.getScroll()) / cell;
        int visible = row * columns + column;

        if (visible < 0 || visible >= this.count())
        {
            return -1;
        }

        /* Filtering renumbers what is on screen, so hand back the index into the backing list -
         * every caller (selection, the context menu) means that one. */
        return this.isFiltering() ? this.filtered.get(visible).b : visible;
    }

    /** Scroll to the row a selection sits in, which in a grid is not the selection's own index. */
    @Override
    public void setCurrentScroll(FileLink element)
    {
        this.setCurrent(element);

        if (!this.current.isEmpty())
        {
            int index = this.current.get(0);
            int row = this.isGrid() ? index / Math.max(1, this.columns()) : index;

            this.scroll.setScroll(row * this.scroll.scrollItemSize);
        }
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

        int cell = this.scroll.scrollItemSize;
        int count = this.count();
        int scroll = (int) this.scroll.getScroll();

        for (int i = 0; i < count; i++)
        {
            int y = this.area.y + (i / columns) * cell - scroll;

            if (y + cell <= this.area.y)
            {
                /* Skip the rest of the row rather than testing each of its cells. */
                i += columns - 1 - (i % columns);

                continue;
            }

            if (y >= this.area.ey())
            {
                break;
            }

            int x = this.area.x + (i % columns) * cell;
            int index = this.isFiltering() ? this.filtered.get(i).b : i;
            FileLink element = this.isFiltering() ? this.filtered.get(i).a : this.list.get(i);
            boolean hover = this.area.isInside(context)
                && context.mouseX >= x && context.mouseX < x + cell
                && context.mouseY >= y && context.mouseY < y + cell;

            this.renderCell(context, element, x, y, cell, hover, this.current.contains(index));
        }
    }

    private void renderCell(UIContext context, FileLink element, int x, int y, int cell, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        int labelHeight = font.getHeight() + 2;
        int pad = 3;

        if (selected)
        {
            context.batcher.box(x + 1, y + 1, x + cell - 1, y + cell - 1, Colors.A50 | BBSSettings.primaryColor.get());
        }
        else if (hover)
        {
            context.batcher.box(x + 1, y + 1, x + cell - 1, y + cell - 1, Colors.setA(Colors.WHITE, 0.08F));
        }

        int box = Math.max(8, cell - pad * 2 - labelHeight);

        this.renderThumb(context, element, x + (cell - box) / 2, y + pad, box);

        String title = font.limitToWidth(element.title, "...", cell - 4);

        context.batcher.textShadow(title, x + (cell - font.getWidth(title)) / 2, y + cell - labelHeight + 1, hover ? Colors.HIGHLIGHT : Colors.WHITE);
    }

    /**
     * The entry's picture, fitted into a square box: the PNG itself over a checkerboard so
     * transparency reads, or a scaled icon when there is no picture to show.
     */
    private void renderThumb(UIContext context, FileLink element, int x, int y, int box)
    {
        if (element.folder)
        {
            context.batcher.iconArea(Icons.FOLDER, Colors.setA(Colors.WHITE, 0.7F), x, y, box, box);

            return;
        }

        Texture texture = null;

        try
        {
            texture = context.render.getTextures().getTexture(element.link);
        }
        catch (Exception e)
        {}

        /* A file that will not load must not take the picker down with it - the placeholder is
         * also how you spot the broken one. */
        if (texture == null || texture.width <= 0 || texture.height <= 0)
        {
            context.batcher.iconArea(Icons.IMAGE, Colors.setA(Colors.WHITE, 0.5F), x, y, box, box);

            return;
        }

        int w = box;
        int h = box;

        /* Fit rather than stretch - a skin is twice as wide as it is tall, and a stretched one is
         * harder to tell from its neighbour than the filename was. */
        if (texture.width > texture.height)
        {
            h = Math.max(1, (int) (texture.height / (float) texture.width * box));
        }
        else if (texture.height > texture.width)
        {
            w = Math.max(1, (int) (texture.width / (float) texture.height * box));
        }

        context.batcher.iconArea(Icons.CHECKBOARD, x + (box - w) / 2, y + (box - h) / 2, w, h);
        context.batcher.fullTexturedBox(texture, x + (box - w) / 2, y + (box - h) / 2, w, h);
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
            if (a.folder != b.folder)
            {
                return a.folder ? -1 : 1;
            }

            return NaturalOrderComparator.compare(true, a.title, b.title);
        });

        return true;
    }

    /** Rows, up to {@link #GRID_MIN}. Past that {@link #renderCell} draws the grid instead. */
    @Override
    protected void renderElementPart(UIContext context, FileLink element, int i, int x, int y, boolean hover, boolean selected)
    {
        int row = this.scroll.scrollItemSize;
        int color = hover ? Colors.HIGHLIGHT : Colors.WHITE;

        if (row <= TEXT_ROW)
        {
            Icon icon = element.folder ? Icons.FOLDER : Icons.IMAGE;

            context.batcher.icon(icon, Colors.setA(Colors.WHITE, hover ? 0.75F : 0.6F), x + 2, y);
            context.batcher.textShadow(element.title, x + 20, y + 4, color);

            return;
        }

        /* Grown but not yet a grid: a real thumbnail beside the name, which is the readable shape
         * while the picture is still too small to identify on its own. */
        FontRenderer font = context.batcher.getFont();
        int pad = 2;
        int box = row - pad * 2;

        this.renderThumb(context, element, x + pad, y + pad, box);
        context.batcher.textShadow(element.title, x + box + 8, y + (row - font.getHeight()) / 2 + 1, color);
    }

    /** Columns come from the width, so a resized picker has to re-measure its scroll extent. */
    @Override
    public void resize()
    {
        super.resize();
        this.update();
    }

    public static class FileLink
    {
        public String title;
        public Link link;
        public boolean folder;

        public FileLink(String title, Link link, boolean folder)
        {
            this.title = title;
            this.link = link;
            this.folder = folder;
        }
    }
}