package no.kabina.kaboot.cabs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CabRepository extends JpaRepository<Cab, Long> {

        Cab findById(long id);

        List<Cab> findByStatus(Cab.CabStatus status);
}
