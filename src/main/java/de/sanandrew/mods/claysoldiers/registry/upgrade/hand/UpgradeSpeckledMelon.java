/* ******************************************************************************************************************
   * Authors:   SanAndreasP
   * Copyright: SanAndreasP
   * License:   Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
   *                http://creativecommons.org/licenses/by-nc-sa/4.0/
   *******************************************************************************************************************/
package de.sanandrew.mods.claysoldiers.registry.upgrade.hand;

import de.sanandrew.mods.claysoldiers.api.CsmConstants;
import de.sanandrew.mods.claysoldiers.api.soldier.ISoldier;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.EnumUpgradeType;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.ISoldierUpgrade;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.ISoldierUpgradeInst;
import de.sanandrew.mods.claysoldiers.entity.ai.attributes.AttributeModifierRnd;
import de.sanandrew.mods.claysoldiers.network.packet.PacketParticle;
import de.sanandrew.mods.claysoldiers.registry.upgrade.UpgradeRegistry;
import de.sanandrew.mods.claysoldiers.util.ClaySoldiersMod;
import de.sanandrew.mods.claysoldiers.util.EnumParticle;
import de.sanandrew.mods.sanlib.lib.util.MiscUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import org.apache.commons.lang3.mutable.MutableFloat;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class UpgradeSpeckledMelon
        implements ISoldierUpgrade
{
    private static final ItemStack[] UPG_ITEMS = { new ItemStack(Items.SPECKLED_MELON, 1) };
    private static final EnumFunctionCalls[] FUNC_CALLS = new EnumFunctionCalls[] { EnumFunctionCalls.ON_ATTACK,
                                                                                    EnumFunctionCalls.ON_DAMAGED_SUCCESS,
                                                                                    EnumFunctionCalls.ON_DEATH };
    private static final byte MAX_USES = 20;

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
        return EnumUpgradeType.MAIN_HAND;
    }

    @Override
    public boolean syncData() {
        return true;
    }

    @Override
    public void onAdded(ISoldier<?> soldier, ItemStack stack, ISoldierUpgradeInst upgradeInst) {
        if( !soldier.getEntity().world.isRemote ) {
            upgradeInst.getNbtData().setByte("uses", MAX_USES);
            soldier.getEntity().playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.2F, ((MiscUtils.RNG.randomFloat() - MiscUtils.RNG.randomFloat()) * 0.7F + 1.0F) * 2.0F);
            stack.stackSize--;
        }
    }

    @Override
    public void onAttack(ISoldier<?> soldier, ISoldierUpgradeInst upgradeInst, Entity target, DamageSource dmgSource, float damage) {
        if( target instanceof EntityLivingBase ) {
            EntityLivingBase quinn = (EntityLivingBase) target;
            if( quinn.getHealth() < quinn.getMaxHealth() * 0.25F ) {
                byte uses = (byte) (upgradeInst.getNbtData().getByte("uses") - 1);
                if( uses < 1 ) {
                    soldier.destroyUpgrade(upgradeInst.getUpgrade(), upgradeInst.getUpgradeType(), false);
                    soldier.getEntity().playSound(SoundEvents.ENTITY_ITEM_BREAK, 0.8F, 0.8F + MiscUtils.RNG.randomFloat() * 0.4F);
                } else {
                    upgradeInst.getNbtData().setByte("uses", uses);
                }

                quinn.heal(15.0F);
                ClaySoldiersMod.proxy.spawnParticle(EnumParticle.HEARTS, target.world.provider.getDimension(), target.posX, target.posY, target.posZ);
            }
        }

        soldier.getEntity().setAttackTarget(null);
    }

    @Override
    public void onDamagedSuccess(ISoldier<?> soldier, ISoldierUpgradeInst upgradeInst, Entity attacker, DamageSource dmgSource, float damage) {
        if( soldier.hasUpgrade(UpgradeRegistry.MH_SPECKLEDMELON, EnumUpgradeType.MAIN_HAND) && soldier.getEntity().getAttackTarget() instanceof ISoldier ) {
            ISoldier tgt = (ISoldier) soldier.getEntity().getAttackTarget();
            if( tgt.getSoldierTeam() != soldier.getSoldierTeam() ){
                soldier.getEntity().setAttackTarget(null);
            }
        }
    }

    @Override
    public void onDeath(ISoldier<?> soldier, ISoldierUpgradeInst upgradeInst, DamageSource dmgSource, List<ItemStack> drops) {
        if( upgradeInst.getNbtData().getByte("uses") >= MAX_USES ) {
            drops.add(upgradeInst.getSavedStack());
        }
    }
}
