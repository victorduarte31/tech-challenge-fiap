# force_delete = true: sem isso, "terraform destroy" falha se houver imagem dentro do repositório,
# exigindo remoção manual antes — risco real de virar recurso órfão numa sessão apressada de 4h.
resource "aws_ecr_repository" "main" {
  name                 = "${var.project_name}-app"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  tags = { Name = "${var.project_name}-ecr" }
}
