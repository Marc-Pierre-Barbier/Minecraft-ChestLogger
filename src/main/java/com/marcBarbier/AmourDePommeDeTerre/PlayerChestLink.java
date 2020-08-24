package com.marcBarbier.AmourDePommeDeTerre;

import net.minecraft.util.math.BlockPos;

public class PlayerChestLink {
    public String pName;
    public BlockPos chestPos;

    public PlayerChestLink(String pName , BlockPos pos){
        this.pName = pName;
        this.chestPos = pos;
    }
}
