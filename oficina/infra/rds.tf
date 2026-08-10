resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  tags       = { Name = "${var.project_name}-db-subnet-group" }
}

resource "aws_db_instance" "main" {
  identifier             = "${var.project_name}-postgres"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  storage_type           = "gp3"
  db_name                = "oficina_db"
  username               = "oficina_admin"
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # Criptografia em repouso com a chave gerenciada da AWS. O default do provider é
  # false — o banco guarda CPF/CNPJ, e-mail e telefone de clientes, então isto não é
  # opcional. Custo zero com a chave padrão do serviço.
  storage_encrypted = true

  # Só aceita conexão da EC2 do k3s, pela rede interna da VPC (ver security_groups.tf).
  publicly_accessible = false

  # ---------------------------------------------------------------------------
  # Os três abaixo são valores de LABORATÓRIO, declarados explicitamente para que a
  # escolha fique visível no código em vez de virem por omissão do provider:
  #
  #   multi_az            = false → sem réplica em outra AZ. Um failover de AZ derruba
  #                                 o banco. Em produção: true (dobra o custo).
  #   backup_retention... = 0     → sem backup automático; a infraestrutura é destruída
  #                                 ao fim de cada sessão. Em produção: 7 a 35 dias.
  #   deletion_protection = false → permite `terraform destroy`, que aqui é obrigatório
  #                                 ao final de cada sessão. Em produção: true.
  # ---------------------------------------------------------------------------
  multi_az                = false
  backup_retention_period = 0
  deletion_protection     = false
  skip_final_snapshot     = true

  # Correções de segurança do PostgreSQL entram na próxima janela de manutenção sem
  # intervenção — o oposto (ficar preso a um patch vulnerável) é o risco maior.
  auto_minor_version_upgrade = true

  tags = { Name = "${var.project_name}-postgres" }
}
