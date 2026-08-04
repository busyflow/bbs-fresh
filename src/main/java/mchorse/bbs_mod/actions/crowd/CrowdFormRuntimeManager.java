package mchorse.bbs_mod.actions.crowd;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.crowd.CrowdSpawnActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdBehaviorActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdRagdollActionClip;
import mchorse.bbs_mod.actions.types.crowd.CrowdUtils;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-owned lifecycle for replay forms configured as crowds. */
public class CrowdFormRuntimeManager
{
    private final ServerWorld world;
    private final Film film;
    private final Map<String, Runtime> runtimes = new HashMap<>();
    private final Set<String> seen = new HashSet<>();

    public CrowdFormRuntimeManager(ServerWorld world, Film film)
    {
        this.world = world;
        this.film = film;
    }

    public void tick(int tick, SuperFakePlayer player)
    {
        this.seen.clear();

        for (Replay replay : this.film.replays.getList())
        {
            if (!replay.enabled.get() || !(replay.form.get() instanceof CrowdForm crowd))
            {
                continue;
            }

            String id = replay.getId();
            this.seen.add(id);

            Runtime runtime = this.runtimes.computeIfAbsent(id, key -> new Runtime(replay));
            runtime.replay = replay;
            runtime.crowd = crowd;
            runtime.applyTextureKeyframe(tick);

            if (runtime.needsRespawn(tick))
            {
                runtime.remove();
                runtime.spawn(tick, player);
            }

            CrowdRagdollActionClip ragdoll = runtime.getRagdoll(tick);

            if (ragdoll == null)
            {
                runtime.applyBehavior(tick, player);
                runtime.applyLookTarget(tick);
                runtime.applyMotionPath(tick);
                runtime.applyJump(tick);
            }

            runtime.applyEquipment(tick);
            runtime.stabilizeFormation(tick);
            runtime.applyRagdoll(tick, ragdoll);
        }

        Iterator<Map.Entry<String, Runtime>> iterator = this.runtimes.entrySet().iterator();

        while (iterator.hasNext())
        {
            Map.Entry<String, Runtime> entry = iterator.next();

            if (!this.seen.contains(entry.getKey()))
            {
                entry.getValue().remove();
                iterator.remove();
            }
        }
    }

    public void reset()
    {
        for (Runtime runtime : this.runtimes.values())
        {
            runtime.remove();
        }

        this.runtimes.clear();
    }

    private final class Runtime
    {
        private Replay replay;
        private CrowdForm crowd;
        private final CrowdSpawnActionClip spawn = new CrowdSpawnActionClip();
        private final CrowdBehaviorActionClip behavior = new CrowdBehaviorActionClip();
        private boolean spawned;
        private Form member;
		private byte[] memberData = new byte[0];
        private int lastMemberDataCheckTick = Integer.MIN_VALUE;
        private int count;
        private int liveLimit;
        private float spacing;
        private boolean perBlock;
        private float health;
        private boolean randomArmor;
        private float randomArmorCoverage;
        private float randomArmorRichness;
        private boolean randomArmorHead;
        private boolean randomArmorChest;
        private boolean randomArmorLegs;
        private boolean randomArmorFeet;
        private int randomArmorHeadMaterials;
        private int randomArmorChestMaterials;
        private int randomArmorLegsMaterials;
        private int randomArmorFeetMaterials;
        private boolean randomTextures;
        private Link randomTextureFolder;
        private boolean recursiveTextures;
        private int textureRevision;
        private int formation;
        private float hollow;
        private float radius;
        private int seed;
        private float variation;
        private boolean useVolume;
        private float volumeX;
        private float volumeY;
        private float volumeZ;
        private float volumeOffsetX;
        private float volumeOffsetY;
        private float volumeOffsetZ;
        private List<LivingEntity> entities = List.of();
        private List<Vec3d> spawnPositions = List.of();
        private List<Vec3d> basePositions = List.of();
        private Vec3d spawnOrigin = Vec3d.ZERO;
        private int[] blockedTicks = new int[0];
        private int[] bypassSides = new int[0];
        private int[] bypassTicks = new int[0];
        private double[] obstacleBaseY = new double[0];
        private double[] jumpGroundY = new double[0];
        private double[] jumpHeights = new double[0];
        private Vec3d[] lastNominalPositions = new Vec3d[0];
        private Vec3d[] steeringDirections = new Vec3d[0];
		private final Map<Long, Double> terrainHeights = new HashMap<>();
		private final ItemStack[] appliedEquipment = new ItemStack[EquipmentSlot.values().length];
		private int activeJumps;
        private int lastMotionTick = Integer.MIN_VALUE;
        private double volumeTopY = Double.POSITIVE_INFINITY;
        private final float[] lookRotation = new float[2];
        private boolean pathApplied;
        private boolean hadLookTarget;
        private boolean ragdollActive;

        private Runtime(Replay replay)
        {
            this.replay = replay;
        }

        /**
         * The name this crowd answers to. An authored group name lets another crowd form
         * name it as its enemy group; without one the crowd gets a private tag derived from
         * its replay so unrelated crowds never fight or gather each other by accident.
         */
        private String tag()
        {
            String group = this.crowd == null || this.crowd.groupName.get() == null
                ? ""
                : this.crowd.groupName.get().trim();

            return group.isEmpty()
                ? "crowd_form_" + Integer.toHexString(this.replay.getId().hashCode())
                : group;
        }

