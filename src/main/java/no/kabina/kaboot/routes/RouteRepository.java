package no.kabina.kaboot.routes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByCabId(int cab_id);

    Route findById(long id);
}