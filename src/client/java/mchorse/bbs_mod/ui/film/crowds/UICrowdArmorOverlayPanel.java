package mchorse.bbs_mod.ui.film.crowds;

import mchorse.bbs_mod.film.crowds.CrowdArmor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks a crowd's armour. Left: the profiles a member can roll into, each with a weight against the
 * others. Right: four columns, one per slot, listing every armour piece that fits it with a weight -
 * plus a "None" weight at the top of each. A member rolls a profile, then rolls each slot on its own,
 * so weighting a few pieces per slot is what mixes the crowd rather than dressing it identically.
 */
public class UICrowdArmorOverlayPanel extends UIOverlayPanel
{
    private static final String[] SLOT_NAMES = { "Helmet", "Chestplate", "Leggings", "Boots" };
    /** Item ids that fit each slot, in the profiles' HEAD/CHEST/LEGS/FEET order, material-ordered. */
    private static final List<String>[] SLOT_ITEMS = buildSlotItems();

    private final CrowdArmor armor;
    private final Runnable onEdit;

    private final UIScrollView body;
    private int profile;

    public UICrowdArmorOverlayPanel(CrowdArmor armor, Runnable onEdit)
    {
        super(IKey.constant("Crowd armor"));

        this.armor = armor;
        this.onEdit = onEdit;
        this.profile = armor.profiles.isEmpty() ? -1 : 0;

        this.body = UI.scrollView(5, 5);
        this.body.full(this.content);
        this.content.add(this.body);

        this.rebuild();
    }

    private void edit()
    {
        if (this.onEdit != null)
        {
            this.onEdit.run();
        }
    }

    private CrowdArmor.Profile current()
    {
        return this.profile < 0 || this.profile >= this.armor.profiles.size() ? null : this.armor.profiles.get(this.profile);
    }

    private void rebuild()
    {
        this.body.removeAll();

        UIButton pick = new UIButton(IKey.constant(this.profileLabel()), (b) -> this.openProfileMenu());
        pick.tooltip(IKey.constant("Which profile you are editing. Members roll one at random, by weight."));
        UIButton add = new UIButton(IKey.constant("New profile"), (b) -> this.addProfile());
        UIButton remove = new UIButton(IKey.constant("Remove"), (b) -> this.removeProfile());
        remove.color(Colors.NEGATIVE);

        this.body.add(UI.row(5, pick, add, remove));

        CrowdArmor.Profile profile = this.current();

        if (profile == null)
        {
            this.body.add(UI.label(IKey.constant("No profiles yet - add one to dress this crowd.")).marginTop(8));
            this.resizeBody();

            return;
        }

        UITextbox name = new UITextbox(64, (text) -> { profile.name = text; this.edit(); });
        name.setText(profile.name);
        UITrackpad weight = new UITrackpad((v) -> { profile.weight = v.intValue(); this.edit(); });
        weight.integer().limit(0);
        weight.setValue(profile.weight);
        weight.tooltip(IKey.constant("How often a member picks this profile, against the other profiles' weights."));

        this.body.add(UI.row(5,
            UI.row(4, UI.label(IKey.constant("Name")).w(40), name),
            UI.row(4, UI.label(IKey.constant("Weight")).w(46), weight)
        ));

        UIElement[] columns = new UIElement[SLOT_ITEMS.length];

        for (int i = 0; i < SLOT_ITEMS.length; i++)
        {
            columns[i] = this.buildColumn(profile, i);
        }

        this.body.add(UI.row(5, columns).marginTop(6));
        this.resizeBody();
    }

