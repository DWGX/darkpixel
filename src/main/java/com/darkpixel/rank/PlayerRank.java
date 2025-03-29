package com.darkpixel.rank;

public class PlayerRank {
    private String name;
    private String rank;
    private int score;
    private long lastModified;
    private long last_sign_in;
    private int sign_in_count;
    private boolean enableEffects;

    public PlayerRank(String name) {
        this.name = name;
        this.rank = "member";
        this.score = 0;
        this.lastModified = System.currentTimeMillis();
        this.last_sign_in = 0;
        this.sign_in_count = 0;
        this.enableEffects = true;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public long getlast_sign_in() { return last_sign_in; }
    public void setlast_sign_in(long last_sign_in) { this.last_sign_in = last_sign_in; }
    public int getsign_in_count() { return sign_in_count; }
    public void setsign_in_count(int sign_in_count) { this.sign_in_count = sign_in_count; }
    public boolean isEnableEffects() { return enableEffects; }
    public void setEnableEffects(boolean enableEffects) { this.enableEffects = enableEffects; }
}