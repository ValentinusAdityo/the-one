package input;

import core.Settings;

public class MessageACOGenerator extends MessageEventGenerator {
    /**
     * Constructor, initializes the interval between events,
     * and the size of messages generated, as well as number
     * of hosts in the network.
     *
     * @param s Settings for this generator.
     */
    public MessageACOGenerator(Settings s) {
        super(s);
    }

    public ExternalEvent nextEvent() {
        int responseSize = 500; /* zero stands for one way messages */
        int msgSize;
        int interval;
        int from;
        int to;

        /* Get two *different* nodes randomly from the host ranges */
        from = drawHostAddress(this.hostRange);
        to = drawToAddress(hostRange, from);

        msgSize = drawMessageSize();
        interval = drawNextEventTimeDiff();

        /* Create event and advance to next event */
        MessageCreateEventACO mce = new MessageCreateEventACO(from, to, this.getID(),
                msgSize, responseSize, this.nextEventsTime);
        this.nextEventsTime += interval;

        if (this.msgTime != null && this.nextEventsTime > this.msgTime[1]) {
            /* next event would be later than the end time */
            this.nextEventsTime = Double.MAX_VALUE;
        }

        return mce;
    }
}
