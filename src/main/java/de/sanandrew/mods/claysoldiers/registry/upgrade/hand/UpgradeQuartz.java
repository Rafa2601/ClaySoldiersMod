/* ******************************************************************************************************************
   * Authors:   SanAndreasP
   * Copyright: SanAndreasP
   * License:   Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
   *                http://creativecommons.org/licenses/by-nc-sa/4.0/
   *******************************************************************************************************************/
package de.sanandrew.mods.claysoldiers.registry.upgrade.hand;

import de.sanandrew.mods.claysoldiers.api.soldier.ISoldier;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.EnumUpgradeType;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.ISoldierUpgrade;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.ISoldierUpgradeInst;
import de.sanandrew.mods.claysoldiers.util.ClaySoldiersMod;
import de.sanandrew.mods.claysoldiers.util.EnumParticle;
import de.sanandrew.mods.sanlib.lib.util.MiscUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Predicate;

public class UpgradeQuartz
        implements ISoldierUpgrade
{
    private static final ItemStack[] UPG_ITEMS = { new ItemStack(Items.QUARTZ, 1) };
    private static final EnumFunctionCalls[] FUNC_CALLS = new EnumFunctionCalls[] { EnumFunctionCalls.ON_DAMAGED_SUCCESS,
                                                                                    EnumFunctionCalls.ON_DEATH };
    private static final byte MAX_USES = 4;

    @Override
    @Nonnull
    public ItemStack[] getStacks() {
        return UPG_ITEMS;
    }

    @Override
    @Nonnull
    public EnumFunctionCalls[] getFunctionCalls() {
        return FUNC_CALLS;
    }

    @Nonnull
    @Override
    public EnumUpgradeType getType(ISoldier<?> checker) {
        return EnumUpgradeType.OFF_HAND;
    }

    @Override
    public boolean syncData() {
        return true;
    }

    @Override
    public void onAdded(ISoldier<?> soldier, ItemStack stack, ISoldierUpgradeInst upgradeInst) {
        if( !soldier.getEntity().world.isRemote ) {
            upgradeInst.getNbtData().setByte("uses", MAX_USES);
            upgradeInst.getNbtData().setLong("cooldownTime", System.currentTimeMillis());
            upgradeInst.getNbtData().setInteger("hitsTaken", 0);
            soldier.getEntity().playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.2F, ((MiscUtils.RNG.randomFloat() - MiscUtils.RNG.randomFloat()) * 0.7F + 1.0F) * 2.0F);
            stack.stackSize--;
        }
    }

    @Override
    public void onDamagedSuccess(ISoldier<?> soldier, ISoldierUpgradeInst upgradeInst, Entity attacker, DamageSource dmgSource, float damage) {
        int hitsTaken = upgradeInst.getNbtData().getInteger("hitsTaken") + 1;
        long lastCooldown = upgradeInst.getNbtData().getLong("cooldownTime");

        upgradeInst.getNbtData().setLong("cooldownTime", System.currentTimeMillis());
        if( System.currentTimeMillis() - lastCooldown < 2_000 ) {
            if( hitsTaken >= 5 ) {
                EntityCreature john = soldier.getEntity();
                hitsTaken = 0;
                byte uses = (byte) (upgradeInst.getNbtData().getByte("uses") - 1);
                if( uses < 1 ) {
                    soldier.destroyUpgrade(upgradeInst.getUpgrade(), upgradeInst.getUpgradeType(), false);
                    john.playSound(SoundEvents.ENTITY_ITEM_BREAK, 0.8F, 0.8F + MiscUtils.RNG.randomFloat() * 0.4F);
                } else {
                    upgradeInst.getNbtData().setByte("uses", uses);
                }

                AxisAlignedBB surroundingBB = john.getEntityBoundingBox().expandXyz(1.0D);
                Predicate<EntityCreature> entityChk = entity -> entity instanceof ISoldier && entity != john;
                soldier.getEntity().world.getEntitiesWithinAABB(EntityCreature.class, surroundingBB, entityChk::test)
                                         .forEach(entity -> entity.knockBack(john, 0.5F, john.posX - entity.posX, john.posZ - entity.posZ));

                ClaySoldiersMod.proxy.spawnParticle(EnumParticle.SHOCKWAVE, john.world.provider.getDimension(), john.posX, john.posY, john.posZ);
            }

            upgradeInst.getNbtData().setInteger("hitsTaken", hitsTaken);
        }
    }

    @Override
    public void onDeath(ISoldier<?> soldier, ISoldierUpgradeInst upgradeInst, DamageSource dmgSource, List<ItemStack> drops) {
        if( upgradeInst.getNbtData().getByte("uses") >= MAX_USES ) {
            drops.add(upgradeInst.getSavedStack());
        }
    }
}
