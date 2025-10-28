package net.tiramisu.mdp.model;

import androidx.room.ColumnInfo;

public class CategorySum {
    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "total")
    public Double total;

    public CategorySum(String category, Double total) {
        this.category = category;
        this.total = total;
    }
}

