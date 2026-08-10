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
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.k3s.id]
  iam_instance_profile   = data.aws_iam_instance_profile.lab_instance_profile.name
  key_name               = aws_key_pair.k3s.key_name

  root_block_device {
    volume_type = "gp3"
    volume_size = 20

    # O disco do node guarda o etcd do k3s (que contém os Secrets do cluster: senha
    # do banco, chave privada do JWT, credencial do ECR) e o kubeconfig. Criptografar
    # com a chave gerenciada da AWS é gratuito; não fazê-lo deixaria tudo isso em
    # texto claro num snapshot ou volume órfão.
    encrypted = true
  }

  # IMDSv2 obrigatório: sem isto, qualquer SSRF na aplicação (ou num pod) alcança
  # http://169.254.169.254 com um simples GET e lê as credenciais temporárias do
  # instance profile. Com http_tokens=required é preciso um PUT prévio para obter o
  # token, o que a maioria dos vetores de SSRF não consegue fazer.
  # hop_limit=1 impede que o tráfego de dentro de um container alcance o IMDS.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "enabled"
  }

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    k3s_public_ip = aws_eip.k3s.public_ip
  })

  tags = { Name = "${var.project_name}-k3s" }
}

resource "aws_eip_association" "k3s" {
  instance_id   = aws_instance.k3s.id
  allocation_id = aws_eip.k3s.id
}
