package mchorse.bbs_mod.film;

import mchorse.bbs_mod.actions.types.SwipeActionClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;

import java.util.List;

public class FirstPersonFilmController extends WorldFilmController
{
    public FirstPersonFilmController(Film film)
    {
        super(film);
    }

    @Override
    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity)
    {
        if (replay.fp.get())
        {
            return;
        }

        super.renderEntity(context, replay, entity);
    }

    /**
     * A first-person replay's body is not drawn - the arms on screen are the player's own, through
     * Minecraft's first-person hand renderer. That renderer animates from the player's swing
     * state, and nothing was ever swinging the player, so a swipe in a first-person replay played
     * out on a body nobody could see and the point of view stayed still. The swipe is mirrored
     * onto the player here, which is also what makes it smooth: vanilla runs the swing down over
     * its six ticks and the renderer interpolates it per frame, rather than it being a pose the
     * film has to draw itself.
     */
    @Override
    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        super.applyReplay(replay, ticks, entity);

        if (replay.fp.get() && swipesAt(replay, ticks))
        {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;

            if (player != null)
            {
                /* The two-argument form: the one-argument override on the client player also
                 * tells the server, which would swing the actor a second time. */
                player.swingHand(Hand.MAIN_HAND, false);
            }
        }
    }

    private static boolean swipesAt(Replay replay, int tick)
    {
        int local = replay.getTick(tick);
        List<Clip> clips = replay.actions.getClips(local);

        for (int i = 0; i < clips.size(); i++)
        {
            if (clips.get(i) instanceof SwipeActionClip swipe && swipe.firesAt(local))
            {
                return true;
            }
        }

        return false;
    }
}
