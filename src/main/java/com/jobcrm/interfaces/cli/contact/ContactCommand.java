package com.jobcrm.interfaces.cli.contact;

import com.jobcrm.core.application.ContactService;
import com.jobcrm.core.domain.contact.Contact;
import com.jobcrm.core.domain.contact.ContactId;
import com.jobcrm.core.domain.contact.ContactRepository;
import com.jobcrm.core.domain.contact.ContactRole;
import com.jobcrm.core.domain.contact.EmailAddress;
import com.jobcrm.interfaces.cli.GlobalOptions;
import com.jobcrm.interfaces.cli.OutputFormat;
import com.jobcrm.interfaces.cli.OutputRenderer;
import com.jobcrm.interfaces.cli.ShortRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code jobcrm contact ...} command group. Follows the canonical shape in {@code CompanyCommand}:
 * a parent command whose subcommands are nested Spring-managed classes that call {@link
 * ContactService} / {@link ContactRepository} and render through {@link OutputRenderer}.
 */
@Component
@Command(
    name = "contact",
    description = "Manage contacts.",
    subcommands = {
      ContactCommand.New.class,
      ContactCommand.List.class,
      ContactCommand.Show.class,
      ContactCommand.Rename.class,
      ContactCommand.AddEmail.class
    })
public class ContactCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }

  // ---- jobcrm contact new ----

  @Component
  @Command(name = "new", description = "Create a contact.")
  static class New implements Runnable {
    @Mixin GlobalOptions options;
    private final ContactService contacts;

    New(ContactService contacts) {
      this.contacts = contacts;
    }

    @Option(names = "--name", required = true, description = "Display name.")
    String name;

    @Option(names = "--email", description = "Email address.")
    String email;

    @Override
    public void run() {
      ContactId id;
      if (email != null && !email.isBlank()) {
        id = contacts.createOrMerge(name, new EmailAddress(email));
      } else {
        id = contacts.create(name);
      }
      OutputRenderer out = new OutputRenderer();
      if (options.format() == OutputFormat.JSON) {
        out.json(Map.of("id", id.value().toString(), "name", name));
      } else if (!options.quiet()) {
        out.line("Created contact " + shortRef(id) + "  " + name);
      }
    }
  }

  // ---- jobcrm contact list ----

  @Component
  @Command(name = "list", description = "List contacts.")
  static class List implements Runnable {
    @Mixin GlobalOptions options;
    private final ContactRepository contacts;

    List(ContactRepository contacts) {
      this.contacts = contacts;
    }

    @Override
    public void run() {
      java.util.List<Contact> all = contacts.findAll();
      OutputRenderer out = new OutputRenderer();
      if (options.format() == OutputFormat.JSON) {
        out.json(all.stream().map(ContactCommand::toMap).toList());
      } else {
        out.table(
            java.util.List.of("REF", "NAME", "EMAILS", "COMPANY"),
            all.stream()
                .map(
                    c ->
                        java.util.List.of(
                            shortRef(c.id()),
                            c.displayName(),
                            emails(c),
                            c.employer().map(ContactCommand::shortCompanyRef).orElse("")))
                .toList());
      }
    }
  }

  // ---- jobcrm contact show <ref> ----

  @Component
  @Command(name = "show", description = "Show a contact's detail.")
  static class Show implements Runnable {
    @Mixin GlobalOptions options;
    private final ContactRepository contacts;

    Show(ContactRepository contacts) {
      this.contacts = contacts;
    }

    @Parameters(index = "0", paramLabel = "<ref>", description = "Contact short-ref or UUID.")
    String ref;

    @Override
    public void run() {
      Contact contact = ShortRef.resolve(ref, contacts.findAll(), c -> c.id().value());
      OutputRenderer out = new OutputRenderer();
      if (options.format() == OutputFormat.JSON) {
        out.json(toMap(contact));
      } else {
        out.line("Contact    " + shortRef(contact.id()));
        out.line("Name       " + contact.displayName());
        out.line("Emails     " + (emails(contact).isEmpty() ? "(none)" : emails(contact)));
        out.line("Phone      " + contact.phone().orElse("(none)"));
        out.line("LinkedIn   " + contact.linkedInUrl().orElse("(none)"));
        out.line(
            "Employer   "
                + contact.employer().map(ContactCommand::shortCompanyRef).orElse("(none)"));
        out.line(
            "Roles      "
                + (contact.defaultRoles().isEmpty()
                    ? "(none)"
                    : contact.defaultRoles().stream()
                        .map(ContactRole::name)
                        .collect(Collectors.joining(", "))));
        out.line("Created    " + contact.createdAt());
      }
    }
  }

  // ---- jobcrm contact rename <ref> --to <newName> ----

  @Component
  @Command(name = "rename", description = "Rename a contact.")
  static class Rename implements Runnable {
    @Mixin GlobalOptions options;
    private final ContactService contacts;
    private final ContactRepository repo;

    Rename(ContactService contacts, ContactRepository repo) {
      this.contacts = contacts;
      this.repo = repo;
    }

    @Parameters(index = "0", paramLabel = "<ref>", description = "Contact short-ref or UUID.")
    String ref;

    @Option(names = "--to", required = true, description = "New display name.")
    String to;

    @Override
    public void run() {
      Contact contact = ShortRef.resolve(ref, repo.findAll(), c -> c.id().value());
      contacts.rename(contact.id(), to);
      if (!options.quiet()) {
        new OutputRenderer().line("Renamed " + shortRef(contact.id()) + " to " + to);
      }
    }
  }

  // ---- jobcrm contact add-email <ref> <email> ----

  @Component
  @Command(name = "add-email", description = "Add an email to a contact.")
  static class AddEmail implements Runnable {
    @Mixin GlobalOptions options;
    private final ContactService contacts;
    private final ContactRepository repo;

    AddEmail(ContactService contacts, ContactRepository repo) {
      this.contacts = contacts;
      this.repo = repo;
    }

    @Parameters(index = "0", paramLabel = "<ref>", description = "Contact short-ref or UUID.")
    String ref;

    @Parameters(index = "1", paramLabel = "<email>", description = "Email address to add.")
    String email;

    @Override
    public void run() {
      Contact contact = ShortRef.resolve(ref, repo.findAll(), c -> c.id().value());
      contacts.addEmail(contact.id(), new EmailAddress(email));
      if (!options.quiet()) {
        new OutputRenderer().line("Added email " + email + " to " + shortRef(contact.id()));
      }
    }
  }

  // ---- shared helpers ----

  static String shortRef(ContactId id) {
    return id.value().toString().substring(0, 8);
  }

  static String shortCompanyRef(com.jobcrm.core.domain.company.CompanyId id) {
    return id.value().toString().substring(0, 8);
  }

  static String emails(Contact c) {
    return c.emails().stream().map(EmailAddress::canonical).collect(Collectors.joining(";"));
  }

  static Map<String, Object> toMap(Contact c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.id().value().toString());
    m.put("displayName", c.displayName());
    m.put("emails", c.emails().stream().map(EmailAddress::canonical).toList());
    m.put("phone", c.phone().orElse(null));
    m.put("linkedInUrl", c.linkedInUrl().orElse(null));
    m.put("employer", c.employer().map(id -> id.value().toString()).orElse(null));
    m.put("roles", c.defaultRoles().stream().map(ContactRole::name).toList());
    m.put("createdAt", c.createdAt().toString());
    m.put("updatedAt", c.updatedAt().toString());
    return m;
  }
}
