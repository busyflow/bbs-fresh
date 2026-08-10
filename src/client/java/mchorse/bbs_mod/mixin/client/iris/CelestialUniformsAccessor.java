package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.uniforms.CelestialUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the pack's own sun path rotation back, so {@link CelestialUniformsMixin} can add the
 * curve to it rather than replace it - a pack that ships a rotation of its own keeps it.
 */
@Mixin(CelestialUniforms.class)
public interface CelestialUniformsAccessor
{
    @Accessor(value = "sunPathRotation", remap = false)
    float bbs$getSunPathRotation();
}
