package dev.novadeck.tools.fleet;

import dev.novadeck.tools.NovaDeckIds;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Fleet health and capacity tools for the NovaDeck ops demo catalog.
 */
@ApplicationScoped
public class FleetTools {

    @Tool("Check whether a fleet node is healthy by node id.")
    public String nodeHealth(String nodeId) {
        return "health{" + nodeId + ",status=WARN,disk=82%}";
    }

    @Tool("Drain a node for maintenance with reason.")
    public String drainNode(String nodeId, String reason) {
        return "drain{" + nodeId + ",reason=" + reason + ",state=scheduling}";
    }

    @Tool("List top CPU consumers on a node (synthetic ranking).")
    public String topCpuOnNode(String nodeId) {
        return "top_cpu{" + nodeId + ",pids=[java:pid42,nginx:pid9]}";
    }

    @Tool("Fetch remaining disk GiB for a node.")
    public String diskGiBFree(String nodeId) {
        return "disk{" + nodeId + ",free_gib=31}";
    }

    @Tool("Report kernel version string for a node.")
    public String kernelVersion(String nodeId) {
        return "kernel{" + nodeId + ",version=6.8.0-generic}";
    }

    @Tool("Trigger synthetic smoke check on a service across fleet slice.")
    public String fleetSmokeSlice(String serviceName, int slicePercent) {
        return "smoke{service=" + serviceName + ",slice=" + slicePercent + "%,pass=true}";
    }

    @Tool("Describe autoscaler desired vs actual replicas for service.")
    public String autoscalerStatus(String serviceName) {
        return "hpa{" + serviceName + ",desired=12,actual=11}";
    }

    @Tool("Find noisy neighbor hint between two nodes.")
    public String noisyNeighborHint(String nodeA, String nodeB) {
        return "hint{compare=" + NovaDeckIds.fleetNode(nodeA) + "," + NovaDeckIds.fleetNode(nodeB) + ",correlation=low}";
    }
}
