package net.tiramisu.mdp.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class TransactionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String userId;

    public String type; // "expense" or "income"
    public double amount;
    public String note;
    public String category;
    public long timestamp;

    public TransactionEntity(@NonNull String userId, String type, double amount, String note, String category, long timestamp) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.note = note;
        this.category = category;
        this.timestamp = timestamp;
    }
}

