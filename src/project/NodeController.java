package project;


import lombok.extern.slf4j.Slf4j;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;

@Slf4j(topic = "Node Controller")
public class NodeController implements Control {
    /**
     * Index of the n,ext node to initialize
     */
    private int nodeIndex = 1;

    public NodeController(String prefix) {
        // ignored
    }

    @Override
    public boolean execute() {
        // skip execution if all nodes awaken
        if (this.nodeIndex == Network.size()) return false;

        // init next node
        Node node = Network.get(this.nodeIndex);
        Transport transport = (Transport) node.getProtocol(DHTProject.getTransportPid());
        log.info("Waking up node " + node);
        transport.awake(node);
        log.info("bootstrapped " + transport);

        this.nodeIndex++;

        return false;
    }
}
