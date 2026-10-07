package com.jobcrm.interfaces.cli.company;

import com.jobcrm.core.application.CompanyService;
import com.jobcrm.core.domain.company.Company;
import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.company.CompanyRepository;
import com.jobcrm.interfaces.cli.GlobalOptions;
import com.jobcrm.interfaces.cli.OutputRenderer;
import com.jobcrm.interfaces.cli.ShortRef;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code jobcrm company ...} command group — the canonical shape every command group follows: a
 * parent command whose subcommands are nested Spring-managed classes that call an application
 * service and render through {@link OutputRenderer}.
 */
@Component
@Command(
    name = "company",
    description = "Manage companies.",
    subcommands = {
      CompanyCommand.New.class,
      CompanyCommand.List.class,
      CompanyCommand.Show.class,
      CompanyCommand.SetDomain.class
    })
public class CompanyCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }

  // ---- jobcrm company new ----

  @Component
  @Command(name = "new", description = "Create a company.")
  static class New implements Runnable {
    @Mixin GlobalOptions options;
    private final CompanyService companies;

    New(CompanyService companies) {
      this.companies = companies;
    }

    @Option(names = "--name", required = true, description = "Company name.")
    String name;

    @Option(names = "--domain", description = "Email sender domain (e.g. stripe.com).")
    String domain;

    @Override
    public void run() {
      CompanyId id = companies.create(name);
      if (domain != null && !domain.isBlank()) {
        companies.setDomain(id, domain);
      }
      OutputRenderer out = new OutputRenderer();
      if (options.format() == com.jobcrm.interfaces.cli.OutputFormat.JSON) {
        out.json(Map.of("id", id.value().toString(), "name", name));
      } else if (!options.quiet()) {
        out.line("Created company " + shortRef(id) + "  " + name);
      }
    }
  }

  // ---- jobcrm company list ----

  @Component
  @Command(name = "list", description = "List companies.")
  static class List implements Runnable {
    @Mixin GlobalOptions options;
    private final CompanyRepository companies;

    List(CompanyRepository companies) {
      this.companies = companies;
    }

    @Override
    public void run() {
      java.util.List<Company> all = companies.findAll();
      OutputRenderer out = new OutputRenderer();
      if (options.format() == com.jobcrm.interfaces.cli.OutputFormat.JSON) {
        out.json(all.stream().map(CompanyCommand::toMap).toList());
      } else {
        out.table(
            java.util.List.of("REF", "NAME", "DOMAIN"),
            all.stream()
                .map(c -> java.util.List.of(shortRef(c.id()), c.name(), c.domain().orElse("")))
                .toList());
      }
    }
  }

  // ---- jobcrm company show <ref> ----

  @Component
  @Command(name = "show", description = "Show a company's detail.")
  static class Show implements Runnable {
    @Mixin GlobalOptions options;
    private final CompanyRepository companies;

    Show(CompanyRepository companies) {
      this.companies = companies;
    }

    @Parameters(index = "0", paramLabel = "<ref>", description = "Company short-ref or UUID.")
    String ref;

    @Override
    public void run() {
      Company company = ShortRef.resolve(ref, companies.findAll(), c -> c.id().value());
      OutputRenderer out = new OutputRenderer();
      if (options.format() == com.jobcrm.interfaces.cli.OutputFormat.JSON) {
        out.json(toMap(company));
      } else {
        out.line("Company  " + shortRef(company.id()));
        out.line("Name     " + company.name());
        out.line("Domain   " + company.domain().orElse("(none)"));
        out.line("Created  " + company.createdAt());
      }
    }
  }

  // ---- jobcrm company set-domain <ref> <domain> ----

  @Component
  @Command(name = "set-domain", description = "Set a company's sender domain.")
  static class SetDomain implements Runnable {
    @Mixin GlobalOptions options;
    private final CompanyService companies;
    private final CompanyRepository repo;

    SetDomain(CompanyService companies, CompanyRepository repo) {
      this.companies = companies;
      this.repo = repo;
    }

    @Parameters(index = "0", paramLabel = "<ref>", description = "Company short-ref or UUID.")
    String ref;

    @Parameters(index = "1", paramLabel = "<domain>", description = "Sender domain.")
    String domain;

    @Override
    public void run() {
      Company company = ShortRef.resolve(ref, repo.findAll(), c -> c.id().value());
      companies.setDomain(company.id(), domain);
      if (!options.quiet()) {
        new OutputRenderer().line("Set domain of " + shortRef(company.id()) + " to " + domain);
      }
    }
  }

  // ---- shared helpers ----

  static String shortRef(CompanyId id) {
    return id.value().toString().substring(0, 8);
  }

  static Map<String, Object> toMap(Company c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.id().value().toString());
    m.put("name", c.name());
    m.put("domain", c.domain().orElse(null));
    m.put("createdAt", c.createdAt().toString());
    m.put("updatedAt", c.updatedAt().toString());
    return m;
  }
}
