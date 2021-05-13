package project;


import lombok.extern.slf4j.Slf4j;
import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import project.protocol.RoutablePacket;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static project.DHTProject.getRandomAwakenNode;
import static project.Utils.getNodeId;
import static project.Utils.getTransport;

/**
 * This class is responsible of triggering our events. We add all action we want in the action
 * list and each action get executed by the controller
 */
@Slf4j(topic = "Node Controller")
public class NodeController implements Control {
    /**
     * Index of the action to execute
     */
    private int actionIndex = 0;

    private final List<Runnable> actions = new ArrayList<>();
    private final int hashTableProtocolId;


    public NodeController(String prefix) {
        this.hashTableProtocolId = Configuration.getPid(prefix + ".application");

        // action node initialisation actions
        for (int i = 1; i < Network.size(); i++) {
            final int index = i;
            this.actions.add(() -> wakeUpNode(index));
        }

        // show the ring
        this.actions.add(this::displayRing);

        // disconnect the first node
        this.actions.add(() -> disconnectNode(0));

        // send messages
        this.actions.add(() -> sendMessageRandom("Hello world"));
        this.actions.add(() -> sendMessageRandom("Hello universe"));
        this.actions.add(() -> sendMessageRandom("Hello cosmos"));

        // test hash table
        this.actions.add(() -> put("La clef", "La valeur"));
        this.actions.add(() -> get("La clef"));
    }

    @Override
    public boolean execute() {
        // skip execution if all action have been executed
        if (this.actionIndex == this.actions.size()) return false;

        // trigger the next action
        log.info("======================== [Action {}] ========================", this.actionIndex);
        this.actions.get(this.actionIndex).run();
        this.actionIndex++;

        return false;
    }

    public void wakeUpNode(int nodeIndex) {
        Node node = Network.get(nodeIndex);
        Transport transport = (Transport) node.getProtocol(DHTProject.getTransportPid());
        log.info("Waking up node {}", nodeIndex);
        transport.awake(node);
        log.info("bootstrapped " + transport);
    }

    public void disconnectNode(int nodeIndex) {
        log.info("Killing node {}", nodeIndex);
        Node node = Network.get(nodeIndex);
        Transport transport = (Transport) node.getProtocol(DHTProject.getTransportPid());
        transport.leave();
    }

    public void displayRing() {
        List<Transport> nodes = new LinkedList<>();
        Node node = Network.get(0);

        while (nodes.size() < Network.size()) {
            Transport transport = (Transport) node.getProtocol(DHTProject.getTransportPid());
            nodes.add(transport);
            node = transport.getRight();
        }

        String idsString = nodes.stream()
                .map(transport -> String.format("%s (%s)", transport.getId(), transport.getLocalNode().getIndex()))
                .collect(Collectors.joining(" => "));
        log.info("Final ring: {}", idsString);
    }

    public void sendMessageRandom(String message) {
        Node randomSender = getRandomAwakenNode();
        Node randomTarget = getRandomAwakenNode();

        log.info(
                "Sending `{}` from {} ({}) to {} ({})", message,
                getNodeId(randomSender), randomSender.getIndex(),
                getNodeId(randomTarget), randomTarget.getIndex()
        );

        Transport transport = getTransport(randomSender);
        transport.sendMessage(getNodeId(randomTarget), message);
    }

    public void put(Object key, Object value) {
        log.info("Inserting key/value in the dht: {}/{}", key, value);

        Node node = getRandomAwakenNode();
        HashTable table = (HashTable) node.getProtocol(this.hashTableProtocolId);
        table.put(key, value);
    }

    public void get(Object key) {
        log.info("Fetching `{}` from the DHT", key);

        Node node = getRandomAwakenNode();
        HashTable table = (HashTable) node.getProtocol(this.hashTableProtocolId);
        table.get(key).thenAccept(value -> log.info("For key `{}`, got: {}", key, value));
    }
}
