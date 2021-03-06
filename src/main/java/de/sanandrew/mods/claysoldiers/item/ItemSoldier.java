/* ******************************************************************************************************************
   * Authors:   SanAndreasP
   * Copyright: SanAndreasP
   * License:   Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
   *                http://creativecommons.org/licenses/by-nc-sa/4.0/
   *******************************************************************************************************************/
package de.sanandrew.mods.claysoldiers.item;

import de.sanandrew.mods.claysoldiers.api.CsmConstants;
import de.sanandrew.mods.claysoldiers.api.soldier.ITeam;
import de.sanandrew.mods.claysoldiers.entity.EntityClaySoldier;
import de.sanandrew.mods.claysoldiers.registry.TeamRegistry;
import de.sanandrew.mods.claysoldiers.util.CsmCreativeTabs;
import de.sanandrew.mods.sanlib.lib.util.MiscUtils;
import de.sanandrew.mods.sanlib.lib.util.UuidUtils;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockFence;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;
import java.util.stream.Collectors;

public class ItemSoldier
        extends Item
{
    private static final IItemPropertyGetter SOLDIER_TEX_ID = (stack, worldIn, entityIn) -> TeamRegistry.INSTANCE.getTeams().indexOf(TeamRegistry.INSTANCE.getTeam(stack));

    public ItemSoldier() {
        super();
        this.setCreativeTab(CsmCreativeTabs.DOLLS);
        this.setUnlocalizedName(CsmConstants.ID + ":doll_soldier");
        this.addPropertyOverride(new ResourceLocation("soldierTeamId"), SOLDIER_TEX_ID);
        this.setMaxDamage(0);
        this.setHasSubtypes(true);
        this.maxStackSize = 16;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.addAll(TeamRegistry.INSTANCE.getTeams().stream().map(team -> TeamRegistry.INSTANCE.setTeam(new ItemStack(this, 1), team)).collect(Collectors.toList()));
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName(stack) + '.' + TeamRegistry.INSTANCE.getTeam(stack).getName();
    }

    @Override
    public EnumActionResult onItemUse(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if( world.isRemote ) {
            return EnumActionResult.SUCCESS;
        } else if( !player.canPlayerEdit(pos.offset(facing), facing, stack) ) {
            return EnumActionResult.FAIL;
        } else if( world.getBlockState(pos).getBlock() == Blocks.CAULDRON && hand != null ) {
            if( !player.isSneaking() && !UuidUtils.areUuidsEqual(TeamRegistry.INSTANCE.getTeam(stack).getId(), TeamRegistry.SOLDIER_CLAY) ) {
                IBlockState state = world.getBlockState(pos);
                int level = state.getValue(BlockCauldron.LEVEL);
                if( level > 0 ) {
                    player.setHeldItem(hand, TeamRegistry.INSTANCE.setTeam(stack.copy(), TeamRegistry.SOLDIER_CLAY));
                    player.inventoryContainer.detectAndSendChanges();

                    player.addStat(StatList.CAULDRON_USED);
                    Blocks.CAULDRON.setWaterLevel(world, pos, state, level - 1);

                    return EnumActionResult.SUCCESS;
                }
            }

            return EnumActionResult.FAIL;
        } else {
            IBlockState iblockstate = world.getBlockState(pos);

            pos = pos.offset(facing);
            double yShift = 0.0D;

            if( facing == EnumFacing.UP && iblockstate.getBlock() instanceof BlockFence ) {
                yShift = 0.5D;
            }

            EntityClaySoldier[] soldiers = spawnSoldiers(world, TeamRegistry.INSTANCE.getTeam(stack), player.isSneaking() ? 1 : stack.stackSize,
                                                         pos.getX() + 0.5D, pos.getY() + yShift, pos.getZ() + 0.4D + MiscUtils.RNG.randomFloat() * 0.2D, stack);

            for( EntityClaySoldier james : soldiers ) {
                if( james != null ) {
                    if( stack.hasDisplayName() ) {
                        james.setCustomNameTag(stack.getDisplayName());
                    }

                    --stack.stackSize;
                }
            }

            if( hand != null && player.capabilities.isCreativeMode ) {
                if( stack.stackSize < 1 ) {
                    player.setHeldItem(hand, null);
                } else {
                    player.setHeldItem(hand, stack.copy());
                }

                player.inventoryContainer.detectAndSendChanges();
            }

            return EnumActionResult.SUCCESS;
        }
    }

    public static EntityClaySoldier[] spawnSoldiers(World world, ITeam team, final int count, double x, double y, double z, ItemStack dollStack) {
        if( team != TeamRegistry.NULL_TEAM ) {
            EntityClaySoldier[] soldiers = new EntityClaySoldier[count];

            for( int i = 0; i < count; i++ ) {
                double xs = x - 0.1D + MiscUtils.RNG.randomFloat() * 0.02D;
                double zs = z - 0.1D + MiscUtils.RNG.randomFloat() * 0.02D;

                ItemStack newDollStack = null;
                if( dollStack != null ) {
                    newDollStack = dollStack.copy();
                    newDollStack.stackSize = 1;
                }
                EntityClaySoldier aleks = new EntityClaySoldier(world, team, newDollStack);
                aleks.setLocationAndAngles(xs, y, zs, MathHelper.wrapDegrees(world.rand.nextFloat() * 360.0F), 0.0F);
                aleks.rotationYawHead = aleks.rotationYaw;
                aleks.renderYawOffset = aleks.rotationYaw;
                aleks.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(aleks)), null);
                world.spawnEntityInWorld(aleks);
                aleks.playLivingSound();

                soldiers[i] = aleks;
            }

            return soldiers;
        } else {
            return new EntityClaySoldier[0];
        }
    }
}
