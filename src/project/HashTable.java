package project;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import peersim.config.Configuration;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import project.protocol.ApplicationPacket.GetPacket;
import project.protocol.ApplicationPacket.GetResponsePacket;
import project.protocol.ApplicationPacket.PutPacket;
import project.protocol.ApplicationPacket.ReplicationPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;
import static project.Utils.getNodeId;

@Slf4j
public class HashTable implements EDProtocol {
    private final Map<Object,Object> storage = new HashMap<>();
    private final Multimap<Object, CompletableFuture<Object>> pendingGets = ArrayListMultimap.create();

    private final String prefix;
    private final int transportProtocolId;
    private Transport node;

    // Initial logger uses the UUID to bez identifiable, will then be changed to the node index
    private Logger logger = LoggerFactory.getLogger("HashTable (unknown)");

    public HashTable(String prefix) {
        this.prefix = prefix;
        this.transportProtocolId = Configuration.getPid(prefix + ".transport");
    }

    @Override
    public Object clone() {
        return new HashTable(this.prefix);
    }

    @Override
    public void processEvent(Node node, int pid, Object event) {
        if (this.node == null) this.setNode(node);

        if (event instanceof PutPacket) this.onPutPacket((PutPacket) event);
        else if (event instanceof GetPacket) this.onGetPacket((GetPacket) event);
        else if (event instanceof ReplicationPacket) this.onReplicationPacket((ReplicationPacket) event);
        else if (event instanceof GetResponsePacket) this.onGetResponsePacket((GetResponsePacket) event);
    }

    /**
     * Put a mapping on the cluster
     * @param key the key of the data
     * @param value the data
     */
    public void put(Object key, Object value) {
        checkState(this.node != null, "HashTable not initialized");

        PutPacket packet = new PutPacket(key, value);
        this.onPutPacket(packet);
    }

    public CompletableFuture<Object> get(Object key) {
        checkState(this.node != null, "HashTable not initialized");

        GetPacket packet = new GetPacket(this.node.getId(), key);
        CompletableFuture<Object> future = new CompletableFuture<>();

        this.pendingGets.put(key, future);
        this.onGetPacket(packet);
        return future;
    }

    private void onGetResponsePacket(GetResponsePacket packet) {
        this.pendingGets
                .removeAll(packet.getKey())
                .forEach(future -> future.complete(packet.getValue()));
    }

    private void onReplicationPacket(ReplicationPacket packet) {
        this.storage.put(packet.getKey(), packet.getValue());
        this.logger.debug("Replicated storage for `{}`", packet.getValue());
    }

    private void onPutPacket(PutPacket packet) {
        // transforming UUIDs to int, keeping most significant bits
        int localId = getId(this.node);
        int leftId = getId(this.node.getLeft());
        int rightId = getId(this.node.getRight());

        // by comparing the hash to our ids, we can distribute date equally across the ring
        int keyHash = packet.getKey().hashCode();

        if (this.node.isFirst() && keyHash < localId || this.node.isLast() && keyHash > localId) this.store(packet, keyHash);
        else if (keyHash > localId + (rightId - localId) / 2)  this.node.send(this.node.getRight(), packet);
        else if (keyHash < localId + (leftId - localId) / 2) this.node.send(this.node.getLeft(), packet);
        else this.store(packet, keyHash);
    }

    private void store(PutPacket packet, int hash) {
        // store on local node
        this.storage.put(packet.getKey(), packet.getValue());

        // replicate on neighbors
        ReplicationPacket replicationPacket = ReplicationPacket.from(packet);
        this.node.send(this.node.getLeft(), replicationPacket);
        this.node.send(this.node.getRight(), replicationPacket);

        this.logger.debug("Stored value for `{}` (hash: {})", packet.getKey(), String.format("%08x", hash));
    }

    private void onGetPacket(GetPacket packet) {
        // transforming UUIDs to int, keeping most significant bits
        int leftId = getId(this.node.getLeft());
        int rightId = getId(this.node.getRight());
        int keyHash = packet.getKey().hashCode();

        // TODO test edge cases

        if (keyHash > rightId && !this.node.isLast()) this.node.sendRight(packet);
        else if (keyHash < leftId && !this.node.isFirst()) this.node.sendLeft(packet);
        else {
            // We should have the data or a replication of the data
            Object value = this.storage.get(packet.getKey());

            GetResponsePacket response = new GetResponsePacket(
                    this.node.getId(), packet.getSender(),
                    packet.getKey(), value
            );

            this.node.route(response);
            this.logger.debug("Found data for `{}`", packet.getKey());
        }
    }

    private void setNode(Node node) {
        this.node = (Transport) node.getProtocol(this.transportProtocolId);
        this.logger = LoggerFactory.getLogger(String.format(
                "HashTable %016x (Node %d)",
                this.node.getId().getMostSignificantBits(), node.getIndex()
        ));
    }

    /**
     * Transform UUIDs to int, keeping most significant bits
     * @param node the node to get the id from
     * @return the most significant
     */
    private static int getId(Node node) {
        return (int) (getNodeId(node).getMostSignificantBits() >>> 32);
    }

    /**
     * Transform UUIDs to int, keeping most significant bits
     * @param node the node to get the id from
     * @return the most significant
     */
    private static int getId(Transport node) {
        return (int) (node.getId().getMostSignificantBits() >>> 32);
    }
}
