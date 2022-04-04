package no.kabina.kaboot;

import java.util.concurrent.Executor;
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
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
public class KabootApplication {

  /**
  * for JobRunr.
  */
  @Bean
  @Profile("!test")  // tests fail - JobMapper not created; what about 'prod' ?
  public StorageProvider storageProvider(JobMapper jobMapper) {
    InMemoryStorageProvider storageProvider = new InMemoryStorageProvider();
    storageProvider.setJobMapper(jobMapper);
    return storageProvider;
  }

  /** for JobRunr.
   */

  @Bean
  @Profile("!test")
  public CommandLineRunner demo() {
    return args -> {
      BackgroundJob.scheduleRecurrently(
              "find-plan",
              DispatcherService::runPlan,
              Cron.minutely()
      );
      Thread.currentThread().join();
    };
  }

  /** Smp Pool.
   */
  @Bean
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("KabootPool-");
    executor.initialize();
    return executor;
  }

  public static void main(String[] args) {
    SpringApplication.run(KabootApplication.class, args).close();
  }
}
