package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

/**
 * Delta transform editor whose selection spans keyframes, plus the film
 * recording layer: while transform recording is live the edit lands on the
 * recorded keyframes ({@link #applyDuringRecording}) instead of the selection,
 * and the fields mirror the recorded transform.
 */
public abstract class UIKeyframePropTransform extends UIDeltaPropTransform
{
    private int playbackRecordingTick = Integer.MIN_VALUE;
    private Transform playbackRecordingSample;
    private Transform playbackWorkingTransform;
    private UIFilmPanel playbackUndoPanel;

    protected abstract void applyDuringRecording(int tick, Consumer<Transform> consumer);

    protected Transform getRecordedTransform(int tick)
    {
        return null;
    }

    /**
     * Resolves the film panel from the menu root rather than walking up the parent chain, so this
     * transform works even when it lives outside the film panel (e.g. the animation state editor,
     * where there is no film panel and recording simply doesn't apply). Mirrors the lookup in
     * {@link UIAnchorKeyframeFactory}.
     */
    protected UIFilmPanel getPanel()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return null;
        }

        List<UIFilmPanel> panels = context.menu.main.getChildren(UIFilmPanel.class);

        return panels.isEmpty() ? null : panels.get(0);
    }

    protected boolean isTransformRecording()
    {
        UIFilmPanel panel = this.getPanel();

        return panel != null && (panel.getController().isTransformRecording() || this.isPlaybackRecording(panel));
    }

    /**
     * Pose editors opt into automatic recording while the film is playing and a
     * gizmo gesture is active. Other transform tracks keep their existing edit
     * behavior.
     */
    protected boolean supportsPlaybackRecording()
    {
        return false;
    }

    private boolean isPlaybackRecording(UIFilmPanel panel)
    {
        return this.supportsPlaybackRecording() && panel.isRunning() && this.isEditing();
    }

    protected int getRecordingTick()
    {
        UIFilmPanel panel = this.getPanel();

        return panel == null ? 0 : panel.getCursor();
    }

    @Override
    protected Transform getTargetTransform()
    {
        if (this.isTransformRecording())
        {
            return this.getRecordedTransform(this.getRecordingTick());
        }

        return this.getTransform();
    }

    @Override
    protected void applyToTarget(Consumer<Transform> consumer)
    {
        if (this.isTransformRecording())
        {
            this.applyDuringRecording(this.getRecordingTick(), consumer);
        }
        else
        {
            this.applyToSelection(consumer);
        }
    }

    @Override
    protected void syncTargetTransform()
    {
        if (this.playbackWorkingTransform != null && this.isTransformRecording())
        {
            Transform target = this.getTargetTransform();

            if (target != null)
            {
                this.playbackWorkingTransform.copy(target);
                this.setTransform(this.playbackWorkingTransform);
            }

            return;
        }

        super.syncTargetTransform();
    }

    @Override
    public void render(UIContext context)
    {
        UIFilmPanel panel = this.getPanel();
        boolean recording = panel != null && this.isPlaybackRecording(panel);

        if (recording)
        {
            int tick = this.getRecordingTick();

            if (this.playbackRecordingTick == Integer.MIN_VALUE)
            {
                this.beginPlaybackRecordingUndo(panel);

                Transform target = this.getRecordedTransform(tick);

                if (target != null)
                {
                    this.playbackRecordingTick = tick;
                    this.playbackRecordingSample = target.copy();
                    this.playbackWorkingTransform = target.copy();
                    this.setTransform(this.playbackWorkingTransform);
                }
            }
            else if (tick != this.playbackRecordingTick && this.playbackRecordingSample != null)
            {
                Transform sample = this.playbackRecordingSample.copy();

                this.applyDuringRecording(tick, (target) -> target.copy(sample));
                this.playbackRecordingTick = tick;

                Transform target = this.getRecordedTransform(tick);

                if (target != null)
                {
                    this.playbackWorkingTransform.copy(target);
                    this.setTransform(this.playbackWorkingTransform);
                }
            }
        }
        else
        {
            this.finishPlaybackRecordingGesture();
        }

        super.render(context);

        if (recording)
        {
            Transform target = this.getRecordedTransform(this.getRecordingTick());

            if (target != null)
            {
                this.playbackRecordingSample = target.copy();
            }
        }
    }

    @Override
    public void endGesture()
    {
        this.finishPlaybackRecordingGesture();
        super.endGesture();
    }

    protected boolean finishPlaybackRecordingGesture()
    {
        boolean recording = this.playbackUndoPanel != null;

        this.endPlaybackRecordingUndo();
        this.playbackRecordingTick = Integer.MIN_VALUE;
        this.playbackRecordingSample = null;
        this.playbackWorkingTransform = null;

        return recording;
    }

    private void beginPlaybackRecordingUndo(UIFilmPanel panel)
    {
        if (this.playbackUndoPanel == panel || panel.getUndoHandler() == null)
        {
            return;
        }

        this.endPlaybackRecordingUndo();

        LinkedHashSet<Replay> replays = new LinkedHashSet<>();
        Replay active = panel.replayEditor == null ? null : panel.replayEditor.getReplay();

        if (active != null)
        {
            replays.add(active);
        }

        if (panel.replayEditor != null && panel.replayEditor.replaysList != null)
        {
            replays.addAll(panel.replayEditor.replaysList.replays.getSelectedReplays());
        }

        if (!replays.isEmpty())
        {
            panel.getUndoHandler().beginLiveRecordingUndo(replays);
            this.playbackUndoPanel = panel;
        }
    }

    private void endPlaybackRecordingUndo()
    {
        if (this.playbackUndoPanel != null && this.playbackUndoPanel.getUndoHandler() != null)
        {
            this.playbackUndoPanel.getUndoHandler().endLiveRecordingUndo();
        }

        this.playbackUndoPanel = null;
    }
}
