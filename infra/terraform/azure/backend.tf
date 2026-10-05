# Remote state. Supply values at init time:
#   terraform init -backend-config=backend.hcl
# backend.hcl (not committed): resource_group_name, storage_account_name, container_name, key
terraform {
  backend "azurerm" {}
}
