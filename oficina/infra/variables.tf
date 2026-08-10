variable "project_name" {
  description = "Prefixo usado no nome dos recursos"
  type        = string
  default     = "oficina"
}

variable "aws_region" {
  description = "Região AWS. Deve coincidir com a região do bucket de state em backend.tf"
  type        = string
  default     = "us-east-1"
}

variable "allowed_cidr" {
  description = "CIDR (IP do aluno, formato x.x.x.x/32) autorizado a acessar SSH (22) e a aplicação (80)"
  type        = string

  # Erra cedo, no plan, em vez de criar um security group inválido — e barra o
  # 0.0.0.0/0 acidental, que abriria SSH e a aplicação para a internet inteira.
  validation {
    condition     = can(cidrnetmask(var.allowed_cidr))
    error_message = "allowed_cidr deve ser um CIDR IPv4 válido, ex.: 203.0.113.10/32."
  }

  validation {
    condition     = var.allowed_cidr != "0.0.0.0/0"
    error_message = "0.0.0.0/0 exporia SSH e a aplicação ao mundo. Use o seu IP público em /32."
  }
}

variable "db_password" {
  description = "Senha do RDS — fornecer via TF_VAR_db_password, nunca hardcoded"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 16
    error_message = "A senha do RDS deve ter ao menos 16 caracteres."
  }
}

variable "instance_type" {
  description = "Tipo da EC2 que hospeda o k3s. t3.medium (2 vCPU / 4 GiB) comporta o cluster mais 4 réplicas da aplicação"
  type        = string
  default     = "t3.medium"
}

variable "db_instance_class" {
  description = "Classe da instância RDS"
  type        = string
  default     = "db.t4g.micro"
}
