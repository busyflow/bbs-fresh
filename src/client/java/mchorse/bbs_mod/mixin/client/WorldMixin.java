package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldMixin
{
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    public void onGetRainGradient(CallbackInfoReturnable<Float> info)
    {
        Double rainFactor = BBSRendering.getWeatherRain();

        if (rainFactor != null)
        {
            info.setReturnValue(rainFactor.floatValue());
        }
    }

    /**
     * The storm, which is a separate reading from the rain.
     *
     * <p>Left alone unless the weather curve is driving, so the old rain-only curve keeps meaning
     * exactly what it did and never quietly darkens the sky.</p>
     */
    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    public void onGetThunderGradient(CallbackInfoReturnable<Float> info)
    {
        Double thunder = BBSRendering.getWeatherThunder();

        if (thunder != null)
        {
            info.setReturnValue(thunder.floatValue());
        }
    }
}