package project.protocol;

import lombok.Value;

import java.util.UUID;

public interface ApplicationPacket extends Packet {
    class InitApplication implements ApplicationPacket {}

    @Value
    class PutPacket implements ApplicationPacket {
        Object key;
        Object value;
    }

    @Value
    class ReplicationPacket implements ApplicationPacket {
        Object key;
        Object value;

        public static ReplicationPacket from(PutPacket packet) {
            return new ReplicationPacket(packet.getKey(), packet.getValue());
        }
    }

    @Value
    class GetPacket implements ApplicationPacket {
        UUID sender;
        Object key;
    }

    @Value
    class GetResponsePacket implements ApplicationPacket, RoutablePacket {
        int senderAddress;
        UUID sender;
        UUID target;
        Object key;
        Object value;
    }
}
