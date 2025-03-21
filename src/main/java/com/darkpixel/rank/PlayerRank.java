package com.darkpixel.rank;

public class PlayerRank {
    private String name;
    private String rank;
    private int score;
    private long lastModified;
    private long lastSignIn;
    private int signInCount;
    private boolean enableEffects;

    public PlayerRank(String name) {
        this.name = name;
        this.rank = "member";
        this.score = 0;
        this.lastModified = System.currentTimeMillis();
        this.lastSignIn = 0;
        this.signInCount = 0;
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
    public long getLastSignIn() { return lastSignIn; }
    public void setLastSignIn(long lastSignIn) { this.lastSignIn = lastSignIn; }
    public int getSignInCount() { return signInCount; }
    public void setSignInCount(int signInCount) { this.signInCount = signInCount; }
    public boolean isEnableEffects() { return enableEffects; }
    public void setEnableEffects(boolean enableEffects) { this.enableEffects = enableEffects; }
}