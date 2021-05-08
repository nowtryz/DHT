package project.protocol;

public class SwitchNeighborPacket implements Packet {
    public static final boolean LEFT = true;
    public static final boolean RIGHT = false;

    public final boolean left;
    public final int address;

    public SwitchNeighborPacket(boolean left, int address) {
        this.left = left;
        this.address = address;
    }
}
