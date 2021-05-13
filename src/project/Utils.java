package project;

import peersim.core.Network;
import peersim.core.Node;

import java.util.UUID;

public class Utils {
    public static UUID getNodeId(Node node) {
        return ((Transport)node.getProtocol(DHTProject.getTransportPid())).getId();
    }

    public static UUID getNodeId(int node) {
        return getNodeId(Network.get(node));
    }

    public static Transport getTransport(Node node) {
        return ((Transport)node.getProtocol(DHTProject.getTransportPid()));
    }

    public static Transport getTransport(int node) {
        return getTransport(Network.get(node));
    }
}
