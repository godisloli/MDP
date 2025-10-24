package net.tiramisu.mdp;

public class Transaction {
    public final String title;
    public final String date; // short date string
    public final double amount; // positive for income, negative for expense
    public final int iconResId;

    public Transaction(String title, String date, double amount, int iconResId) {
        this.title = title;
        this.date = date;
        this.amount = amount;
        this.iconResId = iconResId;
    }
}

