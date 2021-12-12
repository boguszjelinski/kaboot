package no.kabina.kaboot.orders;

import java.util.Optional;
import no.kabina.kaboot.customers.Customer;
import no.kabina.kaboot.customers.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaxiOrderService {
  private final Logger logger = LoggerFactory.getLogger(TaxiOrderService.class);

  private final TaxiOrderRepository taxiOrderRepository;
  private final CustomerRepository customerRepository;

  public TaxiOrderService(TaxiOrderRepository taxiOrderRepository, CustomerRepository customerRepository) {
    this.taxiOrderRepository = taxiOrderRepository;
    this.customerRepository = customerRepository;
  }

  public TaxiOrder saveTaxiOrder(TaxiOrder order, Long custId) {
    Optional<Customer> cust = customerRepository.findById(custId);
    if (cust.isEmpty()) {
      logger.warn("Customer not found, cust_id={}", custId);
      return null;
    }
    order.setCustomer(cust.get());
    return taxiOrderRepository.save(order);
  }

  public TaxiOrder updateTaxiOrder(TaxiOrder order) {
    return taxiOrderRepository.save(order);
  }
}
