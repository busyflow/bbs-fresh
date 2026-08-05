package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.DataPath;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ActionManager
{
    private List<ActionPlayer> players = new ArrayList<>();
    private Map<ServerPlayerEntity, ActionRecorder> recorders = new HashMap<>();
    private Map<ServerWorld, DamageControl> dc = new HashMap<>();

    public void reset()
    {
        this.players.clear();
        this.recorders.clear();
        this.dc.clear();
    }

    public void tick()
    {
        this.players.removeIf((player) ->
        {
            boolean tick = player.tick();

            if (tick)
            {
                if (player.stopDamage)
                {
                    this.stopDamage(player.getWorld());
                }

                player.stop();
            }

            return tick;
        });

        for (Map.Entry<ServerPlayerEntity, ActionRecorder> entry : this.recorders.entrySet())
        {
            entry.getValue().tick(entry.getKey());
        }
    }

    /* Actions playback */

    public void syncData(String filmId, DataPath key, BaseType data)
    {
        for (ActionPlayer player : this.players)
        {
            if (player.film.getId().equals(filmId))
            {
                player.syncData(key, data);
            }
        }
    }

    public ActionPlayer getPlayer(String filmId)
    {
        for (ActionPlayer player : this.players)
        {
            if (player.film.getId().equals(filmId))
            {
                return player;
            }
        }

        return null;
    }

    public ActionPlayer play(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick)
    {
        return this.play(serverPlayer, world, film, tick, 0, -1, PlayerType.NORMAL);
    }

    public ActionPlayer play(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick, PlayerType type)
    {
        return this.play(serverPlayer, world, film, tick, 0, -1, type);
    }

    public ActionPlayer play(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick, int countdown, int exception, PlayerType type)
    {
        if (film != null)
        {
            ActionPlayer player = new ActionPlayer(serverPlayer, world, film, tick, countdown, exception, type);

            this.players.add(player);
            this.trackDamage(world);
            this.sweepOrphanedCrowds(world);

            return player;
        }

        return null;
    }

    public void stop(String filmId)
    {
        Iterator<ActionPlayer> it = this.players.iterator();
        ServerWorld world = null;

        while (it.hasNext())
        {
            ActionPlayer next = it.next();

            if (next.film.getId().equals(filmId))
            {
                world = next.getWorld();

                this.stopDamage(world);
                next.stop();
                it.remove();
            }
        }

        if (world != null)
        {
            this.sweepOrphanedCrowds(world);
        }
    }

    /**
     * Clear out crowd members left behind by runs that are no longer playing.
     *
     * <p>Run on both start and stop, so a crowd cannot outlive the playback that spawned it -
     * whether that playback ended cleanly, was abandoned when the film was reloaded, or never
     * ended at all because the world was closed mid-scene.</p>
     */
    private void sweepOrphanedCrowds(ServerWorld world)
    {
        Set<String> active = new HashSet<>();

        for (ActionPlayer player : this.players)
        {
            active.add(CrowdUtils.getRunTag(player.film));
        }

        CrowdUtils.sweepOrphans(world, active);
    }

    /* Actions recording */

    public void startRecording(Film film, ServerPlayerEntity entity, int tick, int countdown, int replayId)
    {
        ActionPlayer play = this.play(entity, entity.getServerWorld(), film, tick, countdown, replayId, PlayerType.RECORDING);

        play.stopDamage = false;

        this.recorders.put(entity, new ActionRecorder(film, entity, tick, countdown));
    }

    public void addAction(ServerPlayerEntity entity, Supplier<ActionClip> supplier)
    {
        ActionRecorder recorder = this.recorders.get(entity);

        if (recorder != null && supplier != null)
        {
            ActionClip actionClip = supplier.get();

            if (actionClip != null)
            {
                recorder.add(actionClip);
            }
        }
    }

    public ActionRecorder stopRecording(ServerPlayerEntity entity)
    {
        ActionRecorder remove = this.recorders.remove(entity);

        this.stop(remove.getFilm().getId());
        this.stopDamage(entity.getServerWorld());

        return remove;
    }

    /* Damage control */

    public void trackDamage(ServerWorld world)
    {
        DamageControl damageControl = this.dc.get(world);

        if (damageControl == null)
        {
            this.dc.put(world, new DamageControl(world));
        }
        else
        {
            damageControl.nested += 1;
        }
    }

    public void stopDamage(ServerWorld world)
    {
        DamageControl damageControl = this.dc.get(world);

        if (damageControl != null)
        {
            if (damageControl.nested > 0)
            {
                damageControl.nested -= 1;
            }
            else
            {
                damageControl.restore();
                this.dc.remove(world);
            }
        }
    }

    public void resetDamage(ServerWorld world)
    {
        DamageControl dc = this.dc.remove(world);

        if (dc != null)
        {
            dc.restore();
        }
    }

    public void changedBlock(BlockPos pos, BlockState state, BlockEntity blockEntity)
    {
        for (DamageControl control : this.dc.values())
        {
            control.addBlock(pos, state, blockEntity);
        }
    }

    public void spawnedEntity(Entity entity)
    {
        for (DamageControl control : this.dc.values())
        {
            control.addEntity(entity);
        }
    }
}