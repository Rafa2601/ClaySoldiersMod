/* ******************************************************************************************************************
   * Authors:   SanAndreasP
   * Copyright: SanAndreasP
   * License:   Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
   *                http://creativecommons.org/licenses/by-nc-sa/4.0/
   *******************************************************************************************************************/
package de.sanandrew.mods.claysoldiers.api.soldier;

import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.EnumUpgradeType;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.ISoldierUpgrade;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.ISoldierUpgradeInst;
import net.minecraft.entity.EntityCreature;
import net.minecraft.item.ItemStack;

import java.util.UUID;

public interface ISoldier<T extends EntityCreature & ISoldier<T>>
{
    boolean canMove();

    void setMovable(boolean move);

    void setBreathableUnderwater(boolean breathable);

    Team getSoldierTeam();

    T getEntity();

    int getTextureType();

    int getTextureId();

    void setNormalTextureId(byte id);

    void setRareTextureId(byte id);

    void setUniqueTextureId(byte id);

    void destroyUpgrade(ISoldierUpgrade upgrade, EnumUpgradeType type, boolean silent);

    ISoldierUpgradeInst addUpgrade(ISoldierUpgrade upgrade, EnumUpgradeType type, ItemStack stack);

    ISoldierUpgradeInst getUpgradeInstance(UUID upgradeId, EnumUpgradeType type);

    ISoldierUpgradeInst getUpgradeInstance(ISoldierUpgrade upgrade, EnumUpgradeType type);

    boolean hasUpgrade(ItemStack stack, EnumUpgradeType type);

    boolean hasUpgrade(UUID id, EnumUpgradeType type);

    boolean hasUpgrade(ISoldierUpgrade upgrade, EnumUpgradeType type);

    boolean hasMainHandUpgrade();

    boolean hasOffHandUpgrade();
}
