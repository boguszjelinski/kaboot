package no.kabina.kaboot.cabs;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CabRepository extends JpaRepository<Cab, Long> {

  Cab findById(long id);

  List<Cab> findByStatus(Cab.CabStatus status);

  List<Cab> findByLocationAndStatus(int location, Cab.CabStatus status);

  @Query("select o.status, count(o.status) from Cab o group by o.status order by o.status")
  List<Object[]> countStatus();
}
