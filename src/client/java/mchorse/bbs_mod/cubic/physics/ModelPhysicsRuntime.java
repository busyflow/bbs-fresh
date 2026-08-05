package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Orchestrates bone physics: owns the per-entity simulation state and feeds each chain to the
 * {@link ChainSolver}. The solver itself ({@link ChainSolver}, {@link ChainState}, {@link PhysicsForces})
 * holds the maths.
 */
public final class ModelPhysicsRuntime
{
    /** The two collidable points that carry the body while the chains carry its limbs. */
    static final class BodyState
    {
        public int seenImpulse;
        public int lastAge = Integer.MIN_VALUE;
        public final Vector3f[] pos = {new Vector3f(), new Vector3f()};
        public final Vector3f[] prev = {new Vector3f(), new Vector3f()};
        public final Vector3f[] settled = {new Vector3f(), new Vector3f()};
        public final Vector3f[] settledPrev = {new Vector3f(), new Vector3f()};
        public float length;

        public void reset()
        {
            this.seenImpulse = 0;
            this.lastAge = Integer.MIN_VALUE;
            this.length = 0F;
        }
    }

    static final class InstanceState
    {
        public final Map<String, ChainState> chains = new HashMap<>();
        public final BodyState body = new BodyState();
        public int ragdollLastTick = Integer.MIN_VALUE;
        public int ragdollLastAge = Integer.MIN_VALUE;

        /**
         * The model the chains were last simulated against. States are keyed by form, not by model, so a
         * form swapping its model has to drop the sim built on the old skeleton instead of reusing it.
         */
        public String modelId;
    }

    /**
     * Simulation state per form, per entity. The form is identified by its path in the entity's form tree
     * ({@link FormUtils#getPath}, the same identity the film's physics tracks are keyed by), because a
     * {@link ModelInstance} is shared by every form using that model asset — keying by it collapsed body
     * parts that mirror their target and share a model (paired wings, twin braids) onto one state, where
     * the first one rendered simulated and the rest silently rendered its chains.
     */
    private static final WeakHashMap<IEntity, Map<String, InstanceState>> STATES = new WeakHashMap<>();

    private static final int BODY_SUBSTEPS_PER_TICK = 3;
    private static final int BODY_MAX_STEPS = 30;
    private static final float BODY_BASE_GRAVITY = 0.08F;
    private static final float BODY_COLLISION_FRICTION = 0.7F;

    private ModelPhysicsRuntime()
    {
    }

    public static void clearCache()
    {
        ModelPhysicsCache.clear();
        STATES.clear();
    }

    public static void invalidate(String modelId)
    {
        for (Map<String, InstanceState> byForm : STATES.values())
        {
            if (byForm != null)
            {
                byForm.values().removeIf((state) -> Objects.equals(state.modelId, modelId));
            }
        }
    }

