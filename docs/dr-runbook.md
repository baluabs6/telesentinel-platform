# Disaster recovery runbook (Azure primary, AWS pilot light)

## Targets (validate in drills, do not assume)
| Component | DR approach | Target RPO | Notes |
|---|---|---|---|
| PostgreSQL | Logical replication from Azure to RDS (or AWS DMS) | minutes (replication lag) | Replicate the `fraud` and `correlation` schemas and the vector tables |
| Redis | Rebuilt empty | not applicable | Counters are short-lived; fraud windows restart cold, so expect a short blind spot |
| Kafka | New in-cluster Kafka (Strimzi) at failover | in-flight events lost | Event Hubs is in the failed region; upstream systems should retry or buffer |
| MongoDB | Scheduled dumps to S3 | last dump | Raw audit data only; restore after core services are up |
| Images | CI pushes every build to ECR | none | Tags are immutable |
| RTO | Scale EKS 0 to N, promote DB, deploy, DNS | target under 60 min | Route 53 TTL is 60 s and the health check needs about 90 s to trip |

## Failover steps
1. **Declare.** Confirm the Azure outage (health check red, Azure status page). Name an incident commander.
2. **Freeze writes at the source if the primary is partially up** (stop replication changes, avoid split brain).
3. **Scale compute.** `aws eks update-nodegroup-config --cluster-name telesentinel-dr --nodegroup-name dr --scaling-config minSize=2,maxSize=6,desiredSize=3`
4. **Promote the database.** Stop the subscription on RDS, confirm row counts on key tables, note the last replicated timestamp (your actual RPO).
5. **Kafka.** Install Strimzi and apply the Kafka cluster manifest; create the four topics.
6. **Secrets.** Ensure the `telesentinel-secrets` Secret exists in the cluster with all keys the chart references (use empty values for keys you do not need, such as the Kafka JAAS string).
7. **Deploy.** `helm upgrade --install telesentinel deploy/helm/telesentinel -n telesentinel --create-namespace -f deploy/helm/telesentinel/values-aws-dr.yaml --set global.imageRegistry=<account>.dkr.ecr.<region>.amazonaws.com --set global.imageTag=<sha>`
8. **Verify.** Gateway `/actuator/health`, send a test alarm and CDR, check an incident and alert appear.
9. **Cut over DNS.** Set `dr_endpoint` in the `aws-dr` Terraform variables and apply, or let the Route 53 failover record take over if already configured.
10. **Restore Mongo** from the latest S3 dump when stable.

## Failback
Rebuild Azure data stores, replicate DR data back, quiesce DR writes, switch DNS, scale EKS back to zero. Treat failback as a planned change window.

## Drills
Run a game day every quarter: scale to zero, then execute steps 3 to 8 against a test hostname, and record actual RPO and RTO. Update the table above with measured values.