        private void spawn(int tick, SuperFakePlayer player)
        {
            Form member = FormUtils.copy(this.crowd.memberForm.get());

            if (member == null || member instanceof CrowdForm)
            {
                return;
            }

            /* One-time migration cleanup for ghost actors spawned by v0.22 and earlier. */
            CrowdUtils.removeLegacyCrowd(CrowdFormRuntimeManager.this.world, this.tag());

            this.spawn.crowdTag.set(this.tag());
            this.spawn.useActorForm.set(true);
            this.spawn.actorForm.set(member);
            this.spawn.count.set(this.crowd.count.get());
            this.spawn.liveLimit.set(this.desiredLiveLimit());
            this.spawn.spacing.set(this.crowd.spacing.get());
            this.spawn.health.set(this.crowd.health.get());
            this.spawn.randomArmor.set(this.crowd.randomArmor.get());
            this.spawn.randomArmorCoverage.set(this.crowd.randomArmorCoverage.get());
            this.spawn.randomArmorRichness.set(this.crowd.randomArmorRichness.get());
            this.spawn.randomArmorHead.set(this.crowd.randomArmorHead.get());
            this.spawn.randomArmorChest.set(this.crowd.randomArmorChest.get());
            this.spawn.randomArmorLegs.set(this.crowd.randomArmorLegs.get());
            this.spawn.randomArmorFeet.set(this.crowd.randomArmorFeet.get());
            this.spawn.randomArmorHeadMaterials.set(this.crowd.randomArmorHeadMaterials.get());
            this.spawn.randomArmorChestMaterials.set(this.crowd.randomArmorChestMaterials.get());
            this.spawn.randomArmorLegsMaterials.set(this.crowd.randomArmorLegsMaterials.get());
            this.spawn.randomArmorFeetMaterials.set(this.crowd.randomArmorFeetMaterials.get());
            this.spawn.randomTextures.set(this.crowd.randomTextures.get());
            this.spawn.textureOverride.set(this.crowd.textureOverride.get());
            this.spawn.randomTextureFolder.set(this.crowd.randomTextureFolder.get());
            this.spawn.recursiveTextures.set(this.crowd.recursiveTextures.get());
            this.spawn.formation.set(this.crowd.formation.get());
            this.spawn.hollow.set(this.crowd.hollow.get());
            this.spawn.radius.set(this.crowd.radius.get());
            this.spawn.seed.set(this.crowd.seed.get());
            this.spawn.yawVariation.set(this.crowd.variation.get());
            this.spawn.boxX.set(Math.max(0.1F, this.crowd.volume.get().scale.x));
            this.spawn.boxY.set(Math.max(0.1F, this.crowd.volume.get().scale.y));
            this.spawn.boxZ.set(Math.max(0.1F, this.crowd.volume.get().scale.z));
            this.spawn.randomYaw.set(true);
            this.spawn.spawnOnBlock.set(true);
            this.spawn.snapToBlock.set(this.crowd.perBlock.get());
            this.spawn.skipUnsafe.set(false);
            /* remove() performs the authoritative tag sweep before every structural rebuild. */
            this.spawn.replaceExisting.set(false);
            this.entities = this.spawn.spawn(CrowdFormRuntimeManager.this.world, CrowdFormRuntimeManager.this.film, this.replay, tick,
                this.crowd.useVolume.get()
                    ? new Vec3d(this.crowd.volume.get().translate.x, this.crowd.volume.get().translate.y, this.crowd.volume.get().translate.z)
                    : Vec3d.ZERO,
                this.crowd.useVolume.get());
            List<Vec3d> positions = new ArrayList<>(this.entities.size());

            for (LivingEntity entity : this.entities)
            {
                positions.add(entity.getPos());
            }

            this.spawnPositions = new ArrayList<>(positions);
            this.basePositions = new ArrayList<>(positions);
            this.spawnOrigin = CrowdWalkEvaluator.replayOrigin(this.replay, tick);
            this.blockedTicks = new int[this.entities.size()];
            this.bypassSides = new int[this.entities.size()];
            this.bypassTicks = new int[this.entities.size()];
            this.obstacleBaseY = new double[this.entities.size()];
            this.jumpGroundY = new double[this.entities.size()];
            this.jumpHeights = new double[this.entities.size()];
            this.lastNominalPositions = new Vec3d[this.entities.size()];
            this.steeringDirections = new Vec3d[this.entities.size()];
            Arrays.fill(this.obstacleBaseY, Double.NaN);
            Arrays.fill(this.jumpGroundY, Double.NaN);
            this.lastMotionTick = tick - 1;
			this.activeJumps = 0;
			Arrays.fill(this.appliedEquipment, null);

            if (this.crowd.useVolume.get())
            {
                Vec3d replayPosition = CrowdUtils.replayPosition(this.replay, tick);
                this.volumeTopY = replayPosition.y + this.crowd.volume.get().translate.y + Math.max(0D, this.crowd.volume.get().scale.y);
            }
            else
            {
                this.volumeTopY = Double.POSITIVE_INFINITY;
            }
            this.member = this.crowd.memberForm.get();
			this.memberData = this.member == null ? new byte[0]
				: DataStorageUtils.writeToBytes(FormUtils.toData(this.member));
            this.lastMemberDataCheckTick = tick;
            this.count = this.crowd.count.get();
            this.liveLimit = this.desiredLiveLimit();
            this.spacing = this.crowd.spacing.get();
            this.perBlock = this.crowd.perBlock.get();
            this.health = this.crowd.health.get();
            this.randomArmor = this.crowd.randomArmor.get();
            this.randomArmorCoverage = this.crowd.randomArmorCoverage.get();
            this.randomArmorRichness = this.crowd.randomArmorRichness.get();
            this.randomArmorHead = this.crowd.randomArmorHead.get();
            this.randomArmorChest = this.crowd.randomArmorChest.get();
            this.randomArmorLegs = this.crowd.randomArmorLegs.get();
            this.randomArmorFeet = this.crowd.randomArmorFeet.get();
            this.randomArmorHeadMaterials = this.crowd.randomArmorHeadMaterials.get();
            this.randomArmorChestMaterials = this.crowd.randomArmorChestMaterials.get();
            this.randomArmorLegsMaterials = this.crowd.randomArmorLegsMaterials.get();
            this.randomArmorFeetMaterials = this.crowd.randomArmorFeetMaterials.get();
            this.randomTextures = this.crowd.randomTextures.get();
            this.randomTextureFolder = this.crowd.randomTextureFolder.get();
            this.recursiveTextures = this.crowd.recursiveTextures.get();
            this.textureRevision = this.crowd.textureRevision.get();
            this.formation = this.crowd.formation.get();
            this.hollow = this.crowd.hollow.get();
            this.radius = this.crowd.radius.get();
            this.seed = this.crowd.seed.get();
            this.variation = this.crowd.variation.get();
            this.useVolume = this.crowd.useVolume.get();
            this.volumeX = this.crowd.volume.get().scale.x;
            this.volumeY = this.crowd.volume.get().scale.y;
            this.volumeZ = this.crowd.volume.get().scale.z;
            this.volumeOffsetX = this.crowd.volume.get().translate.x;
            this.volumeOffsetY = this.crowd.volume.get().translate.y;
            this.volumeOffsetZ = this.crowd.volume.get().translate.z;
            this.spawned = true;
            this.pathApplied = false;
            this.hadLookTarget = false;
            this.ragdollActive = false;
        }

