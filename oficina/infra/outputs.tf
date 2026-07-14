output "k3s_public_ip" {
  value = aws_eip.k3s.public_ip
}

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "ecr_repository_url" {
  value = aws_ecr_repository.main.repository_url
}

# Recuperar quando necessário: terraform output -raw ssh_private_key > k3s-key.pem && chmod 600 k3s-key.pem
output "ssh_private_key" {
  value     = tls_private_key.k3s.private_key_pem
  sensitive = true
}
