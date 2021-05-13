package project;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;

import java.util.Random;
import java.util.stream.IntStream;

@Slf4j(topic = "Initializer")
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
        Node[] nodes = IntStream.range(0, Network.size())
                .mapToObj(Network::get)
                .filter(node -> !((Transport) node.getProtocol(getTransportPid())).isIdle())
                .toArray(Node[]::new);

        return nodes[new Random().nextInt(nodes.length)];
    }

    public static long getAwakenNodesCount() {
        return IntStream.range(0, Network.size())
                .mapToObj(Network::get)
                .filter(node -> !((Transport) node.getProtocol(getTransportPid())).isIdle())
                .count();
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


        log.info("Done");

        return false;
    }
}
