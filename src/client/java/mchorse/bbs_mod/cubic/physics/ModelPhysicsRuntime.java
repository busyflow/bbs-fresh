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
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
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
    /**
     * The body falling over, which the limb chains cannot express.
     *
     * <p>A chain hangs from an anchor the animation owns, so limbs alone give a figure that flails
     * while standing perfectly upright. What is missing is the one thing that makes it read as a
     * body: it stops holding itself up. That is a topple - the whole model turning about the
     * ground at its feet - so it is a single angle rather than a second physics engine.</p>
     */
    static final class ToppleState
    {
        public int seenImpulse;
        public int lastAge = Integer.MIN_VALUE;

        /** Radians from upright, and the angle of the previous tick for the render to sit between. */
        public float angle;
        public float prevAngle;
        public float velocity;

        /** The horizontal axis it turns about, in the model's own frame. */
        public final Vector3f axis = new Vector3f(1F, 0F, 0F);
    }

    static final class InstanceState
    {
        public final Map<String, ChainState> chains = new HashMap<>();
        public final ToppleState topple = new ToppleState();

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

    /** A body lying flat, the angle a topple stops at. */
    private static final float HALF_PI = (float) (Math.PI * 0.5D);

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

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, ModelConstraintsConfig.BoneConstraint> constraints = ModelConstraintsRuntime.getBones(instance);

        Map<String, InstanceState> byForm = STATES.computeIfAbsent(entity, (e) -> new HashMap<>());
        InstanceState state = byForm.computeIfAbsent(FormUtils.getPath(form), (k) -> new InstanceState());

        if (!Objects.equals(state.modelId, instance.id))
        {
            state.chains.clear();
            state.modelId = instance.id;
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
        applyTopple(ragdoll, model, entity.getAge(), transition, state.topple, baseTransform);

        applyCompiled(entity.getWorld(), entity.getAge(), transition, model, instance, compiled.chains(), wind, constraints, state, baseTransform);

        applyImpulse(ragdoll, state);
    }

    /**
     * Tip the whole model over onto the ground and hold it there.
     *
     * <p>Modelled as a falling stick rather than as another chain, because that is the shape of
     * the motion: a body that stops holding itself up turns about its feet, slowly at first and
     * fastest as it lands, and then stays down. A chain cannot do it - a chain hangs from
     * something the animation is still holding upright.</p>
     *
     * <p>The blow starts it and gravity finishes it. Landing takes the speed rather than
     * returning it, so the body settles instead of rocking, and the small bias in the torque
     * means a body that was pushed almost straight down still eventually goes over rather than
     * balancing forever on the spot.</p>
     */
    private static void applyTopple(RagdollControl ragdoll, IModel model, int age, float transition, ToppleState state, Matrix4f baseTransform)
    {
        if (ragdoll == null || !ragdoll.topple)
        {
            state.seenImpulse = 0;
            state.angle = 0F;
            state.prevAngle = 0F;
            state.velocity = 0F;

            return;
        }

        if (state.seenImpulse != ragdoll.impulse)
        {
            state.seenImpulse = ragdoll.impulse;
            state.angle = 0F;
            state.prevAngle = 0F;
            state.lastAge = age;

            /* It falls the way it was hit: about the horizontal axis across the blow. A blow
             * straight up or down picks an arbitrary one rather than none, so it still goes over. */
            Vector3f fall = new Vector3f(ragdoll.x, 0F, ragdoll.z);

            if (fall.lengthSquared() < 1.0E-6F)
            {
                fall.set(0F, 0F, 1F);
            }

            fall.normalize();

            Vector3f axis = new Vector3f(0F, 1F, 0F).cross(fall).normalize();

            /* The bones turn in the model's frame, not the world's. */
            new Matrix4f(baseTransform).invert().transformDirection(axis);

            if (axis.lengthSquared() < 1.0E-6F)
            {
                axis.set(1F, 0F, 0F);
            }

            state.axis.set(axis.normalize());
            state.velocity = ragdoll.strength * 0.25F;
        }

        if (age != state.lastAge)
        {
            state.lastAge = age;
            state.prevAngle = state.angle;

            if (state.angle < HALF_PI)
            {
                state.velocity += (float) (Math.sin(state.angle + 0.12D) * ragdoll.gravity * 0.045D);
                state.velocity *= 1F - MathUtils.clamp(ragdoll.damping, 0F, 1F) * 0.25F;
                state.angle += state.velocity;

                if (state.angle >= HALF_PI)
                {
                    state.angle = HALF_PI;
                    state.velocity = 0F;
                }
            }
        }

        float angle = Lerps.lerp(state.prevAngle, state.angle, MathUtils.clamp(transition, 0F, 1F));

        if (angle <= 0.0001F)
        {
            return;
        }

        Pose pose = new Pose();

        for (String root : model.getRootGroupKeys())
        {
            PoseTransform transform = new PoseTransform();

            transform.rotationMode = Transform.RotationMode.QUATERNION;
            transform.quat.setAngleAxis(angle, state.axis.x, state.axis.y, state.axis.z);

            /* Follows the fall rather than being applied at once, so the body sinks as it goes
             * over instead of dropping through the floor while still standing. */
            transform.translate.y -= ragdoll.toppleDrop * (float) Math.sin(angle);

            pose.transforms.put(root, transform);
        }

        model.applyPose(pose);
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

        for (ChainState chain : state.chains.values())
        {
            if (chain.seenImpulse == ragdoll.impulse || chain.pos == null || chain.prev == null)
            {
                continue;
            }

            chain.seenImpulse = ragdoll.impulse;

            int count = chain.prev.length;

            for (int i = 0; i < count; i++)
            {
                float along = count <= 1 ? 1F : i / (float) (count - 1);

                chain.prev[i].sub(
                    ragdoll.x * ragdoll.strength * along,
                    ragdoll.y * ragdoll.strength * along,
                    ragdoll.z * ragdoll.strength * along
                );
            }
        }
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
