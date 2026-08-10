# Bucket criado uma única vez, FORA deste módulo (bootstrap manual, ver README.md).
# Troque o valor de "bucket" pelo nome escolhido no bootstrap antes do primeiro "terraform init".
terraform {
  backend "s3" {
    bucket = "victor-duarte-mendonca-oficina-tfstate"
    key    = "oficina/terraform.tfstate"
    region = "us-east-1"

    # Trava de state nativa do S3 (Terraform >= 1.10): grava um .tflock ao lado do
    # state e falha rápido se outro apply estiver em andamento. Sem isto, dois
    # applies concorrentes — o da pipeline e o da sua máquina, cenário real neste
    # projeto — podem corromper o state. Substitui a antiga tabela DynamoDB, que a
    # conta AWS Academy nem sempre permite criar.
    use_lockfile = true

    # O bucket do backend guarda credenciais em texto no state (senha do RDS,
    # chave privada da EC2). Criptografia no lado do servidor é obrigatória.
    encrypt = true
  }
}
