package project.protocol;


import lombok.Value;

public interface DataPacket extends Packet {

    @Value
    class Data implements DataPacket {
        int id;
    }

}
