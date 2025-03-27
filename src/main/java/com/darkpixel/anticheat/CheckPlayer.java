package com.darkpixel.anticheat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.entity.Player;

public class CheckPlayer {
    private final Player player;
    private User user;
    private Location previousLocation;
    private PacketReceiveEvent previousPacketEvent;
    private float yaw, pitch, deltaYaw, previousDeltaYaw;
    private boolean sprinting, sneaking;
    private int previousSlot, currentSlot;

    public CheckPlayer(Player player) {
        this.player = player;
    }

    public void update(PacketReceiveEvent event) {
        this.user = event.getUser();
        this.previousPacketEvent = event;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            if (flying.hasRotationChanged()) {
                this.previousLocation = flying.getLocation();
                this.yaw = flying.getLocation().getYaw();
                this.pitch = flying.getLocation().getPitch();
                this.previousDeltaYaw = this.deltaYaw;
                this.deltaYaw = Math.abs(this.yaw - (this.previousLocation != null ? this.previousLocation.getYaw() : this.yaw));
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            WrapperPlayClientEntityAction action = new WrapperPlayClientEntityAction(event);
            if (action.getAction() == WrapperPlayClientEntityAction.Action.START_SPRINTING) {
                this.sprinting = true;
            } else if (action.getAction() == WrapperPlayClientEntityAction.Action.STOP_SPRINTING) {
                this.sprinting = false;
            } else if (action.getAction() == WrapperPlayClientEntityAction.Action.START_SNEAKING) {
                this.sneaking = true;
            } else if (action.getAction() == WrapperPlayClientEntityAction.Action.STOP_SNEAKING) {
                this.sneaking = false;
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            WrapperPlayClientHeldItemChange heldItemChange = new WrapperPlayClientHeldItemChange(event);
            this.previousSlot = this.currentSlot;
            this.currentSlot = heldItemChange.getSlot();
        }
    }

    public Player getPlayer() {
        return player;
    }

    public User getUser() {
        return user;
    }

    public Location getPreviousLocation() {
        return previousLocation;
    }

    public PacketReceiveEvent getPreviousPacketEvent() {
        return previousPacketEvent;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getDeltaYaw() {
        return deltaYaw;
    }

    public float getPreviousDeltaYaw() {
        return previousDeltaYaw;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public int getPreviousSlot() {
        return previousSlot;
    }

    public int getCurrentSlot() {
        return currentSlot;
    }
}