    private UIElement buildColumn(CrowdArmor.Profile profile, int slotIndex)
    {
        CrowdArmor.Slot slot = profile.slots[slotIndex];
        List<UIElement> rows = new ArrayList<>();

        rows.add(UI.label(IKey.constant(SLOT_NAMES[slotIndex])));

        UITrackpad none = new UITrackpad((v) -> { slot.emptyWeight = v.intValue(); this.edit(); });
        none.integer().limit(0);
        none.setValue(slot.emptyWeight);
        none.tooltip(IKey.constant("Weight of wearing nothing in this slot."));
        rows.add(this.weightRow("None", none));

        for (String itemId : SLOT_ITEMS[slotIndex])
        {
            UITrackpad pad = new UITrackpad((v) -> { slot.set(itemId, v.intValue()); this.edit(); });
            pad.integer().limit(0);
            pad.setValue(slot.get(itemId));
            rows.add(this.weightRow(displayName(itemId), pad));
        }

        return UI.column(3, rows.toArray(new UIElement[0]));
    }

    /** A label that gives way when the column is narrow, next to a fixed-width weight trackpad. */
    private UIElement weightRow(String label, UITrackpad pad)
    {
        pad.w(44);

        return UI.row(4, UI.label(IKey.constant(label)), pad);
    }

    private String profileLabel()
    {
        CrowdArmor.Profile profile = this.current();

        return profile == null ? "(no profile)" : profile.name + "  (" + (this.profile + 1) + "/" + this.armor.profiles.size() + ")";
    }

    private void openProfileMenu()
    {
        if (this.armor.profiles.isEmpty())
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            for (int i = 0; i < this.armor.profiles.size(); i++)
            {
                int index = i;

                menu.action(Icons.MORE, IKey.constant(this.armor.profiles.get(i).name), () ->
                {
                    this.profile = index;
                    this.rebuild();
                });
            }
        });
    }

    private void addProfile()
    {
        CrowdArmor.Profile profile = new CrowdArmor.Profile();

        profile.name = "Armor " + (this.armor.profiles.size() + 1);
        this.armor.profiles.add(profile);
        this.profile = this.armor.profiles.size() - 1;
        this.edit();
        this.rebuild();
    }

    private void removeProfile()
    {
        if (this.profile < 0 || this.profile >= this.armor.profiles.size())
        {
            return;
        }

        this.armor.profiles.remove(this.profile);
        this.profile = Math.min(this.profile, this.armor.profiles.size() - 1);
        this.edit();
        this.rebuild();
    }

    private void resizeBody()
    {
        this.body.resize();
    }

    private static String displayName(String itemId)
    {
        Item item = Registries.ITEM.get(new net.minecraft.util.Identifier(itemId));

        return new ItemStack(item).getName().getString();
    }

    @SuppressWarnings("unchecked")
    private static List<String>[] buildSlotItems()
    {
        List<String>[] slots = new List[] { new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>() };

        for (Item item : Registries.ITEM)
        {
            if (item instanceof ArmorItem armor)
            {
                int index = slotIndex(armor);

                if (index >= 0)
                {
                    slots[index].add(Registries.ITEM.getId(item).toString());
                }
            }
        }

        /* The elytra rides the chest slot but is not an ArmorItem, so it is added by hand. */
        slots[1].add(Registries.ITEM.getId(Items.ELYTRA).toString());

        Comparator<String> byMaterialThenName = Comparator
            .comparingInt((String id) -> materialRank(id))
            .thenComparing(UICrowdArmorOverlayPanel::displayName);

        for (List<String> slot : slots)
        {
            slot.sort(byMaterialThenName);
        }

        return slots;
    }

    private static int slotIndex(ArmorItem item)
    {
        switch (item.getSlotType())
        {
            case HEAD: return 0;
            case CHEST: return 1;
            case LEGS: return 2;
            case FEET: return 3;
            default: return -1;
        }
    }

    /** Rough tier order so a column reads leather-to-netherite rather than alphabetical. */
    private static int materialRank(String id)
    {
        String[] tiers = { "leather", "chainmail", "iron", "golden", "diamond", "netherite", "turtle", "elytra" };

        for (int i = 0; i < tiers.length; i++)
        {
            if (id.contains(tiers[i]))
            {
                return i;
            }
        }

        return tiers.length;
    }
}
