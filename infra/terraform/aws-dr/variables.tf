variable "prefix" {
  type    = string
  default = "telesentinel"
}
variable "region" {
  type        = string
  default     = "us-west-2"
  description = "Pick a region far from the Azure primary."
}
variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}
variable "eks_version" {
  type    = string
  default = "1.31"
}
variable "api_allowed_cidrs" {
  type        = list(string)
  description = "CIDRs allowed to reach the EKS public API endpoint (CI runners, office VPN)."
}

# Pilot light: data layer is always on, compute is scaled to zero until failover.
variable "pilot_light_nodes" {
  type    = number
  default = 0
}
variable "failover_max_nodes" {
  type    = number
  default = 6
}
variable "node_instance_types" {
  type    = list(string)
  default = ["m6i.xlarge"]
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.medium"
}
variable "db_storage_gb" {
  type    = number
  default = 100
}
variable "db_multi_az" {
  type    = bool
  default = false
}
variable "redis_node_type" {
  type    = string
  default = "cache.t4g.small"
}

variable "services" {
  type    = list(string)
  default = ["ts-ingestion", "ts-fraud", "ts-correlation", "ts-rag-assistant", "ts-gateway", "ts-notification"]
}

# DNS failover (optional; leave hosted_zone_id empty to skip)
variable "hosted_zone_id" {
  type    = string
  default = ""
}
variable "api_hostname" {
  type    = string
  default = ""
}
variable "primary_endpoint" {
  type        = string
  default     = ""
  description = "Public hostname of the Azure gateway; health-checked."
}
variable "dr_endpoint" {
  type        = string
  default     = ""
  description = "Public hostname of the AWS load balancer (known after the first failover drill)."
}