        /**
         * Every member is a real actor now, so the crowd spawns its full count. CrowdForm's
         * own ceiling is what keeps that from melting a server tick.
         */
        private int desiredLiveLimit()
        {
            return this.crowd.count.get();
        }

        private boolean needsRespawn(int tick)
        {
			Form currentMember = this.crowd.memberForm.get();
            boolean memberChanged = this.member != currentMember;

            /* Editing a form mutates the same object. Detect that without serializing a
             * potentially large model twenty times per second for every crowd. */
            if (this.spawned && !memberChanged && this.lastMemberDataCheckTick != tick
                && Math.floorMod(tick + this.replay.getId().hashCode(), 5) == 0)
            {
                this.lastMemberDataCheckTick = tick;
                byte[] currentMemberData = currentMember == null ? new byte[0]
                    : DataStorageUtils.writeToBytes(FormUtils.toData(currentMember));

                memberChanged = !Arrays.equals(this.memberData, currentMemberData);
            }

            return !this.spawned && currentMember != null
				|| this.spawned && (memberChanged
                    || this.count != this.crowd.count.get()
                    || this.liveLimit != this.desiredLiveLimit()
                    || this.spacing != this.crowd.spacing.get()
                    || this.perBlock != this.crowd.perBlock.get()
                    || this.health != this.crowd.health.get()
                    || this.randomArmor != this.crowd.randomArmor.get()
                    || this.randomArmorCoverage != this.crowd.randomArmorCoverage.get()
                    || this.randomArmorRichness != this.crowd.randomArmorRichness.get()
                    || this.randomArmorHead != this.crowd.randomArmorHead.get()
                    || this.randomArmorChest != this.crowd.randomArmorChest.get()
                    || this.randomArmorLegs != this.crowd.randomArmorLegs.get()
                    || this.randomArmorFeet != this.crowd.randomArmorFeet.get()
                    || this.randomArmorHeadMaterials != this.crowd.randomArmorHeadMaterials.get()
                    || this.randomArmorChestMaterials != this.crowd.randomArmorChestMaterials.get()
                    || this.randomArmorLegsMaterials != this.crowd.randomArmorLegsMaterials.get()
                    || this.randomArmorFeetMaterials != this.crowd.randomArmorFeetMaterials.get()
                    || this.randomTextures != this.crowd.randomTextures.get()
                    || !java.util.Objects.equals(this.randomTextureFolder, this.crowd.randomTextureFolder.get())
                    || this.recursiveTextures != this.crowd.recursiveTextures.get()
                    || this.textureRevision != this.crowd.textureRevision.get()
                    || this.formation != this.crowd.formation.get()
                    || this.hollow != this.crowd.hollow.get()
                    || this.radius != this.crowd.radius.get()
                    || this.seed != this.crowd.seed.get()
                    || this.variation != this.crowd.variation.get()
                    || this.useVolume != this.crowd.useVolume.get()
                    || this.useVolume && (this.volumeX != this.crowd.volume.get().scale.x
                        || this.volumeY != this.crowd.volume.get().scale.y
                        || this.volumeZ != this.crowd.volume.get().scale.z
                        || this.volumeOffsetX != this.crowd.volume.get().translate.x
                        || this.volumeOffsetY != this.crowd.volume.get().translate.y
                        || this.volumeOffsetZ != this.crowd.volume.get().translate.z));
        }

