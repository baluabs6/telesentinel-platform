data "google_project" "current" {}

locals {
  labels = {
    project     = "telesentinel"
    environment = var.environment
    managed_by  = "terraform"
  }
}

resource "google_project_service" "apis" {
  for_each = toset([
    "aiplatform.googleapis.com",
    "bigquery.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
  ])
  service            = each.value
  disable_on_destroy = false
}

# ---------------- BigQuery: historical CDR and alert analytics ----------------
resource "google_bigquery_dataset" "analytics" {
  dataset_id                 = "telesentinel_${var.environment}"
  location                   = var.bq_location
  delete_contents_on_destroy = false
  labels                     = local.labels
  depends_on                 = [google_project_service.apis]
}

resource "google_bigquery_table" "cdr_history" {
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "cdr_history"
  deletion_protection = var.environment == "prod"
  labels              = local.labels

  time_partitioning {
    type  = "DAY"
    field = "start_time"
  }
  clustering = ["calling_number"]

  schema = jsonencode([
    { name = "cdr_id", type = "STRING", mode = "REQUIRED" },
    { name = "calling_number", type = "STRING", mode = "REQUIRED" },
    { name = "called_number", type = "STRING", mode = "REQUIRED" },
    { name = "start_time", type = "TIMESTAMP", mode = "REQUIRED" },
    { name = "duration_seconds", type = "INT64", mode = "REQUIRED" },
    { name = "call_type", type = "STRING", mode = "REQUIRED" },
  ])
}

resource "google_bigquery_table" "fraud_alert_history" {
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "fraud_alert_history"
  deletion_protection = var.environment == "prod"
  labels              = local.labels

  time_partitioning {
    type  = "DAY"
    field = "created_at"
  }

  schema = jsonencode([
    { name = "rule_id", type = "STRING", mode = "REQUIRED" },
    { name = "subscriber", type = "STRING", mode = "REQUIRED" },
    { name = "severity", type = "STRING", mode = "REQUIRED" },
    { name = "score", type = "INT64", mode = "REQUIRED" },
    { name = "reason", type = "STRING", mode = "NULLABLE" },
    { name = "created_at", type = "TIMESTAMP", mode = "REQUIRED" },
  ])
}

# ---------------- Service account for Vertex AI and BigQuery access ----------------
resource "google_service_account" "app" {
  account_id   = "telesentinel-${var.environment}"
  display_name = "TeleSentinel ${var.environment} workloads"
}

resource "google_project_iam_member" "vertex_user" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${google_service_account.app.email}"
}

resource "google_project_iam_member" "bq_jobs" {
  project = var.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${google_service_account.app.email}"
}

resource "google_bigquery_dataset_iam_member" "bq_editor" {
  dataset_id = google_bigquery_dataset.analytics.dataset_id
  role       = "roles/bigquery.dataEditor"
  member     = "serviceAccount:${google_service_account.app.email}"
}

# ---------------- Keyless access from AKS pods (no service account keys) ----------------
resource "google_iam_workload_identity_pool" "aks" {
  count                     = var.aks_oidc_issuer_url == "" ? 0 : 1
  workload_identity_pool_id = "telesentinel-aks-${var.environment}"
  display_name              = "TeleSentinel AKS ${var.environment}"
  depends_on                = [google_project_service.apis]
}

resource "google_iam_workload_identity_pool_provider" "aks" {
  count                              = var.aks_oidc_issuer_url == "" ? 0 : 1
  workload_identity_pool_id          = google_iam_workload_identity_pool.aks[0].workload_identity_pool_id
  workload_identity_pool_provider_id = "aks"
  attribute_mapping = {
    "google.subject" = "assertion.sub"
  }
  oidc {
    issuer_uri = var.aks_oidc_issuer_url
  }
}

resource "google_service_account_iam_member" "federated" {
  count              = var.aks_oidc_issuer_url == "" ? 0 : 1
  service_account_id = google_service_account.app.name
  role               = "roles/iam.workloadIdentityUser"
  member = join("", [
    "principal://iam.googleapis.com/projects/${data.google_project.current.number}",
    "/locations/global/workloadIdentityPools/${google_iam_workload_identity_pool.aks[0].workload_identity_pool_id}",
    "/subject/system:serviceaccount:${var.k8s_namespace}:${var.k8s_service_account}",
  ])
}

# ---------------- Staging bucket for batch loads into BigQuery ----------------
resource "google_storage_bucket" "staging" {
  name                        = "${var.project_id}-telesentinel-${var.environment}-staging"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  labels                      = local.labels

  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type = "Delete"
    }
  }
}
