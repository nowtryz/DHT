package project;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.edsim.EDSimulator;
import project.protocol.ApplicationPacket;
import project.protocol.Packet;
import project.protocol.Packet.DiscoveryPacket;
import project.protocol.Packet.SwitchNeighborPacket;
import project.protocol.Packet.WelcomePacket;
import project.protocol.RoutablePacket;
import project.protocol.RoutablePacket.MessagePacket;
import project.protocol.RoutablePacket.UndeliverableRoutablePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkState;
import static project.Utils.getNodeId;
import static project.protocol.Packet.SwitchNeighborPacket.LEFT;
import static project.protocol.Packet.SwitchNeighborPacket.RIGHT;

@Getter
public class Transport implements EDProtocol, peersim.transport.Transport {
    private final Map<UUID, Integer> addressesCache = new HashMap<>();
    /**
     * The prefix of this layer in the configuration file
     */
    private final String prefix;

    /**
     * The protocol id of the layer to which we should route packets (hence the pid of the current layer)
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
     * The id of the current node. Randomly generated
     */
    private final UUID id = UUID.randomUUID();

    // Initial logger uses the UUID to bez identifiable, will then be changed to the node index
    private Logger logger = LoggerFactory.getLogger(String.format("Transport %016x", id.getMostSignificantBits()));

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
     * Send to application on local node
     * @param packet the packet to forward
     */
    public void sendToApplication(ApplicationPacket packet) {
        EDSimulator.add(0, packet, this.localNode, this.applicationPid);
    }

    public void send(Node dest, Packet packet) {
        this.send(this.localNode, dest, packet, this.targetPid);
    }

    public void sendLeft(Packet packet) {
        this.send(this.left, packet);
    }

    public void sendRight(Packet packet) {
        this.send(this.right, packet);
    }

    public void route(RoutablePacket packet) {
        if (this.isIdle()) throw new IllegalStateException("Node in idle state");

        // cache sender address
        this.addressesCache.put(packet.getSender(), packet.getSenderAddress());


        Integer cachedAddress = this.addressesCache.get(packet.getTarget());

        if (!packet.getTarget().equals(this.id) && cachedAddress != null) {
            logger.trace("Routing packet directly to {} ({}) (address was cached)", cachedAddress, packet.getTarget());
            this.send(Network.get(cachedAddress), packet);
        } else if (packet.getTarget().compareTo(this.id) > 0) { // packet.target > this.id
            // route to right node
            if (packet.getTarget().compareTo(getNodeId(this.right)) < 0) {
                // the destination node should be placed between us and the right node
                // hence this node is missing, it may have left the ring
                this.nodeNotFoundWhenRouting(packet);
            } else {
                logger.trace("Routing packet to right: {} ({})", this.right.getIndex(), getNodeId(this.right));
                this.send(this.right, packet);
            }
        } else if (packet.getTarget().compareTo(this.id) < 0) {
            // route to left node
            if (packet.getTarget().compareTo(getNodeId(this.left)) > 0) {
                // the destination node should be placed between us and the left node
                // hence this node is missing, it may have left the ring
                this.nodeNotFoundWhenRouting(packet);
            } else {
                logger.trace("Routing packet to left: {} ({})", this.left.getIndex(), getNodeId(this.left));
                this.send(this.left, packet);
            }
        } else {
            this.handleRoutablePacket(packet);
        }
    }

    public void sendMessage(UUID target, String message) {
        this.route(new MessagePacket(this.localNode.getIndex(), this.id, target, message));
    }

    private void nodeNotFoundWhenRouting(RoutablePacket packet) {
        if (packet.getSender().equals(this.id)) {
            // no need to forward an error packet, we just notify the console
            logger.error("Node {} not found", packet.getTarget());
        } else {
            // route a response to the sender, notifying the node is missing
            this.route(new UndeliverableRoutablePacket(
                    this.localNode.getIndex(), this.id,
                    packet.getSender(), "Node not found", packet
            ));
        }
    }

