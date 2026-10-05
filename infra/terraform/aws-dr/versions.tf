terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.60" }
  }
  # terraform init -backend-config=backend.hcl   (bucket, key, region, dynamodb_table)
  backend "s3" {}
}

provider "aws" {
  region = var.region
  default_tags {
    tags = {
      project    = "telesentinel"
      role       = "disaster-recovery"
      managed_by = "terraform"
    }
  }
}
