package no.kabina.kaboot.orders;

import java.util.Optional;
import no.kabina.kaboot.customers.Customer;
import no.kabina.kaboot.customers.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxiOrderService {

  private final TaxiOrderRepository repository;
  private final CustomerRepository customerRepository;

  public TaxiOrderService(TaxiOrderRepository repository, CustomerRepository customerRepository) {
    this.repository = repository;
    this.customerRepository = customerRepository;
  }

  @Transactional
  public TaxiOrder saveTaxiOrder(TaxiOrder order, Long cust_id) {
    Optional<Customer> cust = customerRepository.findById(cust_id);
    order.setCustomer(cust.get());
    repository.save(order);
    //Long lastId = order.getId();
    return order;
  }

  @Transactional
  public TaxiOrder updateTaxiOrder(TaxiOrder order, Long custId) {
    Optional<Customer> cust = customerRepository.findById(custId);
    if (cust.isEmpty() || cust.get().getId().longValue() != order.getCustomer().getId().longValue()) { //not authorised
      return null;
    }
    return repository.save(order);
  }
}
