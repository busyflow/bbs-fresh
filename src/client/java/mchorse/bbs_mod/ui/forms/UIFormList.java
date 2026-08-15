package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIRecentFormCategory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.morphing.UIMorphFormCategoryFilterOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.DiffuseLighting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UIFormList extends UIElement
{
    public IUIFormList palette;

    public UIScrollView forms;

    public UIElement bar;
    public UITextbox search;
    public UIIcon edit;
    public UIIcon close;
    public UIIcon categoryFilter;

    private UIFormCategory recent;
    private List<UIFormCategory> categories = new ArrayList<>();

    private long lastUpdate;
    private int lastScroll;
    private boolean pendingScrollToSelected;

    /* Drag reorder. Coordinated here rather than per category because a drag can cross from one
     * category into another; the source category and the form being carried are held until release. */
    /** Pixels the pointer must travel before a press turns into a drag rather than a plain click. */
    private static final int DRAG_THRESHOLD = 6;

    private UIFormCategory dragSource;
    private Form dragForm;
    private int dragStartX;
    private int dragStartY;

    public UIFormList(IUIFormList palette)
    {
        this.palette = palette;

        this.forms = UI.scrollView(0, 0);
        this.forms.scroll.cancelScrolling();
        this.bar = new UIElement();
        this.search = new UITextbox(100, this::onSearchQuery).placeholder(UIKeys.FORMS_LIST_SEARCH);
        this.edit = new UIIcon(Icons.EDIT, this::edit);
        this.edit.tooltip(UIKeys.FORMS_LIST_EDIT, Direction.TOP);
        this.close = new UIIcon(Icons.CLOSE, this::close);

        this.forms.full(this);
        this.bar.relative(this).x(10).y(1F, -30).w(1F, -20).h(20).row().height(20);
        this.close.w(20);

        this.categoryFilter = new UIIcon(Icons.FILTER, this::openMorphCategoryFilter);
        this.categoryFilter.tooltip(UIKeys.MORPHING_FILTER_CATEGORIES, Direction.TOP);
        this.categoryFilter.w(20);
        this.bar.add(this.categoryFilter, this.search, this.edit, this.close);

        this.add(this.forms, this.bar);

        this.search.keys().register(Keys.FORMS_FOCUS, this::focusSearchInput);

        this.markContainer();
        this.setupForms(BBSModClient.getFormCategories());
    }

    private void openMorphCategoryFilter(UIIcon b)
    {
        Set<String> disabled = BBSSettings.disabledMorphFormCategories.get();
        FormCategories formCategories = BBSModClient.getFormCategories();
        UIMorphFormCategoryFilterOverlayPanel panel = new UIMorphFormCategoryFilterOverlayPanel(
            disabled,
            formCategories.getAllCategories()
        );

        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

        panel.onClose(e ->
        {
            BBSSettings.disabledMorphFormCategories.set(disabled);
            Form selected = this.getSelected();
            this.setupForms(formCategories);
            this.setSelected(selected);
        });
    }

    public void focusSearchInput()
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            this.search.clickItself(context);
        }
    }

    public void setupForms(FormCategories forms)
    {
        this.categories.clear();
        this.forms.removeAll();

        for (FormCategory category : forms.getAllCategories())
        {
            if (BBSSettings.disabledMorphFormCategories.get().contains(category.visible.getId()))
            {
                continue;
            }

            UIFormCategory uiCategory = category.createUI(this);

            this.forms.add(uiCategory);
            this.categories.add(uiCategory);

            if (uiCategory instanceof UIRecentFormCategory)
            {
                this.recent = uiCategory;
            }
        }

        if (!this.categories.isEmpty())
        {
            this.categories.get(this.categories.size() - 1).marginBottom(40);
        }

        this.resize();

        this.lastUpdate = forms.getLastUpdate();
        this.applySearchFromTextbox();
    }

    private void onSearchQuery(String search)
    {
        this.applySearchFilter(search);
    }

    private void applySearchFromTextbox()
    {
        this.applySearchFilter(this.search.getText());
    }

    private void applySearchFilter(String raw)
    {
        String s = raw == null ? "" : raw.trim();

        for (UIFormCategory category : this.categories)
        {
            category.search(s);
        }

        this.afterSearchLayout();
    }

    private void afterSearchLayout()
    {
        int columnW = Math.max(UIFormCategory.CELL_WIDTH, this.forms.area.w);

        for (UIFormCategory category : this.categories)
        {
            category.refreshLayoutForSearch(columnW);
        }

        this.forms.resize();
        this.resize();
    }

    private void edit(UIIcon b)
    {
        this.palette.toggleEditor();
    }

    private void close(UIIcon b)
    {
        this.palette.exit();
    }

    public void selectCategory(UIFormCategory category, Form form, boolean notify)
    {
        this.deselect();

        category.selected = form;

        if (notify)
        {
            this.palette.accept(form);
        }
    }

    public void deselect()
    {
        for (UIFormCategory category : this.categories)
        {
            category.selected = null;
        }
    }

    public UIFormCategory getSelectedCategory()
    {
        for (UIFormCategory category : this.categories)
        {
            if (category.selected != null)
            {
                return category;
            }
        }

        return null;
    }

    public Form getSelected()
    {
        UIFormCategory category = this.getSelectedCategory();

        return category == null ? null : category.selected;
    }

    public void setSelected(Form form)
    {
        boolean found = false;

        this.deselect();

        for (UIFormCategory category : this.categories)
        {
            int index = category.category.getForms().indexOf(form);

            if (index == -1)
            {
                category.selected = null;
            }
            else
            {
                found = true;

                category.select(category.category.getForms().get(index), false);
            }
        }

        if (!found && form != null && this.recent != null)
        {
            Form copy = FormUtils.copy(form);

            this.recent.category.addForm(copy);
            this.recent.select(copy, false);
        }
    }

    /**
     * Request the list to scroll so that the currently selected form becomes
     * visible. The actual scrolling is deferred to {@link #render(UIContext)}
     * because it needs the list's real (resized) width to lay the categories
     * out at their final height.
     */
    public void scrollToSelected()
    {
        this.pendingScrollToSelected = true;
    }

    private void scrollToSelectedForm()
    {
        UIFormCategory category = this.getSelectedCategory();

        if (category == null)
        {
            return;
        }

        /* Categories only learn their real height once they render (until then
         * they keep the inflated, width-0 height from setupForms), so the bounds
         * of off-screen categories above the selection are stale. Lay them all
         * out at the real width first so the offsets below are final. */
        this.afterSearchLayout();

        int contentY = category.area.y - this.forms.area.y;
        int itemHeight = UIFormCategory.HEADER_HEIGHT;
        int index = category.getForms().indexOf(category.selected);

        if (category.category.visible.get() && index >= 0)
        {
            int columnW = Math.max(UIFormCategory.CELL_WIDTH, this.forms.area.w);
            int perRow = Math.max(1, columnW / UIFormCategory.CELL_WIDTH);

            contentY += UIFormCategory.HEADER_HEIGHT + (index / perRow) * UIFormCategory.CELL_HEIGHT;
            itemHeight = UIFormCategory.CELL_HEIGHT;
        }

        /* Center the selected form (or the category header when it's collapsed)
         * within the visible area; scrollTo() clamps to the scroll bounds. */
        this.forms.scroll.setScroll(contentY - (this.forms.area.h - itemHeight) / 2);
    }

    public void beginFormDrag(UIFormCategory source, Form form, int mouseX, int mouseY)
    {
        this.dragSource = source;
        this.dragForm = form;
        this.dragStartX = mouseX;
        this.dragStartY = mouseY;
    }

    /** The category whose grid the pointer sits over, if it can take a dropped form. */
    private UIFormCategory categoryAt(int mouseX, int mouseY)
    {
        for (UIFormCategory category : this.categories)
        {
            if (category.category.canModify(null) && category.category.visible.get()
                && mouseX >= category.area.x && mouseX <= category.area.ex()
                && mouseY >= category.area.y && mouseY < category.area.ey())
            {
                return category;
            }
        }

        return null;
    }

    /** Slot 0..size in a category where the pointer sits, rounded to the nearest gap between cells. */
    private int insertionIndex(UIFormCategory category, int mouseX, int mouseY)
    {
        int perRow = this.perRow(category);
        int size = category.category.getForms().size();
        int localY = mouseY - category.area.y - UIFormCategory.HEADER_HEIGHT;

        if (localY < 0)
        {
            return 0;
        }

        int row = localY / UIFormCategory.CELL_HEIGHT;
        int col = MathUtils.clamp((mouseX - category.area.x + UIFormCategory.CELL_WIDTH / 2) / UIFormCategory.CELL_WIDTH, 0, perRow);

        return MathUtils.clamp(row * perRow + col, 0, size);
    }

    private int perRow(UIFormCategory category)
    {
        return Math.max(1, Math.max(UIFormCategory.CELL_WIDTH, category.area.w) / UIFormCategory.CELL_WIDTH);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragForm != null)
        {
            this.finishFormDrag(context);
        }

        return super.subMouseReleased(context);
    }

    /** A press only counts as a drag once the pointer has left a small dead-zone around where it went down. */
    private boolean dragMovedEnough(UIContext context)
    {
        return Math.abs(context.mouseX - this.dragStartX) > DRAG_THRESHOLD
            || Math.abs(context.mouseY - this.dragStartY) > DRAG_THRESHOLD;
    }

    private void finishFormDrag(UIContext context)
    {
        UIFormCategory source = this.dragSource;
        Form form = this.dragForm;
        boolean moved = this.dragMovedEnough(context);

        this.dragSource = null;
        this.dragForm = null;

        /* A click that never really moved is just a selection (already handled on press) - never a reorder. */
        if (!moved)
        {
            return;
        }

        UIFormCategory target = this.categoryAt(context.mouseX, context.mouseY);

        if (source == null || target == null)
        {
            return;
        }

        int from = source.category.getForms().indexOf(form);
        int insert = this.insertionIndex(target, context.mouseX, context.mouseY);

        if (from == -1)
        {
            return;
        }

        /* Dropped onto its own slot (either edge of where it already sits): nothing to do. */
        if (source == target && (insert == from || insert == from + 1))
        {
            return;
        }

        /* Same category and dropped after itself: removing it first shifts the target down one. */
        if (source == target && insert > from)
        {
            insert -= 1;
        }

        source.category.removeForm(form);
        target.category.insertForm(insert, form);
        target.select(form, false);
    }

    @Override
    public void render(UIContext context)
    {
        FormCategories categories = BBSModClient.getFormCategories();

        if (this.lastScroll >= 0)
        {
            this.forms.scroll.scrollTo(this.lastScroll);

            this.lastScroll = -1;
        }

        if (this.lastUpdate != categories.getLastUpdate())
        {
            this.lastScroll = (int) this.forms.scroll.getScroll();

            Form selected = this.getSelected();

            this.setupForms(categories);
            this.setSelected(selected);
        }

        DiffuseLighting.enableGuiDepthLighting();

        super.render(context);

        DiffuseLighting.disableGuiDepthLighting();

        if (this.pendingScrollToSelected && this.forms.area.w > 0)
        {
            this.scrollToSelectedForm();

            this.pendingScrollToSelected = false;
        }

        /* Render form's display name and ID */
        Form selected = this.getSelected();

        if (selected != null)
        {
            String displayName = selected.getDisplayName();
            String id = selected.getFormId();
            FontRenderer font = context.batcher.getFont();

            int w = Math.max(font.getWidth(displayName), font.getWidth(id));
            int x = this.search.area.x;
            int y = this.search.area.y - 24;

            context.batcher.box(x, y, x + w + 8, this.search.area.y, Colors.A50);
            context.batcher.textShadow(displayName, x + 4, y + 4);
            context.batcher.textShadow(id, x + 4, y + 14, Colors.LIGHTEST_GRAY);
        }

        this.renderDrag(context);
    }

    /** While a form is being dragged, mark the drop slot and draw the form riding under the cursor. */
    private void renderDrag(UIContext context)
    {
        if (this.dragForm == null)
        {
            return;
        }

        int mx = context.mouseX;
        int my = context.mouseY;

        /* Only show the drag UI once the pointer has actually left the dead-zone - a plain click that
         * selects a form must not flash the ghost or marker. */
        if (!this.dragMovedEnough(context))
        {
            return;
        }

        UIFormCategory target = this.categoryAt(mx, my);

        if (target != null)
        {
            int perRow = this.perRow(target);
            int size = target.category.getForms().size();
            int maxRow = size == 0 ? 0 : (size - 1) / perRow;

            /* Draw the marker in the row the pointer is physically over, at the nearest column gap. Deriving
             * the row from the linear insert index instead let a right-side hover (col == perRow) wrap the
             * bar down onto the next row - which read as "too low". */
            int row = MathUtils.clamp((my - target.area.y - UIFormCategory.HEADER_HEIGHT) / UIFormCategory.CELL_HEIGHT, 0, maxRow);
            int col = MathUtils.clamp((mx - target.area.x + UIFormCategory.CELL_WIDTH / 2) / UIFormCategory.CELL_WIDTH, 0, perRow);
            int markerX = target.area.x + col * UIFormCategory.CELL_WIDTH;
            int markerY = target.area.y + UIFormCategory.HEADER_HEIGHT + row * UIFormCategory.CELL_HEIGHT;

            context.batcher.clip(this.forms.area.x, this.forms.area.y, this.forms.area.w, this.forms.area.h, context);
            context.batcher.box(markerX - 1, markerY, markerX + 1, markerY + UIFormCategory.CELL_HEIGHT, 0xff000000 | BBSSettings.accentColor());
            context.batcher.unclip(context);
        }

        int gx = mx - UIFormCategory.CELL_WIDTH / 2;
        int gy = my - UIFormCategory.CELL_HEIGHT / 2;

        context.batcher.box(gx, gy, gx + UIFormCategory.CELL_WIDTH, gy + UIFormCategory.CELL_HEIGHT, Colors.A50);
        FormUtilsClient.renderUI(this.dragForm, context, gx, gy, gx + UIFormCategory.CELL_WIDTH, gy + UIFormCategory.CELL_HEIGHT);
    }
}