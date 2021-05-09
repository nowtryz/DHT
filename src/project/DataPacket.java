package project;


import lombok.Value;
import project.protocol.Packet;

public interface DataPacket extends Packet {

    @Value
    class Data implements DataPacket {
        int id;
    }

}