    public static void apply(IEntity entity, ModelInstance instance, float transition, Matrix4f baseTransform)
    {
        if (entity == null || instance == null || instance.model == null)
        {
            return;
        }

        IModel model = instance.model;

        if (!(instance.form instanceof ModelForm form))
        {
            return;
        }

        ModelPhysicsCache.Compiled compiled = null;
        RagdollControl ragdoll = form.ragdollOverride;

        if (ragdoll != null)
        {
            /* A ragdoll replaces the model's own chains rather than joining them: the same bone
             * cannot be both a strand of hair with its own settings and part of a limb that has
             * gone slack, and the ragdoll is the one the shot is about. */
            compiled = ModelPhysicsCache.getRagdoll(model, ragdoll);
        }
        else if (form.physics.get() instanceof MapType map)
        {
            compiled = ModelPhysicsCache.getFromData(model, map);
        }

        if (compiled == null || (compiled.chains() == null || compiled.chains().isEmpty()) && (ragdoll == null || compiled.ragdoll() == null))
        {
            return;
        }

        Map<String, ModelConstraintsConfig.BoneConstraint> constraints = ModelConstraintsRuntime.getBones(instance);

        Map<String, InstanceState> byForm = STATES.computeIfAbsent(entity, (e) -> new HashMap<>());
        InstanceState state = byForm.computeIfAbsent(FormUtils.getPath(form), (k) -> new InstanceState());

        if (!Objects.equals(state.modelId, instance.id))
        {
            state.chains.clear();
            state.body.reset();
            state.ragdollLastTick = Integer.MIN_VALUE;
            state.ragdollLastAge = Integer.MIN_VALUE;
            state.modelId = instance.id;
        }

        if (ragdoll != null)
        {
            /* The clip object can start at the same timeline tick on every playback, so its
             * impulse id alone cannot distinguish a fresh entry from a previous completed fall.
             * The override itself is recreated when the clip is entered. Consume that edge once,
             * and also treat a backwards playhead as a restart when a clip covers the loop point. */
            boolean tracked = state.ragdollLastTick != Integer.MIN_VALUE;
            int playbackDelta = tracked ? ragdoll.playbackTick - state.ragdollLastTick : 0;
            int ageDelta = tracked ? entity.getAge() - state.ragdollLastAge : 0;
            boolean discontinuity = tracked && (playbackDelta < 0 || Math.abs(playbackDelta - ageDelta) > 3);

            if (ragdoll.fresh || discontinuity)
            {
                state.chains.clear();
                state.body.reset();
            }

            ragdoll.fresh = false;
            state.ragdollLastTick = ragdoll.playbackTick;
            state.ragdollLastAge = entity.getAge();
        }
        else
        {
            state.ragdollLastTick = Integer.MIN_VALUE;
            state.ragdollLastAge = Integer.MIN_VALUE;
        }

        /* The wind track (if keyframed) replaces the configured wind wholesale at playback, mirroring how the
         * physics track layers over the per-chain config. */
        ModelPhysicsConfig.Wind wind = compiled.wind();

        if (form.windControlOverride != null)
        {
            WindControl override = form.windControlOverride;

            wind = new ModelPhysicsConfig.Wind(override.strength, override.x, override.y, override.z, override.turbulence, override.turbulenceSpeed, override.turbulenceScale, override.local);
        }

        wind = resolveWindDirection(wind, baseTransform);

        /* Before the chains, not after: they anchor on the bone frames, so the body has to have
         * fallen already or the limbs would hang from a figure still standing upright. */
        applyBody(ragdoll, compiled.ragdoll(), model, entity.getWorld(), entity.getAge(), transition, state.body, baseTransform);

        if (compiled.chains() != null && !compiled.chains().isEmpty())
        {
            applyCompiled(entity.getWorld(), entity.getAge(), transition, model, instance, compiled.chains(), wind, constraints, state, baseTransform);
        }

        applyImpulse(ragdoll, state);
    }

    /**
     * Simulates a chest and hips point, then turns the root bone to join them.
     *
     * <p>This is deliberately a tiny rigid body, not a second general-purpose physics engine.
     * Two points and one distance constraint are enough for the body to land on blocks, slide off
     * ledges and settle differently on slopes, while retaining the chain solver for the detailed
     * parts where it is strongest. The same block resolver is used for both paths, so a body hit
     * and a hand hit have the same inelastic contacts.</p>
     */
    private static void applyBody(RagdollControl ragdoll, ModelPhysicsCache.RagdollRig rig, IModel model, World world, int age, float transition, BodyState state, Matrix4f baseTransform)
    {
        if (ragdoll == null || !ragdoll.topple || rig == null)
        {
            state.reset();

            return;
        }

        Set<String> wanted = new HashSet<>();

        wanted.add(rig.rootBone());
        wanted.add(rig.chestBone());

        Map<String, PivotFrame> frames = new HashMap<>(4);
        ModelPivotFrames.collect(model, wanted, frames, baseTransform);

        PivotFrame hipsFrame = frames.get(rig.rootBone());
        PivotFrame chestFrame = frames.get(rig.chestBone());

        if (hipsFrame == null || chestFrame == null)
        {
            state.reset();

            return;
        }

        Vector3f rest = new Vector3f(chestFrame.position()).sub(hipsFrame.position());

        if (rest.lengthSquared() < ChainSolver.EPS * ChainSolver.EPS)
        {
            state.reset();

            return;
        }

        if (state.seenImpulse != ragdoll.impulse || state.lastAge == Integer.MIN_VALUE)
        {
            seedBody(state, ragdoll, age, hipsFrame.position(), chestFrame.position());
        }
        else
        {
            stepBody(state, ragdoll, world, age);
        }

        float alpha = Math.max(0F, Math.min(1F, transition));
        Vector3f hips = new Vector3f(state.settledPrev[0]).lerp(state.settled[0], alpha);
        Vector3f chest = new Vector3f(state.settledPrev[1]).lerp(state.settled[1], alpha);
        Vector3f fallen = new Vector3f(chest).sub(hips);

        if (fallen.lengthSquared() < ChainSolver.EPS * ChainSolver.EPS)
        {
            return;
        }

        Quaternionf worldDelta = new Quaternionf().rotationTo(rest.normalize(), fallen.normalize());
        Quaternionf rootRotation = hipsFrame.worldRotation();
        Quaternionf localDelta = new Quaternionf(rootRotation).invert().mul(worldDelta).mul(rootRotation);
        Vector3f localMove = new Vector3f(hips).sub(hipsFrame.position());

        new Quaternionf(hipsFrame.parentRotation()).invert().transform(localMove);

        PoseTransform transform = new PoseTransform();
        transform.rotationMode = Transform.RotationMode.QUATERNION;
        transform.quat.set(localDelta);

        /* Cubic bones store their local pivot shift in pixels and mirror X while rendering it. */
        transform.translate.set(-localMove.x * 16F, localMove.y * 16F, localMove.z * 16F);

        Pose pose = new Pose();
        pose.transforms.put(rig.rootBone(), transform);
        model.applyPose(pose);
    }

