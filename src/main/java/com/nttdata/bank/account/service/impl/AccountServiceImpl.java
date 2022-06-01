package com.nttdata.bank.account.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nttdata.bank.account.client.CustomerClientRest;
import com.nttdata.bank.account.client.ProductClientRest;
import com.nttdata.bank.account.client.TransactionClientRest;
import com.nttdata.bank.account.dto.AccountDTO;
import com.nttdata.bank.account.model.Account;
import com.nttdata.bank.account.model.Product;
import com.nttdata.bank.account.repository.AccountRepository;
import com.nttdata.bank.account.service.AccountService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AccountServiceImpl implements AccountService{
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private TransactionClientRest transactionClientRest;
	
	@Autowired
	private ProductClientRest productClientRest;
	
	@Autowired
	private CustomerClientRest customerClientRest;
	
	@Autowired
	private JsonMapper jsonMapper;

	@Override
	public Flux<AccountDTO> findByCustomerId(String customerId) {
		
		Flux<AccountDTO> accountDTO = accountRepository.findAccountByCustomerId(customerId).map(a -> convertirAAccountDTO(a));
		
		return accountDTO.flatMap( account -> 
										Mono.just(account)
										.zipWith(transactionClientRest.findByAccountId(account.get_id())
													.collectList(),
													(a, t) -> {
														a.setTransactions(t);
														return a;
													}
												)
										.zipWith(productClientRest.findById(account.getProductId())
													,(a, p) -> {
														a.setProduct(p);
														return a;
													}
												)
								);
		
	}

	@Override
	public Mono<Account> save(Account account) {		
		return customerClientRest.showCustomerInformationById(account.getCustomerId())
						.flatMap( customer -> {
							Mono<Account> accountMono = Mono.empty();
							if(customer.getType().equals("Personal")) {						
								accountMono = accountRepository.findAccountByCustomerId(account.getCustomerId())
									.any(a -> a.getProductId().equals(account.getProductId()))
									.flatMap(value ->
										(value) ? productClientRest.findById(account.getProductId())
													.filter(product -> product.getName().equals("Plazo fijo"))
													.switchIfEmpty(Mono.error(new Exception("Ya existe una cuenta con ese producto")))
													.flatMap(product -> accountRepository.save(account))
												//: accountRepository.save(account));
												: productClientRest.findById(account.getProductId())
													.flatMap(p -> saveVipOrPyme(p, account, 1))
									);
								
													
							}
							if(customer.getType().equals("Empresarial")) {
								accountMono = productClientRest.findById(account.getProductId())
												.filter(product -> product.getName().equals("Cuenta corriente") || product.getName().equals("Tarjeta de crédito"))
												.switchIfEmpty(Mono.error(new Exception("Un cliente empresarial solo puede tener cuenta corriente o tarjetas de crédito.")))
												.flatMap(p -> saveVipOrPyme(p, account, 2));
							}
					
					return accountMono;
				}
			 );
	}
	
	
	@Override
	public Mono<Account> updateBalance(String id, Double balance, String type) {
		return accountRepository.findById(id)
				.flatMap(a -> {
					Mono<Account> accountMono = Mono.empty();
					Double newBalance = 0D;
					if(type.equals("1")) {
						newBalance = a.getBalance() + balance;
						a.setBalance(newBalance);
						accountMono = accountRepository.save(a);
					}
					if(type.equals("2")) {
						
						/*if(a.getBalance() < balance) {
							accountMono = Mono.error(new Exception("No tiene saldo suficiente."));
						} else {
							newBalance = a.getBalance() - balance;
							a.setBalance(newBalance);
							accountMono = accountRepository.save(a);
						}*/
						if(a.getBalance() > balance) {
							newBalance = a.getBalance() - balance;
							a.setBalance(newBalance);
							accountMono = accountRepository.save(a);
						}
					}
					return accountMono;
				});
	}
	
	
	public Mono<Account> saveVipOrPyme(Product product, Account account, int type){
		String tarjetaCredito = "62794e96303edb362daa4c34";
	    Mono<Account> accountMono = Mono.empty();
	    if(type == 1) {
	    	if(product.getType().equals("VIP")) {
	    		accountMono = accountRepository.findAccountByCustomerId(account.getCustomerId())
	    				.any(a -> a.getProductId().equals(tarjetaCredito))
	    				.flatMap(value -> 
	    					(value) ? accountRepository.save(account)
	    							: Mono.error(new Exception("Debe tener una tarjeta de credito, para sacar una cuenta VIP")));
	        }else if(product.getType().equals("PYME")) {
	    		accountMono = Mono.error(new Exception("Un cliente personal no puede tener el producto PYME."));
	    	} else{
	            accountMono = accountRepository.save(account);
	        }
	    }
	    if(type == 2) {
	    	if(product.getType().equals("PYME")) {
	    		accountMono = accountRepository.findAccountByCustomerId(account.getCustomerId())
	                    .any( a -> a.getProductId().equals(tarjetaCredito) )
	                    .flatMap(value ->
	                    	(value) ? accountRepository.save(account)
	                                : Mono.error(new Exception("Debe tener una tarjeta de credito, para sacar una cuenta PYME"))
	                    );
	        }else if(product.getType().equals("VIP")) {
	    		accountMono = Mono.error(new Exception("Un cliente empresarial no puede tener el producto VIP."));
	    	} else{
	            accountMono = accountRepository.save(account);
	        }
	    }
	    return accountMono;
	}
	
	
	private AccountDTO convertirAAccountDTO(Account account) {
		return jsonMapper.convertValue(account, AccountDTO.class);
	}

}
