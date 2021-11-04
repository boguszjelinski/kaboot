package no.kabina.kaboot.orders;

import java.time.LocalDateTime;
import java.util.List;
import no.kabina.kaboot.routes.Leg;
import no.kabina.kaboot.routes.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxiOrderRepository extends JpaRepository<TaxiOrder, Long> {

  TaxiOrder findById(long id);
  List<TaxiOrder> findByFromStand(int fromStand);
  List<TaxiOrder> findByStatus(TaxiOrder.OrderStatus status);
  List<TaxiOrder> findByLeg(Leg leg);
  List<TaxiOrder> findByRoute(Route route);

  @Query("select o from TaxiOrder o where o.status = :status and (o.atTime is NULL or o.atTime < :time)")
  List<TaxiOrder> findByStatusAndTime(@Param("status") TaxiOrder.OrderStatus status,
                                      @Param("time")LocalDateTime atTime);
}
