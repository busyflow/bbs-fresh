package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSSettings;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.KillCommand;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;

@Mixin(KillCommand.class)
public class KillCommandMixin
{
    @ModifyVariable(method = "execute", at = @At("HEAD"), argsOnly = true)
    private static Collection<? extends Entity> bbs$filterSelf(Collection<? extends Entity> entities, ServerCommandSource source)
    {
        if (BBSSettings.killExcludeSelf == null || !BBSSettings.killExcludeSelf.get())
        {
            return entities;
        }

        Entity self = source.getEntity();

        if (self == null)
        {
            return entities;
        }

        ArrayList<Entity> filtered = new ArrayList<>(entities);

        filtered.removeIf(e -> e == self);

        return filtered;
    }
}
