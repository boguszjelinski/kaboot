package no.kabina.kaboot.routes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegRepository extends JpaRepository<Leg, Long> {

  List<Leg> findByRouteId(Long routeId);
  List<Leg> findByStatusOrderByRouteAscPlaceAsc(Route.RouteStatus status);
}