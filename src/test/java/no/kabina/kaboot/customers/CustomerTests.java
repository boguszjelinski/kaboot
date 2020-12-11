package no.kabina.kaboot.customers;

import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class CustomerTests {

    @Test
    public void test1() throws Exception {
        Customer cust = new Customer();
        assertThat(cust != null).isTrue();
    }
}
