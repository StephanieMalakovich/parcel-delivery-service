package by.delivery.main_service.dto;

import by.delivery.main_service.repository.entity.Client;

public record ClientResponse(Integer id, Integer userId, String fullName, String phone) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(client.getId(), client.getUserId(), client.getFullName(), client.getPhone());
    }
}
