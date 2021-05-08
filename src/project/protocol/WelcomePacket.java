package project.protocol;

public class WelcomePacket implements Packet {
    public final int left;
    public final int right;

    public WelcomePacket(int left, int right) {
        this.left = left;
        this.right = right;
    }
}
