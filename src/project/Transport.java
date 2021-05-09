package project;

import lombok.Getter;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.edsim.EDSimulator;
import project.protocol.Packet;
import project.protocol.Packet.DiscoveryPacket;
import project.protocol.Packet.MessagePacket;
import project.protocol.Packet.SwitchNeighborPacket;
import project.protocol.WelcomePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static project.Utils.getNodeId;
import static project.protocol.Packet.SwitchNeighborPacket.LEFT;
import static project.protocol.Packet.SwitchNeighborPacket.RIGHT;

@Getter
public class Transport implements EDProtocol, peersim.transport.Transport {
    /**
     * The prefix of this layer in the configuration file
     */
    private final String prefix;

    /**
     * The protocol id of the layer to which we should send packets (hence the pid of the current layer)
     */
    private final int targetPid;

    /**
     * The protocol id of the application layer, hence the hash table
     */
    private final int applicationPid;

    /**
     * Weather or not this node is sleeping, hence if it is waiting to connect to other nodes
     */
    private boolean idle = true;

    /**
     * Local node
     */
    private Node localNode = null;

    /**
     * Left node in the ring. A node with an inferior id
     */
    private Node left = null;

    /**
     * Right node in the ring. A node with a greater id
     */
    private Node right = null;


    /**
     * Stocked data on the node
     */
    private HashMap<Integer,String> datas = new HashMap<>();

    /**
     * The id of the current node. Randomly generated
     */
    private final UUID id = UUID.randomUUID();

    private final Logger logger = Logger.getLogger("Transport " + id);

    public Transport(String prefix) {
        this.prefix = prefix;
        this.targetPid = Configuration.getPid(prefix + ".target");
//        this.applicationPid = Configuration.getPid("application");
        this.applicationPid = 0; // not used for the moment
    }



    @Override
    public void send(Node src, Node dest, Object packet, int pid) {
        EDSimulator.add(getLatency(src, dest), packet, dest, pid);
    }

    /**
     * Packet received
     * @param node the local node
     * @param pid the identifier of this protocol
     * @param event the delivered event
     */
    @Override
    public void processEvent(Node node, int pid, Object event) {
        //logger.info("Received packet: " + event);
        if (event instanceof DiscoveryPacket) this.onDiscoverPacket((DiscoveryPacket) event);
        else if (event instanceof WelcomePacket) this.onWelcomePacket((WelcomePacket) event);
        else if (event instanceof SwitchNeighborPacket) this.onSwitchNeighborPacket((SwitchNeighborPacket) event);
        else if (event instanceof MessagePacket) this.onMessagePacket((MessagePacket) event);
        else throw new IllegalArgumentException("Event not recognized: " + event);
    }

    /**
     * A new message is received
     * @param packet the packet received
     */
    private void onMessagePacket(MessagePacket packet) {
        System.out.println(packet.getMessage());
    }

