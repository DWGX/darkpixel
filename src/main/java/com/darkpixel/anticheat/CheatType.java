package com.darkpixel.anticheat;

public enum CheatType {
    FAST_HEAD_ROTATION("Fast Head Rotation"),
    HIGH_CPS("High Click Speed"),
    INVALID_MOVEMENT("Invalid Movement"),
    VERTICAL_TELEPORT("Vertical Teleport"),
    BLINK("Blink Detected"),
    KILLAURA("KillAura Detected"),
    REACH("Reach Hack Detected"),
    AUTO_CLICKER("Auto Clicker Detected"),
    FLY_HACK("Fly Hack Detected"),
    SPEED_HACK("Speed Hack Detected");

    private final String name;

    CheatType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}