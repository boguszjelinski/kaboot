package no.kabina.kaboot.cabs;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CabRepository extends JpaRepository<Cab, Long> {

  Cab findById(long id);

  List<Cab> findByStatus(Cab.CabStatus status);
}
