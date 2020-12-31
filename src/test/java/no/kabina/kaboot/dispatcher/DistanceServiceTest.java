package no.kabina.kaboot.dispatcher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DistanceServiceTest {

    @Test
    void getDistance() {
        assertThat(DistanceService.getDistance(0,1)).isSameAs(1);
    }
}