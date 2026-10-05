# Runbook: CELL_DOWN / CELL_OUTAGE on a single gNB or eNB
Symptoms: one cell reports CELL_DOWN, parent router has no alarms, neighbours show higher load.
Steps:
1. Confirm the parent router and backhaul are healthy; if not, use the LINK_DOWN runbook instead.
2. Try a remote soft reset of the baseband unit. Wait 5 minutes for the cell to re-attach.
3. If a hardware alarm is present (VSWR, radio unit fault), raise a field ticket and note the radio unit serial.
4. Check neighbour cells for congestion; increase their coverage temporarily if the vendor tool allows it.
5. If the outage follows a software upgrade, compare the running config against the golden config.
