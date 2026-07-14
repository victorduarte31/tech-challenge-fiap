# Conta AWS Academy bloqueia iam:CreateRole/CreatePolicy — nenhum "resource aws_iam_role" existe
# neste módulo. LabRole/LabInstanceProfile já vêm com as permissões necessárias (EC2, RDS, ECR)
# pré-anexadas pelo Academy. Se o nome divergir na sua conta, ajuste os dois "name" abaixo —
# "terraform plan" falha com NoSuchEntity e o nome errado fica explícito no erro.
data "aws_iam_role" "lab_role" {
  name = "LabRole"
}

data "aws_iam_instance_profile" "lab_instance_profile" {
  name = "LabInstanceProfile"
}

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_availability_zones" "available" {
  state = "available"
}
