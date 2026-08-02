package mchorse.bbs_mod.entity;

import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActorEntity extends LivingEntity implements IEntityFormProvider
{
    private static final TrackedData<Boolean> CROWD_CONTROLLED = DataTracker.registerData(ActorEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> CROWD_RAGDOLL_TILT = DataTracker.registerData(ActorEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> CROWD_RAGDOLL_DIRECTION = DataTracker.registerData(ActorEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public static DefaultAttributeContainer.Builder createActorAttributes()
    {
        return LivingEntity.createLivingAttributes()
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1D)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1D)
            .add(EntityAttributes.GENERIC_ATTACK_SPEED)
            .add(EntityAttributes.GENERIC_LUCK);
    }

    private boolean despawn;
    private boolean crowdLook;
    private float crowdYaw;
    private float crowdBodyYaw;
    private float crowdHeadYaw;
    private float crowdPitch;
    private boolean crowdApplyYaw;
    private boolean crowdApplyBodyYaw;
    private boolean crowdApplyHeadYaw;
    private boolean crowdApplyPitch;
    private MCEntity entity = new MCEntity(this);
    private Form form;

    private Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();

    public ActorEntity(EntityType<? extends LivingEntity> entityType, World world)
    {
        super(entityType, world);
    }

    public MCEntity getEntity()
    {
        return this.entity;
    }

    @Override
    public int getEntityId()
    {
        return this.getId();
    }

    @Override
    public Form getForm()
    {
        return this.form;
    }

    @Override
    public void setForm(Form form)
    {
        Form lastForm = this.form;

        this.form = form;

        if (!this.getWorld().isClient())
        {
            if (lastForm != null) lastForm.onDemorph(this);
            if (form != null) form.onMorph(this);
        }
    }

    @Override
    protected void initDataTracker()
    {
        super.initDataTracker();

        this.dataTracker.startTracking(CROWD_CONTROLLED, false);
        this.dataTracker.startTracking(CROWD_RAGDOLL_TILT, 0F);
        this.dataTracker.startTracking(CROWD_RAGDOLL_DIRECTION, 0F);
    }

    public void setCrowdControlled(boolean crowdControlled)
    {
        if (this.dataTracker.get(CROWD_CONTROLLED) != crowdControlled)
        {
            this.dataTracker.set(CROWD_CONTROLLED, crowdControlled);
        }
    }

    public boolean isCrowdControlled()
    {
        return this.dataTracker.get(CROWD_CONTROLLED);
    }

    public void setCrowdRagdoll(float tilt, float direction)
    {
        float safeTilt = Math.max(0F, tilt);

        if (Math.abs(this.dataTracker.get(CROWD_RAGDOLL_TILT) - safeTilt) > 0.001F)
        {
            this.dataTracker.set(CROWD_RAGDOLL_TILT, safeTilt);
        }

        if (Math.abs(this.dataTracker.get(CROWD_RAGDOLL_DIRECTION) - direction) > 0.001F)
        {
            this.dataTracker.set(CROWD_RAGDOLL_DIRECTION, direction);
        }
    }

    public float getCrowdRagdollTilt()
    {
        return this.dataTracker.get(CROWD_RAGDOLL_TILT);
    }

    public float getCrowdRagdollDirection()
    {
        return this.dataTracker.get(CROWD_RAGDOLL_DIRECTION);
    }

    public void setCrowdLook(float bodyYaw, float headYaw, float pitch)
    {
        this.setCrowdLook(bodyYaw, bodyYaw, headYaw, pitch, true, true, true, true);
    }

    public void setCrowdLook(float yaw, float bodyYaw, float headYaw, float pitch,
        boolean applyYaw, boolean applyBodyYaw, boolean applyHeadYaw, boolean applyPitch)
    {
        this.crowdLook = true;
        this.crowdYaw = yaw;
        this.crowdBodyYaw = bodyYaw;
        this.crowdHeadYaw = headYaw;
        this.crowdPitch = pitch;
        this.crowdApplyYaw = applyYaw;
        this.crowdApplyBodyYaw = applyBodyYaw;
        this.crowdApplyHeadYaw = applyHeadYaw;
        this.crowdApplyPitch = applyPitch;
        this.applyCrowdLook();
    }

    public void clearCrowdLook()
    {
        this.crowdLook = false;
    }

    private void applyCrowdLook()
    {
        if (this.crowdApplyYaw) this.setYaw(this.crowdYaw);
        if (this.crowdApplyBodyYaw) this.setBodyYaw(this.crowdBodyYaw);
        if (this.crowdApplyHeadYaw) this.setHeadYaw(this.crowdHeadYaw);
        if (this.crowdApplyPitch) this.setPitch(this.crowdPitch);
    }

    @Override
    public boolean isPushable()
    {
        return !this.isCrowdControlled() && super.isPushable();
    }

    @Override
    public void tickMovement()
    {
        if (this.isCrowdControlled())
        {
            this.setVelocity(Vec3d.ZERO);

            return;
        }

        super.tickMovement();
    }

    @Override
    public boolean shouldRender(double distance)
    {
        double d = this.getBoundingBox().getAverageSideLength();

        if (Double.isNaN(d))
        {
            d = 1D;
        }

        return distance < (d * 256D) * (d * 256D);
    }

    @Override
    public Iterable<ItemStack> getHandItems()
    {
        return List.of(this.getEquippedStack(EquipmentSlot.MAINHAND), this.getEquippedStack(EquipmentSlot.OFFHAND));
    }

    @Override
    public Iterable<ItemStack> getArmorItems()
    {
        return List.of(this.getEquippedStack(EquipmentSlot.FEET), this.getEquippedStack(EquipmentSlot.LEGS), this.getEquippedStack(EquipmentSlot.CHEST), this.getEquippedStack(EquipmentSlot.HEAD));
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot)
    {
        return this.equipment.getOrDefault(slot, ItemStack.EMPTY);
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack)
    {
        this.equipment.put(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    @Override
    public Arm getMainArm()
    {
        return Arm.RIGHT;
    }

    @Override
    public void tick()
    {
        super.tick();

        this.tickHandSwing();

        if (this.form != null && this.shouldUpdateForm())
        {
            this.form.update(this.entity);
        }

        if (this.crowdLook)
        {
            this.applyCrowdLook();
        }

        if (this.getWorld().isClient || this.isCrowdControlled())
        {
            return;
        }

        /* Pickup items */
        Box box = this.getBoundingBox().expand(1D, 0.5D, 1D);
        List<Entity> list = this.getWorld().getOtherEntities(this, box);

        for (Entity entity : list)
        {
            if (entity instanceof ItemEntity itemEntity)
            {
                ItemStack itemStack = itemEntity.getStack();
                int i = itemStack.getCount();

                if (!entity.isRemoved() && !itemEntity.cannotPickup())
                {
                    ((ServerWorld) this.getWorld()).getChunkManager().sendToOtherNearbyPlayers(entity, new ItemPickupAnimationS2CPacket(entity.getId(), this.getId(), i));
                    entity.discard();
                }
            }
        }
    }

    private boolean shouldUpdateForm()
    {
        if (!this.isCrowdControlled())
        {
            return true;
        }

        /* Crowd forms are visual state; the dedicated runtime owns movement, damage and look.
         * Never run form animation logic on the server, and stagger distant client animation
         * updates while still rendering every frame. */
        if (!this.getWorld().isClient)
        {
            return false;
        }

        PlayerEntity viewer = this.getWorld().getClosestPlayer(this, 256D);
        double distanceSquared = viewer == null ? Double.POSITIVE_INFINITY : this.squaredDistanceTo(viewer);
        int interval = distanceSquared <= 32D * 32D ? 1
            : distanceSquared <= 64D * 64D ? 2
            : distanceSquared <= 128D * 128D ? 4
            : 8;

        return Math.floorMod(this.age + this.getId(), interval) == 0;
    }

    @Override
    public void checkDespawn()
    {
        super.checkDespawn();

        if (this.despawn)
        {
            this.discard();
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player)
    {
        super.onStartedTrackingBy(player);

        ServerNetwork.sendEntityForm(player, this);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt)
    {
        super.readCustomDataFromNbt(nbt);

        this.despawn = nbt.getBoolean("despawn");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt)
    {
        super.writeCustomDataToNbt(nbt);

        nbt.putBoolean("despawn", true);
    }

    @Override
    protected int getPermissionLevel()
    {
        return 4;
    }
}
