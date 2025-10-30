package net.tiramisu.mdp;

public class Transaction {
    public final String title;
    public final String date; // short date string
    public final double amount; // positive for income, negative for expense
    public final int iconResId;
    // optional extra long for sorting (timestamp)
    public long extraLong = 0L;
    // optional fields for detail view
    public String category;
    public String note;

    public Transaction(String title, String date, double amount, int iconResId) {
        this.title = title;
        this.date = date;
        this.amount = amount;
        this.iconResId = iconResId;
    }
}
