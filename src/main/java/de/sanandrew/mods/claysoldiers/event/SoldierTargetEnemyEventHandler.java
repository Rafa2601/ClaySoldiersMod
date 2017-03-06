/* ******************************************************************************************************************
   * Authors:   SanAndreasP
   * Copyright: SanAndreasP
   * License:   Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
   *                http://creativecommons.org/licenses/by-nc-sa/4.0/
   *******************************************************************************************************************/
package de.sanandrew.mods.claysoldiers.event;

import de.sanandrew.mods.claysoldiers.api.event.SoldierTargetEnemyEvent;
import de.sanandrew.mods.claysoldiers.api.soldier.upgrade.EnumUpgradeType;
import de.sanandrew.mods.claysoldiers.entity.EntityClaySoldier;
import de.sanandrew.mods.claysoldiers.registry.upgrade.UpgradeRegistry;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class SoldierTargetEnemyEventHandler
{
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTargeting(SoldierTargetEnemyEvent evt) {
        if( evt.target instanceof EntityClaySoldier && ((EntityClaySoldier) evt.target).hasUpgrade(UpgradeRegistry.MC_EGG, EnumUpgradeType.MISC) ) {
            evt.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onTargeting2(SoldierTargetEnemyEvent evt) {
        if( evt.attacker.hasUpgrade(UpgradeRegistry.MC_GLASS, EnumUpgradeType.MISC) ) {
            evt.setResult(Event.Result.DEFAULT);
        }
    }
}
