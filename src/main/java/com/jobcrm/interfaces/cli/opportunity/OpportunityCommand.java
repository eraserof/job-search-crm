package com.jobcrm.interfaces.cli.opportunity;

import com.jobcrm.core.application.CompanyService;
import com.jobcrm.core.application.OpportunityService;
import com.jobcrm.core.domain.company.Company;
import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.company.CompanyRepository;
import com.jobcrm.core.domain.contact.Contact;
import com.jobcrm.core.domain.contact.ContactRepository;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.opportunity.Opportunity;
import com.jobcrm.core.domain.opportunity.OpportunityId;
import com.jobcrm.core.domain.opportunity.OpportunityRepository;
import com.jobcrm.core.domain.opportunity.Stage;
import com.jobcrm.interfaces.cli.GlobalOptions;
import com.jobcrm.interfaces.cli.OutputFormat;
import com.jobcrm.interfaces.cli.OutputRenderer;
import com.jobcrm.interfaces.cli.ShortRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code jobcrm opp ...} command group. Follows the canonical shape in {@code CompanyCommand}: a
 * parent command whose subcommands are nested Spring-managed classes that call application services
 * and render through {@link OutputRenderer}.
 */
@Component
@Command(
    name = "opp",
    description = "Manage opportunities.",
    subcommands = {
      OpportunityCommand.New.class,
      OpportunityCommand.List.class,
      OpportunityCommand.Show.class,
      OpportunityCommand.Advance.class,
      OpportunityCommand.AttachContact.class
    })