    private static void seedBody(BodyState state, RagdollControl ragdoll, int age, Vector3f hips, Vector3f chest)
    {
        state.seenImpulse = ragdoll.impulse;
        state.lastAge = age;
        state.pos[0].set(hips);
        state.pos[1].set(chest);
        state.prev[0].set(hips);
        state.prev[1].set(chest);
        state.length = state.pos[0].distance(state.pos[1]);

        Vector3f push = new Vector3f(ragdoll.x, ragdoll.y, ragdoll.z).mul(ragdoll.strength);
        Vector3f sideways = new Vector3f(-ragdoll.z, 0F, ragdoll.x);

        if (sideways.lengthSquared() < ChainSolver.EPS * ChainSolver.EPS)
        {
            sideways.set(1F, 0F, 0F);
        }

        sideways.normalize().mul(ragdoll.strength * ragdoll.flail * 0.22F);

        /* Giving the chest more of the blow makes an immediate, visible lean; gravity and block
         * contact take over from the following sub-step instead of a permanently applied force. */
        state.prev[0].sub(new Vector3f(push).mul(0.3F));
        state.prev[1].sub(push);
        state.prev[0].add(sideways);
        state.prev[1].sub(sideways);
        state.pos[0].add(new Vector3f(push).mul(0.015F));
        state.pos[1].add(new Vector3f(push).mul(0.05F));
        copyBody(state.pos, state.settled);
        copyBody(state.pos, state.settledPrev);
    }

    private static void stepBody(BodyState state, RagdollControl ragdoll, World world, int age)
    {
        int delta = age - state.lastAge;

        if (delta <= 0)
        {
            return;
        }

        if (delta > BODY_MAX_STEPS / BODY_SUBSTEPS_PER_TICK)
        {
            /* A scrub has no missing intermediate simulation to replay. Keeping the settled body
             * rather than inventing a catch-up avoids a frame stall and lets the normal hit start
             * again when playback resumes. */
            state.lastAge = age;

            return;
        }

        copyBody(state.settled, state.settledPrev);

        int steps = Math.min(delta * BODY_SUBSTEPS_PER_TICK, BODY_MAX_STEPS);
        float h = 1F / BODY_SUBSTEPS_PER_TICK;
        float damp = (float) Math.pow(1F - Math.max(0F, Math.min(1F, ragdoll.damping)), h);
        float gravity = BODY_BASE_GRAVITY * ragdoll.gravity * h * h;
        float radius = Math.max(0.16F, ragdoll.radius * 1.75F);
        Vector3f velocity = new Vector3f();

        for (int step = 0; step < steps; step++)
        {
            for (int i = 0; i < state.pos.length; i++)
            {
                velocity.set(state.pos[i]).sub(state.prev[i]).mul(damp);
                state.prev[i].set(state.pos[i]);
                state.pos[i].add(velocity).y -= gravity;
            }

            for (int pass = 0; pass < 3; pass++)
            {
                constrainBodyLength(state);

                if (ragdoll.collisions && world != null)
                {
                    ModelPhysicsWorldCollisions.resolve(world, state.pos, state.prev, 0, state.pos.length, radius, BODY_COLLISION_FRICTION);
                }
            }

            constrainBodyLength(state);
        }

        copyBody(state.pos, state.settled);
        state.lastAge = age;
    }

    private static void constrainBodyLength(BodyState state)
    {
        Vector3f delta = new Vector3f(state.pos[1]).sub(state.pos[0]);
        float length = delta.length();

        if (length <= ChainSolver.EPS || state.length <= ChainSolver.EPS)
        {
            return;
        }

        delta.mul((length - state.length) / (length * 2F));
        state.pos[0].add(delta);
        state.pos[1].sub(delta);
    }

