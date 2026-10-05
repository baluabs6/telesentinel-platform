output "bigquery_dataset" { value = google_bigquery_dataset.analytics.dataset_id }
output "service_account_email" { value = google_service_account.app.email }
output "staging_bucket" { value = google_storage_bucket.staging.name }
output "workload_identity_provider" {
  value = var.aks_oidc_issuer_url == "" ? null : google_iam_workload_identity_pool_provider.aks[0].name
}
