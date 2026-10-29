package by.delivery.main_service.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.delivery.main_service.dto.ClientResponse;
import by.delivery.main_service.dto.CreateClientRequest;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.dto.UpdateClientRequest;
import by.delivery.main_service.repository.ClientRepository;
import by.delivery.main_service.repository.entity.Client;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> getAll(Pageable pageable) {
        return PageResponse.of(clientRepository.findAll(pageable).map(ClientResponse::from));
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Integer clientId) {
        return ClientResponse.from(load(clientId));
    }

    @Transactional(readOnly = true)
    public ClientResponse getByUserId(String userId) {
        return ClientResponse.from(clientRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Профиль клиента не найден: " + userId)));
    }

    @Transactional
    public ClientResponse create(CreateClientRequest request) {
        if (clientRepository.existsByUserId(request.userId())) {
            throw new IllegalStateException("Профиль клиента для пользователя " + request.userId() + " уже существует");
        }

        Client client = new Client();
        client.setUserId(request.userId());
        client.setFullName(request.fullName());
        client.setPhone(request.phone());
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(Integer clientId, UpdateClientRequest request) {
        Client client = load(clientId);
        client.setFullName(request.fullName());
        client.setPhone(request.phone());
        return ClientResponse.from(client);
    }

    @Transactional
    public void delete(Integer clientId) {
        clientRepository.delete(load(clientId));
        clientRepository.flush();
    }

    private Client load(Integer clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Клиент не найден: " + clientId));
    }
}
