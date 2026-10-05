output "postgres_fqdn" { value = azurerm_postgresql_flexible_server.main.fqdn }
output "postgres_admin_login" { value = var.postgres_admin_login }
output "postgres_admin_password" {
  value     = random_password.postgres.result
  sensitive = true
}
output "redis_hostname" { value = azurerm_redis_cache.main.hostname }
output "redis_primary_key" {
  value     = azurerm_redis_cache.main.primary_access_key
  sensitive = true
}
output "cosmos_mongo_connection_string" {
  value     = azurerm_cosmosdb_account.mongo.primary_mongodb_connection_string
  sensitive = true
}
