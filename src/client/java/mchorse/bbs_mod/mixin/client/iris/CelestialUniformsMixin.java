package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.client.BBSRendering;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the sun direction curve reach a shader pack's sun.
 *
 * <p>The vanilla path for this rotates the sky as it is drawn, which a shader pack never looks
 * at: it works the sun out for itself from uniforms Iris fills in, so the curve moved a sun that
 * was no longer the one on screen and the lighting stayed where it was.</p>
 *
 * <p>Iris derives every celestial position - sun, moon and the shadow light that drives the
 * pack's shadows - from one field, the pack's own {@code sunPathRotation}. Adding the curve to
 * that field as it is read moves all three together, through the pack's ordinary uniforms, so it
 * works on Complementary and anything else without a patch of its own.</p>
 *
 * <p>Worth knowing: this is Iris' path rotation, applied about a different axis than the vanilla
 * sky rotation, so the same number tilts the sun's arc rather than swinging it round the compass.
 * It is the only sun direction a pack is given without editing the pack.</p>
 */
@Mixin(CelestialUniforms.class)
public class CelestialUniformsMixin
{
    @Redirect(
        method = {"getCelestialPosition", "getCelestialPositionInWorldSpace"},
        at = @At(
            value = "FIELD",
            target = "Lnet/irisshaders/iris/uniforms/CelestialUniforms;sunPathRotation:F",
            opcode = Opcodes.GETFIELD
        ),
        remap = false
    )
    private float bbs$addSunDirection(CelestialUniforms instance)
    {
        float rotation = ((CelestialUniformsAccessor) (Object) instance).bbs$getSunPathRotation();
        Double direction = BBSRendering.getSunDirection();

        return direction == null ? rotation : rotation + direction.floatValue();
    }
}
