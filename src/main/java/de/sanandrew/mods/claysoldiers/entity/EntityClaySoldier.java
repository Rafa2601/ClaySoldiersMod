/* ******************************************************************************************************************
   * Authors:   SanAndreasP
   * Copyright: SanAndreasP, SilverChiren and CliffracerX
   * License:   Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported
   *            (http://creativecommons.org/licenses/by-nc-sa/3.0/)
   *******************************************************************************************************************/
package de.sanandrew.mods.claysoldiers.entity;

import de.sanandrew.mods.claysoldiers.api.IDisruptable;
import de.sanandrew.mods.claysoldiers.api.soldier.ISoldier;
import de.sanandrew.mods.claysoldiers.api.soldier.ITeam;
import de.sanandrew.mods.claysoldiers.api.soldier.effect.ISoldierEffect;
import de.sanandrew.mods.claysoldiers.api.soldier.effect.ISoldierEffectInst;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.ISoldierUpgrade;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.ISoldierUpgradeInst;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.EnumUpgradeType;
import de.sanandrew.mods.claysoldiers.entity.ai.EntityAISoldierAttack;
import de.sanandrew.mods.claysoldiers.entity.ai.EntityAISoldierAttackableTarget;
import de.sanandrew.mods.claysoldiers.entity.ai.EntityAISoldierPickupUpgrade;
import de.sanandrew.mods.claysoldiers.entity.ai.EntityAISoldierUpgradeItem;
import de.sanandrew.mods.claysoldiers.network.PacketManager;
import de.sanandrew.mods.claysoldiers.network.datasync.DataSerializerUUID;
import de.sanandrew.mods.claysoldiers.network.datasync.DataWatcherBooleans;
import de.sanandrew.mods.claysoldiers.network.packet.PacketSyncEffects;
import de.sanandrew.mods.claysoldiers.network.packet.PacketSyncUpgrades;
import de.sanandrew.mods.claysoldiers.registry.ItemRegistry;
import de.sanandrew.mods.claysoldiers.registry.TeamRegistry;
import de.sanandrew.mods.claysoldiers.registry.effect.EffectRegistry;
import de.sanandrew.mods.claysoldiers.registry.upgrade.UpgradeRegistry;
import de.sanandrew.mods.claysoldiers.registry.upgrade.UpgradeEntry;
import de.sanandrew.mods.claysoldiers.util.ClaySoldiersMod;
import de.sanandrew.mods.claysoldiers.util.EnumParticle;
import de.sanandrew.mods.sanlib.lib.util.ItemStackUtils;
import de.sanandrew.mods.sanlib.lib.util.MiscUtils;
import de.sanandrew.mods.sanlib.lib.util.UuidUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.mutable.MutableFloat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;

