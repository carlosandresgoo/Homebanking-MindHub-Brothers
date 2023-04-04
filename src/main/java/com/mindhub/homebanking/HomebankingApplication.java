package com.mindhub.homebanking;



import com.mindhub.homebanking.models.Account;
import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.repositories.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.mindhub.homebanking.repositories.ClientRepository;

import java.time.LocalDateTime;

@SpringBootApplication
public class HomebankingApplication {

	public static void main(String[] args) {

		SpringApplication.run(HomebankingApplication.class, args);
	}

	@Bean
	public CommandLineRunner initData(ClientRepository repository , AccountRepository repositorys) {
		return (args) -> {
			// save a couple of Client

			Client Client1 = new Client("Melba" , "Morel", "melba@gmail.com");
			repository.save(Client1);
//			Client Client2 = new Client("Chloe", "O'Brian" , "Chole@gmail.com");
//			repository.save(Client2);
			Account Account1 = new Account("vin001" , LocalDateTime.now() , 5000 , Client1);
			repositorys.save(Account1);
			Account Account2 = new Account("vin002" ,  LocalDateTime.now().plusDays(1) , 7500 , Client1 );
			repositorys.save(Account2);
//			Account Account3 = new Account("vin003" , LocalDateTime.now() , 8000 , Client2);
//			repositorys.save(Account3);
//			Account Account4 = new Account("vin004" ,  LocalDateTime.now().plusDays(1) , 9500 , Client2 );
//			repositorys.save(Account4);


		};
	}


}
