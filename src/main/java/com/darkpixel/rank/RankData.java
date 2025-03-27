package com.darkpixel.rank;

import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.List;

public class RankData {
    String rank;
    int score;
    private Particle joinParticle;
    private String joinMessage;
    private String chatColor;
    private boolean showRank;
    private boolean showVip;
    private boolean showGroup;
    private long banUntil;
    private String banReason;
    private List<String> groups;

    public RankData(String rank, int score) {
        this.rank = rank;
        this.score = score;
        this.joinParticle = Particle.FIREWORK;
        this.joinMessage = "欢迎 {player} 加入服务器！";
        this.chatColor = "normal";
        this.showRank = false; // 默认不展示Rank
        this.showVip = false;
        this.showGroup = true; // 默认展示身份组
        this.banUntil = 0;
        this.banReason = null;
        this.groups = new ArrayList<>();
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
    public boolean isShowVip() { return showVip; }
    public void setShowVip(boolean showVip) { this.showVip = showVip; }
    public boolean isShowGroup() { return showGroup; }
    public void setShowGroup(boolean showGroup) { this.showGroup = showGroup; }
    public long getBanUntil() { return banUntil; }
    public void setBanUntil(long banUntil) { this.banUntil = banUntil; }
    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; }
    public List<String> getGroups() { return groups; }
    public void setGroups(List<String> groups) { this.groups = groups; }
}