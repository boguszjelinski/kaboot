package no.kabina.kaboot.routes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Leg, Long> {

    List<Leg> findByRouteId(Long routeId);
}