variable "project_id" { type = string }
variable "environment" { type = string }
variable "region" {
  type    = string
  default = "us-central1"
}
variable "bq_location" {
  type    = string
  default = "US"
}

# Optional keyless access from AKS pods (Workload Identity Federation). Leave empty to skip.
variable "aks_oidc_issuer_url" {
  type    = string
  default = ""
}
variable "k8s_namespace" {
  type    = string
  default = "telesentinel"
}
variable "k8s_service_account" {
  type    = string
  default = "ts-rag-assistant"
}
