package mchorse.bbs_mod.ui.film.clips.renderer;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.MathUtils;

public class UIAudioClipRenderer extends UIClipRenderer<AudioClip>
{
    @Override
    protected void renderBackground(UIContext context, int color, AudioClip clip, Area fullArea, Area visibleArea, boolean selected, boolean current)
    {
        Link link = clip.audio.get();

        if (link != null)
        {
            SoundBuffer player = BBSModClient.getSounds().get(link, true);

            if (player != null && player.getWaveform() != null)
            {
                int offset = clip.offset.get();
                int duration = clip.duration.get();

                context.batcher.box(visibleArea.x, visibleArea.y, visibleArea.ex(), visibleArea.ey(), Colors.mulRGB(color, 0.6F));

                if (fullArea.w > 0 && duration > 0)
                {
                    float visibleStart = MathUtils.clamp((visibleArea.x - fullArea.x) / (float) fullArea.w, 0F, 1F);
                    float visibleEnd = MathUtils.clamp((visibleArea.ex() - fullArea.x) / (float) fullArea.w, 0F, 1F);
                    float startTick = offset + duration * visibleStart;
                    float endTick = offset + duration * visibleEnd;

                    player.getWaveform().render(
                        context.batcher,
                        Colors.WHITE,
                        visibleArea.x,
                        visibleArea.y,
                        visibleArea.w,
                        visibleArea.h,
                        TimeUtils.toSeconds(startTick),
                        TimeUtils.toSeconds(endTick)
                    );
                }
            }
        }
        else
        {
            super.renderBackground(context, color, clip, fullArea, visibleArea, selected, current);
        }
    }

    @Override
    public String getDefaultLabel(UIClips clips, AudioClip clip)
    {
        Link link = clip.audio.get();
        if (link != null)
        {
            String name = StringUtils.fileName(link.path);
            if (!name.isEmpty()) return name;
        }
        return super.getDefaultLabel(clips, clip);
    }
}
