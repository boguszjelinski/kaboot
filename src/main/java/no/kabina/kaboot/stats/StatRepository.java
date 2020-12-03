package no.kabina.kaboot.stats;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StatRepository extends JpaRepository<Stat, Long> {

  Stat findById(long id);
  Stat findByName(String name);
}
