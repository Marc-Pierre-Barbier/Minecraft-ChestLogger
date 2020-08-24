package com.marcBarbier.AmourDePommeDeTerre;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class saveData {
    private static final String FICHIERCOFFRES = "config/coffres.db";
    private static final String FICHIERITEMS = "config/items.db";

    private static List<CoffreConfData> coffres;

    public saveData() {
        File items = new File(FICHIERITEMS);
        File coffres = new File(FICHIERCOFFRES);
        try {
            if (!items.exists() || !coffres.exists()) {
                //on supprime tout si il manque un des deux fichier
                new FileWriter(items).close();
                new FileWriter(coffres).close();
                this.coffres = new ArrayList<>();
            } else {
                try {
                    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(coffres));
                    this.coffres = (List<CoffreConfData>) ois.readObject();
                } catch (ClassNotFoundException | EOFException e) {
                    new FileWriter(coffres).close();
                    new FileWriter(items).close();
                    this.coffres = new ArrayList<>();
                }
            }
        }catch (IOException e){
            System.err.println("ERROR MOD NOT PROPERLY INITIALIZED");
        }

        if(coffres == null)
            System.err.println("ERROR MOD NOT PROPERLY INITIALIZED");
    }


    public static void fetch(BlockPos pos, Direction doubleChest, PlayerEntity p, World w) {
        if(true) {
            File items = new File(FICHIERITEMS);
            CoffreConfData chest = null;
            CoffreConfData chestb = null;

            //on cherche le coffre dans la liste
            for (CoffreConfData c : coffres) {
                if ((c.isThere(pos))) {
                    chest = c;
                } else if (doubleChest != null && c.isThere(pos.offset(doubleChest))) {
                    chestb = c;
                }
            }

            if (chest == null) {
                p.sendMessage(new StringTextComponent("this chest isn't curently logged"), p.getUniqueID());
                return;
            }

            List<ItemConfData> readlist;
            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(items));
                readlist = (List<ItemConfData>) ois.readObject();

                p.sendMessage(new StringTextComponent("==================================================="),p.getUniqueID());
                for (ItemConfData item : readlist) {
                    if (item.getCoffreID() == chest.getId() || (chestb != null && item.getCoffreID() == chestb.getId())) {
                        StringBuilder str = new StringBuilder();
                        if (item.getQuantite() > 0) {
                            str.append("taken " + item.getQuantite() + "x" + item.getItemName() + " from");
                        } else {
                            str.append("placed " + (-item.getQuantite()) + "x" + item.getItemName() + " in");
                        }
                        str.append(" container by " + item.getPlayerName());

                        p.sendMessage(new StringTextComponent(str.toString()),p.getUniqueID());
                    }
                }
                p.sendMessage(new StringTextComponent("==================================================="),p.getUniqueID());

                ois.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }else{
            //TODO receive info from client
        }
    }

    public void saveTransaction(int[] itemsQuantity, List<String> itemsName, BlockPos pos,String player){
        int coffreID = -1;

        for(CoffreConfData c : coffres){
            if(c.isThere(pos)){
                coffreID = c.getId();
                break;
            }
        }
        if(coffreID == -1){
            coffreID = coffres.size();
            coffres.add(new CoffreConfData(pos,coffreID));
        }

        List<ItemConfData> listItems = new ArrayList<>();
        int index=0;
        for(String name : itemsName){
            if(itemsQuantity[index] != 0){
                listItems.add(new ItemConfData(coffreID,itemsQuantity[index],name,player));
            }
            index++;
        }

        saveStream(listItems,coffres);
    }

    private void saveStream(List<ItemConfData> listItems, List<CoffreConfData> listeCoffres) {
        File items = new File(FICHIERITEMS);
        File coffres = new File(FICHIERCOFFRES);

        try {
            List<ItemConfData> readList;
            ObjectInputStream ois = null;
            try{
                ois = new ObjectInputStream((new FileInputStream(items)));
                readList = (List<ItemConfData>)ois.readObject();
                readList.addAll(listItems);
                ois.close();
            }catch (EOFException e){
                readList = listItems;
                if(ois != null)ois.close();
            }

            new FileWriter(items).close();

            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(items));
            oos.writeObject(readList);
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }catch (ClassNotFoundException e){
            e.printStackTrace();
        }

        try {
            new FileWriter(coffres).close();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(coffres));
            oos.writeObject(listeCoffres);
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
