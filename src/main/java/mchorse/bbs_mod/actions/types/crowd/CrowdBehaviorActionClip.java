package mchorse.bbs_mod.actions.types.crowd;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class CrowdBehaviorActionClip extends ActionClip
{
    /** {@code target} value meaning "use this clip's own replay" (default). */
    public static final int TARGET_SELF = -1;
    /**
     * {@code target} value meaning "no replay target" Ã¢â‚¬â€ the crowd ignores every
     * replay and anchors to each mob's spawn point instead, so Wander mopes /
     * wanders around where they spawned rather than walking to a replay.
     */
    public static final int TARGET_NONE = -2;

    public final ValueString crowdTag = new ValueString("crowd_tag", "crowd_1");
    public final ValueInt target = new ValueInt("target", TARGET_SELF);
    public final ValueInt mode = new ValueInt("mode", CrowdBehaviorMode.FOLLOW.ordinal(), 0, CrowdBehaviorMode.values().length - 1);
    public final ValueBoolean pause = new ValueBoolean("pause", false);
    public final ValueBoolean sprint = new ValueBoolean("sprint", false);
    public final ValueFloat speed = new ValueFloat("speed", 1F, 0F, 8F);
    public final ValueInt moveEase = new ValueInt("move_ease", 10, 0, 200);
    public final ValueFloat stopDistance = new ValueFloat("stop_distance", 1.5F, 0F, 32F);
    public final ValueFloat targetSpread = new ValueFloat("target_spread", 2F, 0F, 64F);
    public final ValueFloat disperseRadius = new ValueFloat("disperse_radius", 12F, 0F, 128F);
    public final ValueInt wanderInterval = new ValueInt("wander_interval", 80, 20, 400);
    public final ValueInt lookAroundTicks = new ValueInt("look_around_ticks", 25, 0, 200);
    public final ValueFloat range = new ValueFloat("range", 128F, 8F, 512F);
    public final ValueInt pathRefresh = new ValueInt("path_refresh", 5, 1, 40);
    public final ValueBoolean crouch = new ValueBoolean("crouch", false);
    public final ValueBoolean zigZag = new ValueBoolean("zig_zag", false);
    public final ValueBoolean randomJump = new ValueBoolean("random_jump", false);
    public final ValueFloat jumpRate = new ValueFloat("jump_rate", 0.25F, 0F, 10F);
    public final ValueBoolean armSwing = new ValueBoolean("arm_swing", false);
    public final ValueFloat armSwingRate = new ValueFloat("arm_swing_rate", 1F, 0F, 20F);
    public final ValueBoolean headMotion = new ValueBoolean("head_motion", true);
    public final ValueFloat energy = new ValueFloat("energy", 1F, 0F, 2F);
    public final ValueBoolean lookAtTarget = new ValueBoolean("look_at_target", true);
    public final ValueInt lookEase = new ValueInt("look_ease", 10, 0, 200);
    public final ValueFloat headYawLimit = new ValueFloat("head_yaw_limit", 70F, 0F, 120F);
    public final ValueBoolean lookBodyYaw = new ValueBoolean("look_body_yaw", false);
    public final ValueBoolean lookHeadYaw = new ValueBoolean("look_head_yaw", true);
    public final ValueBoolean lookHeadPitch = new ValueBoolean("look_head_pitch", true);
    public final ValueBoolean shoot = new ValueBoolean("shoot", false);
    public final ValueFloat shootRate = new ValueFloat("shoot_rate", 0.5F, 0F, 20F);
    public final ValueItemStack shootItem = new ValueItemStack("shoot_item");
    public final ValueForm projectileModel = new ValueForm("projectile_model");
    public final ValueFloat projectileSpeed = new ValueFloat("projectile_speed", 1.6F, 0F, 16F);
    public final ValueInt projectileLifeSpan = new ValueInt("projectile_life_span", 100, 1, 1200);
    public final ValueForm impactModel = new ValueForm("impact_model");
    public final ValueInt impactBounces = new ValueInt("impact_bounces", 0, 0, 64);
    public final ValueFloat impactBounceDamping = new ValueFloat("impact_bounce_damping", 0.5F, 0F, 1F);
    public final ValueBoolean impactVanish = new ValueBoolean("impact_vanish", true);
    public final ValueFloat impactDamage = new ValueFloat("impact_damage", 0F, 0F, 1024F);
    public final ValueFloat impactKnockback = new ValueFloat("impact_knockback", 0F, 0F, 64F);
    public final ValueBoolean impactCollideBlocks = new ValueBoolean("impact_collide_blocks", true);
    public final ValueBoolean impactCollideEntities = new ValueBoolean("impact_collide_entities", true);
    public final ValueString cmdFiring = new ValueString("cmd_firing", "");
    public final ValueString cmdImpact = new ValueString("cmd_impact", "");
    public final ValueString cmdVanish = new ValueString("cmd_vanish", "");
    public final ValueString cmdTicking = new ValueString("cmd_ticking", "");
    public final ValueInt ticking = new ValueInt("ticking", 0, 0, 1200);

    public CrowdBehaviorActionClip()
    {
        super();

        this.shootItem.set(new ItemStack(Items.SNOWBALL));

        this.add(this.crowdTag);
        this.add(this.target);
        this.add(this.mode);
        this.add(this.pause);
        this.add(this.sprint);
        this.add(this.speed);
        this.add(this.moveEase);
        this.add(this.stopDistance);
        this.add(this.targetSpread);
        this.add(this.disperseRadius);
        this.add(this.wanderInterval);
        this.add(this.lookAroundTicks);
        this.add(this.range);
        this.add(this.pathRefresh);
        this.add(this.crouch);
        this.add(this.zigZag);
        this.add(this.randomJump);
        this.add(this.jumpRate);
        this.add(this.armSwing);
        this.add(this.armSwingRate);
        this.add(this.headMotion);
        this.add(this.energy);
        this.add(this.lookAtTarget);
        this.add(this.lookEase);
        this.add(this.headYawLimit);
        this.add(this.lookBodyYaw);
        this.add(this.lookHeadYaw);
        this.add(this.lookHeadPitch);
        this.add(this.shoot);
        this.add(this.shootRate);
        this.add(this.shootItem);
        this.add(this.projectileModel);
        this.add(this.projectileSpeed);
        this.add(this.projectileLifeSpan);
        this.add(this.impactModel);
        this.add(this.impactBounces);
        this.add(this.impactBounceDamping);
        this.add(this.impactVanish);
        this.add(this.impactDamage);
        this.add(this.impactKnockback);
        this.add(this.impactCollideBlocks);
        this.add(this.impactCollideEntities);
        this.add(this.cmdFiring);
        this.add(this.cmdImpact);
        this.add(this.cmdVanish);
        this.add(this.cmdTicking);
        this.add(this.ticking);

        this.frequency.set(1);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        if (player == null || !CrowdUtils.isServerLevel(player.getWorld()) || film == null)
        {
            return;
        }

        boolean noTarget = this.target.get() == TARGET_NONE;
        Vec3d gatherPos;

        if (noTarget)
        {
            /* No replay target: anchor each mob to its own spawn point. We only
             * need a rough centre to find the tagged crowd, so use the clip's
             * own replay position for that gather query. */
            if (replay == null)
            {
                return;
            }

            gatherPos = CrowdUtils.replayPosition(replay, tick);
        }
        else
        {
            Replay targetReplay = CrowdUtils.getReplay(film, this.target.get());

            if (targetReplay == null)
            {
                targetReplay = replay;
            }

            if (targetReplay == null)
            {
                return;
            }

            gatherPos = CrowdUtils.replayPosition(targetReplay, tick);
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        CrowdBehaviorMode mode = CrowdBehaviorMode.get(this.mode.get());
        List<LivingEntity> crowd = CrowdUtils.getCrowd(world, film, this.crowdTag.get(), gatherPos, this.range.get());

        if (crowd.isEmpty())
        {
            return;
        }

        double stopDistance = Math.max(0D, this.stopDistance.get());
        double stopDistanceSq = stopDistance * stopDistance;
        double speed = Math.max(0D, this.speed.get()) * this.getMoveBlend(tick);
        int refresh = Math.max(1, this.pathRefresh.get());
        double jumpChancePerTick = this.randomJump.get() ? Math.min(1D, Math.max(0D, this.jumpRate.get()) / 20D) : 0D;
        float shootRate = this.shoot.get() ? Math.max(0F, this.shootRate.get()) : 0F;
        boolean paused = this.pause.get() || speed <= 0.0001D;
        GunProperties projectileProperties = shootRate > 0F ? this.createProjectileProperties() : null;
        float lookBlend = this.getLookBlend(tick);

        for (LivingEntity entity : crowd)
        {
            if (!entity.isAlive() || entity.isRemoved())
            {
                continue;
            }

            boolean lookingAround = this.isLookAroundPause(entity, mode, tick);
            Vec3d destination = this.getDestination(entity, gatherPos, mode, tick, noTarget);
            double distanceSq = entity.squaredDistanceTo(destination);
            boolean holding = mode == CrowdBehaviorMode.HOLD;
            boolean moving = !holding && !lookingAround && !paused && distanceSq > stopDistanceSq;
            boolean sprinting = this.sprint.get() && moving;

            entity.setSprinting(sprinting);
            entity.setSneaking(this.crouch.get() || mode == CrowdBehaviorMode.SAD_WALK);
            this.moveEntity(world, entity, destination, speed, moving, refresh, tick);

            Vec3d lookPoint = this.getLookPoint(entity, gatherPos, mode, tick, noTarget);

            this.applyLook(entity, lookPoint, mode, tick, lookBlend);
            this.applyPerformanceMotion(entity, mode, tick);
            this.constrainHeadYaw(entity);

            double jumpChance = mode == CrowdBehaviorMode.CHEER
                ? Math.max(jumpChancePerTick, 0.045D * Math.max(0.2D, this.energy.get()))
                : jumpChancePerTick;

            if (mode == CrowdBehaviorMode.SAD_WALK)
            {
                jumpChance = 0D;
            }

            if (jumpChance > 0D && entity.isOnGround() && !entity.isTouchingWater() && world.getRandom().nextDouble() < jumpChance)
            {
                this.jump(entity);
            }

            float swingRate = (this.armSwing.get() || mode == CrowdBehaviorMode.CHEER) ? this.armSwingRate.get() : 0F;

            if (mode == CrowdBehaviorMode.CHEER)
            {
                swingRate = Math.max(swingRate, 1.25F * Math.max(0.25F, this.energy.get()));
            }

            if (swingRate > 0F && shouldPulse(entity, tick, swingRate))
            {
                entity.swingHand(Hand.MAIN_HAND);
            }

            if (sprinting && moving)
            {
                this.spawnSprintParticles(world, entity, tick);
            }

            if (!noTarget && projectileProperties != null && shouldShoot(entity, tick, shootRate))
            {
                this.shootSnowballLike(world, entity, gatherPos, projectileProperties);
            }
        }
    }

    private float getLookBlend(int tick)
    {
        return this.getEaseBlend(tick, this.lookEase.get());
    }

    private float getMoveBlend(int tick)
    {
        return this.getEaseBlend(tick, this.moveEase.get());
    }

    private float getEaseBlend(int tick, int ease)
    {
        if (ease <= 0)
        {
            return 1F;
        }

        float t = Math.min(1F, Math.max(0F, (tick - this.tick.get()) / (float) ease));

        return t * t * (3F - 2F * t);
    }

    private boolean isLookAroundPause(LivingEntity entity, CrowdBehaviorMode mode, int tick)
    {
        if (mode != CrowdBehaviorMode.WANDER_LOOK)
        {
            return false;
        }

        int cycle = Math.max(20, this.wanderInterval.get());
        int pause = Math.min(cycle - 1, Math.max(0, this.lookAroundTicks.get()));

        if (pause <= 0)
        {
            return false;
        }

        int offset = Math.floorMod(entity.getUuid().hashCode(), cycle);
        int phase = Math.floorMod(tick - this.tick.get() + offset, cycle);

        return phase < pause;
    }

    private Vec3d getDestination(LivingEntity entity, Vec3d targetPos, CrowdBehaviorMode mode, int tick, boolean noTarget)
    {
        /* No replay target: every mode roams freely (each mob picks its own
         * drifting direction) instead of walking towards a replay. HOLD is
         * already kept stationary by the caller. The Wander+look idle pause
         * still stops them so they can glance around. */
        if (noTarget)
        {
            if (mode == CrowdBehaviorMode.WANDER_LOOK && this.isLookAroundPause(entity, mode, tick))
            {
                return entity.getPos();
            }

            return this.getFreeWanderDestination(entity, tick);
        }

        Vec3d personal = CrowdUtils.personalOffset(entity, this.targetSpread.get());

        if (mode == CrowdBehaviorMode.WANDER || mode == CrowdBehaviorMode.WANDER_LOOK)
        {
            if (this.isLookAroundPause(entity, mode, tick))
            {
                return entity.getPos();
            }

            return this.getWanderDestination(entity, targetPos, tick);
        }

        if (mode == CrowdBehaviorMode.DISPERSE)
        {
            Vec3d direction = this.horizontalDirection(personal, entity);
            double radius = Math.max(this.targetSpread.get(), this.disperseRadius.get());

            return targetPos.add(direction.x * radius, 0D, direction.z * radius);
        }

        if (mode == CrowdBehaviorMode.SAD_WALK || this.zigZag.get())
        {
            Vec3d direction = this.horizontalDirection(personal, entity);
            Vec3d side = new Vec3d(-direction.z, 0D, direction.x);
            double wave = Math.sin((tick + entity.getId() * 13) * 0.11D) * Math.min(2.5D, Math.max(0D, this.targetSpread.get()));

            personal = personal.add(side.multiply(wave));
        }

        return targetPos.add(personal);
    }

    /**
     * Free, un-anchored wandering used by the "None" target. Each wander cycle
     * the mob picks a fresh random heading and aims a few blocks ahead of its
     * CURRENT position, so the destination always stays reachable and ahead Ã¢â‚¬â€
     * the mob keeps strolling and simply turns every cycle, instead of fighting
     * to reach a fixed (often blocked) point and shuffling in place.
     */
    private Vec3d getFreeWanderDestination(LivingEntity entity, int tick)
    {
        int cycle = Math.max(20, this.wanderInterval.get());
        int relative = Math.max(0, tick - this.tick.get());
        int seed = entity.getUuid().hashCode();
        int step = Math.floorDiv(relative + Math.floorMod(seed, cycle), cycle);
        double angle = randomUnit(seed, step, 0x3f91) * Math.PI * 2D;
        double radius = Math.max(2D, this.disperseRadius.get());
        double distance = Math.max(3D, radius * (0.55D + randomUnit(seed, step, 0x71ab) * 0.6D));
        Vec3d pos = entity.getPos();

        return pos.add(Math.cos(angle) * distance, 0D, Math.sin(angle) * distance);
    }

    private Vec3d getWanderDestination(LivingEntity entity, Vec3d center, int tick)
    {
        int cycle = Math.max(20, this.wanderInterval.get());
        int relative = Math.max(0, tick - this.tick.get());
        int seed = entity.getUuid().hashCode();
        int step = Math.floorDiv(relative + Math.floorMod(seed, cycle), cycle);
        double radius = Math.max(1D, this.disperseRadius.get());
        double angle = randomUnit(seed, step, 0x3f91) * Math.PI * 2D;
        double distance = radius * (0.35D + randomUnit(seed, step, 0x71ab) * 0.65D);

        return center.add(Math.cos(angle) * distance, 0D, Math.sin(angle) * distance);
    }

    private Vec3d getLookPoint(LivingEntity entity, Vec3d targetPos, CrowdBehaviorMode mode, int tick, boolean noTarget)
    {
        if (mode == CrowdBehaviorMode.WANDER_LOOK && this.isLookAroundPause(entity, mode, tick))
        {
            float limit = Math.max(20F, Math.min(120F, this.headYawLimit.get()));
            double wave = Math.sin((tick + entity.getId() * 19) * 0.12D);
            double yaw = Math.toRadians(entity.getBodyYaw() + wave * limit);
            double distance = 4D;

            return entity.getPos().add(-Math.sin(yaw) * distance, entity.getEyeHeight(entity.getPose()), Math.cos(yaw) * distance);
        }

        /* No replay target: don't stare anywhere Ã¢â‚¬â€ face the direction of travel
         * (handled by the movement code) so free-wandering looks natural. */
        if (noTarget)
        {
            return null;
        }

        if (mode == CrowdBehaviorMode.WANDER || mode == CrowdBehaviorMode.WANDER_LOOK)
        {
            return this.getWanderDestination(entity, targetPos, tick).add(0D, 1.25D, 0D);
        }

        if (mode == CrowdBehaviorMode.TALK)
        {
            Vec3d personal = CrowdUtils.personalOffset(entity, Math.max(1D, this.targetSpread.get()));

            return targetPos.subtract(personal.x, -1.1D, personal.z);
        }

        return targetPos.add(0D, 1.25D, 0D);
    }

    private Vec3d horizontalDirection(Vec3d vector, LivingEntity entity)
    {
        Vec3d direction = new Vec3d(vector.x, 0D, vector.z);

        if (direction.lengthSquared() < 1.0E-6D)
        {
            double angle = (entity.getUuid().hashCode() & 0xffff) / 65535D * Math.PI * 2D;

            direction = new Vec3d(Math.cos(angle), 0D, Math.sin(angle));
        }

        return direction.normalize();
    }

    private static double randomUnit(int seed, int step, int salt)
    {
        int hash = seed;

        hash ^= step * 0x9e3779b9;
        hash ^= salt;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;

        return (hash & 0x00ffffff) / (double) 0x01000000;
    }

    private void moveEntity(ServerWorld world, LivingEntity entity, Vec3d destination, double speed, boolean moving, int refresh, int tick)
    {
        if (entity instanceof MobEntity mob)
        {
            if (moving)
            {
                if (Math.floorMod(tick + mob.getId(), refresh) == 0 || mob.getNavigation().isIdle())
                {
                    mob.getNavigation().startMovingTo(destination.x, destination.y, destination.z, speed);
                }
            }
            else
            {
                mob.getNavigation().stop();
                this.stopHorizontal(entity);
            }

            return;
        }

        if (!moving)
        {
            this.stopHorizontal(entity);

            return;
        }

        Vec3d delta = destination.subtract(entity.getPos());
        Vec3d horizontal = new Vec3d(delta.x, 0D, delta.z);

        if (horizontal.lengthSquared() < 1.0E-5D)
        {
            this.stopHorizontal(entity);

            return;
        }

        double step = Math.min(0.42D, Math.max(0.02D, speed * 0.08D));
        Vec3d desired = horizontal.normalize().multiply(step);
        Vec3d motion = this.steerAroundObstacles(world, entity, desired, tick);
        Vec3d velocity = entity.getVelocity();

        entity.setVelocity(motion.x, velocity.y, motion.z);

        if (motion.lengthSquared() > 1.0E-6D)
        {
            float yaw = (float) Math.toDegrees(Math.atan2(-motion.x, motion.z));

            entity.setYaw(stepAngle(entity.getYaw(), yaw, 18F));
            entity.setBodyYaw(stepAngle(entity.getBodyYaw(), yaw, 18F));
        }
        else if (entity.isOnGround())
        {
            this.jump(entity);
        }
    }

    private Vec3d steerAroundObstacles(ServerWorld world, LivingEntity entity, Vec3d desired, int tick)
    {
        if (this.canMove(world, entity, desired))
        {
            return desired;
        }

        boolean flip = Math.floorMod(entity.getUuid().hashCode() + tick / 10, 2) == 0;
        double[] angles = flip
            ? new double[] {35D, -35D, 70D, -70D, 110D, -110D, 160D}
            : new double[] {-35D, 35D, -70D, 70D, -110D, 110D, -160D};

        for (double angle : angles)
        {
            Vec3d rotated = rotateXZ(desired, angle);

            if (this.canMove(world, entity, rotated))
            {
                return rotated;
            }
        }

        return Vec3d.ZERO;
    }

    private boolean canMove(ServerWorld world, LivingEntity entity, Vec3d motion)
    {
        Box box = entity.getBoundingBox().offset(motion.x, 0D, motion.z);

        return world.isSpaceEmpty(box);
    }

    private static Vec3d rotateXZ(Vec3d vector, double degrees)
    {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new Vec3d(vector.x * cos - vector.z * sin, 0D, vector.x * sin + vector.z * cos);
    }

    private void stopHorizontal(LivingEntity entity)
    {
        Vec3d velocity = entity.getVelocity();

        entity.setVelocity(0D, velocity.y, 0D);
    }

    private void jump(LivingEntity entity)
    {
        if (entity instanceof MobEntity mob)
        {
            mob.getJumpControl().setActive();
            mob.setJumping(true);

            return;
        }

        Vec3d velocity = entity.getVelocity();

        entity.setVelocity(velocity.x, Math.max(velocity.y, 0.42D), velocity.z);
        entity.setJumping(true);
    }

    private void spawnSprintParticles(ServerWorld world, LivingEntity entity, int tick)
    {
        if (Math.floorMod(tick + entity.getId(), 2) != 0 || !entity.isOnGround())
        {
            return;
        }

        BlockPos below = entity.getBlockPos().down();
        BlockState state = world.getBlockState(below);

        if (state.isAir())
        {
            return;
        }

        double width = Math.max(0.25D, entity.getWidth() * 0.35D);

        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
            entity.getX(), entity.getY() + 0.05D, entity.getZ(),
            3, width, 0.01D, width, 0.02D);
    }

    private static boolean shouldShoot(LivingEntity mob, int tick, float shootRate)
    {
        return shouldPulse(mob, tick, shootRate);
    }

    private static boolean shouldPulse(LivingEntity mob, int tick, float rate)
    {
        if (rate <= 0F)
        {
            return false;
        }

        int interval = Math.max(1, Math.round(20F / rate));
        int offset = Math.floorMod(mob.getUuid().hashCode(), interval);

        return Math.floorMod(tick + offset, interval) == 0;
    }

    private GunProperties createProjectileProperties()
    {
        GunProperties properties = new GunProperties();

        properties.projectileForm = this.createProjectileForm();
        properties.impactForm = FormUtils.copy(this.impactModel.get());
        properties.useTarget = true;
        properties.lifeSpan = Math.max(1, this.projectileLifeSpan.get());
        properties.speed = Math.max(0F, this.projectileSpeed.get());
        properties.friction = 0.99F;
        properties.gravity = 0.03F;
        properties.yaw = true;
        properties.pitch = true;
        properties.bounces = Math.max(0, this.impactBounces.get());
        properties.bounceDamping = Math.max(0F, Math.min(1F, this.impactBounceDamping.get()));
        properties.vanish = this.impactVanish.get();
        properties.damage = Math.max(0F, this.impactDamage.get());
        properties.knockback = Math.max(0F, this.impactKnockback.get());
        properties.collideBlocks = this.impactCollideBlocks.get();
        properties.collideEntities = this.impactCollideEntities.get();
        properties.cmdFiring = this.cmdFiring.get();
        properties.cmdImpact = this.cmdImpact.get();
        properties.cmdVanish = this.cmdVanish.get();
        properties.cmdTicking = this.cmdTicking.get();
        properties.ticking = Math.max(0, this.ticking.get());

        return properties;
    }

    private Form createProjectileForm()
    {
        Form model = this.projectileModel.get();

        if (model != null)
        {
            return FormUtils.copy(model);
        }

        ItemStack stack = this.shootItem.get();

        if (stack == null || stack.isEmpty())
        {
            stack = new ItemStack(Items.SNOWBALL);
        }
        else
        {
            stack = stack.copy();
        }

        ItemForm itemForm = new ItemForm();

        itemForm.stack.set(stack);

        return itemForm;
    }

    private void shootSnowballLike(ServerWorld world, LivingEntity mob, Vec3d targetPos, GunProperties properties)
    {
        if (properties.speed <= 0F)
        {
            return;
        }

        GunProjectileEntity projectile = new GunProjectileEntity(BBSMod.GUN_PROJECTILE_ENTITY, world);
        double x = targetPos.x - mob.getX();
        double y = (targetPos.y + 0.52D) - (mob.getY() + mob.getEyeHeight(mob.getPose()));
        double z = targetPos.z - mob.getZ();
        double horizontal = Math.sqrt(x * x + z * z);

        projectile.setOwner(mob);
        projectile.setProperties(properties);
        projectile.setForm(FormUtils.copy(properties.projectileForm));
        projectile.setPos(mob.getX(), mob.getY() + mob.getEyeHeight(mob.getPose()), mob.getZ());
        projectile.setVelocity(new Vec3d(x, y + horizontal * 0.2D, z).normalize().multiply(properties.speed));
        projectile.calculateDimensions();

        world.spawnEntity(projectile);
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_SNOW_GOLEM_SHOOT, SoundCategory.NEUTRAL, 1F, 1F / (world.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!properties.cmdFiring.isEmpty() && world.getServer() != null)
        {
            world.getServer().getCommandManager().executeWithPrefix(mob.getCommandSource(), properties.cmdFiring);
        }
    }

    private void applyLook(LivingEntity mob, Vec3d targetPos, CrowdBehaviorMode mode, int tick, float blend)
    {
        if (targetPos == null)
        {
            return;
        }

        if (!this.lookAtTarget.get() || (!this.lookBodyYaw.get() && !this.lookHeadYaw.get() && !this.lookHeadPitch.get()))
        {
            return;
        }

        double dx = targetPos.x - mob.getX();
        double dy = targetPos.y - (mob.getY() + mob.getEyeHeight(mob.getPose()));
        double dz = targetPos.z - mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal < 1.0E-6D && Math.abs(dy) < 1.0E-6D)
        {
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.max(horizontal, 1.0E-6D)));
        float stepScale = 0.25F + 0.75F * blend;
        float maxYawStep = 12F * stepScale;
        float maxPitchStep = 8F * stepScale;

        if (mode == CrowdBehaviorMode.SAD_WALK)
        {
            pitch = 28F;
        }

        if (mob instanceof MobEntity entity && !this.lookBodyYaw.get() && this.lookHeadYaw.get() && this.lookHeadPitch.get() && mode != CrowdBehaviorMode.SAD_WALK)
        {
            entity.getLookControl().lookAt(targetPos.x, targetPos.y, targetPos.z, maxYawStep, maxPitchStep);

            return;
        }

        if (this.lookBodyYaw.get())
        {
            float bodyYaw = stepAngle(mob.getBodyYaw(), yaw, maxYawStep);

            mob.setBodyYaw(bodyYaw);
            mob.setYaw(bodyYaw);
        }

        if (this.lookHeadYaw.get())
        {
            mob.setHeadYaw(stepAngle(mob.getHeadYaw(), yaw, maxYawStep));
        }

        if (this.lookHeadPitch.get())
        {
            mob.setPitch(stepAngle(mob.getPitch(), pitch, maxPitchStep));
        }
    }

    private void applyPerformanceMotion(LivingEntity entity, CrowdBehaviorMode mode, int tick)
    {
        if (!this.headMotion.get() && mode != CrowdBehaviorMode.SAD_WALK && mode != CrowdBehaviorMode.WANDER_LOOK)
        {
            return;
        }

        float energy = Math.max(0F, this.energy.get());
        float phase = (tick + entity.getId() * 17) * 0.18F;

        if (mode == CrowdBehaviorMode.WANDER_LOOK && this.isLookAroundPause(entity, mode, tick))
        {
            float limit = Math.max(20F, Math.min(120F, this.headYawLimit.get()));
            float yawOffset = (float) Math.sin(phase * 0.55F) * limit;
            float pitch = (float) Math.sin(phase * 0.72F) * 8F * Math.max(0.25F, energy);

            entity.setHeadYaw(entity.getBodyYaw() + yawOffset);
            entity.setPitch(stepAngle(entity.getPitch(), pitch, 4F));
        }
        else if (mode == CrowdBehaviorMode.CHEER)
        {
            entity.setHeadYaw(entity.getHeadYaw() + (float) Math.sin(phase * 1.35F) * 10F * energy);
            entity.setPitch(entity.getPitch() + (float) Math.sin(phase * 1.9F) * 5F * energy);
        }
        else if (mode == CrowdBehaviorMode.TALK)
        {
            float nod = (float) Math.sin(phase * 1.2F) > 0.15F ? 7F : -2F;

            entity.setPitch(stepAngle(entity.getPitch(), entity.getPitch() + nod * energy, 5F));
        }
        else if (mode == CrowdBehaviorMode.SAD_WALK)
        {
            entity.setPitch(stepAngle(entity.getPitch(), 28F, 5F));
            entity.setHeadYaw(entity.getHeadYaw() + (float) Math.sin(phase * 0.45F) * 2F);
        }
    }

    private void constrainHeadYaw(LivingEntity entity)
    {
        float limit = Math.max(0F, Math.min(120F, this.headYawLimit.get()));
        float bodyYaw = entity.getBodyYaw();
        float delta = wrapDegrees(entity.getHeadYaw() - bodyYaw);

        if (delta > limit)
        {
            delta = limit;
        }
        else if (delta < -limit)
        {
            delta = -limit;
        }

        entity.setHeadYaw(bodyYaw + delta);
    }

    private static float stepAngle(float current, float target, float maxStep)
    {
        float delta = wrapDegrees(target - current);

        if (delta > maxStep)
        {
            delta = maxStep;
        }
        else if (delta < -maxStep)
        {
            delta = -maxStep;
        }

        return current + delta;
    }

    private static float wrapDegrees(float angle)
    {
        angle = angle % 360F;

        if (angle >= 180F)
        {
            angle -= 360F;
        }
        else if (angle < -180F)
        {
            angle += 360F;
        }

        return angle;
    }

    @Override
    protected Clip create()
    {
        return new CrowdBehaviorActionClip();
    }
}
