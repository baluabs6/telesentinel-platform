data "aws_availability_zones" "available" {
  state = "available"
}
data "aws_caller_identity" "current" {}

locals {
  name = "${var.prefix}-dr"
  azs  = slice(data.aws_availability_zones.available.names, 0, 2)
}

# ---------------- Network ----------------
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.13"

  name = local.name
  cidr = var.vpc_cidr
  azs  = local.azs

  private_subnets = [cidrsubnet(var.vpc_cidr, 4, 0), cidrsubnet(var.vpc_cidr, 4, 1)]
  public_subnets  = [cidrsubnet(var.vpc_cidr, 8, 200), cidrsubnet(var.vpc_cidr, 8, 201)]

  enable_nat_gateway = true
  single_nat_gateway = true # cost saving for a standby site

  public_subnet_tags  = { "kubernetes.io/role/elb" = "1" }
  private_subnet_tags = { "kubernetes.io/role/internal-elb" = "1" }
}

# ---------------- EKS: pilot light (zero nodes until failover) ----------------
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.24"

  cluster_name    = local.name
  cluster_version = var.eks_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  cluster_endpoint_public_access       = true
  cluster_endpoint_public_access_cidrs = var.api_allowed_cidrs

  enable_cluster_creator_admin_permissions = true

  eks_managed_node_groups = {
    dr = {
      instance_types = var.node_instance_types
      min_size       = 0
      max_size       = var.failover_max_nodes
      desired_size   = var.pilot_light_nodes
    }
  }
}

# ---------------- Image registry (images are pushed here by CI on every release) ----------------
resource "aws_ecr_repository" "svc" {
  for_each             = toset(var.services)
  name                 = "telesentinel/${each.value}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "svc" {
  for_each   = aws_ecr_repository.svc
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 30 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 30 }
      action       = { type = "expire" }
    }]
  })
}

# ---------------- PostgreSQL (pgvector via CREATE EXTENSION vector) ----------------
resource "aws_security_group" "data" {
  name        = "${local.name}-data"
  description = "Postgres and Redis from inside the VPC only"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description = "PostgreSQL"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
  ingress {
    description = "Redis"
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
}

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = module.vpc.private_subnets
}

resource "aws_db_instance" "postgres" {
  identifier        = "${local.name}-pg"
  engine            = "postgres"
  engine_version    = "16"
  instance_class    = var.db_instance_class
  allocated_storage = var.db_storage_gb
  storage_encrypted = true

  db_name                     = "telesentinel"
  username                    = "tsadmin"
  manage_master_user_password = true # stored in Secrets Manager

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data.id]
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period   = 7
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-pg-final"
}

# ---------------- Redis (counters are short-lived, so DR only needs an empty cache) ----------------
resource "aws_elasticache_subnet_group" "main" {
  name       = local.name
  subnet_ids = module.vpc.private_subnets
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${local.name}-redis"
  description                = "TeleSentinel DR Redis"
  engine                     = "redis"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 1
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.data.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}

# ---------------- S3 backups (Mongo dumps, Postgres dumps, Terraform outputs) ----------------
resource "aws_s3_bucket" "backups" {
  bucket = "${var.prefix}-dr-backups-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "backups" {
  bucket                  = aws_s3_bucket.backups.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "backups" {
  bucket = aws_s3_bucket.backups.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    id     = "tiering-and-expiry"
    status = "Enabled"
    filter {}
    transition {
      days          = 30
      storage_class = "GLACIER_IR"
    }
    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

# ---------------- Route 53 failover: Azure primary, AWS secondary ----------------
resource "aws_route53_health_check" "primary" {
  count             = var.hosted_zone_id == "" ? 0 : 1
  fqdn              = var.primary_endpoint
  type              = "HTTPS"
  port              = 443
  resource_path     = "/actuator/health"
  failure_threshold = 3
  request_interval  = 30
}

resource "aws_route53_record" "primary" {
  count           = var.hosted_zone_id == "" ? 0 : 1
  zone_id         = var.hosted_zone_id
  name            = var.api_hostname
  type            = "CNAME"
  ttl             = 60
  set_identifier  = "azure-primary"
  records         = [var.primary_endpoint]
  health_check_id = aws_route53_health_check.primary[0].id

  failover_routing_policy {
    type = "PRIMARY"
  }
}

resource "aws_route53_record" "secondary" {
  count          = var.hosted_zone_id == "" || var.dr_endpoint == "" ? 0 : 1
  zone_id        = var.hosted_zone_id
  name           = var.api_hostname
  type           = "CNAME"
  ttl            = 60
  set_identifier = "aws-dr"
  records        = [var.dr_endpoint]

  failover_routing_policy {
    type = "SECONDARY"
  }
}
