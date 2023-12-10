package me.earth.earthhack.impl.core.mixins.entity;

import me.earth.earthhack.api.cache.ModuleCache;
import me.earth.earthhack.api.cache.SettingCache;
import me.earth.earthhack.api.event.bus.instance.Bus;
import me.earth.earthhack.api.setting.Setting;
import me.earth.earthhack.api.setting.settings.BooleanSetting;
import me.earth.earthhack.api.setting.settings.NumberSetting;
import me.earth.earthhack.impl.core.ducks.entity.IEntity;
import me.earth.earthhack.impl.event.events.movement.MoveEvent;
import me.earth.earthhack.impl.modules.Caches;
import me.earth.earthhack.impl.modules.client.management.Management;
import me.earth.earthhack.impl.modules.misc.nointerp.NoInterp;
import me.earth.earthhack.impl.modules.movement.autosprint.AutoSprint;
import me.earth.earthhack.impl.modules.movement.autosprint.mode.SprintMode;
import me.earth.earthhack.impl.modules.movement.step.Step;
import me.earth.earthhack.impl.modules.movement.velocity.Velocity;
import me.earth.earthhack.impl.util.math.StopWatch;
import me.earth.earthhack.impl.util.minecraft.entity.EntityType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntity
{
    // private static final ModuleCache<NoRender>
    //         NO_RENDER = Caches.getModule(NoRender.class);
    @Unique
    private static final ModuleCache<AutoSprint>
            SPRINT = Caches.getModule(AutoSprint.class);
    @Unique
    private static final ModuleCache<Velocity>
            VELOCITY = Caches.getModule(Velocity.class);
    @Unique
    private static final ModuleCache<NoInterp>
            NOINTERP = Caches.getModule(NoInterp.class);
    @Unique
    private static final SettingCache<Boolean, BooleanSetting, Velocity>
            NO_PUSH = Caches.getSetting
            (Velocity.class, BooleanSetting.class, "NoPush", false);
    @Unique
    private static final SettingCache<Boolean, BooleanSetting, Step>
            STEP_COMP = Caches.getSetting
            (Step.class, BooleanSetting.class, "Compatibility", false);

    @Unique
    private static final SettingCache
            <Integer, NumberSetting<Integer>, Management> DEATH_TIME =
            Caches.getSetting(Management.class, Setting.class, "DeathTime", 500);

    @Shadow
    private Vec3d pos;
    @Shadow
    private Vec3d velocity;
    @Shadow
    private float yaw;
    @Shadow
    private float pitch;
    @Shadow
    private boolean onGround;
    @Shadow
    private World world;
    @Shadow
    public double prevX;
    @Shadow
    public double prevY;
    @Shadow
    public double prevZ;
    @Shadow
    public double lastRenderX;
    @Shadow
    public double lastRenderY;
    @Shadow
    public double lastRenderZ;
    @Final
    @Shadow
    protected DataTracker dataTracker;
    @Shadow
    private float stepHeight;
    @Shadow
    private Entity.RemovalReason removalReason;
    @Shadow
    private EntityDimensions dimensions;
    @Shadow
    public float prevYaw;
    @Shadow
    public float prevPitch;

    @Unique
    private long oldServerX;
    @Unique
    private long oldServerY;
    @Unique
    private long oldServerZ;
    @Unique
    private final StopWatch pseudoWatch = new StopWatch();
    @Unique
    private MoveEvent moveEvent;
    @Unique
    private Float prevHeight;
    @Unique
    private Supplier<EntityType> type;
    @Unique
    private boolean pseudoDead;
    @Unique
    private long stamp;
    @Unique
    private boolean dummy;

    @Shadow
    public abstract Box getBoundingBox();
    @Shadow
    public abstract boolean isSneaking();
    @Shadow
    protected abstract boolean getFlag(int flag);
    @Shadow
    public abstract boolean equals(Object p_equals_1_);
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);
    @Shadow
    public abstract boolean hasVehicle();

    @Shadow
    public boolean noClip;

    @Shadow
    public abstract void move(MovementType movementType, Vec3d movement);
    @Shadow
    public abstract Text getName();

    @Shadow public abstract int getId();

    @Override
    public EntityType getType()
    {
        return type.get();
    }

    @Override
    public long getDeathTime()
    {
        // TODO!!!
        return 0;
    }

    @Override
    public void setOldServerPos(long x, long y, long z)
    {
        this.oldServerX = x;
        this.oldServerY = y;
        this.oldServerZ = z;
    }

    @Override
    public long getOldServerPosX()
    {
        return oldServerX;
    }

    @Override
    public long getOldServerPosY()
    {
        return oldServerY;
    }

    @Override
    public long getOldServerPosZ()
    {
        return oldServerZ;
    }

    @Override
    public boolean isPseudoDead()
    {
        if (pseudoDead
                && !removalReason.shouldDestroy()
                && pseudoWatch.passed(DEATH_TIME.getValue()))
        {
            pseudoDead = false;
        }

        return pseudoDead;
    }

    @Override
    public void setPseudoDead(boolean pseudoDead)
    {
        this.pseudoDead = pseudoDead;
        if (pseudoDead)
        {
            pseudoWatch.reset();
        }
    }

    @Override
    public StopWatch getPseudoTime()
    {
        return pseudoWatch;
    }

    @Override
    public long getTimeStamp()
    {
        return stamp;
    }

    @Override
    public boolean isDummy()
    {
        return dummy;
    }

    @Override
    public void setDummy(boolean dummy)
    {
        this.dummy = dummy;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    public void ctrHook(CallbackInfo info)
    {
        this.type = EntityType.getEntityType(Entity.class.cast(this));
        this.stamp = System.currentTimeMillis();
    }

    @Inject(
            method = "spawnSprintingParticles",
            at = @At("HEAD"),
            cancellable = true)
    public void createRunningParticlesHook(CallbackInfo ci)
    {
        //noinspection ConstantConditions
        if (ClientPlayerEntity.class.isInstance(this)
                && SPRINT.isEnabled()
                && SPRINT.get().getMode() == SprintMode.Rage)
        {
            ci.cancel();
        }
    }

    @Inject(
            method = "move",
            at = @At("HEAD"),
            cancellable = true)
    public void moveEntityHook_Head(MovementType movementType, Vec3d movement, CallbackInfo ci)
    {
        //noinspection ConstantConditions
        if (ClientPlayerEntity.class.isInstance(this))
        {
            this.moveEvent = new MoveEvent(movementType, movement.getX(), movement.getY(), movement.getZ(), this.isSneaking());
            Bus.EVENT_BUS.post(this.moveEvent);
            if (moveEvent.isCancelled()) {
                ci.cancel();
            }
        }
    }
    /*
    @ModifyVariable(
             method = "move",
             at = @At(
                     value = "HEAD"),
             ordinal = 0)
    private double setX(double x)
    {
        return this.moveEvent != null ? this.moveEvent.getX() : x;
    }

    @ModifyVariable(
            method = "move",
            at = @At("HEAD"),
            ordinal = 1)
    private double setY(double y)
    {
        return this.moveEvent != null ? this.moveEvent.getY() : y;
    }

    @ModifyVariable(
             method = "move",
             at = @At("HEAD"),
             ordinal = 2)
    private double setZ(double z)
    {
         return this.moveEvent != null ? this.moveEvent.getZ() : z;
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/entity/Entity.isSneaking()Z",
                    ordinal = 0))
    public boolean isSneakingHook(Entity entity)
    {
        return this.moveEvent != null
                ? this.moveEvent.isSneaking()
                : entity.isSneaking();
    }

    @Inject(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;stepOnBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;ZZLnet/minecraft/util/math/Vec3d;)Z",
                    ordinal = 1))
    public void onGroundHook(MovementType type,
                             Vec3d movement,
                             CallbackInfo info)
    {
        //noinspection ConstantConditions
        if (ClientPlayerEntity.class.isInstance(this) && !STEP_COMP.getValue()) {
            StepEvent event = new StepEvent(Stage.PRE,
                    this.getBoundingBox(),
                    this.stepHeight);
            Bus.EVENT_BUS.post(event);
            this.prevHeight = this.stepHeight;
            this.stepHeight = event.getHeight();
        }
    }

    @Inject(
            method = "move",
            at = @At(
                    value = "FIELD",
                    target = "net/minecraft/entity/Entity.stepHeight:F",
                    ordinal = 3,
                    shift = At.Shift.BEFORE))
    public void onGroundHookComp(MovementType type,
                                 Vec3d movement,
                                 CallbackInfo info) {
        //noinspection ConstantConditions
        if (ClientPlayerEntity.class.isInstance(this) && STEP_COMP.getValue()) {
            StepEvent event = new StepEvent(Stage.PRE,
                    this.getBoundingBox(),
                    this.stepHeight);
            Bus.EVENT_BUS.post(event);
            this.prevHeight = this.stepHeight;
            this.stepHeight = event.getHeight();
        }
    }
    */
}
