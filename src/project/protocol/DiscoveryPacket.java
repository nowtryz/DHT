package project.protocol;

import java.util.UUID;

// for the sake of simplicity, fields are public
public class DiscoveryPacket implements Packet {
    public final int address;
    public final UUID nodeId;

    public DiscoveryPacket(int address, UUID nodeId) {
        this.address = address;
        this.nodeId = nodeId;
    }
}
