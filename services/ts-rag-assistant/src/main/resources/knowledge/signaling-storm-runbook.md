# Runbook: Signaling storm after an outage
Symptoms: very high attach or registration rates, AMF/MME/HSS CPU above 80 percent, many timeouts.
Steps:
1. Enable overload control on the AMF/MME to reject a share of new registrations with a back-off timer.
2. Stagger re-registration if a large area just recovered; do not bring all cells up at once.
3. Check for a single misbehaving app or device model generating repeated attach attempts.
4. Watch Diameter and SBI error rates; scale out the affected network function pods if cloud native.
5. Keep overload control on until the registration rate is back to normal for 15 minutes.
