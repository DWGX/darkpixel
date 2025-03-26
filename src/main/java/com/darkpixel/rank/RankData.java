package com.darkpixel.rank;

import org.bukkit.Particle;

public class RankData {
    private String rank;
    private int score;
    private Particle joinParticle;
    private String joinMessage;

    public RankData(String rank, int score) {
        this.rank = rank;
        this.score = score;
        this.joinParticle = Particle.FIREWORK;
        this.joinMessage = "欢迎 {player} 加入服务器！";
    }

    public String getRank() {
        return rank;
    }

    public int getScore() {
        return score;
    }

    public Particle getJoinParticle() {
        return joinParticle;
    }

    public void setJoinParticle(Particle joinParticle) {
        this.joinParticle = joinParticle;
    }

    public String getJoinMessage() {
        return joinMessage;
    }

    public void setJoinMessage(String joinMessage) {
        this.joinMessage = joinMessage;
    }
}