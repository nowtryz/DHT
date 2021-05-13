package project.protocol;


import lombok.Value;

import java.util.UUID;

public interface RoutablePacket extends Packet {
    int getSenderAddress();
    UUID getSender();
    UUID getTarget();

    @Value
    class MessagePacket implements RoutablePacket {
        int senderAddress;
        UUID sender;
        UUID target;
        String message;
    }

    @Value
    class UndeliverableRoutablePacket implements RoutablePacket {
        int senderAddress;
        UUID sender;
        UUID target;
        String reason;
        RoutablePacket originalPacket;
    }
}
