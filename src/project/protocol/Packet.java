package project.protocol;

import lombok.Value;

import java.util.UUID;

public interface Packet {
    @Value
    class DiscoveryPacket implements Packet {
        int address;
        UUID nodeId;
    }

    @Value
    class WelcomePacket implements Packet {
        int left;
        int right;
    }

    @Value
    class SwitchNeighborPacket implements Packet {
        public static final boolean LEFT = true;
        public static final boolean RIGHT = false;

        boolean left;
        int address;
    }

    @Value
    class MessagePacket implements Packet {
        Object message;
    }
}
