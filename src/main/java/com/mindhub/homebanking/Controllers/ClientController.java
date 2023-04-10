package com.mindhub.homebanking.Controllers;


import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import static java.util.stream.Collectors.toList;


@RestController
    public class ClientController {

        @Autowired
        private ClientRepository repository;

        @RequestMapping("/api/clients")
        public List<ClientDTO> getClient() {
            return repository.findAll().stream().map(client -> new ClientDTO(client)).collect(toList());
        }

    @RequestMapping("/api/clients/{id}")
    public ClientDTO getClient (@PathVariable Long id){
        return repository.findById(id).map(client -> new ClientDTO(client)).orElse(null);
    }

    }

