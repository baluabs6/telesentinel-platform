# Fraud playbook: Wangiri (one-ring callback scam)
Pattern: one calling number places many very short calls (a few seconds) to many different numbers so victims call back a premium number.
Confidence: high when a caller reaches the threshold of distinct targets inside the window; medium if the calls are from a known call centre or alerting service.
Actions:
1. Block or rate-limit the calling number at the session border controller.
2. Check whether the callback number is premium or international revenue-share and add it to the watch list.
3. Notify customer care so they can warn callers who report missed calls from the number.
4. Do not block if the number is on the allow list of a registered business sender.
