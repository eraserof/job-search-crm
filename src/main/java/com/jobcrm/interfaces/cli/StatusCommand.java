package com.jobcrm.interfaces.cli;

import com.jobcrm.core.domain.company.Company;
import com.jobcrm.core.domain.company.CompanyId;
import com.jobcrm.core.domain.company.CompanyRepository;
import com.jobcrm.core.domain.opportunity.Opportunity;
import com.jobcrm.core.domain.opportunity.OpportunityRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * {@code jobcrm status <ref>} — a top-level convenience command that reports an opportunity's
 * current state. Deterministic, no agent involved: it mirrors {@code opp show} by resolving the
 * opportunity short-ref and printing stage, company, role, and counts.
 */
@Component
@Command(name = "status", description = "Show the current status of an opportunity.")
public class StatusCommand implements Runnable {

  @Mixin GlobalOptions options;
  private final OpportunityRepository opportunities;
  private final CompanyRepository companies;

  public StatusCommand(OpportunityRepository opportunities, CompanyRepository companies) {
    this.opportunities = opportunities;
    this.companies = companies;
  }

  @Parameters(index = "0", paramLabel = "<ref>", description = "Opportunity short-ref or UUID.")
  String ref;

  @Override
  public void run() {
    Opportunity opp = ShortRef.resolve(ref, opportunities.findAll(), o -> o.id().value());
    String company = companyName(opp.companyId());
    OutputRenderer out = new OutputRenderer();
    if (options.format() == OutputFormat.JSON) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", opp.id().value().toString());
      m.put("stage", opp.stage().name());
      m.put("company", company);
      m.put("role", opp.role());
      m.put("createdAt", opp.createdAt().toString());
      m.put("contactCount", opp.contacts().size());
      m.put("interactionCount", opp.interactions().size());
      m.put("openTaskCount", opp.openTasks().size());
      out.json(m);
    } else {
      out.line("Opportunity   " + opp.id().value().toString().substring(0, 8));
      out.line("Stage         " + opp.stage().name());
      out.line("Company       " + company);
      out.line("Role          " + opp.role());
      out.line("Created       " + opp.createdAt());
      out.line("Contacts      " + opp.contacts().size());
      out.line("Interactions  " + opp.interactions().size());
      out.line("Open tasks    " + opp.openTasks().size());
    }
  }

  private String companyName(CompanyId id) {
    return companies.findById(id).map(Company::name).orElse(id.value().toString().substring(0, 8));
  }
}
