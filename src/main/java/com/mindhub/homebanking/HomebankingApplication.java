package com.mindhub.homebanking;



import com.mindhub.homebanking.models.Account;
import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.models.Transaction;
import com.mindhub.homebanking.models.TransactionType;
import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.repositories.TransactionRepository;
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
	public CommandLineRunner initData(ClientRepository repository , AccountRepository repositorys , TransactionRepository transaction) {
		return (args) -> {
			// save a couple of Client

			Client client1 = new Client("Melba" , "Morel", "melba@gmail.com");
			repository.save(client1);
//			Client Client2 = new Client("Chloe", "O'Brian" , "Chole@gmail.com");
//			repository.save(Client2);
			Account account1 = new Account("vin001" , LocalDateTime.now() , 5000.00 );
			client1.addAccount(account1);
			repositorys.save(account1);

			Account account2 = new Account("vin002" ,  LocalDateTime.now().plusDays(1) , 7500.00 );
			client1.addAccount(account2);
			repositorys.save(account2);
			Transaction transaction1 = new Transaction( TransactionType.CREDIT,4203.17,"Hola", LocalDateTime.now() );
			account1.addTransaction(transaction1);
			transaction.save(transaction1);
			Transaction transaction2 = new Transaction(TransactionType.DEBIT,3330.37,"Hola", LocalDateTime.now() );
			account2.addTransaction(transaction2);
			transaction.save(transaction2);
//			Account Account3 = new Account("vin003" , LocalDateTime.now() , 8000 , Client2);
//			repositorys.save(Account3);
//			Account Account4 = new Account("vin004" ,  LocalDateTime.now().plusDays(1) , 9500 , Client2 );
//			repositorys.save(Account4);


		};
	}


}