        private void applyLookTarget(int tick)
        {
            if (!this.spawned || this.entities.isEmpty())
            {
                return;
            }

            CrowdLookEvaluator.Sample sample = CrowdLookEvaluator.sample(CrowdFormRuntimeManager.this.film, this.replay, tick);

            if (sample == null)
            {
                if (!this.hadLookTarget)
                {
                    return;
                }

                for (LivingEntity entity : this.entities)
                {
                    if (entity instanceof ActorEntity actor)
                    {
                        actor.clearCrowdLook();
                    }
                }

                this.hadLookTarget = false;

                return;
            }

            this.hadLookTarget = true;

            for (int i = 0; i < this.entities.size(); i++)
            {
                LivingEntity entity = this.entities.get(i);

                if (entity == null || entity.isRemoved())
                {
                    continue;
                }

                if (!CrowdLookEvaluator.rotation(entity.getX(), entity.getEyeY(), entity.getZ(), sample, this.lookRotation))
                {
                    continue;
                }

                float headYaw = this.lookRotation[0];
                float bodyYaw = entity.getBodyYaw();
                CrowdLookTarget control = sample.control();
                float limit = Math.max(0F, this.crowd.behaviorHeadYawLimit.get());
                float delta = MathHelper.wrapDegrees(headYaw - bodyYaw);

                if (Math.abs(delta) > limit)
                {
                    bodyYaw += delta - Math.copySign(limit, delta);
                }

                if (entity instanceof ActorEntity actor)
                {
                    actor.setCrowdLook(bodyYaw, bodyYaw, headYaw, this.lookRotation[1],
                        control.yaw(), control.bodyYaw(), control.headYaw(), control.pitch());
                }
                else
                {
                    if (control.yaw()) entity.setYaw(bodyYaw);
                    if (control.bodyYaw()) entity.setBodyYaw(bodyYaw);
                    if (control.headYaw()) entity.setHeadYaw(headYaw);
                    if (control.pitch()) entity.setPitch(this.lookRotation[1]);
                }
            }
        }

        private void applyTextureKeyframe(int tick)
        {
            if (this.replay.keyframes.crowdTexture.isEmpty())
            {
                return;
            }

            CrowdTexture value = this.replay.keyframes.crowdTexture.interpolate(tick, new CrowdTexture());
            Link texture = value != null && !value.random ? value.texture : null;
            Link folder = value != null && value.random ? value.folder : null;
            boolean random = value != null && value.random;
            boolean recursive = value != null && value.recursive;

            if (java.util.Objects.equals(this.crowd.textureOverride.get(), texture)
                && java.util.Objects.equals(this.crowd.randomTextureFolder.get(), folder)
                && this.crowd.randomTextures.get() == random
                && this.crowd.recursiveTextures.get() == recursive)
            {
                return;
            }

            this.crowd.textureOverride.set(texture);
            this.crowd.randomTextureFolder.set(folder);
            this.crowd.textureFolder.set(folder);
            this.crowd.randomTextures.set(random);
            this.crowd.recursiveTextures.set(recursive);
            this.crowd.textureRevision.set(this.crowd.textureRevision.get() + 1);
        }

        private void stabilizeFormation(int tick)
        {
            if (!this.spawned || this.crowd.behaviorEnabled.get()
                || !this.replay.keyframes.crowdWalk.isEmpty()
                || Math.floorMod(tick + this.replay.getId().hashCode(), 10) != 0)
            {
                return;
            }

            int size = Math.min(this.entities.size(), this.basePositions.size());

            for (int i = 0; i < size; i++)
            {
                LivingEntity entity = this.entities.get(i);

                if (entity == null || entity.isRemoved() || entity.isDead() || entity.hurtTime > 0)
                {
                    continue;
                }

                Vec3d base = this.basePositions.get(i);
                double y = this.activeJumps > 0 ? Math.max(base.y, entity.getY()) : base.y;

                entity.setPos(base.x, y, base.z);
                entity.setVelocity(Vec3d.ZERO);
            }
        }