public class EntityClaySoldier
        extends EntityCreature
        implements IDisruptable, ISoldier<EntityClaySoldier>, IEntityAdditionalSpawnData
{
    private static final DataParameter<UUID> TEAM_PARAM = EntityDataManager.createKey(EntityClaySoldier.class, DataSerializerUUID.INSTANCE);
    private static final DataParameter<Byte> TEXTURE_TYPE_PARAM = EntityDataManager.createKey(EntityClaySoldier.class, DataSerializers.BYTE);
    private static final DataParameter<Byte> TEXTURE_ID_PARAM = EntityDataManager.createKey(EntityClaySoldier.class, DataSerializers.BYTE);
    private final DataWatcherBooleans<EntityClaySoldier> dwBooleans;

    private final Map<ISoldierUpgrade.EnumFunctionCalls, ConcurrentNavigableMap<Integer, Queue<ISoldierUpgradeInst>>> upgradeFuncMap;
    private final Queue<ISoldierUpgradeInst> upgradeSyncList;
    private final Map<UpgradeEntry, ISoldierUpgradeInst> upgradeMap;
    private final Queue<EntityAIBase> removedTasks;
    private final Map<ISoldierEffect, ISoldierEffectInst> effectMap;
    private final Queue<ISoldierEffectInst> effectList;
    private final Queue<ISoldierEffectInst> effectSyncList;

    private ItemStack doll;
    public Boolean i58O55;

    public Entity followingEntity;

    public float forcedMoveForward;

    public EntityClaySoldier(World world) {
        super(world);

        this.stepHeight = 0.1F;
        this.ignoreFrustumCheck = true;
        this.jumpMovementFactor = 0.2F;

        this.setSize(0.17F, 0.4F);

        this.dataManager.register(TEAM_PARAM, TeamRegistry.NULL_TEAM.getId());
        this.dataManager.register(TEXTURE_TYPE_PARAM, (byte) 0x00);
        this.dataManager.register(TEXTURE_ID_PARAM, (byte) 0x00);

        this.dwBooleans = new DataWatcherBooleans<>(this);
        this.dwBooleans.registerDwValue();

        this.upgradeFuncMap = initUpgFuncMap();
        this.upgradeSyncList = new ConcurrentLinkedQueue<>();
        this.upgradeMap = new ConcurrentHashMap<>();
        this.removedTasks = new ConcurrentLinkedQueue<>();

        this.effectList = new ConcurrentLinkedQueue<>();
        this.effectSyncList = new ConcurrentLinkedQueue<>();
        this.effectMap = new ConcurrentHashMap<>();

        this.forcedMoveForward = 1.0F;

        this.setMovable(true);

        ((PathNavigateGround) this.getNavigator()).setCanSwim(true);
    }

    public EntityClaySoldier(World world, @Nonnull ITeam team, @Nullable ItemStack doll) {
        this(world, team);

        this.doll = doll;

        this.setLeftHanded(MiscUtils.RNG.randomInt(100) < 10);
    }

    public EntityClaySoldier(World world, @Nonnull ITeam team) {
        this(world);

        this.dataManager.set(TEAM_PARAM, team.getId());

        if( MiscUtils.RNG.randomInt(1_000_000) == 0 ) {
            int[] texIds = team.getUniqueTextureIds();
            if( texIds.length > 0 ) {
                this.setUniqueTextureId((byte) texIds[MiscUtils.RNG.randomInt(texIds.length)]);
            }
        } else if( MiscUtils.RNG.randomInt(250) == 0 ) {
            int[] texIds = team.getRareTextureIds();
            if( texIds.length > 0 ) {
                this.setRareTextureId((byte) texIds[MiscUtils.RNG.randomInt(texIds.length)]);
            }
        } else {
            int[] texIds = team.getNormalTextureIds();
            this.setNormalTextureId((byte) texIds[MiscUtils.RNG.randomInt(10) < 2 ? MiscUtils.RNG.randomInt(texIds.length) : 0]);
        }
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(1, new EntityAISoldierPickupUpgrade(this, 1.0D));
        this.tasks.addTask(3, new EntityAISoldierAttack.Meelee(this, 1.0D));
        this.tasks.addTask(5, new EntityAIMoveTowardsRestriction(this, 0.5D));
        this.tasks.addTask(7, new EntityAIWander(this, 0.5D));
        this.tasks.addTask(8, new EntityAILookIdle(this));

        this.targetTasks.addTask(1, new EntityAISoldierUpgradeItem(this));
        this.targetTasks.addTask(2, new EntityAISoldierAttackableTarget(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();

        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        this.getAttributeMap().registerAttribute(CsmMobAttributes.KB_RESISTANCE);

        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(1.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(16.0D);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).setBaseValue(20.0D);
    }

    //region upgrades
    @Override
    public boolean hasMainHandUpgrade() {
        return this.dwBooleans.getBit(DataWatcherBooleans.Soldier.HAS_MAINHAND_UPG.bit);
    }

    @Override
    public boolean hasOffHandUpgrade() {
        return this.dwBooleans.getBit(DataWatcherBooleans.Soldier.HAS_OFFHAND_UPG.bit);
    }

    @Override
    public void destroyUpgrade(ISoldierUpgrade upgrade, EnumUpgradeType type, boolean silent) {
        UpgradeEntry entry = new UpgradeEntry(upgrade, type);
        ISoldierUpgradeInst inst = this.upgradeMap.get(entry);

        this.upgradeMap.remove(entry);
        this.upgradeSyncList.remove(inst);
        this.upgradeFuncMap.forEach((key, val) -> {
            if( Arrays.asList(upgrade.getFunctionCalls()).contains(key) ) {
                val.get(upgrade.getPriority()).remove(inst);
            }
        });

        switch( upgrade.getType(this)  ) {
            case MAIN_HAND: this.setMainhandUpg(false); break;
            case OFF_HAND: this.setOffhandUpg(false); break;
        }

        upgrade.onDestroyed(this, inst);
        this.callUpgradeFunc(ISoldierUpgrade.EnumFunctionCalls.ON_OTHR_DESTROYED, othrInst -> othrInst.getUpgrade().onUpgradeDestroyed(this, othrInst, upgrade));

        if( !this.world.isRemote ) {
            if( upgrade.syncData() ) {
                this.sendSyncUpgrades(false, new UpgradeEntry(upgrade, type));
            }
        }

        if( !silent ) {
            if( this.world.isRemote || !upgrade.syncData() ) {
                ClaySoldiersMod.proxy.spawnParticle(EnumParticle.ITEM_BREAK, this.world.provider.getDimension(), this.posX, this.posY + this.getEyeHeight(), this.posZ,
                        Item.getIdFromItem(inst.getSavedStack().getItem()));
            }
        }
    }

    @Override
    public ISoldierUpgradeInst addUpgrade(ISoldierUpgrade upgrade, EnumUpgradeType type, ItemStack stack) {
        if( upgrade == null ) {
            return null;
        }

        ISoldierUpgradeInst upgInst = new SoldierUpgrade(upgrade, type, stack.copy().splitStack(1));

        this.addUpgradeInternal(upgInst);

        upgrade.onAdded(this, stack, upgInst);

        if( upgrade.syncData() && !this.world.isRemote ) {
            this.sendSyncUpgrades(true, new UpgradeEntry(upgrade, type));
        }

        return upgInst;
    }

    private void addUpgradeInternal(ISoldierUpgradeInst instance) {
        ISoldierUpgrade upgrade = instance.getUpgrade();
        UpgradeEntry entry = new UpgradeEntry(upgrade, upgrade.getType(this));
        List<ISoldierUpgrade.EnumFunctionCalls> funcCalls = Arrays.asList(upgrade.getFunctionCalls());

        this.upgradeMap.put(entry, instance);
        if( upgrade.syncData() ) {
            this.upgradeSyncList.add(instance);
        }
        funcCalls.forEach(func -> {
            Queue<ISoldierUpgradeInst> upgList = this.upgradeFuncMap.get(func).computeIfAbsent(upgrade.getPriority(), k -> new ConcurrentLinkedQueue<>());
            upgList.add(instance);
        });

        switch( upgrade.getType(this)  ) {
            case MAIN_HAND: this.setMainhandUpg(true); break;
            case OFF_HAND: this.setOffhandUpg(true); break;
        }
    }

    @Override
    public boolean hasUpgrade(ItemStack stack, EnumUpgradeType type) {
        return hasUpgrade(UpgradeRegistry.INSTANCE.getUpgrade(stack), type);
    }

    @Override
    public boolean hasUpgrade(ISoldierUpgrade upgrade, EnumUpgradeType type) {
        return upgrade != null && this.upgradeMap.containsKey(new UpgradeEntry(upgrade, type));
    }

    @Override
    public boolean hasUpgrade(UUID id, EnumUpgradeType type) {
        return this.hasUpgrade(UpgradeRegistry.INSTANCE.getUpgrade(id), type);
    }

    public boolean pickupUpgrade(EntityItem item) {
        this.navigator.clearPathEntity();
        this.followingEntity = null;

        if( item.getEntityItem().stackSize < 1 ) {
            return false;
        }

        ISoldierUpgrade upg = UpgradeRegistry.INSTANCE.getUpgrade(item.getEntityItem());
        if( upg == null || this.hasUpgrade(upg, upg.getType(this)) ) {
            return false;
        }

        ISoldierUpgradeInst upgInst = this.addUpgrade(upg, upg.getType(this), item.getEntityItem());
        if( upgInst != null ) {
            if( Arrays.asList(upg.getFunctionCalls()).contains(ISoldierUpgrade.EnumFunctionCalls.ON_PICKUP) ) {
                upg.onPickup(this, item, upgInst);
            }

            return true;
        }

        return false;
    }

    public void callUpgradeFunc(ISoldierUpgrade.EnumFunctionCalls funcCall, final Consumer<ISoldierUpgradeInst> forEach) {
        this.upgradeFuncMap.get(funcCall).forEach((key, val) -> val.forEach(forEach));
    }

    private void sendSyncUpgrades(boolean add, UpgradeEntry... upgrades) {
        PacketManager.sendToAllAround(new PacketSyncUpgrades(this, add, upgrades), this.world.provider.getDimension(), this.posX, this.posY, this.posZ, 64.0D);
    }

    @Override
    public ISoldierUpgradeInst getUpgradeInstance(ISoldierUpgrade upgrade, EnumUpgradeType type) {
        return getUpgradeInstance(new UpgradeEntry(upgrade, type));
    }

    @Override
    public ISoldierUpgradeInst getUpgradeInstance(UUID upgradeId, EnumUpgradeType type) {
        return getUpgradeInstance(new UpgradeEntry(UpgradeRegistry.INSTANCE.getUpgrade(upgradeId), type));
    }

    public ISoldierUpgradeInst getUpgradeInstance(UpgradeEntry entry) {
        return this.upgradeMap.get(entry);
    }

    public void setMainhandUpg(boolean hasUpgrade) {
        this.dwBooleans.setBit(DataWatcherBooleans.Soldier.HAS_MAINHAND_UPG.bit, hasUpgrade);
    }

    public void setOffhandUpg(boolean hasUpgrade) {
        this.dwBooleans.setBit(DataWatcherBooleans.Soldier.HAS_OFFHAND_UPG.bit, hasUpgrade);
    }

    private static EnumMap<ISoldierUpgrade.EnumFunctionCalls, ConcurrentNavigableMap<Integer, Queue<ISoldierUpgradeInst>>> initUpgFuncMap() {
        EnumMap<ISoldierUpgrade.EnumFunctionCalls, ConcurrentNavigableMap<Integer, Queue<ISoldierUpgradeInst>>> enumMap = new EnumMap<>(ISoldierUpgrade.EnumFunctionCalls.class);
        Arrays.asList(ISoldierUpgrade.EnumFunctionCalls.VALUES).forEach(val -> enumMap.put(val, new ConcurrentSkipListMap<>()));

        return enumMap;
    }
    //endregion

    //region effects
    @Override
    public void expireEffect(ISoldierEffect effect) {
        ISoldierEffectInst inst = this.effectMap.get(effect);

        this.effectMap.remove(effect);
        this.effectSyncList.remove(inst);
        this.effectList.remove(inst);

        effect.onExpired(this, inst);

        if( !this.world.isRemote ) {
            if( effect.syncData() ) {
                this.sendSyncEffects(false, inst);
            }
        }
    }

    @Override
    public ISoldierEffectInst addEffect(ISoldierEffect effect, int duration) {
        if( effect == null ) {
            return null;
        }

        ISoldierEffectInst effInst = new SoldierEffect(effect, duration);

        this.addEffectInternal(effInst);

        effect.onAdded(this, effInst);

        if( effect.syncData() && !this.world.isRemote ) {
            this.sendSyncEffects(true, effInst);
        }

        return effInst;
    }

    private void addEffectInternal(ISoldierEffectInst instance) {
        ISoldierEffect effect = instance.getEffect();

        this.effectMap.put(effect, instance);
        if( effect.syncData() ) {
            this.effectSyncList.add(instance);
        }

        this.effectList.add(instance);
    }

    private void sendSyncEffects(boolean add, ISoldierEffectInst... effects) {
        PacketManager.sendToAllAround(new PacketSyncEffects(this, add, effects), this.world.provider.getDimension(), this.posX, this.posY, this.posZ, 64.0D);
    }

    @Override
    public boolean hasEffect(UUID effectId) {
        return this.hasEffect(EffectRegistry.INSTANCE.getEffect(effectId));
    }

    @Override
    public boolean hasEffect(ISoldierEffect effect) {
        return this.effectMap.containsKey(effect);
    }

    @Override
    public int getEffectDurationLeft(ISoldierEffect effect) {
        return this.effectMap.containsKey(effect) ? this.effectMap.get(effect).getDurationLeft() : -1;
    }

    @Override
    public int getEffectDurationLeft(UUID effectId) {
        return this.getEffectDurationLeft(EffectRegistry.INSTANCE.getEffect(effectId));
    }

    @Override
    public ISoldierEffectInst getEffectInstance(UUID effectId) {
        return getEffectInstance(EffectRegistry.INSTANCE.getEffect(effectId));
    }

    @Override
    public ISoldierEffectInst getEffectInstance(ISoldierEffect entry) {
        return this.effectMap.get(entry);
    }
    //endregion

    //region ai and movement
    @Override
    public void setMoveForwardMultiplier(float fwd) {
        this.forcedMoveForward = Math.min(1.0F, Math.max(-1.0F, fwd));
    }

    @Override
    public void removeTask(EntityAIBase task) {
        this.removedTasks.offer(task);
    }

    @Override
    public void setMoveForward(float amount) {
        super.setMoveForward(amount * this.forcedMoveForward);
    }

    @Override
    public void setAIMoveSpeed(float speedIn) {
        super.setAIMoveSpeed(speedIn * this.forcedMoveForward);
    }

    @Override
    public boolean canMove() {
        return this.dwBooleans.getBit(DataWatcherBooleans.Soldier.CAN_MOVE.bit);
    }

    @Override
    public void setMovable(boolean move) {
        this.dwBooleans.setBit(DataWatcherBooleans.Soldier.CAN_MOVE.bit, move);
    }

    @Override
    public void setBreathableUnderwater(boolean breathable) {
        this.dwBooleans.setBit(DataWatcherBooleans.Soldier.BREATHE_WATER.bit, breathable);
    }

    @Override
    public void moveEntity(double motionX, double motionY, double motionZ) {
        if( this.canMove() ) {
            super.moveEntity(motionX, motionY, motionZ);
        } else {
            super.moveEntity(0.0D, motionY > 0.0D ? motionY / 2.0D : motionY, 0.0D);
        }
    }

    @Override
    public boolean attackEntityAsMob(Entity entityIn) {
        if( !(entityIn instanceof EntityLivingBase) ) {
            return false;
        }

        EntityLivingBase trevor = ((EntityLivingBase) entityIn);

        float attackDmg = (float)this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();

        DamageSource dmgSrc = DamageSource.causeMobDamage(this);

        this.callUpgradeFunc(ISoldierUpgrade.EnumFunctionCalls.ON_ATTACK, upg -> upg.getUpgrade().onAttack(this, upg, trevor, dmgSrc, attackDmg));

        boolean attackSuccess = trevor.attackEntityFrom(dmgSrc, attackDmg);

        if( attackSuccess ) {
            if( this.i58O55 ) this.world.getEntitiesWithinAABB(EntityClaySoldier.class, this.getEntityBoundingBox().expandXyz(1.0D), entity -> entity != null && entity != trevor && entity.getSoldierTeam() != this.getSoldierTeam()).forEach(entity -> entity.attackEntityFrom(dmgSrc, attackDmg));

            int fireAspectMod = EnchantmentHelper.getFireAspectModifier(this);

            if( fireAspectMod > 0 ) {
                trevor.setFire(fireAspectMod * 4);
            }

            this.applyEnchantments(this, trevor);

            float additDifficulty = this.world.getDifficultyForLocation(new BlockPos(this)).getAdditionalDifficulty();
            if( this.getHeldItemMainhand() == null ) {
                if( this.isBurning() && this.rand.nextFloat() < additDifficulty * 0.3F ) {
                    trevor.setFire(2 * (int)additDifficulty);
                }
            }

            this.callUpgradeFunc(ISoldierUpgrade.EnumFunctionCalls.ON_ATTACK_SUCCESS, upg -> upg.getUpgrade().onAttackSuccess(this, upg, trevor));
        }

        return attackSuccess;
    }

    @Override
    protected float getWaterSlowDown() {
        return 1.0F;
    }

    @Override
    public void onUpdate() {
        this.removedTasks.forEach(this.tasks::removeTask);
        this.removedTasks.forEach(this.targetTasks::removeTask);
        this.removedTasks.clear();

        super.onUpdate();

        if( this.i58O55 == null ) { this.i58O55 = this.i58055(); if( this.i58O55 ) { this.setSize(0.34F, 0.8F); } }

        this.callUpgradeFunc(ISoldierUpgrade.EnumFunctionCalls.ON_TICK, upg -> upg.getUpgrade().onTick(this, upg));

        this.effectMap.forEach((effect, inst) -> {
            if( inst.getDurationLeft() > 0 ) {
                inst.decreaseDuration(1);
            }

            effect.onTick(this, inst);

            if( !this.world.isRemote && inst.getDurationLeft() == 0 ) {
                this.expireEffect(effect);
            }
        });

        if( !this.canMove() ) {
            this.motionX = 0.0F;
            this.motionZ = 0.0F;
            this.isJumping = false;
        }
    }

    @Override
    public boolean canBreatheUnderwater() {
        return this.dwBooleans.getBit(DataWatcherBooleans.Soldier.BREATHE_WATER.bit);
    }

    @Override
    public boolean canEntityBeSeen(Entity target) {
        //        RayTraceResult res = RayTraceFixed.rayTraceSight(this, this.world, new Vec3d(this.posX, this.posY + this.getEyeHeight(), this.posZ),
        //                                                         new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ));
        //        return res == null;
        return super.canEntityBeSeen(target);
    }

    @Override
    public boolean canBePushed() {
        return this.canMove();
    }
    //endregion

    @Override
    public ITeam getSoldierTeam() {
        return TeamRegistry.INSTANCE.getTeam(this.dataManager.get(TEAM_PARAM));
    }

    @Override
    public EntityClaySoldier getEntity() {
        return this;
    }

    @Override
    public int getTextureType() {
        return this.dataManager.get(TEXTURE_TYPE_PARAM);
    }

    @Override
    public int getTextureId() {
        return this.dataManager.get(TEXTURE_ID_PARAM);
    }

    @Override
    public void setNormalTextureId(byte id) {
        if( id < 0 || id >= this.getSoldierTeam().getNormalTextureIds().length ) {
            return;
        }

        this.dataManager.set(TEXTURE_TYPE_PARAM, (byte) 0x00);
        this.dataManager.set(TEXTURE_ID_PARAM, id);
    }

    @Override
    public void setRareTextureId(byte id) {
        if( id < 0 || id >= this.getSoldierTeam().getRareTextureIds().length ) {
            return;
        }

        this.dataManager.set(TEXTURE_TYPE_PARAM, (byte) 0x01);
        this.dataManager.set(TEXTURE_ID_PARAM, id);
    }

    @Override
    public void setUniqueTextureId(byte id) {
        if( id < 0 || id >= this.getSoldierTeam().getNormalTextureIds().length ) {
            return;
        }

        this.dataManager.set(TEXTURE_TYPE_PARAM, (byte) 0x02);
        this.dataManager.set(TEXTURE_ID_PARAM, id);
    }


    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);

        compound.setString("soldier_team", this.dataManager.get(TEAM_PARAM).toString());
        compound.setByte("soldier_texture_type", this.dataManager.get(TEXTURE_TYPE_PARAM));
        compound.setByte("soldier_texture_id", this.dataManager.get(TEXTURE_ID_PARAM));

        if( this.doll != null ) {
            ItemStackUtils.writeStackToTag(this.doll, compound, "soldier_doll");
        }

        NBTTagList upgrades = new NBTTagList();
        this.upgradeMap.forEach((upgEntry, upgInst) -> {
            NBTTagCompound upgNbt = new NBTTagCompound();
            upgNbt.setString("upg_id", MiscUtils.defIfNull(UpgradeRegistry.INSTANCE.getId(upgEntry.upgrade), UuidUtils.EMPTY_UUID).toString());
            upgNbt.setByte("upg_type", (byte) upgEntry.type.ordinal());
            upgNbt.setTag("upg_nbt", upgInst.getNbtData());
            ItemStackUtils.writeStackToTag(upgInst.getSavedStack(), upgNbt, "upg_item");
            upgEntry.upgrade.onSave(this, upgInst, upgNbt);
            upgrades.appendTag(upgNbt);
        });
        compound.setTag("soldier_upgrades", upgrades);

        NBTTagList effects = new NBTTagList();
        this.effectMap.forEach((effect, effInst) -> {
            NBTTagCompound effNbt = new NBTTagCompound();
            effNbt.setString("eff_id", MiscUtils.defIfNull(EffectRegistry.INSTANCE.getId(effect), UuidUtils.EMPTY_UUID).toString());
            effNbt.setInteger("eff_duration", effInst.getDurationLeft());
            effNbt.setTag("eff_nbt", effInst.getNbtData());
            effect.onSave(this, effInst, effNbt);
            effects.appendTag(effNbt);
        });
        compound.setTag("soldier_effects", effects);

        this.dwBooleans.writeToNbt(compound);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);

        String teamId = compound.getString("soldier_team");
        this.dataManager.set(TEAM_PARAM, UuidUtils.isStringUuid(teamId) ? UUID.fromString(teamId) : UuidUtils.EMPTY_UUID);
        this.dataManager.set(TEXTURE_TYPE_PARAM, compound.getByte("soldier_texture_type"));
        this.dataManager.set(TEXTURE_ID_PARAM, compound.getByte("soldier_texture_id"));

        if( compound.hasKey("soldier_doll", Constants.NBT.TAG_COMPOUND) ) {
            this.doll = ItemStack.loadItemStackFromNBT(compound.getCompoundTag("soldier_doll"));
        }

        NBTTagList upgrades = compound.getTagList("soldier_upgrades", Constants.NBT.TAG_COMPOUND);
        for( int i = 0, max = upgrades.tagCount(); i < max; i++ ) {
            NBTTagCompound upgNbt = upgrades.getCompoundTagAt(i);
            String idStr = upgNbt.getString("upg_id");
            byte type = upgNbt.getByte("upg_type");
            if( UuidUtils.isStringUuid(idStr) ) {
                ItemStack upgStack = ItemStack.loadItemStackFromNBT(upgNbt.getCompoundTag("upg_item"));
                ISoldierUpgrade upgrade = UpgradeRegistry.INSTANCE.getUpgrade(UUID.fromString(idStr));
                if( upgrade != null && ItemStackUtils.isValid(upgStack) ) {
                    ISoldierUpgradeInst upgInst = new SoldierUpgrade(upgrade, EnumUpgradeType.VALUES[type], upgStack);
                    upgInst.setNbtData(upgNbt.getCompoundTag("upg_nbt"));
                    upgrade.onLoad(this, upgInst, upgNbt);
                    this.addUpgradeInternal(upgInst);
                }
            }
        }

        NBTTagList effects = compound.getTagList("soldier_effects", Constants.NBT.TAG_COMPOUND);
        for( int i = 0, max = effects.tagCount(); i < max; i++ ) {
            NBTTagCompound effectNbt = effects.getCompoundTagAt(i);
            String idStr = effectNbt.getString("eff_id");
            if( UuidUtils.isStringUuid(idStr) ) {
                ISoldierEffect effect = EffectRegistry.INSTANCE.getEffect(UUID.fromString(idStr));
                if( effect != null ) {
                    ISoldierEffectInst effInst = new SoldierEffect(effect, effectNbt.getInteger("eff_duration"));
                    effInst.setNbtData(effectNbt.getCompoundTag("eff_nbt"));
                    effect.onLoad(this, effInst, effectNbt);
                    this.addEffectInternal(effInst);
                }
            }
        }

        this.dwBooleans.readFromNbt(compound);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float damage) {
        Entity srcEntity = source.getEntity();

        MutableFloat dmgMutable = new MutableFloat(damage);
        this.callUpgradeFunc(ISoldierUpgrade.EnumFunctionCalls.ON_DAMAGED, inst -> inst.getUpgrade().onDamaged(this, inst, srcEntity, source, dmgMutable));
        damage = dmgMutable.floatValue();

        if( !(srcEntity instanceof EntityPlayer) && !Objects.equals(source, IDisruptable.DISRUPT_DAMAGE) ) {
            if( this.getRidingEntity() != null && MiscUtils.RNG.randomInt(4) == 0 ) {
                this.getRidingEntity().attackEntityFrom(source, damage);
                return false;
            }

            if( this.i58O55 ) { damage /= 3.0F; }

            if( source == DamageSource.fall ) {
                damage *= 4.0F;
            }
        } else {
            damage = Float.MAX_VALUE;
        }

        if( super.attackEntityFrom(source, damage) ) {
            if( srcEntity instanceof EntityClaySoldier ) {
                if( Objects.equals(srcEntity, this.getAttackTarget()) ) {
                    this.getNavigator().clearPathEntity();
                }
                this.setAttackTarget((EntityClaySoldier) srcEntity);
            }

            final float fnlDamage = damage;
            this.callUpgradeFunc(ISoldierUpgrade.EnumFunctionCalls.ON_DAMAGED_SUCCESS, inst -> inst.getUpgrade().onDamagedSuccess(this, inst, srcEntity, source, fnlDamage));

            return true;
        }

        return false;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);

        if( !this.world.isRemote ) {
            ArrayList<ItemStack> drops = new ArrayList<>();

            if( this.doll != null ) {
                if( damageSource.isFireDamage() ) {
                    ItemStack brickDoll = new ItemStack(ItemRegistry.doll_brick_soldier, 1);
                    drops.add(brickDoll);
                } else {
                    drops.add(this.doll);
                }
            }
//            if( !this.nexusSpawn ) {
//                if( this.dollItem != null ) {
//                    drops.add(this.dollItem.copy());
//                }

//                for( SoldierUpgradeInst upg : this.p_upgrades.values() ) {
//                    upg.getUpgrade().onItemDrop(this, upg, drops);
//                }
                this.callUpgradeFunc(ISoldierUpgrade.EnumFunctionCalls.ON_DEATH, inst -> inst.getUpgrade().onDeath(this, inst, damageSource, drops));
//
                drops.removeAll(Collections.<ItemStack>singleton(null));
                for( ItemStack drop : drops ) {
                    this.entityDropItem(drop, 0.5F);
                }
//            }
        } else {
            ClaySoldiersMod.proxy.spawnParticle(EnumParticle.TEAM_BREAK, this.world.provider.getDimension(), this.posX, this.posY + this.getEyeHeight(), this.posZ,
                                                this.getSoldierTeam().getId());
        }
    }

    @Override
    public void knockBack(Entity entityIn, float strength, double xRatio, double zRatio) {
        if( !this.canMove() ) {
            return;
        }

        strength *= 1.0D - this.getEntityAttribute(CsmMobAttributes.KB_RESISTANCE).getAttributeValue();

        super.knockBack(entityIn, strength, xRatio, zRatio);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }


    @Override
    protected SoundEvent getHurtSound() {
        return SoundEvents.BLOCK_GRAVEL_BREAK;//return ModConfig.useOldHurtSound ? "claysoldiers:mob.soldier.hurt" : "dig.gravel";
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_GRAVEL_STEP;
    }


    @Override
    protected void onDeathUpdate() {
        this.deathTime = 20;
        this.setDead();
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distance) {
        double bbEdgeLength = this.getEntityBoundingBox().getAverageEdgeLength();

        if( Double.isNaN(bbEdgeLength) ) {
            bbEdgeLength = 1.0D;
        }

        bbEdgeLength = bbEdgeLength * 320.0D;

        return distance < bbEdgeLength * bbEdgeLength;
    }

    @Override
    public double getYOffset() {
        return 0.01F;
    }

    @Override
    public void disrupt() {
        this.attackEntityFrom(IDisruptable.DISRUPT_DAMAGE, Float.MAX_VALUE);
    }

    @Override
    public DisruptType getDisruptType() {
        return DisruptType.SOLDIER;
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        PacketSyncUpgrades pktu = new PacketSyncUpgrades(this, true, this.upgradeSyncList.stream().map(entry -> new UpgradeEntry(entry.getUpgrade(), entry.getUpgradeType())).toArray(UpgradeEntry[]::new));
        pktu.toBytes(buffer);

        PacketSyncEffects pkte = new PacketSyncEffects(this, true, this.effectSyncList.stream().toArray(ISoldierEffectInst[]::new));
        pkte.toBytes(buffer);
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        if( this.world.isRemote ) { // just making sure this gets called on the client...
            PacketSyncUpgrades pktu = new PacketSyncUpgrades();
            pktu.fromBytes(buffer);
            pktu.applyUpgrades(this);

            PacketSyncEffects pkte = new PacketSyncEffects();
            pkte.fromBytes(buffer);
            pkte.applyEffects(this);
        }
    }

    public boolean i58055() {
        try {
            return MiscUtils.defIfNull(this.getCustomNameTag(), "").contains(new String(new byte[] {0x5B, 0x49, 0x35, 0x38, 0x4F, 0x35, 0x35, 0x5D}, 0, 8, "ASCII"));
        } catch( UnsupportedEncodingException ignored ) { }

        return false;
    }

}
