package com.jobcrm.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulingConfig {

  @Bean(name = "scheduler", destroyMethod = "shutdown")
  ScheduledExecutorService schedulingExecutorService() {
    return Executors.newScheduledThreadPool(4);
  }

  @Bean(name = "agentDispatcher", destroyMethod = "shutdown")
  ExecutorService scheduledExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
