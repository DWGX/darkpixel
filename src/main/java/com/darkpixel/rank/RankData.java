package com.darkpixel.rank;

public class RankData {
    private String rank;
    private int score;

    public RankData(String rank, int score) {
        this.rank = rank;
        this.score = score;
    }

    public String getRank() {
        return rank;
    }

    public int getScore() {
        return score;
    }
}