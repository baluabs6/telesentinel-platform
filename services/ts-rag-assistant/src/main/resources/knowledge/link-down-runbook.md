# Runbook: LINK_DOWN on aggregation or backhaul router
Symptoms: LINK_DOWN or NO_BACKHAUL alarms on a router and on every cell site behind it.
Likely causes: fiber cut, optical transceiver failure, power loss at the aggregation site, bad config push.
Steps:
1. Check whether a change window or software upgrade ran in the last 2 hours; if yes, roll back first.
2. Check optical power (Rx/Tx dBm) on both ends. Very low Rx on one side suggests a fiber cut or dirty connector.
3. Ping the next hop from the core router. If it answers, the fault is on the access side only.
4. Check site power and battery alarms. A site on battery with an alarm for mains failure will drop within hours.
5. Dispatch the field team with the fiber route and the number of subscribers affected so they can prioritise.
Escalate to the transport team lead if the link is not restored within 30 minutes for more than 5000 subscribers.