        /** One deterministic O(n) pass moves the full crowd; no mob AI or collision graph is involved. */
        private void applyMotionPath(int tick)
        {
            if (!this.spawned || this.entities.isEmpty())
            {
                return;
            }

            CrowdWalkEvaluator.Frame frame = CrowdWalkEvaluator.frame(this.replay, tick);

            if (frame == null)
            {
                if (this.pathApplied)
                {
                    this.restoreSpawnPositions();
                }

                this.pathApplied = false;
                return;
            }

            Vec3d origin = CrowdWalkEvaluator.replayOrigin(this.replay, tick);
			/* Reuse this per-runtime cache instead of allocating a thousand-entry map every
			 * tick for terrain-following crowds. Each frame needs fresh heights, but not a
			 * fresh backing table. */
			Map<Long, Double> terrain = null;

			if (frame.path().terrainFollow)
			{
				this.terrainHeights.clear();
				terrain = this.terrainHeights;
			}
            int size = Math.min(this.entities.size(), this.basePositions.size());
            boolean continuous = this.lastMotionTick == Integer.MIN_VALUE || tick == this.lastMotionTick + 1;

            if (!continuous)
            {
                Arrays.fill(this.lastNominalPositions, null);
                Arrays.fill(this.steeringDirections, null);
            }

            for (int i = 0; i < size; i++)
            {
                LivingEntity entity = this.entities.get(i);

                if (entity == null || entity.isRemoved())
                {
                    continue;
                }

                if (entity.hurtTime > 0 || entity.isDead())
                {
                    this.basePositions.set(i, entity.getPos());
                    continue;
                }

                Vec3d startLocal = i < this.spawnPositions.size() ? this.spawnPositions.get(i).subtract(this.spawnOrigin) : null;

                if (startLocal != null && frame.path().terrainFollow)
                {
                    startLocal = new Vec3d(startLocal.x, 0D, startLocal.z);
                }
                Vec3d local = CrowdWalkEvaluator.member(frame, i, startLocal);
                double x = origin.x + local.x;
                double y = origin.y + local.y;
                double z = origin.z + local.z;

                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                {
                    continue;
                }

                if (terrain != null)
                {
                    int bx = MathHelper.floor(x);
                    int bz = MathHelper.floor(z);
                    long key = (((long) bx) << 32) ^ (bz & 0xffffffffL);
                    double fallbackGround = y - local.y;
                    double ground = terrain.computeIfAbsent(key,
                        ignored -> this.loadedSurfaceY(bx, bz, fallbackGround));

                    y = ground + local.y;
                }

                Vec3d nominal = new Vec3d(x, y, z);
                Vec3d current = entity.getPos();
                boolean exactKeyframe = Math.abs(tick - frame.startTick()) < 0.0001F
                    || Math.abs(tick - frame.targetTick()) < 0.0001F;
                Vec3d position = exactKeyframe
                    ? nominal
                    : this.collisionSafe(entity, nominal, i, frame.path().terrainFollow);
                Vec3d actualMovement = position.subtract(current);

                if (i < this.lastNominalPositions.length)
                {
                    this.lastNominalPositions[i] = nominal;
                }
                this.basePositions.set(i, position);
                entity.setPos(position.x, position.y, position.z);
                entity.setVelocity(Vec3d.ZERO);
                entity.setJumping(false);
                entity.setSprinting(frame.moving() && frame.path().run);

                if (frame.path().faceTravel && actualMovement.horizontalLengthSquared() > 1.0E-8D)
                {
                    float targetYaw = (float) (Math.atan2(actualMovement.z, actualMovement.x) * 180D / Math.PI - 90D);
                    float yaw = MathHelper.lerpAngleDegrees(0.28F, entity.getYaw(), targetYaw);

                    entity.setYaw(yaw);
                    entity.setHeadYaw(yaw);
                    entity.setBodyYaw(yaw);
                    entity.setPitch(0F);
                    entity.prevYaw = yaw;
                    entity.prevHeadYaw = yaw;
                    entity.prevBodyYaw = yaw;
                    entity.prevPitch = 0F;
                }
            }

            this.pathApplied = true;
            this.lastMotionTick = tick;
        }

        /**
         * Check the swept actor volume against blocks before committing a path step. Raising first
         * allows ordinary one-block uphill travel; walls still stop the member instead of letting a
         * high speed setting teleport it through solid geometry. Actor-to-actor collision remains
         * disabled deliberatelyÃ¢â‚¬â€the stable formation mapping prevents crossing without an O(nÃ‚Â²) mob AI.
         */
        private Vec3d collisionSafe(LivingEntity entity, Vec3d desired, int index, boolean terrainFollow)
        {
            Vec3d current = entity.getPos();
            Vec3d delta = desired.subtract(current);

            if (delta.lengthSquared() < 1.0E-10D)
            {
                return desired;
            }

            /* Terrain following already resolves an exact grounded destination. Accept a clear
             * destination directly so an uphill diagonal never gets mistaken for a jump arc. */
            if (terrainFollow && CrowdFormRuntimeManager.this.world.isSpaceEmpty(entity,
                entity.getBoundingBox().offset(delta).expand(-0.02D)))
            {
                this.clearObstacleState(index);
                return desired;
            }

            var swept = entity.getBoundingBox().stretch(delta).expand(-0.02D);

            if (CrowdFormRuntimeManager.this.world.isSpaceEmpty(entity, swept))
            {
                if (index < this.bypassSides.length && this.bypassSides[index] != 0
                    && index < this.steeringDirections.length && this.steeringDirections[index] != null)
                {
                    Vec3d direct = delta.normalize();
                    Vec3d old = this.steeringDirections[index];
                    Vec3d blended = old.multiply(0.72D).add(direct.multiply(0.28D));

                    if (blended.lengthSquared() > 1.0E-10D)
                    {
                        blended = blended.normalize();
                        Vec3d smoothStep = blended.multiply(delta.length());

                        if (CrowdFormRuntimeManager.this.world.isSpaceEmpty(entity,
                            entity.getBoundingBox().stretch(smoothStep).expand(-0.02D)))
                        {
                            this.steeringDirections[index] = blended;

                            if (blended.dotProduct(direct) > 0.995D)
                            {
                                this.clearObstacleState(index);
                            }

                            return current.add(smoothStep);
                        }
                    }
                }

                this.clearObstacleState(index);
                return desired;
            }

            if (index < this.blockedTicks.length)
            {
                this.blockedTicks[index]++;

                if (Double.isNaN(this.obstacleBaseY[index]))
                {
                    this.obstacleBaseY[index] = current.y;
                }
            }

            /* On first contact with a tall obstacle, consume only the collision-free part of
             * this tick's forward budget. That creates a natural approach and stop at the wall
             * instead of instantly throwing the actor sideways when a long segment moves many
             * blocks per tick. Steering begins on the following tick. */
            if (index < this.blockedTicks.length && this.blockedTicks[index] == 1)
            {
                for (double fraction : new double[] {0.8D, 0.6D, 0.4D, 0.2D, 0.1D})
                {
                    Vec3d partial = delta.multiply(fraction);

                    if (CrowdFormRuntimeManager.this.world.isSpaceEmpty(entity,
                        entity.getBoundingBox().stretch(partial).expand(-0.02D)))
                    {
                        Vec3d horizontalPartial = new Vec3d(partial.x, 0D, partial.z);

                        if (index < this.steeringDirections.length && horizontalPartial.lengthSquared() > 1.0E-10D)
                        {
                            this.steeringDirections[index] = horizontalPartial.normalize();
                        }

                        return current.add(partial);
                    }
                }

                return current;
            }

            /* Tall obstacles get stable local wall-following. Each member keeps its chosen side
             * until the direct route clears, preventing left/right oscillation and dense piles. */
            Vec3d horizontal = new Vec3d(delta.x, 0D, delta.z);

            if (horizontal.lengthSquared() > 1.0E-8D)
            {
                horizontal = horizontal.normalize();
                int sideSign = index < this.bypassSides.length ? this.bypassSides[index] : 0;

                if (sideSign == 0)
                {
                    int preferred = (index & 1) == 0 ? 1 : -1;
                    sideSign = this.canBypass(entity, horizontal, preferred) ? preferred : -preferred;

                    if (index < this.bypassSides.length)
                    {
                        this.bypassSides[index] = sideSign;
                    }
                }

                Vec3d side = new Vec3d(-horizontal.z * sideSign, 0D, horizontal.x * sideSign);
                Vec3d targetDirection = side.multiply(0.94D).add(horizontal.multiply(0.06D)).normalize();
                Vec3d oldDirection = index < this.steeringDirections.length ? this.steeringDirections[index] : null;

                if (oldDirection == null || oldDirection.lengthSquared() < 1.0E-10D)
                {
                    oldDirection = horizontal;
                }

                double stepLength = Math.max(0.06D, delta.horizontalLength());

                /* Increase the turn gradually until a collision-free tangent is found. This
                 * preserves visual momentum while never correcting backward or teleporting. */
                for (int attempt = 0; attempt < 5; attempt++)
                {
                    double blend = Math.min(1D, 0.24D + attempt * 0.19D);
                    Vec3d direction = oldDirection.multiply(1D - blend).add(targetDirection.multiply(blend));

                    if (direction.lengthSquared() < 1.0E-10D)
                    {
                        continue;
                    }

                    direction = direction.normalize();

                    for (double scale : new double[] {1D, 0.66D, 0.33D})
                    {
                        Vec3d bypass = direction.multiply(stepLength * scale);

                        if (CrowdFormRuntimeManager.this.world.isSpaceEmpty(entity,
                            entity.getBoundingBox().stretch(bypass).expand(-0.02D)))
                        {
                            if (index < this.bypassTicks.length) this.bypassTicks[index]++;
                            if (index < this.steeringDirections.length) this.steeringDirections[index] = direction;
                            entity.setJumping(false);
                            return current.add(bypass);
                        }
                    }
                }
            }

            entity.setJumping(false);
            return current;
        }

