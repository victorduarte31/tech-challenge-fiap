# Par de chaves gerado pelo próprio Terraform — não importa uma chave que o aluno já teria, evita a
# precondição "já ter uma chave SSH configurada localmente" em cada sessão nova. A EC2 é recriada a
# cada sessão (destruída no terraform destroy), a chave é recriada junto.
resource "tls_private_key" "k3s" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "k3s" {
  key_name   = "${var.project_name}-k3s-key"
  public_key = tls_private_key.k3s.public_key_openssh
}

# Alocado independente da instância — sem dependência circular: o IP já existe antes da EC2 subir,
# então user_data pode referenciá-lo diretamente via templatefile().
resource "aws_eip" "k3s" {
  domain = "vpc"
  tags   = { Name = "${var.project_name}-k3s-eip" }
}

resource "aws_instance" "k3s" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = "t3.medium"
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.k3s.id]
  iam_instance_profile   = data.aws_iam_instance_profile.lab_instance_profile.name
  key_name               = aws_key_pair.k3s.key_name

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    k3s_public_ip = aws_eip.k3s.public_ip
  })

  tags = { Name = "${var.project_name}-k3s", Project = var.project_name }
}

resource "aws_eip_association" "k3s" {
  instance_id   = aws_instance.k3s.id
  allocation_id = aws_eip.k3s.id
}
