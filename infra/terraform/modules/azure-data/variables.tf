variable "name" { type = string }
variable "location" { type = string }
variable "resource_group_name" { type = string }
variable "vnet_id" { type = string }
variable "aks_subnet_id" { type = string }
variable "postgres_subnet_id" { type = string }
variable "private_endpoint_subnet_id" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}

variable "postgres_admin_login" {
  type    = string
  default = "tsadmin"
}
variable "postgres_sku" {
  type    = string
  default = "B_Standard_B2s"
}
variable "postgres_storage_mb" {
  type    = number
  default = 65536
}
variable "postgres_high_availability" {
  type    = bool
  default = false
}
variable "geo_redundant_backup" {
  type    = bool
  default = false
}

variable "redis_sku" {
  type    = string
  default = "Standard"
}
variable "redis_family" {
  type    = string
  default = "C"
}
variable "redis_capacity" {
  type    = number
  default = 1
}
