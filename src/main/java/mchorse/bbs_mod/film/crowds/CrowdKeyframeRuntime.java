package mchorse.bbs_mod.film.crowds;

import mchorse.bbs_mod.actions.crowd.CrowdJumpEvaluator;
import mchorse.bbs_mod.actions.crowd.CrowdLookEvaluator;
import mchorse.bbs_mod.actions.crowd.CrowdTexture;
import mchorse.bbs_mod.actions.crowd.CrowdWalkEvaluator;
import mchorse.bbs_mod.actions.types.crowd.CrowdDrivenEntity;
import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.actions.types.crowd.CrowdPaintArea;
import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Applies a replay's crowd keyframes to the crowd it drives.
 *
 * <p>A crowd exists because the film says it does - that is {@link CrowdReconciler}'s job and it
 * is deliberately separate from this. What this does is the other half: read where the crowd
 * walks, what it looks at, when it jumps and what it wears at this tick, and put that on the
 * members. Keeping the two apart is the point; a crowd that failed to be animated is a still
 * crowd, not a missing one.</p>
 *
 * <p>A channel with no keyframes says nothing rather than saying zero, so a crowd can be walked
 * by keyframes and left to its behaviour clip for everything else.</p>
 */
public class CrowdKeyframeRuntime
{
    private static final float[] ROTATION = new float[2];

    private CrowdKeyframeRuntime()
    {}

    public static void apply(ServerWorld world, Film film, int tick)
    {
        if (world == null || film == null || film.crowds.getList().isEmpty())
        {
            return;
        }

        for (Replay replay : film.replays.getList())
        {
            if (!replay.enabled.get() || !(replay.form.get() instanceof CrowdForm form))
            {
                continue;
            }

            Crowd crowd = film.crowds.byTag(form.crowd.get());

            if (crowd == null || !crowd.existsAt(tick))
            {
                continue;
            }

            /* Unlimited range: membership is by tag, and a crowd is routinely wider than any
             * box worth querying. See CrowdUtils#getCrowd. */
            List<LivingEntity> members = CrowdUtils.getCrowd(world, film, crowd.crowdTag.get(), Vec3d.ZERO, 0D);

            if (members.isEmpty())
            {
                continue;
            }

            applyWalk(world, film, replay, crowd, members, tick);
            applyJump(replay, members, tick);
            applyLook(film, replay, members, tick);
            applyTexture(replay, members, tick);
            applyColor(replay, members, tick);
        }
    }

    /**
     * Walk the crowd along its waypoints.
     *
     * <p>Members are placed rather than steered. The waypoints already say where everyone is at
     * this tick, down to the stagger that makes the near edge leave first, so asking the
     * navigator to make its own way there would fight the curve the shot was authored on.</p>
     */
    private static void applyWalk(ServerWorld world, Film film, Replay replay, Crowd crowd,
        List<LivingEntity> members, int tick)
    {
        CrowdWalkEvaluator.Frame frame = CrowdWalkEvaluator.frame(replay, tick);

        if (frame == null)
        {
            return;
        }

        Vec3d anchor = crowdAnchor(film, crowd);
        CrowdFormation formation = crowd.getFormation();
        CrowdPaintArea paint = formation == CrowdFormation.PAINT ? new CrowdPaintArea(crowd.getCells()) : null;
        int count = crowd.count.get();
        double spacing = crowd.spacing.get();
        double[] position = new double[3];

        for (LivingEntity member : members)
        {
            int index = CrowdUtils.entityIndex(member);
            Vec3d base = memberBase(crowd, paint, formation, anchor, index, count, spacing);

            if (base == null)
            {
                continue;
            }

            /* The local formation position is immutable for this playback. Feeding the current
             * entity position back in here made every frame's offset become the next frame's
             * starting offset, which compounded into launches across the map. */
            CrowdWalkEvaluator.memberPosition(frame, index, base.x, base.y, base.z, position);

            if (frame.path().terrainFollow)
            {
                position[1] = groundY(world, position[0], position[2], position[1]);
            }

            double dx = position[0] - member.getX();
            double dz = position[2] - member.getZ();

            /* setPos lets the normal entity tracker interpolate the short per-tick steps. A
             * teleport-style refresh here is both visibly harsh and can create fall damage
             * after the entity briefly believes it has travelled vertically. */
            member.setPos(position[0], position[1], position[2]);
            member.setVelocity(Vec3d.ZERO);
            member.fallDistance = 0F;
            member.setOnGround(true);

            /* Facing follows travel unless a look keyframe overrides it below, so a walking
             * crowd does not moonwalk to its destination. */
            if (frame.moving() && frame.path().faceTravel && (dx * dx + dz * dz) > 1.0E-6D)
            {
                float yaw = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90F;

                member.setYaw(yaw);
                member.setBodyYaw(yaw);
                member.setHeadYaw(yaw);
            }
        }
    }

