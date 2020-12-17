package no.kabina.kaboot;

import no.kabina.kaboot.dispatcher.DispatcherService;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.cron.Cron;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class KabootApplication {

  /**
  * for JobRunr
  */
  @Bean
  @Profile("!test")  // tests fail - JobMapper not created; what about 'prod' ?
  public StorageProvider storageProvider(JobMapper jobMapper) {
    InMemoryStorageProvider storageProvider = new InMemoryStorageProvider();
    storageProvider.setJobMapper(jobMapper);
    return storageProvider;
  }

  /**
   * for JobRunr
   */

  @Bean
  @Profile("!test")
  public CommandLineRunner demo() {
    return args -> {
      BackgroundJob.scheduleRecurrently(
              "find-plan",
              DispatcherService::findPlan,
              Cron.minutely()
      );
      Thread.currentThread().join();
    };
  }

  public static void main(String[] args) {
    SpringApplication.run(KabootApplication.class, args);
  }

}
