variable "prefix" {
  type    = string
  default = "telesentinel"
}
variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "stage", "prod"], var.environment)
    error_message = "environment must be dev, stage or prod."
  }
}
variable "location" {
  type    = string
  default = "eastus"
}

variable "aks_vm_size" {
  type    = string
  default = "Standard_D4s_v5"
}
variable "aks_min_nodes" {
  type    = number
  default = 2
}
variable "aks_max_nodes" {
  type    = number
  default = 5
}

variable "postgres_sku" {
  type    = string
  default = "B_Standard_B2s"
}
variable "postgres_high_availability" {
  type    = bool
  default = false
}
variable "geo_redundant_backup" {
  type    = bool
  default = false
}

# Model availability differs per region; check the Azure OpenAI model table before applying.
variable "chat_model_name" {
  type    = string
  default = "gpt-4o-mini"
}
variable "chat_model_version" {
  type    = string
  default = "2024-07-18"
}
variable "embedding_model_name" {
  type    = string
  default = "text-embedding-3-small"
}
variable "embedding_model_version" {
  type    = string
  default = "1"
}
variable "openai_capacity" {
  type        = number
  default     = 30
  description = "Thousands of tokens per minute per deployment."
}

variable "kafka_topics" {
  type    = list(string)
  default = ["telesentinel.alarms", "telesentinel.cdrs", "telesentinel.fraud-alerts", "telesentinel.incidents"]
}
