package project;

import lombok.extern.java.Log;
import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import project.protocol.Packet;


import java.util.stream.IntStream;

@Log
public class DHTProject implements Control {
    private static int TRANSPORT_PID = -1;

    public DHTProject(String prefix) {
        log.info("Creating initializer " + prefix);
        TRANSPORT_PID = Configuration.getPid(prefix + ".transport");
    }

    public static int getTransportPid() {
        if (TRANSPORT_PID == -1) throw new IllegalStateException("DHT project not yet initialized");
        return TRANSPORT_PID;
    }

    /**
     * For this simulation, we'll say that awaken nodes spread heart beats or any sign of life to the network.
     * Hence it is possible to find a random node from the awaken ones.
     * @return a random awaken node
     */
    public static Node getRandomAwakenNode() {
        return IntStream.range(0, Network.size())
                .mapToObj(Network::get)
                .filter(node -> !((Transport) node.getProtocol(getTransportPid())).isIdle())
                // this should be random
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Unable to find any awaken node"));
    }

    @Override
    public boolean execute() {
        log.info("Starting simulation");


        if (Network.size() < 2) {
            // We want to network to have at least a size of 2 in order to only wake up the first node.
            throw new IllegalStateException("The size of the network must be greater than 2");
        }

        // Initialize the ring by waking up the first node
        log.info("Initializing first node");
        Node initialNode = Network.get(0);
        Transport initialTransport = (Transport) initialNode.getProtocol(TRANSPORT_PID);
        initialTransport.awakeAsInitialNode(initialNode);

        // Sequentially awake other nodes
        for (int i = 1; i < Network.size(); i++) {
            log.info("Waking up node " + i);

            Node node = Network.get(i);
            Transport transport = (Transport) node.getProtocol(TRANSPORT_PID);
            transport.awake(node);
            //transport.send(new Packet.MessagePacket("Bonjour"), initialNode);
            log.info("bootstrapped " + transport);
        }

        // Data creation
        DataPacket dp = new DataPacket.Data(5);




        log.info("Done");


        return false;
    }
}
