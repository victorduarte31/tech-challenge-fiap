package br.com.oficina.application.service;

import br.com.oficina.application.Pagination;
import br.com.oficina.application.dto.ClientRequestDto;
import br.com.oficina.application.dto.ClientResponseDto;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.exception.ResourceNotFoundException;
import br.com.oficina.domain.model.Client;
import br.com.oficina.domain.ports.out.ClientRepositoryPort;
import br.com.oficina.infrastructure.validation.CpfCnpjUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class ClientService {

    public static final String CLIENTE = "Cliente";
    ClientRepositoryPort clientRepository;

    public ClientService(ClientRepositoryPort clientRepository) {
        this.clientRepository = clientRepository;
    }

    public List<ClientResponseDto> listAll(int page, int size) {
        return clientRepository.listAll(Pagination.page(page), Pagination.cap(size)).stream()
            .map(ClientResponseDto::from)
            .toList();
    }

    public ClientResponseDto findById(Long id) {
        Client client = clientRepository.fetchById(id)
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
        Client client = new Client(dto.name(), normalized, dto.clientType(), dto.email(), dto.phone());
        return ClientResponseDto.from(clientRepository.save(client));
    }

    @Transactional
    public ClientResponseDto update(Long id, ClientRequestDto dto) {
        Client client = clientRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(CLIENTE, id));

        String normalized = CpfCnpjUtils.normalize(dto.cpfCnpj());
        if (!client.getCpfCnpj().equals(normalized) && clientRepository.existsByCpfCnpj(normalized)) {
            throw new BusinessException("CPF/CNPJ já cadastrado: " + dto.cpfCnpj());
        }
        client.update(dto.name(), normalized, dto.clientType(), dto.email(), dto.phone());
        return ClientResponseDto.from(clientRepository.save(client));
    }

    @Transactional
    public void delete(Long id) {
        clientRepository.fetchById(id)
            .orElseThrow(() -> new ResourceNotFoundException(CLIENTE, id));
        clientRepository.removeById(id);
    }
}
