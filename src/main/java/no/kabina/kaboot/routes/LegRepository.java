package no.kabina.kaboot.routes;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegRepository extends JpaRepository<Leg, Long> {

  List<Leg> findByStatusOrderByRouteAscPlaceAsc(Route.RouteStatus status);

  List<Leg> findByFromStandAndStatus(int fromStand, Route.RouteStatus status);

  List<Leg> findByToStandAndStatus(int toStand, Route.RouteStatus status);
}