        private boolean canBypass(LivingEntity entity, Vec3d forward, int sideSign)
        {
            Vec3d side = new Vec3d(-forward.z * sideSign, 0D, forward.x * sideSign).multiply(0.42D);

            return CrowdFormRuntimeManager.this.world.isSpaceEmpty(entity,
                entity.getBoundingBox().offset(side).expand(-0.02D));
        }

        private void clearObstacleState(int index)
        {
            if (index < this.blockedTicks.length) this.blockedTicks[index] = 0;
            if (index < this.bypassSides.length) this.bypassSides[index] = 0;
            if (index < this.bypassTicks.length) this.bypassTicks[index] = 0;
            if (index < this.obstacleBaseY.length) this.obstacleBaseY[index] = Double.NaN;
            if (index < this.steeringDirections.length) this.steeringDirections[index] = null;
        }

        private void applyEquipment(int tick)
        {
            if (!this.spawned || this.entities.isEmpty())
            {
                return;
            }

            this.applyEquipmentSlotIfKeyframed(EquipmentSlot.MAINHAND, this.replay.keyframes.mainHand, tick);
            this.applyEquipmentSlotIfKeyframed(EquipmentSlot.OFFHAND, this.replay.keyframes.offHand, tick);
            this.applyEquipmentSlotIfKeyframed(EquipmentSlot.HEAD, this.replay.keyframes.armorHead, tick);
            this.applyEquipmentSlotIfKeyframed(EquipmentSlot.CHEST, this.replay.keyframes.armorChest, tick);
            this.applyEquipmentSlotIfKeyframed(EquipmentSlot.LEGS, this.replay.keyframes.armorLegs, tick);
            this.applyEquipmentSlotIfKeyframed(EquipmentSlot.FEET, this.replay.keyframes.armorFeet, tick);
        }

