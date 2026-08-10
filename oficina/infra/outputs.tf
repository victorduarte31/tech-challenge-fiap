output "k3s_public_ip" {
  description = "IP público da EC2 do k3s. A aplicação responde em http://<ip>/ via Ingress"
  value       = aws_eip.k3s.public_ip
}

output "application_url" {
  description = "URL base da API no ambiente provisionado"
  value       = "http://${aws_eip.k3s.public_ip}"
}

output "rds_endpoint" {
  description = "Host do RDS, usado no Secret oficina-secrets (DB_HOST)"
  value       = aws_db_instance.main.address
}

output "ecr_repository_url" {
  description = "URL do repositório ECR, usada na imagem do Deployment"
  value       = aws_ecr_repository.main.repository_url
}

# Recuperar quando necessário: terraform output -raw ssh_private_key > k3s-key.pem && chmod 600 k3s-key.pem
output "ssh_private_key" {
  description = "Chave privada de acesso SSH à EC2 (gerada pelo Terraform)"
  value       = tls_private_key.k3s.private_key_pem
  sensitive   = true
}
