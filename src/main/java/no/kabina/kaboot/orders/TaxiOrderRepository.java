package no.kabina.kaboot.orders;

import java.util.List;
import no.kabina.kaboot.routes.Leg;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxiOrderRepository extends JpaRepository<TaxiOrder, Long> {

  List<TaxiOrder> findByFromStand(int fromStand);

  List<TaxiOrder> findByStatus(TaxiOrder.OrderStatus status);

  List<TaxiOrder> findByLeg(Leg leg);

  TaxiOrder findById(long id);
}
