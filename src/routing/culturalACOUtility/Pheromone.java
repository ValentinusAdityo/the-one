package routing.culturalACOUtility;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

import java.util.HashMap;
import java.util.Map;

public class Pheromone {
    protected Map<DTNHost, Map<DTNHost, Double>> pheromoneTable;

    public static final String EVAPORATION_RATE_SETTINGS = "evaporationRate";   // Bobot evaporasi Pheromone

    protected static double EVAPORATION_RATE = 0.1;

    public Pheromone(Settings s) {
        pheromoneTable = new HashMap<DTNHost, Map<DTNHost, Double>>();
        if(s.contains(EVAPORATION_RATE_SETTINGS)){
            EVAPORATION_RATE =s.getDouble(EVAPORATION_RATE_SETTINGS);
        }
    }

    public void createPheromoneTable(Message m) {
        m.addProperty("destination", m.getTo());
        m.addProperty("pathLength", (double) m.getHopCount());
        pheromoneTable.putIfAbsent((DTNHost) m.getProperty("destination"), new HashMap<>());
    }

    public double getPheromone(DTNHost peer, Message m) {
        if(m.getProperty("destination")==null){
            return 0.0;
        }
        return pheromoneTable.get((DTNHost) m.getProperty("destination")).getOrDefault(peer, 0.0);
    }

    public void updatePheromone(DTNHost thisHost, DTNHost from, Message m) {
        for(Connection con : thisHost.getConnections()) {
            DTNHost neighbor = con.getOtherNode(thisHost);
            if(m.getProperty("pathLength")==null){
                return;
            } else{
                double pathLength = (Double) m.getProperty("pathLength");
                //sSystem.out.println(pathLength);
                //if(pheromoneTable.get((DTNHost) m.getProperty("destination")).containsValue(neighbor)) {}
                //if(pheromoneTable.get(neighbor).containsKey(m.getProperty("destination"))) {}
                double currentPheromone = getPheromone(neighbor, m);
                double newPheromone = (1 - EVAPORATION_RATE)*(currentPheromone +  (1.0/pathLength));
                if(neighbor.equals(from)) {
                    pheromoneTable.get((DTNHost) m.getProperty("destination")).put(neighbor, newPheromone);
            }

            }
        }
    }
}
