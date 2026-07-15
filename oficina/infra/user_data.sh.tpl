#!/bin/bash
set -euxo pipefail

# Redundante em algumas AMIs Amazon Linux 2023 (já vem pré-instalado), mantido explícito para não
# depender de suposição que pode mudar entre variantes da AMI.
dnf install -y aws-cli

# --tls-san é essencial: sem isso, o certificado autoassinado do k3s só cobre os IPs conhecidos no
# momento da instalação (IP privado, 127.0.0.1) — o Elastic IP é associado por um recurso Terraform
# separado, então o k3s nunca saberia que precisa incluir esse IP no certificado, e todo acesso via
# kubectl/kubeconfig externo falharia com "certificate is valid for X, not <eip>".
curl -sfL https://get.k3s.io | sh -s - --write-kubeconfig-mode 644 --tls-san ${k3s_public_ip}

mkdir -p /home/ec2-user/.kube
cp /etc/rancher/k3s/k3s.yaml /home/ec2-user/.kube/config
sed -i "s/127.0.0.1/${k3s_public_ip}/" /home/ec2-user/.kube/config
chown -R ec2-user:ec2-user /home/ec2-user/.kube
