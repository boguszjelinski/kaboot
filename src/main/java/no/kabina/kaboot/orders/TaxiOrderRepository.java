package no.kabina.kaboot.orders;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxiOrderRepository extends JpaRepository<TaxiOrder, Long> {

    List<TaxiOrder> findByFromStand(int fromStand);

    TaxiOrder findById(long id);
}