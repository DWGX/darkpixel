package com.darkpixel.rank;

public class RankGroup {
    private final String name;
    private final String color;
    private final String emoji;
    private final String badge;
    private final String prefix;

    public RankGroup(String name, String color, String emoji, String badge, String prefix) {
        this.name = name;
        this.color = color;
        this.emoji = emoji;
        this.badge = badge;
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getBadge() {
        return badge;
    }

    public String getPrefix() {
        return prefix;
    }
}