package no.kabina.kaboot.routes;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RouteController {

    private final RouteRepository repository;

    public RouteController(RouteRepository repository) {
        this.repository = repository;
    }

    /*@GetMapping("/orders/{id}")
    public String one(@PathVariable int id) {
        TaxiOrder taxiOrder = repository.findById(id);
        return taxiOrder.toString();
    }*/

    @GetMapping(path="/routes", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Route> getValidRouteByCab(@PathVariable int cabId) {
        return repository.findByCabId(cabId);
    }
}
