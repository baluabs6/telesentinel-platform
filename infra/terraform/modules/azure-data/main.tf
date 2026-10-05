terraform {
  required_providers {
    azurerm = { source = "hashicorp/azurerm", version = "~> 3.116" }
    random  = { source = "hashicorp/random", version = "~> 3.6" }
  }
}

# ---------- PostgreSQL Flexible Server (private, pgvector enabled) ----------
resource "random_password" "postgres" {
  length  = 32
  special = false
}

resource "azurerm_private_dns_zone" "postgres" {
  name                = "${var.name}.private.postgres.database.azure.com"
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  name                  = "${var.name}-pg-link"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.postgres.name
  virtual_network_id    = var.vnet_id
}

resource "azurerm_postgresql_flexible_server" "main" {
  name                   = "${var.name}-pg"
  resource_group_name    = var.resource_group_name
  location               = var.location
  version                = "16"
  delegated_subnet_id    = var.postgres_subnet_id
  private_dns_zone_id    = azurerm_private_dns_zone.postgres.id
  administrator_login    = var.postgres_admin_login
  administrator_password = random_password.postgres.result
  sku_name               = var.postgres_sku
  storage_mb             = var.postgres_storage_mb
  zone                   = "1"

  backup_retention_days        = 14
  geo_redundant_backup_enabled = var.geo_redundant_backup

  dynamic "high_availability" {
    for_each = var.postgres_high_availability ? [1] : []
    content {
      mode = "ZoneRedundant"
    }
  }

  tags = var.tags

  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]

  lifecycle {
    ignore_changes = [zone]
  }
}

resource "azurerm_postgresql_flexible_server_database" "app" {
  name      = "telesentinel"
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# Spring AI's pgvector store creates these extensions on startup, so they must be allow-listed.
resource "azurerm_postgresql_flexible_server_configuration" "extensions" {
  name      = "azure.extensions"
  server_id = azurerm_postgresql_flexible_server.main.id
  value     = "VECTOR,HSTORE,UUID-OSSP"
}

# ---------- Azure Cache for Redis (private endpoint only) ----------
resource "azurerm_redis_cache" "main" {
  name                          = "${var.name}-redis"
  location                      = var.location
  resource_group_name           = var.resource_group_name
  capacity                      = var.redis_capacity
  family                        = var.redis_family
  sku_name                      = var.redis_sku
  enable_non_ssl_port           = false
  minimum_tls_version           = "1.2"
  public_network_access_enabled = false
  tags                          = var.tags
}

resource "azurerm_private_dns_zone" "redis" {
  name                = "privatelink.redis.cache.windows.net"
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "redis" {
  name                  = "${var.name}-redis-link"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.redis.name
  virtual_network_id    = var.vnet_id
}

resource "azurerm_private_endpoint" "redis" {
  name                = "${var.name}-redis-pe"
  location            = var.location
  resource_group_name = var.resource_group_name
  subnet_id           = var.private_endpoint_subnet_id
  tags                = var.tags

  private_service_connection {
    name                           = "redis"
    private_connection_resource_id = azurerm_redis_cache.main.id
    subresource_names              = ["redisCache"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "redis"
    private_dns_zone_ids = [azurerm_private_dns_zone.redis.id]
  }
}

# ---------- Cosmos DB with MongoDB API (reachable only from the AKS subnet) ----------
resource "azurerm_cosmosdb_account" "mongo" {
  name                = "${var.name}-cosmos"
  location            = var.location
  resource_group_name = var.resource_group_name
  offer_type          = "Standard"
  kind                = "MongoDB"

  mongo_server_version              = "7.0"
  is_virtual_network_filter_enabled = true

  capabilities {
    name = "EnableMongo"
  }

  consistency_policy {
    consistency_level = "Session"
  }

  geo_location {
    location          = var.location
    failover_priority = 0
  }

  virtual_network_rule {
    id = var.aks_subnet_id
  }

  tags = var.tags
}

resource "azurerm_cosmosdb_mongo_database" "app" {
  name                = "telesentinel"
  resource_group_name = var.resource_group_name
  account_name        = azurerm_cosmosdb_account.mongo.name
}
