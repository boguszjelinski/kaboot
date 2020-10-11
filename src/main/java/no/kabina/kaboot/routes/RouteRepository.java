package no.kabina.kaboot.routes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByCabIdAndStatus(Long cabId, Route.RouteStatus status);
}