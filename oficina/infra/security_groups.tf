resource "aws_security_group" "k3s" {
  name_prefix = "${var.project_name}-k3s-"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.allowed_cidr]
  }

  # Porta que o Traefik (ingress controller embarcado no k3s) publica no node.
  # Sem esta regra a aplicação só era alcançável por "kubectl port-forward" — o
  # Service é ClusterIP e nada no security group abria caminho de fora.
  # Restrita ao IP do aluno: é um ambiente de laboratório sem TLS nem WAF na frente.
  ingress {
    description = "HTTP (Traefik/Ingress da aplicacao)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [var.allowed_cidr]
  }

  # A API do k3s (6443) NÃO é exposta. O acesso administrativo, inclusive o da
  # pipeline, passa por túnel do SSM Session Manager sobre a saída HTTPS da
  # instância (ver .github/workflows/ci-cd.yml). Isso elimina a necessidade de
  # reabrir o security group para o IP dinâmico do runner do GitHub.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Saida liberada: instalacao do k3s, pull do ECR e canal do SSM"
  }

  tags = { Name = "${var.project_name}-k3s-sg" }
}

resource "aws_security_group" "rds" {
  name_prefix = "${var.project_name}-rds-"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL apenas da EC2 do k3s"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.k3s.id]
  }

  # O RDS não inicia conexões: sem rota para a internet (subnets privadas sem NAT)
  # e sem necessidade de saída. Deixar 0.0.0.0/0 aqui seria permissão sem uso.
  egress {
    description     = "Respostas ao security group do k3s"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.k3s.id]
  }

  tags = { Name = "${var.project_name}-rds-sg" }
}
