package project;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.GeneralNode;
import peersim.core.Network;
import peersim.core.Node;
import project.protocol.MessagePacket;

import java.util.stream.IntStream;

public class DHTProject implements Control {
    private static int TRANSPORT_PID = -1;

    public DHTProject(String prefix) {
        System.out.println("Creating initializer" + prefix);
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
                .filter(node -> !((Transport) node.getProtocol(getTransportPid())).isIddle())
                // this should be random
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Unable to find any awaken node"));
    }

    @Override
    public boolean execute() {
        System.out.println("Starting simulation");

        
        if (Network.size() > 1) {
            // We want to network to have at least a size of 2 in order to only wake up the first node.
            // Other nodes will be added later on
            throw new IllegalStateException("The initial size of the network must be 1");
        }

        // Initialize the ring by waking up the first node
        System.out.println("Initializing first node");
        Node initialNode = Network.get(0);
        Transport initialTransport = (Transport) initialNode.getProtocol(TRANSPORT_PID);
        initialTransport.awakeAsInitialNode(initialNode);
        
        //ENZO adding up other nodes
        for (int i = 1; i < 10; i++){
            Node dest = new GeneralNode("protocol.transport project.Transport");
            Network.add(dest);

            //ENZO send a MessagePacket (etape 2)
            Transport transport = (Transport) dest.getProtocol(TRANSPORT_PID);
            transport.send(new MessagePacket("Bonjour je suis nouveau dans le cercle :) "), initialNode);
        }


        // Sequentially awake other nodes
        for (int i = 1; i < Network.size(); i++) {
            System.out.println("Waking up node " + i);
           
            Node node = Network.get(i);
            Transport transport = (Transport) node.getProtocol(TRANSPORT_PID);
            transport.awake(node);
        }


        System.out.println("Done");


        return false;
    }
}
