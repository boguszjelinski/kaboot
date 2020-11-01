package no.kabina.kaboot;

import no.kabina.kaboot.scheduler.SchedulerService;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class KabootApplication {

  /**
  * for JobRunr
  */
  @Bean
  public StorageProvider storageProvider(JobMapper jobMapper) {
    InMemoryStorageProvider storageProvider = new InMemoryStorageProvider();
    storageProvider.setJobMapper(jobMapper);
    return storageProvider;
  }

  /**
   * for JobRunr
   */
  @Bean
  public CommandLineRunner demo() {
    return (args) -> {
      BackgroundJob.scheduleRecurrently(
          "find-plan",
          SchedulerService::findPlan,
          Cron.minutely()
      );
      Thread.currentThread().join();
    };
  }

  public static void main(String[] args) {
    SpringApplication.run(KabootApplication.class, args);

  }
}