    private static Vec3d crowdAnchor(Film film, Crowd crowd)
    {
        Replay anchor = CrowdUtils.getReplay(film, crowd.anchor.get());

        return anchor == null ? Vec3d.ZERO : CrowdUtils.replayPosition(anchor, crowd.start.get());
    }

    private static Vec3d memberBase(Crowd crowd, CrowdPaintArea paint, CrowdFormation formation,
        Vec3d anchor, int index, int count, double spacing)
    {
        if (paint != null)
        {
            return paint.point(index, count, 0);
        }

        Vec3d offset = CrowdUtils.formationPoint(formation, index, count, spacing, crowd.holeRadius.get());

        return anchor.add(offset);
    }

    /** Keep the crowd's feet on the surface without loading or generating a new chunk. */
    private static double groundY(ServerWorld world, double x, double z, double fallback)
    {
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);

        if (world.getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, false) == null)
        {
            return fallback;
        }

        return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
    }

    private static void applyJump(Replay replay, List<LivingEntity> members, int tick)
    {
        CrowdJumpEvaluator.Frame frame = CrowdJumpEvaluator.frame(replay, tick);

        if (frame == null || !frame.hasPotential())
        {
            return;
        }

        for (LivingEntity member : members)
        {
            double height = frame.height(CrowdUtils.entityIndex(member));

            if (height > 0D)
            {
                /* Lifted rather than launched: the arc is authored, so giving it to the physics
                 * as an impulse would land members at heights the keyframes never asked for. */
                member.setPos(member.getX(), member.getY() + height, member.getZ());
            }
        }
    }

    private static void applyLook(Film film, Replay replay, List<LivingEntity> members, int tick)
    {
        CrowdLookEvaluator.Sample sample = CrowdLookEvaluator.sample(film, replay, tick);

        if (sample == null)
        {
            return;
        }

        for (LivingEntity member : members)
        {
            if (!CrowdLookEvaluator.rotation(member.getX(), member.getEyeY(), member.getZ(), sample, ROTATION))
            {
                continue;
            }

            member.setYaw(ROTATION[0]);
            member.setHeadYaw(ROTATION[0]);
            member.setBodyYaw(ROTATION[0]);
            member.setPitch(ROTATION[1]);

            /* Tell the client this facing is the truth, or it derives body yaw from movement
             * and the torsos lag behind the heads. */
            if (member instanceof CrowdDrivenEntity driven)
            {
                driven.bbs$driveBodyYaw();
            }
        }
    }

    /**
     * Dress the crowd.
     *
     * <p>One texture for everyone unless the keyframe asks for random, which is the only thing
     * in here that makes members differ from each other. Textures live on BBS model forms, so
     * members spawned as plain mobs have nothing to set and are left alone.</p>
     */
    private static void applyTexture(Replay replay, List<LivingEntity> members, int tick)
    {
        if (replay.keyframes.crowdTexture.isEmpty())
        {
            return;
        }

        CrowdTexture texture = replay.keyframes.crowdTexture.interpolate(replay.getTick(tick));

        if (texture == null)
        {
            return;
        }

        List<Link> folder = texture.random ? CrowdTextures.list(texture.folder, texture.recursive) : List.of();

        if (texture.random && folder.isEmpty())
        {
            return;
        }

        for (LivingEntity member : members)
        {
            if (!(member instanceof ActorEntity actor))
            {
                continue;
            }

            Form form = actor.getForm();

            if (form == null)
            {
                continue;
            }

            Link link = texture.random
                ? folder.get(Math.floorMod(CrowdUtils.entityIndex(member), folder.size()))
                : texture.texture;

            if (link == null)
            {
                continue;
            }

            BaseValue property = FormUtils.getProperty(form, "texture");

            if (property instanceof ValueLink value && !link.equals(value.get()))
            {
                value.set(link);
                actor.setForm(form);
            }
        }
    }

    /** Tints the whole crowd together; there is no per-member colour. */
    private static void applyColor(Replay replay, List<LivingEntity> members, int tick)
    {
        if (replay.keyframes.crowdColor.isEmpty())
        {
            return;
        }

        Color color = replay.keyframes.crowdColor.interpolate(replay.getTick(tick));

        if (color == null)
        {
            return;
        }

        for (LivingEntity member : members)
        {
            if (!(member instanceof ActorEntity actor))
            {
                continue;
            }

            Form form = actor.getForm();

            if (form == null)
            {
                continue;
            }

            /* Colour is a property of the individual form types rather than of Form itself, so
             * it is looked up by name the same way the texture is. A form that has none - an
             * anchor, say - simply is not tinted. */
            BaseValue property = FormUtils.getProperty(form, "color");

            if (property instanceof ValueColor value && !value.get().equals(color))
            {
                value.set(color);
                actor.setForm(form);
            }
        }
    }
}
