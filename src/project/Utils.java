package project;

import peersim.core.Node;

import java.util.UUID;

public class Utils {
    public static UUID getNodeId(Node node) {
        return ((Transport)node.getProtocol(DHTProject.getTransportPid())).getId();
    }
}
