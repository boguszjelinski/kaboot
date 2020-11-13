package no.kabina.kaboot.orders;

import java.util.Optional;
import no.kabina.kaboot.customers.Customer;
import no.kabina.kaboot.customers.CustomerRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxiOrderService {
  private Logger logger = LoggerFactory.getLogger(TaxiOrderService.class);

  private final TaxiOrderRepository repository;
  private final CustomerRepository customerRepository;

  public TaxiOrderService(TaxiOrderRepository repository, CustomerRepository customerRepository) {
    this.repository = repository;
    this.customerRepository = customerRepository;
  }

  @Transactional
  public TaxiOrder saveTaxiOrder(TaxiOrder order, Long cust_id) {
    Optional<Customer> cust = customerRepository.findById(cust_id);
    if (cust.get() == null) {
        logger.warn("Customer not found, cust_id=" + cust_id);
        return null;
    }
    order.setCustomer(cust.get());
    return repository.save(order);
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
