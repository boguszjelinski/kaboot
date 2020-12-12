package no.kabina.kaboot.routes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {

  List<Route> findByCabIdAndStatus(Long cabId, Route.RouteStatus status);
}