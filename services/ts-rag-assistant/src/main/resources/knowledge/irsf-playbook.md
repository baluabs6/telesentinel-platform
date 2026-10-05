# Fraud playbook: IRSF (International Revenue Share Fraud)
Pattern: a subscriber or hacked PBX generates many minutes to premium or high-risk international ranges in a short time.
Confidence: high when minutes to risky ranges exceed the hourly limit; check whether the subscriber normally calls those ranges.
Actions:
1. Suspend outbound international calls for the subscriber and keep local calls working if possible.
2. Contact the account owner by a trusted channel to verify, since a hacked PBX is the most common cause.
3. Block the destination range temporarily if several subscribers hit it within the same hour.
4. Report the destination to the wholesale team so that termination costs can be disputed.
