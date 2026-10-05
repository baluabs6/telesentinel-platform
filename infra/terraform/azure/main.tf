data "azurerm_client_config" "current" {}

resource "random_string" "suffix" {
  length  = 4
  upper   = false
  special = false
}

locals {
  name = "${var.prefix}-${var.environment}"
  uniq = "${var.prefix}-${var.environment}-${random_string.suffix.result}"
  tags = {
    project     = "telesentinel"
    environment = var.environment
    managed_by  = "terraform"
  }
}

resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name}"
  location = var.location
  tags     = local.tags
}

# ---------------- Network ----------------
resource "azurerm_virtual_network" "main" {
  name                = "vnet-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  address_space       = ["10.20.0.0/16"]
  tags                = local.tags
}

resource "azurerm_subnet" "aks" {
  name                 = "snet-aks"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.20.0.0/20"]
  service_endpoints    = ["Microsoft.AzureCosmosDB"]
}

resource "azurerm_subnet" "postgres" {
  name                 = "snet-postgres"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.20.16.0/24"]

  delegation {
    name = "postgres"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_subnet" "private_endpoints" {
  name                 = "snet-private-endpoints"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.20.17.0/24"]
}

# ---------------- Data stores (PostgreSQL + pgvector, Redis, Mongo API) ----------------
module "data" {
  source = "../modules/azure-data"

  name                       = local.uniq
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  vnet_id                    = azurerm_virtual_network.main.id
  aks_subnet_id              = azurerm_subnet.aks.id
  postgres_subnet_id         = azurerm_subnet.postgres.id
  private_endpoint_subnet_id = azurerm_subnet.private_endpoints.id
  postgres_sku               = var.postgres_sku
  postgres_high_availability = var.postgres_high_availability
  geo_redundant_backup       = var.geo_redundant_backup
  tags                       = local.tags
}

# ---------------- Container registry and AKS ----------------
resource "azurerm_container_registry" "main" {
  name                = replace("${var.prefix}${var.environment}${random_string.suffix.result}", "-", "")
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "Standard"
  admin_enabled       = false
  tags                = local.tags
}

resource "azurerm_kubernetes_cluster" "main" {
  name                = "aks-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  dns_prefix          = local.uniq

  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  default_node_pool {
    name                = "system"
    vm_size             = var.aks_vm_size
    vnet_subnet_id      = azurerm_subnet.aks.id
    enable_auto_scaling = true
    min_count           = var.aks_min_nodes
    max_count           = var.aks_max_nodes
    zones               = ["1", "2", "3"]
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin    = "azure"
    network_policy    = "azure"
    load_balancer_sku = "standard"
    service_cidr      = "10.21.0.0/16"
    dns_service_ip    = "10.21.0.10"
  }

  tags = local.tags
}

resource "azurerm_role_assignment" "aks_acr_pull" {
  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id
}

# ---------------- Azure OpenAI (chat + embeddings for Spring AI) ----------------
resource "azurerm_cognitive_account" "openai" {
  name                  = "oai-${local.uniq}"
  location              = azurerm_resource_group.main.location
  resource_group_name   = azurerm_resource_group.main.name
  kind                  = "OpenAI"
  sku_name              = "S0"
  custom_subdomain_name = "oai-${local.uniq}"
  tags                  = local.tags
}

resource "azurerm_cognitive_deployment" "chat" {
  name                 = "chat"
  cognitive_account_id = azurerm_cognitive_account.openai.id
  model {
    format  = "OpenAI"
    name    = var.chat_model_name
    version = var.chat_model_version
  }
  scale {
    type     = "Standard"
    capacity = var.openai_capacity
  }
}

resource "azurerm_cognitive_deployment" "embedding" {
  name                 = "embedding"
  cognitive_account_id = azurerm_cognitive_account.openai.id
  model {
    format  = "OpenAI"
    name    = var.embedding_model_name
    version = var.embedding_model_version
  }
  scale {
    type     = "Standard"
    capacity = var.openai_capacity
  }
}

# ---------------- Event Hubs with Kafka endpoint ----------------
resource "azurerm_eventhub_namespace" "kafka" {
  name                = "evh-${local.uniq}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "Standard"
  capacity            = 1
  tags                = local.tags
}

resource "azurerm_eventhub" "topics" {
  for_each            = toset(var.kafka_topics)
  name                = each.value
  namespace_name      = azurerm_eventhub_namespace.kafka.name
  resource_group_name = azurerm_resource_group.main.name
  partition_count     = 6
  message_retention   = 3
}

# ---------------- Key Vault (all generated secrets land here) ----------------
resource "azurerm_key_vault" "main" {
  name                       = replace("kv-${var.prefix}-${var.environment}-${random_string.suffix.result}", "-", "")
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  enable_rbac_authorization  = true
  purge_protection_enabled   = var.environment == "prod"
  soft_delete_retention_days = 14
  tags                       = local.tags
}

resource "azurerm_role_assignment" "deployer_secrets" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

locals {
  secrets = {
    "postgres-password"          = module.data.postgres_admin_password
    "redis-primary-key"          = module.data.redis_primary_key
    "cosmos-mongo-connection"    = module.data.cosmos_mongo_connection_string
    "eventhubs-kafka-connection" = azurerm_eventhub_namespace.kafka.default_primary_connection_string
    "azure-openai-api-key"       = azurerm_cognitive_account.openai.primary_access_key
  }
}

resource "azurerm_key_vault_secret" "app" {
  for_each     = nonsensitive(toset(keys(local.secrets)))
  name         = each.value
  value        = local.secrets[each.value]
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.deployer_secrets]
}
