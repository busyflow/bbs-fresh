package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValueChannels;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.HashMap;
import java.util.Map;

public class CurveClip extends CameraClip
{
    public static final String SHADER_CURVES_PREFIX = "curve.";

    public static final String CHROMA_SKY_COLOR = "chroma_sky_color";

    /** The sun curve, in the game's own ticks. Kept under its old id so films still load. */
    public static final String SUN_TIME = "sun_rotation";

    /** Where on the compass the sun rises, in degrees. Turns the sky without touching the clock. */
    public static final String SUN_DIRECTION = "sun_direction";

    /** Clear at 0, rain at 1, thunderstorm at 2, running into each other in between. */
    public static final String WEATHER_STATE = "weather_state";

    /** Replaces the world with a flat colour while it is above 0.5. */
    public static final String CHROMA_SKY = "chroma_sky";

    /** A whole day is 24000 ticks, so a curve that never passes this was written in thousands. */
    private static final double OLD_SUN_TIME_CEILING = 24D;

    public static boolean isColorChannelId(String id)
    {
        return CHROMA_SKY_COLOR.equals(id);
    }

    public final ValueChannels channels = new ValueChannels("channels");

    public static Map<String, Double> getValues(ClipContext context)
    {
        return context.clipData.get("curve_data", HashMap::new);
    }

    public static Map<String, Integer> getColorValues(ClipContext context)
    {
        return context.clipData.get("curve_color_data", HashMap::new);
    }

    public CurveClip()
    {
        this.add(this.channels);
        this.channels.addChannel("sun_rotation");
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        Map<String, Double> values = getValues(context);

        for (KeyframeChannel<Double> channel : this.channels.getChannels())
        {
            if (!channel.isEmpty())
            {
                values.put(channel.getId(), channel.interpolate(context.relativeTick + context.transition));
            }
        }

        Map<String, Integer> colorValues = getColorValues(context);

        for (KeyframeChannel<Color> channel : this.channels.getColorChannels())
        {
            if (!channel.isEmpty())
            {
                var color = channel.interpolate(context.relativeTick + context.transition, null);

                if (color != null)
                {
                    colorValues.put(channel.getId(), color.getARGBColor());
                }
            }
        }
    }

    @Override
    protected Clip create()
    {
        return new CurveClip();
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isMap())
        {
            MapType map = data.asMap();

            if (map.has("key") && map.has("channel"))
            {
                ValueString key = new ValueString("key", "sun_rotation");

                key.fromData(map.get("key"));

                KeyframeChannel<Double> channel = this.channels.addChannel(key.get());

                channel.fromData(map.get("channel"));
            }
        }

        super.fromData(data);

        this.migrateSunTime();
    }

    /**
     * Bring an older film's sun curve up to the game's own clock.
     *
     * <p>The channel used to be read in thousands of ticks, so a film asking for noon had 6 in
     * it where it now wants 6000. Told apart by the numbers themselves: a whole day is 24000
     * ticks, so anything that never rises above 24 is a curve written in the old units - and a
     * sun time of 24 ticks, a second after dawn, is not something anyone sits down to author.</p>
     */
    private void migrateSunTime()
    {
        KeyframeChannel<Double> channel = null;

        for (KeyframeChannel<Double> candidate : this.channels.getChannels())
        {
            if (SUN_TIME.equals(candidate.getId()))
            {
                channel = candidate;

                break;
            }
        }

        if (channel == null || channel.isEmpty())
        {
            return;
        }

        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            if (keyframe.getValue() != null && keyframe.getValue() > OLD_SUN_TIME_CEILING)
            {
                return;
            }
        }

        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            if (keyframe.getValue() != null)
            {
                keyframe.setValue(keyframe.getValue() * 1000D);
            }
        }
    }
}