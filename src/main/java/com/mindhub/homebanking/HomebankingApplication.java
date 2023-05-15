package com.mindhub.homebanking;



import com.mindhub.homebanking.models.*;
import com.mindhub.homebanking.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootApplication
public class HomebankingApplication {

	public static void main(String[] args) {

		SpringApplication.run(HomebankingApplication.class, args);

	}

	List<Integer> mortgage = List.of(12, 24, 36 , 48 , 60);
	List<Integer> personal = List.of(6, 12, 24);
	List<Integer> automotive = List.of(6, 12, 24,36);
	@Autowired
	private PasswordEncoder passwordEncoder;


	@Bean
	public CommandLineRunner initData(ClientRepository repository , AccountRepository repositorys , TransactionRepository transaction , LoanRepository loan, ClientLoanRepository clientloan , CardRepository cardrepository) {
		return (args) -> {
			// save a couple of Client

				Client client1 = new Client("Melba" , "Morel", "melba@gmail.com",passwordEncoder.encode("123"));
			repository.save(client1);

			Account account1 = new Account("VIN-001" , LocalDateTime.now() , 5000.00,true );
			client1.addAccount(account1);
			repositorys.save(account1);
			Account account2 = new Account("VIN-002" ,  LocalDateTime.now().plusDays(1) , 7500.00,true );
			client1.addAccount(account2);
			repositorys.save(account2);

			Transaction transaction1 = new Transaction( TransactionType.CREDIT,4203.17,"TRAGO", LocalDateTime.now(),true, account1.getBalance() );
			account1.addTransaction(transaction1);
			transaction.save(transaction1);
			Transaction transaction2 = new Transaction(TransactionType.DEBIT,3330.37,"HERRAMIENTAS", LocalDateTime.now(),true, account1.getBalance() );
			account2.addTransaction(transaction2);
			transaction.save(transaction2);
			Transaction transaction3 = new Transaction( TransactionType.DEBIT,4203.17,"COMIDA", LocalDateTime.now(),true, account1.getBalance() );
			account1.addTransaction(transaction3);
			transaction.save(transaction3);
			Transaction transaction4 = new Transaction(TransactionType.CREDIT,3330.37,"PASAJE", LocalDateTime.now(),true, account1.getBalance() );
			account2.addTransaction(transaction4);
			transaction.save(transaction4);


			Loan loan1 = new Loan("Mortgage", 500000 , mortgage );
			loan.save(loan1);
			Loan loan2 = new Loan("Personal", 100000 , personal );
			loan.save(loan2);
			Loan loan3 = new Loan("Automotive", 300000 , automotive);
			loan.save(loan3);

			ClientLoan clientLoan1 = new ClientLoan( 400000.00,60, 400000.00+(400000.00*0.2));
			client1.addClientLoan(clientLoan1);
			loan1.addClientLoan(clientLoan1);
			clientloan.save(clientLoan1);
			ClientLoan clientLoan2 = new ClientLoan( 50000.00,12 , 50000.00+(50000.00*0.2));
			client1.addClientLoan(clientLoan2);
			loan2.addClientLoan(clientLoan2);
			clientloan.save(clientLoan2);

			Card card1 =new Card (client1.getFirtsName() + " " + client1.getLastName() , CardType.DEBIT, CardColor.GOLD, "6506-9042-4354-1232",345 , LocalDate.now().plusYears(5), LocalDate.now(), true);
			client1.addCard(card1);
			cardrepository.save(card1);
			Card card2 =new Card (client1.getFirtsName() + " " + client1.getLastName() , CardType.CREDIT, CardColor.TITANIUM, "6506-4343-4354-1232",654 , LocalDate.now().plusYears(5), LocalDate.now(),true);
			client1.addCard(card2);
			cardrepository.save(card2);


			// Client 2

			Client client2 = new Client("Chloe", "O'Brian" , "Chole@gmail.com",passwordEncoder.encode("234"));
			repository.save(client2);

			Account account3 = new Account("VIN-003" , LocalDateTime.now() , 432000.00,true );
			client2.addAccount(account3);
			repositorys.save(account3);
			Account account4 = new Account("VIN-004" ,  LocalDateTime.now().plusDays(1) , 323230.00,true );
			client2.addAccount(account4);
			repositorys.save(account4);

			Transaction transaction5 = new Transaction( TransactionType.CREDIT,4203.17,"TRAGO", LocalDateTime.now(),true, account2.getBalance() );
			account3.addTransaction(transaction5);
			transaction.save(transaction5);
			Transaction transaction6 = new Transaction(TransactionType.DEBIT,3330.37,"HERRAMIENTAS", LocalDateTime.now(),true, account2.getBalance() );
			account4.addTransaction(transaction6);
			transaction.save(transaction6);
			Transaction transaction7 = new Transaction( TransactionType.DEBIT,4203.17,"COMIDA", LocalDateTime.now(),true, account2.getBalance() );
			account3.addTransaction(transaction7);
			transaction.save(transaction7);
			Transaction transaction8 = new Transaction(TransactionType.CREDIT,3330.37,"PASAJE", LocalDateTime.now(),true, account2.getBalance() );
			account4.addTransaction(transaction8);
			transaction.save(transaction8);



			ClientLoan clientLoan3 = new ClientLoan(100000.00, 24,100000.00+(100000.00*0.2) );
			client2.addClientLoan(clientLoan3);
			loan2.addClientLoan(clientLoan3);
			clientloan.save(clientLoan3);
			ClientLoan clientLoan4 = new ClientLoan(200000.00, 36, 200000.00+(200000.00*0.2)  );
			client2.addClientLoan(clientLoan4);
			loan3.addClientLoan(clientLoan4);
			clientloan.save(clientLoan4);


			Card card3 =new Card (client2.getFirtsName() + " " + client2.getLastName( ), CardType.CREDIT, CardColor.SILVER, "5632-9042-4354-4311",986 , LocalDate.now().plusYears(5), LocalDate.now(),true);
			client2.addCard(card3);
			cardrepository.save(card3);


			repository.save(client1);

		};
	}



}