    /**
     * Packet received
     * @param node the local node
     * @param pid the identifier of this protocol
     * @param event the delivered event
     */
    @Override
    public void processEvent(Node node, int pid, Object event) {
        logger.trace("Received packet: " + event);
        if (event instanceof DiscoveryPacket) this.onDiscoverPacket((DiscoveryPacket) event);
        else if (event instanceof WelcomePacket) this.onWelcomePacket((WelcomePacket) event);
        else if (event instanceof SwitchNeighborPacket) this.onSwitchNeighborPacket((SwitchNeighborPacket) event);
        else if (event instanceof RoutablePacket) this.onRoutablePacket((RoutablePacket) event);
        else if (event instanceof ApplicationPacket) this.sendToApplication((ApplicationPacket) event);
        else throw new IllegalArgumentException("Event not recognized: " + event);
    }

    /**
     * A routable packet is received
     * @param packet the packet received
     */
    private void onRoutablePacket(RoutablePacket packet) {
        this.route(packet);
    }

    private void handleRoutablePacket(RoutablePacket packet) {
        if (packet instanceof MessagePacket) this.onMessagePacket((MessagePacket) packet);
        if (packet instanceof UndeliverableRoutablePacket) this.onUndeliverableRoutablePacket((UndeliverableRoutablePacket) packet);
        if (packet instanceof ApplicationPacket) this.sendToApplication((ApplicationPacket) packet);
    }

    private void onMessagePacket(MessagePacket packet) {
        logger.info("Received a message from {}: {}", packet.getSender(), packet.getMessage());
    }

