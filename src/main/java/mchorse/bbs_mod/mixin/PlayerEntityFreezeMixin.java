package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSSettings;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds the player's health and hunger still while the Fresh "pause regeneration" toggles are on, so a
 * shot can sit on peaceful without the bars drifting. The vitals are snapshotted at the top of the tick
 * and put back at the bottom: hunger is pinned exactly (the bar never moves), health only has increases
 * undone (damage still lands, it just never heals back). Server side only - the client mirrors it.
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityFreezeMixin
{
    @Unique
    private float bbs$health;
    @Unique
    private int bbs$food;
    @Unique
    private boolean bbs$freezeHealth;
    @Unique
    private boolean bbs$freezeHunger;

    @Inject(method = "tick", at = @At("HEAD"))
    private void bbs$captureVitals(CallbackInfo ci)
    {
        PlayerEntity player = (PlayerEntity) (Object) this;

        this.bbs$freezeHealth = BBSSettings.pauseHealthRegen != null && BBSSettings.pauseHealthRegen.get();
        this.bbs$freezeHunger = BBSSettings.pauseHunger != null && BBSSettings.pauseHunger.get();

        if (player.getWorld().isClient || (!this.bbs$freezeHealth && !this.bbs$freezeHunger))
        {
            return;
        }

        this.bbs$health = player.getHealth();
        this.bbs$food = player.getHungerManager().getFoodLevel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void bbs$freezeVitals(CallbackInfo ci)
    {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (player.getWorld().isClient)
        {
            return;
        }

        if (this.bbs$freezeHealth && player.getHealth() > this.bbs$health)
        {
            player.setHealth(this.bbs$health);
        }

        if (this.bbs$freezeHunger)
        {
            HungerManager hunger = player.getHungerManager();

            if (hunger.getFoodLevel() != this.bbs$food)
            {
                hunger.setFoodLevel(this.bbs$food);
            }
        }
    }
}
