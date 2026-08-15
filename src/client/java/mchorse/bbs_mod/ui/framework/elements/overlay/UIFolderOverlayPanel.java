package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIFileLinkList;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import java.util.List;
import java.util.function.Consumer;

public class UIFolderOverlayPanel extends UIMessageBarOverlayPanel
{
    public UIFileLinkList list;

    private Consumer<Link> callback;

    public UIFolderOverlayPanel(IKey title, IKey message, Consumer<Link> callback)
    {
        super(title, message);

        this.callback = callback;

        this.list = new UIFileLinkList((l) -> {});
        this.list.filter((l) -> l.path.endsWith("/"));
        this.list.background();
        this.list.setPath(null);

        this.confirm.label = UIKeys.GENERAL_PICK;
        this.confirm.w(100);

        UIElement pinned = this.buildPinnedBar();

        if (pinned != null)
        {
            /* Same pinned-folder shortcuts as the texture picker, so the folders you pin are one click
             * away here too; the list drops down to make room. */
            pinned.relative(this.content).xy(6, 26).w(1F, -12).h(20);
            this.list.relative(this.content).xy(6, 50).w(1F, -12).h(1F, -84);
            this.content.add(pinned);
        }
        else
        {
            this.list.relative(this.content).xy(6, 36).w(1F, -12).h(1F, -70);
        }

        this.content.add(this.list);
    }

    /** A row of quick-jump buttons for the globally pinned texture folders, or null when none are pinned. */
    private UIElement buildPinnedBar()
    {
        List<String> folders = BBSSettings.pinnedTextureFolders.getList();

        if (folders.isEmpty())
        {
            return null;
        }

        UIElement bar = new UIElement();

        bar.row(4).height(20);

        for (String folder : folders)
        {
            Link link = LinkUtils.create(folder);

            if (link == null)
            {
                continue;
            }

            String name = StringUtils.fileName(link.path).replaceAll("/", "");
            String label = name.isEmpty() ? link.source : name;

            bar.add(new UIButton(IKey.constant(label), (b) -> this.list.setPath(link)));
        }

        return bar.getChildren().isEmpty() ? null : bar;
    }

    public UIFolderOverlayPanel confirmLabel(IKey key)
    {
        this.confirm.label = key;

        return this;
    }

    @Override
    public void confirm()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.list.path);
        }

        super.confirm();
    }
}
