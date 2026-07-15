# Bucket criado uma única vez, FORA deste módulo (bootstrap manual, ver README.md).
# Troque o valor de "bucket" pelo nome escolhido no bootstrap antes do primeiro "terraform init".
terraform {
  backend "s3" {
    bucket = "victor-duarte-mendonca-oficina-tfstate"
    key    = "oficina/terraform.tfstate"
    region = "us-east-1"
  }
}