        private void applyBehavior(int tick, SuperFakePlayer player)
        {
            if (!this.spawned || !this.crowd.behaviorEnabled.get()
                || !this.replay.keyframes.crowdWalk.isEmpty())
            {
                return;
            }

            this.behavior.crowdTag.set(this.tag());
            this.behavior.seed.set(this.crowd.seed.get());
            this.behavior.target.set(this.crowd.behaviorTarget.get());
            this.behavior.mode.set(this.crowd.behaviorMode.get());
            this.behavior.pause.set(this.crowd.behaviorPause.get());
            this.behavior.speed.set(this.crowd.behaviorSpeed.get());
            this.behavior.sprint.set(this.crowd.behaviorSprint.get());
            this.behavior.moveEase.set(this.crowd.behaviorMoveEase.get());
            this.behavior.stopDistance.set(this.crowd.behaviorStopDistance.get());
            this.behavior.targetSpread.set(this.crowd.behaviorTargetSpread.get());
            this.behavior.disperseRadius.set(this.crowd.behaviorDisperseRadius.get());
            this.behavior.wanderInterval.set(this.crowd.behaviorWanderInterval.get());
            this.behavior.lookAroundTicks.set(this.crowd.behaviorLookAroundTicks.get());
            this.behavior.areaX.set(this.crowd.behaviorAreaX.get());
            this.behavior.areaY.set(this.crowd.behaviorAreaY.get());
            this.behavior.areaZ.set(this.crowd.behaviorAreaZ.get());
            this.behavior.separation.set(this.crowd.behaviorSeparation.get());
            this.behavior.maxStepHeight.set(this.crowd.behaviorMaxStepHeight.get());
            this.behavior.crouch.set(this.crowd.behaviorCrouch.get());
            this.behavior.zigZag.set(this.crowd.behaviorZigZag.get());
            /* Crowd actors have gravity disabled. The deterministic jump pass below owns
             * cheering arcs, while the behavior clip continues to own horizontal motion. */
            this.behavior.randomJump.set(false);
            this.behavior.jumpRate.set(this.crowd.behaviorJumpRate.get());
            this.behavior.armSwing.set(this.crowd.behaviorArmSwing.get());
            this.behavior.armSwingRate.set(this.crowd.behaviorArmSwingRate.get());
            this.behavior.headMotion.set(this.crowd.behaviorHeadMotion.get());
            this.behavior.energy.set(this.crowd.behaviorEnergy.get());
            this.behavior.lookAtTarget.set(this.crowd.behaviorLookAtTarget.get());
            this.behavior.lookEase.set(this.crowd.behaviorLookEase.get());
            this.behavior.headYawLimit.set(this.crowd.behaviorHeadYawLimit.get());
            this.behavior.lookBodyYaw.set(this.crowd.behaviorLookBodyYaw.get());
            this.behavior.lookHeadYaw.set(this.crowd.behaviorLookHeadYaw.get());
            this.behavior.lookHeadPitch.set(this.crowd.behaviorLookHeadPitch.get());
            this.behavior.enemyGroup.set(this.crowd.behaviorEnemyGroup.get());
            this.behavior.fightDamage.set(this.crowd.behaviorFightDamage.get());
            this.behavior.attackRate.set(this.crowd.behaviorAttackRate.get());
            this.behavior.engagementDistance.set(this.crowd.behaviorEngagementDistance.get());
            this.behavior.fightRadius.set(this.crowd.behaviorFightRadius.get());
            this.behavior.retargetTicks.set(this.crowd.behaviorRetargetTicks.get());
            this.behavior.fightRandomness.set(this.crowd.behaviorFightRandomness.get());
            this.behavior.shoot.set(this.crowd.behaviorShoot.get());
            this.behavior.shootRate.set(this.crowd.behaviorShootRate.get());
            this.behavior.projectileModel.set(this.crowd.behaviorProjectileModel.get());
            this.behavior.projectileSpeed.set(this.crowd.behaviorProjectileSpeed.get());
            this.behavior.projectileLifeSpan.set(this.crowd.behaviorProjectileLifeSpan.get());
            this.behavior.impactModel.set(this.crowd.behaviorImpactModel.get());
            this.behavior.impactBounces.set(this.crowd.behaviorImpactBounces.get());
            this.behavior.impactBounceDamping.set(this.crowd.behaviorImpactBounceDamping.get());
            this.behavior.impactVanish.set(this.crowd.behaviorImpactVanish.get());
            this.behavior.impactDamage.set(this.crowd.behaviorImpactDamage.get());
            this.behavior.impactKnockback.set(this.crowd.behaviorImpactKnockback.get());
            this.behavior.impactCollideBlocks.set(this.crowd.behaviorImpactCollideBlocks.get());
            this.behavior.impactCollideEntities.set(this.crowd.behaviorImpactCollideEntities.get());
            this.behavior.range.set(512F);
            this.behavior.applyAction(null, player, CrowdFormRuntimeManager.this.film, this.replay, tick);
        }

        private void applyEquipmentSlotIfKeyframed(EquipmentSlot slot, KeyframeChannel<ItemStack> channel, int tick)
        {
            if (channel == null || channel.isEmpty())
            {
                return;
            }

            this.applyEquipmentSlot(slot, channel.interpolate(tick, ItemStack.EMPTY));
        }

        private void applyEquipmentSlot(EquipmentSlot slot, ItemStack stack)
        {
            ItemStack safe = stack == null ? ItemStack.EMPTY : stack;
			int index = slot.ordinal();

			/* Six slot comparisons for every actor every tick becomes expensive at 1,000+
			 * members. Equipment tracks change rarely, so fan a changed stack out once and
			 * skip the entire crowd on the common unchanged frame. */
			if (this.appliedEquipment[index] != null && ItemStack.areEqual(this.appliedEquipment[index], safe))
			{
				return;
			}

			this.appliedEquipment[index] = safe.copy();

            for (LivingEntity entity : this.entities)
            {
                if (entity != null && !entity.isRemoved() && !ItemStack.areEqual(entity.getEquippedStack(slot), safe))
                {
                    entity.equipStack(slot, safe.copy());
                }
            }
        }

        private void restoreSpawnPositions()
        {
            int size = Math.min(this.entities.size(), Math.min(this.basePositions.size(), this.spawnPositions.size()));

            for (int i = 0; i < size; i++)
            {
                LivingEntity entity = this.entities.get(i);
                Vec3d position = this.spawnPositions.get(i);

                this.basePositions.set(i, position);

                if (entity != null && !entity.isRemoved())
                {
                    entity.setPos(position.x, position.y, position.z);
                    entity.setVelocity(Vec3d.ZERO);
                    entity.setSprinting(false);
                }
            }
        }

