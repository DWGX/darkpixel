package com.darkpixel.rank;

public class RankGroup {
    private String name;
    private String color;
    private String emoji;
    private String badge;

    public RankGroup(String name, String color, String emoji, String badge) {
        this.name = name;
        this.color = color;
        this.emoji = emoji;
        this.badge = badge;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }
}