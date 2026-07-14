variable "project_name" {
  description = "Prefixo usado no nome dos recursos"
  type        = string
  default     = "oficina"
}

variable "allowed_cidr" {
  description = "CIDR (IP do aluno, formato x.x.x.x/32) autorizado a acessar SSH (22) e a API do k3s (6443)"
  type        = string
}

variable "db_password" {
  description = "Senha do RDS — fornecer via TF_VAR_db_password, nunca hardcoded"
  type        = string
  sensitive   = true
}