    /**
     * A new node tries to join the cluster and is asking for the addresses of its neighbors
     * @param packet the packet received
     */
    private void onDiscoverPacket(DiscoveryPacket packet) {
        Node newNode = Network.get(packet.getAddress());

        // are we the initial node?
        if (this.left.equals(this.right) && getNodeId(this.left).equals(this.id)) {
            WelcomePacket welcomePacket = new WelcomePacket(this.localNode.getIndex(), this.localNode.getIndex());
            this.send(this.localNode, newNode,welcomePacket, this.targetPid);
            this.right = newNode;
            this.left = newNode;
            logger.log(Level.INFO, "Joining {} to form a ring of size 2", packet.getAddress());
        }
        else if (packet.getNodeId().compareTo(this.id) > 0) { // packet.nodeId > this.id
            // the sender has a greater id than the local node
            // should be on the right
            UUID rightId = getNodeId(this.right);

            if (
                    // the node is inferior to our right node
                    packet.getNodeId().compareTo(rightId) < 0 // packet.nodeId < rightId
                    // Our right node is inferior than the current node. We are the last node in the ring and the new node
                    // is greater than the local node. We add the node at the end of the ring
                    || this.id.compareTo(rightId) > 0 // this.id > rightId
            ) {
                // the node should be placed between the right and the local node

                // send back neighbors address to the node that joined the cluster
                WelcomePacket welcomePacket = new WelcomePacket(this.localNode.getIndex(), this.right.getIndex());
                this.send(this.localNode, newNode,welcomePacket, this.targetPid);

                // Notify the right node that his left node has changed
                SwitchNeighborPacket switchNeighbor = new SwitchNeighborPacket(LEFT, packet.getAddress());
                this.send(this.localNode, this.right, switchNeighbor, this.targetPid);
                this.right = newNode;
            } else {
                // the node should be placed after the right node
                // we follow the packet to the next node in the ring
                this.send(this.localNode,this.right,  packet, this.targetPid);
            }
        }

        else {
            // the send has an inferior id than the local node
            // should be on the left
            UUID leftId = getNodeId(this.left);

            if(
                    // the node is greater than the left node
                    packet.getNodeId().compareTo(leftId) > 0 // packet.nodeIf > leftId
                    // Our left node is greater than the current node. We are the first node in the ring and the new node
                    // is less than the local node. We add the node at the beginning of the ring
                    || this.id.compareTo(leftId) < 0 // this.id < leftId
            ) {
                // the node should be placed between the left and the local node

                // send back neighbors address to the node that joined the cluster
                WelcomePacket welcomePacket = new WelcomePacket(this.left.getIndex(), this.localNode.getIndex());
                this.send(this.localNode,Network.get(packet.getAddress()) ,welcomePacket, this.targetPid);

                // Notify the left node that his right node has changed
                SwitchNeighborPacket switchNeighbor = new SwitchNeighborPacket(RIGHT, packet.getAddress());
                this.send(this.localNode,this.left ,switchNeighbor,this.targetPid);
                this.left = Network.get(packet.getAddress());
            } else {
                // the node should be placed after the left node
                // we follow the packet to the next node in the ring
                this.send(this.localNode,this.left ,packet,this.targetPid);
            }
        }
    }

    /**
     * We got an answer from the ring and we now know the addresses of our neighbors
     * @param packet the packet received
     */
    private void onWelcomePacket(WelcomePacket packet) {
        this.left = Network.get(packet.left);
        this.right = Network.get(packet.right);
        this.idle = false;
        logger.info(this.localNode.toString());
    }

    /**
     * A new node entered the cluster and we must change one of our neighbors
     * @param packet the packet received
     */
    private void onSwitchNeighborPacket(SwitchNeighborPacket packet) {
        if (packet.isLeft()) this.left = Network.get(packet.getAddress());
        else this.right = Network.get(packet.getAddress());
    }

    /**
     * Called from the initializer to wake up this node and start connecting to other nodes
     */
    public void awake(Node localNode) {
        this.localNode = localNode;
        Node target = DHTProject.getRandomAwakenNode();

        Packet packet = new DiscoveryPacket(localNode.getIndex(), this.id);
        this.send(this.localNode,target,packet, this.targetPid);
    }

    /**
     * Initialize this as the first node in the ring
     */
    public void awakeAsInitialNode(Node localNode) {
        this.localNode = localNode;
        this.left = localNode;
        this.right = localNode;
        this.idle = false;
    }

    @Override
    public Object clone() {
        return new Transport(this.prefix);
    }

    public String toString() {
        return "Transport(" +
                "idle=" + this.isIdle() + ", " +
                "localNode=" + (this.getLocalNode() == null ? "null" : this.localNode.getIndex()) + ", " +
                "left=" + (this.isIdle() ? "null" : this.getLeft().getIndex()) + ", " +
                "right=" + (this.isIdle() ? "null" : this.getRight().getIndex()) + ", " +
                "id=" + this.getId() + ")";
    }

    @Override
    public long getLatency(Node src, Node dest) {
        long min = Configuration.getInt(prefix + ".mindelay");
        long max = Configuration.getInt(prefix + ".maxdelay");
        long range = max-min;

        return (range==1?min:min + CommonState.r.nextLong(range));
    }
}
