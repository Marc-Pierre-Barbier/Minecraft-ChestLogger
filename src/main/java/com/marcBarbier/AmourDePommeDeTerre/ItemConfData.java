package com.marcBarbier.AmourDePommeDeTerre;

import java.io.Serializable;

public class ItemConfData implements Serializable {
    private int coffreID;
    private int quantite;
    private String itemName;
    private String playerName;

    public ItemConfData(int coffreID, int quantite, String itemName, String playerName)
    {
        this.coffreID=coffreID;
        this.quantite=quantite;
        this.itemName=itemName;
        this.playerName = playerName;
    }

    public int getCoffreID() {
        return coffreID;
    }

    public int getQuantite() {
        return quantite;
    }

    public String getItemName() {
        return itemName;
    }

    public String getPlayerName() {
        return playerName;
    }
}
