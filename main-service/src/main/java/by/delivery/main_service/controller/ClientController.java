package by.delivery.main_service.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.delivery.main_service.dto.ClientResponse;
import by.delivery.main_service.dto.CreateClientRequest;
import by.delivery.main_service.dto.PageResponse;
import by.delivery.main_service.dto.UpdateClientRequest;
import by.delivery.main_service.service.ClientService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public PageResponse<ClientResponse> getAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return clientService.getAll(pageable);
    }

    @GetMapping("/me")
    public ClientResponse getMe(@RequestHeader("X-User-Id") Integer userId) {
        return clientService.getByUserId(userId);
    }

    @GetMapping("/{id}")
    public ClientResponse getById(@PathVariable Integer id) {
        return clientService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody CreateClientRequest request) {
        return clientService.create(request);
    }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable Integer id, @Valid @RequestBody UpdateClientRequest request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        clientService.delete(id);
    }
}
