package com.marcBarbier.AmourDePommeDeTerre;

import net.minecraft.block.ChestBlock;
import net.minecraft.block.ContainerBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class ItemList extends Item {

    private boolean used =false;

    public ItemList(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        BlockPos pos = context.getPos();
        if (used && context.getWorld().getBlockState(pos).getBlock() instanceof ContainerBlock) {
            Direction DoubleChest = detectNeiboursChest(pos, context.getWorld());
            saveData.fetch(pos, DoubleChest, context.getPlayer(), context.getWorld());
            used = false;
            try {
                Thread.sleep(200);//0.2ms
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
ave
        } else {
            used = true;
        }

        return super.onItemUse(context);
    }

    private Direction detectNeiboursChest(BlockPos pos, World world){
        Direction directions[] = {Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
        BlockPos attempt = new BlockPos(pos);
        for(Direction d : directions)
        {
            if(world.getBlockState(attempt.offset(d)).getBlock() instanceof ChestBlock)
            {
                return d;
            }
        }
        return null;
    }
}
