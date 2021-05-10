package project;


import lombok.extern.slf4j.Slf4j;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;

@Slf4j(topic = "Node Controller")
public class NodeController implements Control {

    public void addNodeToNetwork(Node node , Transport transport){
        // Sequentially awake other node
        log.info("Waking up node " + node);
        transport.awake(node);
        log.info("bootstrapped " + transport);
    }

    @Override
    public boolean execute() {
        return false;
    }
}