public class OpportunityCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }

  // ---- jobcrm opp new ----

  @Component
  @Command(name = "new", description = "Create an opportunity.")
  static class New implements Runnable {
    @Mixin GlobalOptions options;
    private final OpportunityService opportunities;
    private final CompanyService companies;

    New(OpportunityService opportunities, CompanyService companies) {
      this.opportunities = opportunities;
      this.companies = companies;
    }

    @Option(names = "--company", required = true, description = "Company name.")
    String company;

    @Option(names = "--role", required = true, description = "Role title.")
    String role;

    @Override
    public void run() {
      CompanyId companyId = companies.findOrCreateByName(company);
      OpportunityId id = opportunities.create(companyId, role);
      OutputRenderer out = new OutputRenderer();
      if (options.format() == OutputFormat.JSON) {
        out.json(Map.of("id", id.value().toString(), "company", company, "role", role));
      } else if (!options.quiet()) {
        out.line("Created opportunity " + shortRef(id) + "  " + company + " / " + role);
      }
    }
  }

  // ---- jobcrm opp list ----

  @Component
  @Command(name = "list", description = "List opportunities.")
  static class List implements Runnable {
    @Mixin GlobalOptions options;
    private final OpportunityRepository opportunities;
    private final CompanyRepository companies;

    List(OpportunityRepository opportunities, CompanyRepository companies) {
      this.opportunities = opportunities;
      this.companies = companies;
    }

    @Override
    public void run() {
      java.util.List<Opportunity> all = opportunities.findAll();
      Map<UUID, String> names = companyNames(companies);
      OutputRenderer out = new OutputRenderer();
      if (options.format() == OutputFormat.JSON) {
        out.json(all.stream().map(o -> toMap(o, names)).toList());
      } else {
        out.table(
            java.util.List.of("REF", "COMPANY", "ROLE", "STAGE"),
            all.stream()
                .map(
                    o ->
                        java.util.List.of(
                            shortRef(o.id()),
                            companyName(names, o.companyId()),
                            o.role(),
                            o.stage().name()))
                .toList());
      }
    }
  }

  // ---- jobcrm opp show <ref> ----

  @Component
  @Command(name = "show", description = "Show an opportunity's detail.")
  static class Show implements Runnable {
    @Mixin GlobalOptions options;
    private final OpportunityRepository opportunities;
    private final CompanyRepository companies;

    Show(OpportunityRepository opportunities, CompanyRepository companies) {
      this.opportunities = opportunities;
      this.companies = companies;
    }

    @Parameters(index = "0", paramLabel = "<ref>", description = "Opportunity short-ref or UUID.")
    String ref;

    @Override
    public void run() {
      Opportunity opp = ShortRef.resolve(ref, opportunities.findAll(), o -> o.id().value());
      Map<UUID, String> names = companyNames(companies);
      OutputRenderer out = new OutputRenderer();
      if (options.format() == OutputFormat.JSON) {
        out.json(toMap(opp, names));
      } else {
        out.line("Opportunity   " + shortRef(opp.id()));
        out.line("Company       " + companyName(names, opp.companyId()));
        out.line("Role          " + opp.role());
        out.line("Stage         " + opp.stage().name());
        out.line("Created       " + opp.createdAt());
        out.line("Contacts      " + opp.contacts().size());
        out.line("Interactions  " + opp.interactions().size());
        out.line("Open tasks    " + opp.openTasks().size());
      }
    }
  }

  // ---- jobcrm opp advance <ref> --to <STAGE> ----

  @Component
  @Command(name = "advance", description = "Advance an opportunity to a new stage.")
  static class Advance implements Runnable {
    @Mixin GlobalOptions options;
    private final OpportunityService opportunities;
    private final OpportunityRepository repo;

    Advance(OpportunityService opportunities, OpportunityRepository repo) {
      this.opportunities = opportunities;
      this.repo = repo;
    }

    @Parameters(index = "0", paramLabel = "<ref>", description = "Opportunity short-ref or UUID.")
    String ref;

    @Option(names = "--to", required = true, description = "Target stage.")
    Stage to;

    @Override
    public void run() {
      Opportunity opp = ShortRef.resolve(ref, repo.findAll(), o -> o.id().value());
      opportunities.advanceStage(opp.id(), to);
      if (!options.quiet()) {
        new OutputRenderer().line("Advanced " + shortRef(opp.id()) + " to " + to.name());
      }
    }
  }

  // ---- jobcrm opp attach-contact <ref> <contactRef> --role <ROLE> ----

  @Component
  @Command(name = "attach-contact", description = "Attach a contact to an opportunity.")
  static class AttachContact implements Runnable {
    @Mixin GlobalOptions options;
    private final OpportunityService opportunities;
    private final OpportunityRepository oppRepo;
    private final ContactRepository contacts;

    AttachContact(
        OpportunityService opportunities,
        OpportunityRepository oppRepo,
        ContactRepository contacts) {
      this.opportunities = opportunities;
      this.oppRepo = oppRepo;
      this.contacts = contacts;
    }

    @Parameters(index = "0", paramLabel = "<ref>", description = "Opportunity short-ref or UUID.")
    String ref;

    @Parameters(
        index = "1",
        paramLabel = "<contactRef>",
        description = "Contact short-ref or UUID.")
    String contactRef;

    @Option(names = "--role", required = true, description = "Contact role in this opportunity.")
    ContactRole role;

    @Override
    public void run() {
      Opportunity opp = ShortRef.resolve(ref, oppRepo.findAll(), o -> o.id().value());
      Contact contact = ShortRef.resolve(contactRef, contacts.findAll(), c -> c.id().value());
      opportunities.attachContact(opp.id(), contact.id(), role);
      if (!options.quiet()) {
        new OutputRenderer()
            .line(
                "Attached contact "
                    + contact.id().value().toString().substring(0, 8)
                    + " to "
                    + shortRef(opp.id())
                    + " as "
                    + role.name());
      }
    }
  }

  // ---- shared helpers ----

  static String shortRef(OpportunityId id) {
    return id.value().toString().substring(0, 8);
  }

  /** Builds a small id -> company-name cache to avoid N lookups during list/show. */
  static Map<UUID, String> companyNames(CompanyRepository companies) {
    Map<UUID, String> names = new LinkedHashMap<>();
    for (Company c : companies.findAll()) {
      names.put(c.id().value(), c.name());
    }
    return names;
  }

  static String companyName(Map<UUID, String> names, CompanyId id) {
    String name = names.get(id.value());
    return name != null ? name : id.value().toString().substring(0, 8);
  }

  static Map<String, Object> toMap(Opportunity o, Map<UUID, String> names) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", o.id().value().toString());
    m.put("company", companyName(names, o.companyId()));
    m.put("role", o.role());
    m.put("stage", o.stage().name());
    m.put("createdAt", o.createdAt().toString());
    m.put("contactCount", o.contacts().size());
    m.put("interactionCount", o.interactions().size());
    m.put("openTaskCount", o.openTasks().size());
    return m;
  }

  // Retained for API symmetry with other command groups (unused direct id extractor).
  @SuppressWarnings("unused")
  private static final Function<Opportunity, UUID> ID_OF = o -> o.id().value();
}