    private static void copyBody(Vector3f[] source, Vector3f[] target)
    {
        for (int i = 0; i < source.length; i++)
        {
            target[i].set(source[i]);
        }
    }

    /**
     * Shove the limbs once, on the tick the blow lands.
     *
     * <p>Verlet keeps speed as the gap between where a point is and where it was, so a blow is
     * dealt by moving the history backwards rather than by adding a force: the point is already
     * where it was, and now it was somewhere further behind, so it leaves with that speed and
     * nothing keeps pushing it. Applying it every frame instead would be a jet, not an impact,
     * and the body would sail off rather than fall.</p>
     *
     * <p>Further down a limb gets more of it, so an arm whips rather than sliding across sideways
     * - the shoulder barely moves and the hand carries.</p>
     */
    private static void applyImpulse(RagdollControl ragdoll, InstanceState state)
    {
        if (ragdoll == null)
        {
            for (ChainState chain : state.chains.values())
            {
                /* Forget the blow once the ragdoll ends, so scrubbing back into it lands again. */
                chain.seenImpulse = 0;
            }

            return;
        }

        if (ragdoll.strength <= 0F)
        {
            return;
        }

        for (Map.Entry<String, ChainState> entry : state.chains.entrySet())
        {
            ChainState chain = entry.getValue();

            if (chain.seenImpulse == ragdoll.impulse || chain.pos == null || chain.prev == null)
            {
                continue;
            }

            chain.seenImpulse = ragdoll.impulse;

            int count = chain.prev.length;
            int hash = 31 * entry.getKey().hashCode() + ragdoll.impulse;
            float jitterX = signedHash(hash ^ 0x68bc21eb);
            float jitterY = signedHash(hash ^ 0x02e5be93);
            float jitterZ = signedHash(hash ^ 0x7f4a7c15);

            for (int i = 0; i < count; i++)
            {
                float along = count <= 1 ? 1F : i / (float) (count - 1);
                float scatter = ragdoll.strength * ragdoll.flail * along * 0.18F;

                chain.prev[i].sub(
                    ragdoll.x * ragdoll.strength * along + jitterX * scatter,
                    ragdoll.y * ragdoll.strength * along + jitterY * scatter,
                    ragdoll.z * ragdoll.strength * along + jitterZ * scatter
                );
            }
        }
    }

    /** Stable noise keeps playback and export identical while stopping every limb moving as one slab. */
    private static float signedHash(int value)
    {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;

        return ((value & 0xffff) / 32767.5F) - 1F;
    }

    /**
     * When the wind direction is local to the model, rotates it by the model's world orientation (the
     * rotation baked into {@code baseTransform}, the same transform the chain positions live in), so the
     * wind follows the model as it turns. The solver only ever sees a plain world-space direction. A
     * world-space or inactive wind is returned unchanged.
     */
    private static ModelPhysicsConfig.Wind resolveWindDirection(ModelPhysicsConfig.Wind wind, Matrix4f baseTransform)
    {
        if (wind == null || !wind.local() || !wind.active() || baseTransform == null)
        {
            return wind;
        }

        Vector3f dir = new Vector3f(wind.x(), wind.y(), wind.z());

        baseTransform.transformDirection(dir);

        return new ModelPhysicsConfig.Wind(wind.strength(), dir.x, dir.y, dir.z, wind.turbulence(), wind.turbulenceSpeed(), wind.turbulenceScale(), false);
    }

    private static void applyCompiled(World world, int age, float transition, IModel model, ModelInstance instance, List<ModelPhysicsCache.CompiledChain> compiledChains, ModelPhysicsConfig.Wind wind, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, InstanceState state, Matrix4f baseTransform)
    {
        Set<String> wanted = new HashSet<>();
        Set<String> chainIds = new HashSet<>();

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            chainIds.add(chain.id());
            wanted.addAll(chain.chainRootToEnd());

            if (chain.targetBone() != null && !chain.targetBone().isEmpty())
            {
                wanted.add(chain.targetBone());
            }
        }