        /**
         * Lightweight crowd jumping. It moves the existing actors through a short arc rather than
         * enabling gravity or mob AI, which keeps large crowds predictable and inexpensive.
         */
        private void applyJump(int tick)
        {
            if (!this.spawned || this.entities.isEmpty())
            {
                return;
            }

            double ambientRate = this.crowd.behaviorRandomJump.get()
                ? this.crowd.behaviorJumpRate.get()
                : 0D;
            CrowdJumpEvaluator.Frame jumps = CrowdJumpEvaluator.frame(this.replay, tick, ambientRate);

			/* No active jump and no jump cue: avoid walking the complete crowd merely to
			 * decide nothing should happen. */
			if ((jumps == null || !jumps.hasPotential()) && this.activeJumps == 0)
			{
				return;
			}

            int size = Math.min(this.entities.size(), Math.min(this.jumpGroundY.length, this.jumpHeights.length));
            int active = 0;

            for (int i = 0; i < size; i++)
            {
                LivingEntity entity = this.entities.get(i);

                if (entity == null || entity.isRemoved())
                {
                    continue;
                }

                if (entity.hurtTime > 0 || entity.isDead())
                {
                    continue;
                }

                double height = jumps == null ? 0D : jumps.height(i);
                double previousHeight = this.jumpHeights[i];

                if (height > 0.000001D)
                {
                    if (previousHeight <= 0.000001D || Double.isNaN(this.jumpGroundY[i]))
                    {
                        this.jumpGroundY[i] = entity.getY();
                    }

                    double groundY = this.jumpGroundY[i];
                    double y = Math.min(groundY + height, this.volumeTopY);

                    entity.setPos(entity.getX(), Math.max(groundY, y), entity.getZ());
                    entity.setVelocity(Vec3d.ZERO);
                    entity.setJumping(true);
                    active++;
                }
                else if (previousHeight > 0.000001D && !Double.isNaN(this.jumpGroundY[i]))
                {
                    entity.setPos(entity.getX(), this.jumpGroundY[i], entity.getZ());
                    entity.setVelocity(Vec3d.ZERO);
                    entity.setJumping(false);
                    this.jumpGroundY[i] = Double.NaN;
                }

                this.jumpHeights[i] = height;
            }

            this.activeJumps = active;
        }

        private CrowdRagdollActionClip getRagdoll(int tick)
        {
            CrowdRagdollActionClip selected = null;

            for (Clip clip : this.replay.actions.getClips(tick))
            {
                if (clip instanceof CrowdRagdollActionClip ragdoll && ragdoll.enabled.get())
                {
                    selected = ragdoll;
                }
            }

            return selected;
        }

        private void applyRagdoll(int tick, CrowdRagdollActionClip ragdoll)
        {
            float progress = ragdoll == null ? 0F : ragdoll.progress(tick);

            if (progress <= 0F && !this.ragdollActive)
            {
                return;
            }

            this.ragdollActive = progress > 0F;

            for (LivingEntity entity : this.entities)
            {
                if (!(entity instanceof ActorEntity actor) || entity.isRemoved())
                {
                    continue;
                }

                if (progress <= 0F)
                {
                    actor.setCrowdRagdoll(0F, 0F);

                    continue;
                }

                float direction = ragdoll.direction.get();

                if (ragdoll.randomDirection.get())
                {
                    int hash = entity.getUuid().hashCode();
                    direction = ((hash & 0xffff) / 65535F) * 360F - 180F;
                }

                actor.clearCrowdLook();
                actor.setVelocity(Vec3d.ZERO);
                actor.setSprinting(false);
                actor.setSneaking(false);
                actor.setCrowdRagdoll(ragdoll.tilt.get() * progress, direction);
            }
        }

        /** Never let crowd terrain-following synchronously generate a chunk on the server thread. */
        private double loadedSurfaceY(int x, int z, double fallback)
        {
            BlockPos probe = new BlockPos(x, MathHelper.floor(fallback), z);

            if (!CrowdFormRuntimeManager.this.world.isChunkLoaded(probe))
            {
                return fallback;
            }

            return CrowdFormRuntimeManager.this.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        }

        private void remove()
        {
            if (this.spawned)
            {
                for (LivingEntity entity : this.entities)
                {
                    if (entity != null && !entity.isRemoved())
                    {
                        entity.discard();
                    }
                }

                this.entities = List.of();
                this.spawnPositions = List.of();
                this.basePositions = List.of();
                this.spawnOrigin = Vec3d.ZERO;
                this.blockedTicks = new int[0];
                this.bypassSides = new int[0];
                this.bypassTicks = new int[0];
                this.obstacleBaseY = new double[0];
                this.jumpGroundY = new double[0];
                this.jumpHeights = new double[0];
                this.lastNominalPositions = new Vec3d[0];
                this.steeringDirections = new Vec3d[0];
                this.lastMotionTick = Integer.MIN_VALUE;
				this.activeJumps = 0;
				this.terrainHeights.clear();
				Arrays.fill(this.appliedEquipment, null);
                this.spawned = false;
                this.pathApplied = false;
                this.hadLookTarget = false;
                this.ragdollActive = false;
            }

            /* References can be lost after reloads or interrupted live edits. The persistent
             * film/run/crowd tags are the authority, so a count reduction always removes every
             * old member before the replacement crowd is spawned. */
            CrowdUtils.removeCrowd(CrowdFormRuntimeManager.this.world, CrowdFormRuntimeManager.this.film, this.tag());
            this.member = null;
        }
    }
}