    private void onUndeliverableRoutablePacket(UndeliverableRoutablePacket packet) {
        logger.error("Was not able to deliver a message to {}: {}", packet.getOriginalPacket().getTarget(), packet.getReason());
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
            logger.debug("Joining {} to form a ring of size 2", packet.getAddress());
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

                // send back neighbors addresses to the node that joined the cluster
                this.logger.debug("Welcoming node {} ({}) as my new right node", packet.getAddress(), packet.getNodeId());
                WelcomePacket welcomePacket = new WelcomePacket(this.localNode.getIndex(), this.right.getIndex());
                this.send(newNode, welcomePacket);

                // Notify the right node that his left node has changed
                this.logger.debug("Notifying node {} ({}) of their new left node", this.right.getIndex(), getNodeId(this.right));
                SwitchNeighborPacket switchNeighbor = new SwitchNeighborPacket(LEFT, packet.getAddress());
                this.send(this.right, switchNeighbor);
                this.right = newNode;
            } else {
                // the node should be placed after the right node
                // we follow the packet to the next node in the ring
                this.logger.trace(
                        "Following discovery of {} ({}) to {} ({})",
                        packet.getAddress(), packet.getNodeId(), this.right.getIndex(), getNodeId(this.right)
                );
                this.send(this.right, packet);
            }
        }

        else {
            // the sender has an inferior id than the local node
            // should be on the left
            UUID leftId = getNodeId(this.left);

            if(
                    // the node is greater than the left node
                    packet.getNodeId().compareTo(leftId) > 0 // packet.nodeId > leftId
                    // Our left node is greater than the current node. We are the first node in the ring and the new node
                    // is less than the local node. We add the node at the beginning of the ring
                    || this.id.compareTo(leftId) < 0 // this.id < leftId
            ) {
                // the node should be placed between the left and the local node

                // send back neighbors addresses to the node that joined the cluster
                this.logger.debug("Welcoming node {} ({}) as my new left node", packet.getAddress(), packet.getNodeId());
                WelcomePacket welcomePacket = new WelcomePacket(this.left.getIndex(), this.localNode.getIndex());
                this.send(newNode, welcomePacket);

                // Notify the left node that his right node has changed
                this.logger.debug("Notifying node {} ({}) of their new right node", this.left.getIndex(), getNodeId(this.left));
                SwitchNeighborPacket switchNeighbor = new SwitchNeighborPacket(RIGHT, packet.getAddress());
                this.send(this.left, switchNeighbor);
                this.left = newNode;
            } else {
                // the node should be placed after the left node
                // we follow the packet to the next node in the ring
                this.logger.trace(
                        "Following discovery of {} ({}) to {} ({})",
                        packet.getAddress(), packet.getNodeId(), this.left.getIndex(), getNodeId(this.left)
                );
                this.send(this.left, packet);
            }
        }
    }

    /**
     * We got an answer from the ring and we now know the addresses of our neighbors
     * @param packet the packet received
     */
    private void onWelcomePacket(WelcomePacket packet) {
        this.left = Network.get(packet.getLeft());
        this.right = Network.get(packet.getRight());
        this.idle = false;
        this.logger.debug("Awaken and joined the ring (left={}, right={})", packet.getLeft(), packet.getRight());
        this.logger.debug("The ring has now a size of {}", DHTProject.getAwakenNodesCount());
        this.sendToApplication(new ApplicationPacket.InitApplication());
    }

    /**
     * A new node entered the cluster and we must change one of our neighbors
     * @param packet the packet received
     */
    private void onSwitchNeighborPacket(SwitchNeighborPacket packet) {
        if (packet.isLeft()) {
            logger.debug(
                    "Switching left neighbor from {} to {}",
                    this.left == null ? "null" : this.left.getIndex(),
                    packet.getAddress()
            );
            this.left = Network.get(packet.getAddress());
        } else {
            logger.debug(
                    "Switching right neighbor from {} to {}",
                    this.right == null ? "null" : this.right.getIndex(),
                    packet.getAddress()
            );
            this.right = Network.get(packet.getAddress());
        }
    }

    /**
     * Called from the initializer to wake up this node and start connecting to other nodes
     */
    public void awake(Node localNode) {
        checkState(this.isIdle(), "The node is already awaken");

        this.localNode = localNode;
        this.updateLogger();

        Node target = DHTProject.getRandomAwakenNode();
        Packet packet = new DiscoveryPacket(localNode.getIndex(), this.id);

        this.logger.debug(
                "Starting discovery, contacting node {} ({}) and waiting for response",
                target.getIndex(), getNodeId(target)
        );

        this.send(target, packet);
    }

    /**
     * Initialize this as the first node in the ring
     */
    public void awakeAsInitialNode(Node localNode) {
        checkState(this.isIdle(), "The node is already awaken");

        this.localNode = localNode;
        this.left = localNode;
        this.right = localNode;
        this.idle = false;
        this.updateLogger();

        this.logger.info("Awaken as initial node");
    }

    /**
     * Leave the ring and notify the its left and right neighbors that their respective right and left neighbor have
     * change
     */
    public void leave() {
        checkState(!this.isIdle(), "Cannot leave as the node is not part of the ring");
        this.logger.info("Leaving the ring (notifying neighbors)");

        this.idle = true;
        SwitchNeighborPacket leftSwitch = new SwitchNeighborPacket(RIGHT, this.right.getIndex());
        SwitchNeighborPacket rightSwitch = new SwitchNeighborPacket(LEFT, this.left.getIndex());
        this.send(this.left, leftSwitch);
        this.send(this.right, rightSwitch);
        this.left = null;
        this.right = null;
    }

    private void updateLogger() {
        this.logger = LoggerFactory.getLogger(String.format(
                "Transport %016x (Node %d)",
                this.id.getMostSignificantBits(), this.localNode.getIndex()
        ));
    }

    public boolean isEdgeNode() {
        return this.isFirst() || this.isLast();
    }

    /**
     * check if our left node has a greater id
     * @return true if it is the case
     */
    public boolean isFirst() {
        checkState(!this.isIdle(), "Node in idle state");
        return this.id.compareTo(getNodeId(this.left)) < 0;
    }

    /**
     * Check if ur right node has a smaller id
     * @return true if it is the case
     */
    public boolean isLast() {
        checkState(!this.isIdle(), "Node in idle state");
        return  this.id.compareTo(getNodeId(this.right)) > 0;
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
