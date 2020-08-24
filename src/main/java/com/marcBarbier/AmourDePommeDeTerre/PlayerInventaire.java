package com.marcBarbier.AmourDePommeDeTerre;

import java.util.List;

public class PlayerInventaire {
    protected String PlayerName;
    protected List<String> itemsName;
    protected int[] itemsQuantity;

    public PlayerInventaire(String playerName, List<String> itemNames, int[] itemsQuantity)
    {
        this.PlayerName = playerName;
        this.itemsQuantity = itemsQuantity;
        this.itemsName = itemNames;
    }
}
