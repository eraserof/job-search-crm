package com.jobcrm.app;

import com.jobcrm.infrastructure.config.JobcrmConfig;
import com.jobcrm.interfaces.cli.JobcrmCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Spring Boot entry point. Boots the application context, then hands argv to the Picocli root
 * command via {@link CommandLineRunner}. The exit code produced by Picocli is propagated back to
 * the process through {@link ExitCodeGenerator}.
 *
 * <p>{@link ConfigurationPropertiesScan} activates {@link JobcrmConfig} (and any future
 * {@code @ConfigurationProperties} records) so the user-facing config binds from the packaged
 * defaults, the optional {@code ~/.jobcrm/config.yml} overlay, and {@code JOBCRM_*} env vars.
 */
@SpringBootApplication(scanBasePackages = "com.jobcrm")
@ConfigurationPropertiesScan("com.jobcrm")
public class Main implements CommandLineRunner, ExitCodeGenerator {

  private final IFactory factory;
  private final JobcrmCommand rootCommand;
  private int exitCode;

  public Main(IFactory factory, JobcrmCommand rootCommand) {
    this.factory = factory;
    this.rootCommand = rootCommand;
  }

  @Override
  public void run(String... args) {
    exitCode = new CommandLine(rootCommand, factory).execute(args);
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }

  public static void main(String[] args) {
    System.exit(SpringApplication.exit(SpringApplication.run(Main.class, args)));
  }
}
