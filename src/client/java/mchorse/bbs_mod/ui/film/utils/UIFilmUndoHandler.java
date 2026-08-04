package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class UIFilmUndoHandler extends UIFormUndoHandler
{
    private Timer actionsTimer = new Timer(100);
    private Set<BaseValue> syncData = new HashSet<>();
    /** Whole-replay snapshots used to make one live gizmo recording undoable in one step. */
    private final Map<BaseValue, BaseType> liveRecordingValues = new LinkedHashMap<>();
    private int liveRecordingDepth;

    public UIFilmUndoHandler(UIFilmPanel panel)
    {
        super(panel);
    }

    @Override
    public void handlePreValues(BaseValue baseValue, int flag)
    {
        /* time_spent_active is a passive counter updated every second; it should not
         * pollute undo history with dozens of entries per minute */
        if (baseValue.getPath().getLast().equals("time_spent_active"))
        {
            return;
        }

        if (this.isLiveRecordingChild(baseValue))
        {
            return;
        }

        super.handlePreValues(baseValue, flag);
    }

    /**
     * Start an atomic live-recording undo. Each supplied replay is saved once;
     * per-frame keyframe notifications below it are intentionally ignored until
     * the recording ends, preventing Ctrl+Z from undoing only one recorded tick.
     */
    public void beginLiveRecordingUndo(Collection<? extends BaseValue> values)
    {
        if (values == null || values.isEmpty())
        {
            return;
        }

        if (this.liveRecordingDepth == 0)
        {
            /* Keep the previous edit separate from this recording pass. */
            super.submitUndo();
        }

        for (BaseValue value : values)
        {
            if (value == null || this.liveRecordingValues.containsKey(value))
            {
                continue;
            }

            super.handlePreValues(value, IValueListener.FLAG_UNMERGEABLE);
            this.liveRecordingValues.put(value, this.cachedValues.get(value));
        }

        this.liveRecordingDepth++;
    }

    /** Seal the recording as one undo entry, or discard it when nothing changed. */
    public void endLiveRecordingUndo()
    {
        if (this.liveRecordingDepth <= 0)
        {
            return;
        }

        if (--this.liveRecordingDepth > 0)
        {
            return;
        }

        for (Map.Entry<BaseValue, BaseType> entry : this.liveRecordingValues.entrySet())
        {
            if (BaseType.equals(entry.getValue(), entry.getKey().toData()))
            {
                this.cachedValues.remove(entry.getKey());
            }
        }

        this.liveRecordingValues.clear();

        if (this.cachedValues.isEmpty())
        {
            this.cacheMarkLastUndoNoMerging = false;
        }
        else
        {
            super.submitUndo();
        }
    }

    @Override
    public void submitUndo()
    {
        if (this.liveRecordingDepth == 0)
        {
            super.submitUndo();
        }
    }

    private boolean isLiveRecordingChild(BaseValue value)
    {
        while (value != null)
        {
            if (this.liveRecordingValues.containsKey(value))
            {
                return true;
            }

            value = value.getParent();
        }

        return false;
    }

    @Override
    protected void handleValue(BaseValue value)
    {
        super.handleValue(value);

        if (this.isReplayActions(value))
        {
            /* TODO: Variant A for the lazy-channel desync — if 'value' is a keyframe
             * inside a channel, promote it to its parent KeyframeChannel here so the
             * sync sends the whole channel ('properties/<key>') instead of an indexed
             * keyframe path ('properties/<key>/0'). A channel created client-side by
             * FormProperties.getOrCreate during UI building (UIReplaysEditor
             * .collectFormPropertySheets) is never synced, so the server lacks it and
             * the indexed path can't be resolved. Currently handled reactively by the
             * full-film resync request (ServerNetwork.requestFilmResync). */
            this.syncData.add(value);
            this.actionsTimer.mark();
        }
    }

    @Override
    protected void handleTimers()
    {
        super.handleTimers();

        if (this.actionsTimer.checkReset())
        {
            for (BaseValue syncData : this.syncData)
            {
                ClientNetwork.sendSyncData(((UIFilmPanel) this.uiElement).getData().getId(), syncData);
            }

            this.syncData.clear();
        }
    }

    private boolean isReplayActions(BaseValue value)
    {
        String path = value.getPath().toString();

        if (
            path.endsWith("/replays") ||
            path.endsWith("/keyframes") ||
            path.contains("/keyframes/x") ||
            path.contains("/keyframes/y") ||
            path.contains("/keyframes/z") ||
            path.contains("/keyframes/item_main_hand") ||
            path.contains("/keyframes/item_off_hand") ||
            path.contains("/keyframes/item_head") ||
            path.contains("/keyframes/item_chest") ||
            path.contains("/keyframes/item_legs") ||
            path.contains("/keyframes/item_feet") ||
            path.contains("/form/") ||
            path.contains("/properties/") ||
            path.endsWith("/properties") ||
            path.endsWith("/actor") ||
            path.endsWith("/enabled") ||
            path.endsWith("/form")
        ) {
            return true;
        }

        /* Specifically for overwriting full replay like what's done when recording
         * data in the world! */
        if (value.getParent() != null && value.getParent().getId().equals("replays"))
        {
            return true;
        }

        while (value != null)
        {
            if (value instanceof Clips clips && clips.getFactory() == BBSMod.getFactoryActionClips())
            {
                return true;
            }

            value = value.getParent();
        }

        return false;
    }
}
