output "eks_cluster_name" { value = module.eks.cluster_name }
output "ecr_repositories" { value = { for k, r in aws_ecr_repository.svc : k => r.repository_url } }
output "postgres_endpoint" { value = aws_db_instance.postgres.address }
output "postgres_secret_arn" { value = aws_db_instance.postgres.master_user_secret[0].secret_arn }
output "redis_endpoint" { value = aws_elasticache_replication_group.redis.primary_endpoint_address }
output "backup_bucket" { value = aws_s3_bucket.backups.bucket }
