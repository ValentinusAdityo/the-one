package routing;

import core.*;
import routing.culturalACOUtility.Centrality;
import routing.culturalACOUtility.Duration;
import routing.culturalACOUtility.Pheromone;
import routing.culturalACOUtility.SWindowCentrality;
import routing.util.RoutingInfo;
import util.Tuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

import java.util.*;

public class CulturalACORouter extends ActiveRouter {

    public static final String CENTRALITY_ALG_SETTING = "centralityAlg";
    public static final String PHEROMONE_SETTINGS = "pheromoneSettings";

    /** Cultural ACO router's setting namespace ({@value})*/
    public static final String CULTURALACO_NS = "CulturalACORouter";

    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    protected Centrality centrality;
    protected Map<DTNHost, Double> betweenness;
    protected Pheromone pheromoneTable;

    public enum antTypes { FORWARD, BACKWARD }
    public enum searchStatus { EXPLORATION, EXPLOITATION }

    protected antTypes type;
    protected Message msg;
    private DTNHost host;

    // Bobot untuk komponen utility
    private double alpha = 0.6;  // Bobot Pheromone
    private double beta = 0.4;   // Bobot Utility

    /**
     * Constructor. Creates a new message router based on the settings in
     * the given Settings object.
     * @param s The settings object
     */
    public CulturalACORouter(Settings s) {
        super(s);
        Settings culturalACOSettings = new Settings(CULTURALACO_NS);
        if(s.contains(CENTRALITY_ALG_SETTING))
            this.centrality = (Centrality)
                    s.createIntializedObject(s.getSetting(CENTRALITY_ALG_SETTING));
        else
            this.centrality = new SWindowCentrality(s);
        if(s.contains(PHEROMONE_SETTINGS))
            this.pheromoneTable = (Pheromone)
                    s.createIntializedObject(s.getSetting(PHEROMONE_SETTINGS));
    }

    /**
     * Copyconstructor.
     * @param proto The router prototype where setting values are copied from
     */
    protected CulturalACORouter(CulturalACORouter proto) {
        super(proto);
        this.centrality = proto.centrality.replicate();
        this.betweenness = new HashMap<DTNHost, Double>();
        this.pheromoneTable = proto.pheromoneTable;
        startTimestamps = new HashMap<DTNHost, Double>();
        connHistory = new HashMap<DTNHost, List<Duration>>();
    }

    // Method untuk update betweenness
    public void updateBetweenness(DTNHost thisHost) {

        betweenness.put(thisHost, betweenness.getOrDefault(thisHost, 0.0) + 1.0);
    }

    /**
     public double getNormalizedBetweenness(DTNHost node) {
     updateBetweenness(node);
     double max = Collections.max(betweenness.values());
     return betweenness.get(node) / max;
     } */

    /**
     * Mendapatkan nilai betweenness ternormalisasi (0-1)
     */
    private double getNormalizedBetweenness(DTNHost thisHost) {
        if (betweenness.isEmpty()) return 0;
        double max = Collections.max(betweenness.values());
        return max > 0 ? betweenness.getOrDefault(thisHost, 0.0) / max : 0;
    }

    private double getUtility(DTNHost thisHost) {
        double normalizedBetweenness = getNormalizedBetweenness(thisHost);

        // Dapatkan centrality
        double globalCentrality = this.centrality.getGlobalCentrality(connHistory);

        // Hitung weighted utility
        double utility = beta * (normalizedBetweenness + globalCentrality);

        return utility;
    }

