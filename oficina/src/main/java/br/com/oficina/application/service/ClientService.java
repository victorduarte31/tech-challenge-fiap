package br.com.oficina.application.service;

import br.com.oficina.application.dto.ClientRequestDto;
import br.com.oficina.application.dto.ClientResponseDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Client;
import br.com.oficina.infrastructure.repository.ClientRepository;
import br.com.oficina.infrastructure.validation.CpfCnpjUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class ClientService {

    public static final String CLIENTE = "Cliente";
    ClientRepository clientRepository;
    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public List<ClientResponseDto> listAll() {
        return clientRepository.listAll().stream()
            .map(ClientResponseDto::from)
            .toList();
    }

    public ClientResponseDto findById(Long id) {
        Client client = clientRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(CLIENTE, id));
        return ClientResponseDto.from(client);
    }

    public ClientResponseDto findByCpfCnpj(String cpfCnpj) {
        String normalized = CpfCnpjUtils.normalize(cpfCnpj);
        Client client = clientRepository.findByCpfCnpj(normalized)
            .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com CPF/CNPJ: " + cpfCnpj));
        return ClientResponseDto.from(client);
    }

    @Transactional
    public ClientResponseDto create(ClientRequestDto dto) {
        String normalized = CpfCnpjUtils.normalize(dto.cpfCnpj());
        if (clientRepository.existsByCpfCnpj(normalized)) {
            throw new BusinessException("CPF/CNPJ já cadastrado: " + dto.cpfCnpj());
        }
        Client client = new Client();
        client.name = dto.name();
        client.cpfCnpj = normalized;
        client.clientType = dto.clientType();
        client.email = dto.email();
        client.phone = dto.phone();
        clientRepository.persist(client);
        return ClientResponseDto.from(client);
    }

    @Transactional
    public ClientResponseDto update(Long id, ClientRequestDto dto) {
        Client client = clientRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(CLIENTE, id));

        String normalized = CpfCnpjUtils.normalize(dto.cpfCnpj());
        if (!client.cpfCnpj.equals(normalized) && clientRepository.existsByCpfCnpj(normalized)) {
            throw new BusinessException("CPF/CNPJ já cadastrado: " + dto.cpfCnpj());
        }
        client.name = dto.name();
        client.cpfCnpj = normalized;
        client.clientType = dto.clientType();
        client.email = dto.email();
        client.phone = dto.phone();
        return ClientResponseDto.from(client);
    }

    @Transactional
    public void delete(Long id) {
        Client client = clientRepository.findByIdOptional(id)
            .orElseThrow(() -> new ResourceNotFoundException(CLIENTE, id));
        clientRepository.delete(client);
    }
}