        if (!state.chains.isEmpty())
        {
            Iterator<String> it = state.chains.keySet().iterator();

            while (it.hasNext())
            {
                if (!chainIds.contains(it.next()))
                {
                    it.remove();
                }
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames, baseTransform);

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            applyChain(world, age, transition, model, instance, chain, wind, constraints, frames, state);
        }
    }

    private static void applyChain(World world, int age, float transition, IModel model, ModelInstance instance, ModelPhysicsCache.CompiledChain chain, ModelPhysicsConfig.Wind wind, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Map<String, PivotFrame> frames, InstanceState instanceState)
    {
        List<String> ids = chain.chainRootToEnd();
        int pivotCount = ids.size();
        int pointCount = pivotCount + 1;

        if (pivotCount < 1)
        {
            return;
        }

        /* The film physics track layers a per-chain control over the config, keyed by the chain's
         * root bone, replacing its dynamic scalars wholesale (mirrors the IK track). */
        PhysicsControl control = null;

        if (instance != null && instance.form instanceof ModelForm modelForm && !modelForm.physicsControlOverrides.isEmpty())
        {
            control = modelForm.physicsControlOverrides.get(ids.get(0));
        }

        if (control != null && !control.enabled)
        {
            return;
        }

        float weight = control != null ? control.weight : chain.weight();

        if (weight <= 0F)
        {
            return;
        }

        float gravity = control != null ? control.gravity : chain.gravity();
        float damping = control != null ? control.damping : chain.damping();
        float stiffness = control != null ? control.stiffness : chain.stiffness();

        ChainState state = instanceState.chains.computeIfAbsent(chain.id(), (k) -> new ChainState());

        if (state.pos == null || state.pos.length != pointCount)
        {
            state.pos = new Vector3f[pointCount];
            state.prev = new Vector3f[pointCount];
            state.settledLocal = new Vector3f[pointCount];
            state.settledPrevLocal = new Vector3f[pointCount];
            state.render = new Vector3f[pointCount];
            state.poseLocal = new Vector3f[pointCount];

            for (int i = 0; i < pointCount; i++)
            {
                state.pos[i] = new Vector3f();
                state.prev[i] = new Vector3f();
                state.settledLocal[i] = new Vector3f();
                state.settledPrevLocal[i] = new Vector3f();
                state.render[i] = new Vector3f();
                state.poseLocal[i] = new Vector3f();
            }

            state.lastAge = Integer.MIN_VALUE;
        }

        List<PivotFrame> chainFrames = new ArrayList<>(pivotCount);

        for (int i = 0; i < pivotCount; i++)
        {
            PivotFrame frame = frames.get(ids.get(i));

            if (frame == null)
            {
                return;
            }

            chainFrames.add(frame);
        }

        PivotFrame rootFrame = chainFrames.get(0);
        Vector3f anchor = rootFrame.position();
        Quaternionf anchorRotation = rootFrame.worldRotation();

        Vector3f target = null;
        if (instance != null && instance.form instanceof ModelForm modelForm)
        {
            String rootBone = ids.get(0);
            Vector3f worldPos = modelForm.physicsTargetOverrides.get(rootBone);

            if (worldPos != null)
            {
                float targetWeight = modelForm.physicsTargetWeights.getOrDefault(rootBone, 1F);

                if (targetWeight >= 1F)
                {
                    target = new Vector3f(worldPos);
                }
                else if (targetWeight > 0F)
                {
                    /* The binding is fading in or out (it crossed a no-target keyframe). Easing the pin point
                     * from the chain's current tip toward the full target by the fade amount lets the chain
                     * travel there smoothly instead of snapping, with no soft-target mode in the solver. */
                    Vector3f tip = state.pos[state.pos.length - 1];

                    target = state.lastAge == Integer.MIN_VALUE
                        ? new Vector3f(worldPos)
                        : new Vector3f(tip).lerp(worldPos, targetWeight);
                }
                /* targetWeight <= 0: fully faded out — leave the chain free this frame. */
            }
        }

        if (target != null)
        {
            if (state.lastAge == Integer.MIN_VALUE)
            {
                state.pos[state.pos.length - 1].set(target);
                state.prev[state.pos.length - 1].set(target);
            }
        }
        else if (chain.targetBone() != null && !chain.targetBone().isEmpty())
        {
            PivotFrame targetFrame = frames.get(chain.targetBone());
            if (targetFrame != null)
            {
                target = targetFrame.position();
                if (state.lastAge == Integer.MIN_VALUE)
                {
                    state.pos[state.pos.length - 1].set(target);
                    state.prev[state.pos.length - 1].set(target);
                }
            }
        }

        ChainSolver.computePoseTargets(model, ids, chainFrames, chain.restLengths(), anchor, anchorRotation, target != null, state);
        ChainSolver.step(world, age, transition, model, ids, chain, gravity, damping, stiffness, wind, constraints, anchor, anchorRotation, chainFrames.get(0).parentRotation(), target, chainFrames, state);

        Vector3f[] positions = ChainSolver.renderInterpolate(state, state.renderAlpha, anchor, anchorRotation, target);
        ModelRotationBlender.applyWeightedRotations(model, chainFrames.get(0).parentRotation(), ids, positions, weight);
    }
}