    public Message messageTransferred(String id, DTNHost from) {
        Message msg = super.messageTransferred(id, from);
        if(msg.getTo() == getHost()){
        pheromoneTable.createPheromoneTable(msg);
        msg.updateProperty("antType", antTypes.BACKWARD);
        }

        if(msg.getProperty("antType").equals(antTypes.BACKWARD)){
            //update betweenness
            updateBetweenness(getHost());
            //update pheromone
            pheromoneTable.updatePheromone(getHost(), from, msg);
        }

        return msg;
    }


    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);

        if (con.isUp()) {
            DTNHost other = con.getOtherNode(getHost());
            CulturalACORouter otherRouter = (CulturalACORouter) other.getRouter();

            this.startTimestamps.put(other, SimClock.getTime());
            otherRouter.startTimestamps.put(getHost(), SimClock.getTime());
        }

        if (!con.isUp()) {
            DTNHost other = con.getOtherNode(getHost());
            double time = startTimestamps.get(other);
            double etime = SimClock.getTime();

            // Find or create the connection history list
            List<Duration> history;
            if(!connHistory.containsKey(other))
            {
                history = new LinkedList<Duration>();
                connHistory.put(other, history);
            }
            else
                history = connHistory.get(other);

            // add this connection to the list
            if(etime - time > 0)
                history.add(new Duration(time, etime));

            startTimestamps.remove(other);
        }
    }

    @Override
    public void update() {
        super.update();
        if (!canStartTransfer() ||isTransferring()) {
            return; // nothing to transfer or is currently transferring
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        tryOtherMessages();
    }

    /**
     * Tries to send all other messages to all connected hosts ordered by
     * their utility
     * @return The return value of {@link #tryMessagesForConnected(List)}
     */

    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages =
                new ArrayList<Tuple<Message, Connection>>();

        Collection<Message> msgCollection = getMessageCollection();
        Map<DTNHost, Double> candidates = new HashMap<>();

		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            CulturalACORouter othRouter = (CulturalACORouter)other.getRouter();

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            for (Message m : msgCollection) {
                if (othRouter.hasMessage(m.getId())) {
                    continue; // skip messages that the other one has
                }

                double pheromone = pheromoneTable.getPheromone(other, m);
                double utility = getUtility(other);
                candidates.put(other, alpha*pheromone + beta*utility);

                DTNHost bestForwarder = candidates.isEmpty() ? null :
                        candidates.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .get().getKey();

                if (bestForwarder.equals(other) && othRouter.getUtility(m.getTo()) > getUtility(m.getTo())) {
                    // the other node has higher probability of delivery
                    messages.add(new Tuple<Message, Connection>(m,con));
                }
            }
        }

        if (messages.size() == 0) {
            return null;
        }

        // sort the message-connection tuples
        Collections.sort(messages, new CulturalACORouter.TupleComparator());
        return tryMessagesForConnected(messages);	// try to send messages
    }

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by
     * their delivery probability by the host on the other side of the
     * connection (GRTRMax)
     */
    private class TupleComparator implements Comparator
            <Tuple<Message, Connection>> {

        public int compare(Tuple<Message, Connection> tuple1,
                           Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection
            double p1 = ((CulturalACORouter)tuple1.getValue().
                    getOtherNode(getHost()).getRouter()).getUtility(
                    tuple1.getKey().getTo());
            // -"- tuple2...
            double p2 = ((CulturalACORouter)tuple2.getValue().
                    getOtherNode(getHost()).getRouter()).getUtility(
                    tuple2.getKey().getTo());

            // bigger probability should come first
            if (p2-p1 == 0) {
                /* equal probabilities -> let queue mode decide */
                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            }
            else if (p2-p1 < 0) {
                return -1;
            }
            else {
                return 1;
            }
        }
    }

    /*
    @Override
    public RoutingInfo getRoutingInfo() {
        ageDeliveryPreds();
        RoutingInfo top = super.getRoutingInfo();
        RoutingInfo ri = new RoutingInfo(preds.size() +
                " delivery prediction(s)");

        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            DTNHost host = e.getKey();
            Double value = e.getValue();

            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
                    host, value)));
        }

        top.addMoreInfo(ri);
        return top;
    }
*/

    @Override
    public MessageRouter replicate() {
        CulturalACORouter r = new CulturalACORouter(this);
        return r;
    }
}
