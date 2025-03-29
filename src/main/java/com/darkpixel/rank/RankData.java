package com.darkpixel.rank;

import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RankData {
    private String rank;
    private int score;
    private Particle joinParticle;
    private String joinMessage;
    private String chatColor;
    private boolean showRank;
    private boolean showGroup;
    private boolean showScore;
    private long banUntil;
    private String banReason;
    private List<String> groups;
    private List<String> displayOrder;

    public RankData(String rank, int score) {
        this.rank = rank;
        this.score = score;
        this.joinParticle = Particle.FIREWORK;
        this.joinMessage = "欢迎 {player} 加入服务器！";
        this.chatColor = "normal";
        this.showRank = true;
        this.showGroup = true;
        this.showScore = true;
        this.banUntil = 0;
        this.banReason = null;
        this.groups = new ArrayList<>();
        this.displayOrder = new ArrayList<>(Arrays.asList("score", "group", "rank"));
    }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public Particle getJoinParticle() { return joinParticle; }
    public void setJoinParticle(Particle joinParticle) { this.joinParticle = joinParticle; }
    public String getJoinMessage() { return joinMessage; }
    public void setJoinMessage(String joinMessage) { this.joinMessage = joinMessage; }
    public String getChatColor() { return chatColor; }
    public void setChatColor(String chatColor) { this.chatColor = chatColor; }
    public boolean isShowRank() { return showRank; }
    public void setShowRank(boolean showRank) { this.showRank = showRank; }
    public boolean isShowGroup() { return showGroup; }
    public void setShowGroup(boolean showGroup) { this.showGroup = showGroup; }
    public boolean isShowScore() { return showScore; }
    public void setShowScore(boolean showScore) { this.showScore = showScore; }
    public long getBanUntil() { return banUntil; }
    public void setBanUntil(long banUntil) { this.banUntil = banUntil; }
    public String getBanReason() { return banReason; }
    public void setBanReason(String reason) { this.banReason = reason; }
    public List<String> getGroups() { return groups; }
    public void setGroups(List<String> groups) { this.groups = groups; }
    public List<String> getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(List<String> displayOrder) { this.displayOrder = displayOrder; }
}