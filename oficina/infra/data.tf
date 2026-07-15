# Conta AWS Academy bloqueia iam:CreateRole/CreatePolicy — nenhum "resource aws_iam_role" existe
# neste módulo. O LabInstanceProfile já vem com as permissões necessárias (EC2, RDS, ECR)
# pré-anexadas pelo Academy e é o único IAM referenciado (ec2.tf). Se o nome divergir na sua
# conta, ajuste o "name" abaixo — "terraform plan" falha com NoSuchEntity e o nome errado fica
# explícito no erro.
data "aws_iam_instance_profile" "lab_instance_profile" {
  name = "LabInstanceProfile"
}

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_availability_zones" "available" {
  state = "available"
}
