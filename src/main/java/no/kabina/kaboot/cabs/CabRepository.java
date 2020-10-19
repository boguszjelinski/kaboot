package no.kabina.kaboot.cabs;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CabRepository extends JpaRepository<Cab, Long> {
        Cab findById(long id);
}
