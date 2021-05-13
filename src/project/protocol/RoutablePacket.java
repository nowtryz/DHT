package project.protocol;


import lombok.Value;

import java.util.UUID;

public interface RoutablePacket extends Packet {
    UUID getSender();
    UUID getTarget();

    @Value
    class MessagePacket implements RoutablePacket {
        UUID sender;
        UUID target;
        String message;
    }

    @Value
    class UndeliverableRoutablePacket implements RoutablePacket {
        UUID sender;
        UUID target;
        String reason;
        RoutablePacket originalPacket;
    }
}
