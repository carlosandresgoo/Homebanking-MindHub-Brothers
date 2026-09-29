package com.mindhub.homebanking.repository;


import com.mindhub.homebanking.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource

public interface AccountRepository extends JpaRepository <Account, Long>  {

}