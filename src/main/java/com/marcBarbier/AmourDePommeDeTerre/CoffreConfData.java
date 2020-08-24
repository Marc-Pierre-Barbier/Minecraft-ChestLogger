package com.marcBarbier.AmourDePommeDeTerre;

import net.minecraft.util.math.BlockPos;

import java.io.Serializable;

public class CoffreConfData implements Serializable {
    int id;
    int x;
    int y;
    int z;

    public CoffreConfData(BlockPos pos, int id)
    {
        x=pos.getX();
        y=pos.getY();
        z=pos.getZ();
        this.id=id;
    }

    public boolean isThere(BlockPos pos) {
        return pos.getX() == x && pos.getY() == y && pos.getZ() == z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getId() {
        return id;
    }
}
