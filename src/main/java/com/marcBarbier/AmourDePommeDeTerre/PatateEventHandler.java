package com.marcBarbier.AmourDePommeDeTerre;

import net.minecraft.block.ContainerBlock;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class PatateEventHandler {

    private List<PlayerInventaire> playersInChest = new ArrayList<>();

    private List<PlayerChestLink> playersandPos = new ArrayList<>();

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(new ItemList(new Item.Properties().group(ItemGroup.MISC)));
    }

    @SubscribeEvent
    public void logChest(PlayerInteractEvent event) {
        //if the clicked block is a container
        if(event.getWorld().getBlockState(event.getPos()).getBlock() instanceof ContainerBlock)
        {
            playersandPos.add(new PlayerChestLink(event.getPlayer().getName().getString(),event.getPos()));
        }
    }

    @SubscribeEvent
    public void test(PlayerContainerEvent event) {

        //make sure we aren't in player inv
        //if(!playersInBlockInventories.contains(event.getPlayer().getName().getString()))return;
        if(event.getContainer().getClass().equals(PlayerContainer.class))return;
        if(event.getContainer().getInventory().size() <= 36){
            System.out.println("invalid container");
            return;
        }

        List<String> itemNames = new ArrayList<>();
        int QuantityItems[] = new int[37]; //36 car depuis un coffre on a pas acces au slot secondaire

        int index = 0;
        for(Slot s : event.getContainer().inventorySlots)
        {
            if(index < 36)
            {
                addItem(s,itemNames,QuantityItems);
            }else{
                //TODO comparer les item du cofre en prenant pour comparaison l'inventaire du joueur
            }

            index++;
        }
        addItem(event.getPlayer().getHeldItemOffhand(),itemNames,QuantityItems);

        if (event instanceof PlayerContainerEvent.Open) {
            playersInChest.add(new PlayerInventaire(event.getPlayer().getDisplayName().getString(),itemNames,QuantityItems));
        }
        else {
            PlayerInventaire player = null;

            //on obtien le joueur
            for (PlayerInventaire p : playersInChest) {
                if (p.PlayerName.equals(event.getPlayer().getDisplayName().getString())) {
                    player = p;
                    break;
                }
            }

            if (player == null) {
                System.err.println("error unregistered player handled");
                return;
            }

            int indexFermeture = 0;
            for (String name : itemNames) {
                int indexOuverture = player.itemsName.indexOf(name);
                if (indexOuverture == -1) {
                    addItem(name, -QuantityItems[indexFermeture], player.itemsName, player.itemsQuantity);
                }else {
                    player.itemsQuantity[indexOuverture] -= QuantityItems[indexFermeture];
                }
                indexFermeture++;
            }

            PlayerChestLink link =null;
            for(PlayerChestLink l : playersandPos)
            {
                if(l.pName.equals(player.PlayerName))
                {
                    link = l;
                }
            }
            if(link == null){
                System.err.println("error null link please report this error");
                return;
            }

            AmourDePommeDeTerre.sql.saveTransaction(player.itemsQuantity,player.itemsName, link.chestPos,event.getPlayer().getDisplayName().getString());
            playersandPos.remove(link);
            playersInChest.remove(player);
        }
    }

    private void addItem(Slot s, List<String> itemNames, int QuantityItems[]) {
        addItem(s.getStack(),itemNames,QuantityItems);
    }
    private void addItem(ItemStack s, List<String> itemNames, int QuantityItems[]){
        addItem(s.getItem().getDisplayName(s).getString(),s.getStack().getCount(),itemNames,QuantityItems);
    }
    private void addItem(String name,int quantite, List<String> itemNames, int QuantityItems[]){
        if(!name.equals("minecraft:air")) {
            if (!itemNames.contains(name)) {
                itemNames.add(name);
                QuantityItems[itemNames.size() - 1] = quantite;
            } else {
                QuantityItems[itemNames.indexOf(name)] = quantite;
            }
        }
    }
}
