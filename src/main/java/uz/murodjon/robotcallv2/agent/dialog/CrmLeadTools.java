package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import uz.murodjon.robotcallv2.crm.application.dto.CreateCrmTaskRequest;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateLeadStageRequest;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmCatalogUseCase;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmBooking;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientProfile;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmPipeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the agent can do to the caller's lead in the company's Uysot CRM, and what it can
 * read about the person: their card, their bookings.
 *
 * <p>Offered through {@link CrmToolCallbacks}, so only the tools the company switched on
 * (and whose CRM grants it gave) reach the model. A write goes to the lead found by the
 * caller's number; a number the CRM does not know has no lead to write to, and the answer
 * says so instead of inventing one. Every figure in an answer is one the CRM stated and is
 * handed to {@link FactGuard}. A failed call answers "not available", never an error the
 * model would relay.
 */
public class CrmLeadTools {

    private static final Logger log = LoggerFactory.getLogger(CrmLeadTools.class);

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Tashkent");
    private static final DateTimeFormatter SPACED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SOURCE = "VOICE_AGENT";
    private static final int MAX_TEXT = 300;
    private static final int MAX_CONTRACTS = 3;

    private static final String NOT_AVAILABLE = "THE CRM IS NOT AVAILABLE RIGHT NOW — do not claim that it was "
            + "saved or found. Tell the client a specialist will take care of it.";
    private static final String NO_LEAD = "THIS NUMBER HAS NO LEAD IN THE CRM — nothing was changed. "
            + "If it matters, offer to open one (createLead) or tell the client a manager will contact them.";

    private final DialogOutcomeSink session;
    private final long companyId;
    private final CrmUseCase crm;
    private final CrmCatalogUseCase catalog;

    public CrmLeadTools(DialogOutcomeSink session, long companyId, CrmUseCase crm, CrmCatalogUseCase catalog) {
        this.session = session;
        this.companyId = companyId;
        this.crm = crm;
        this.catalog = catalog;
    }

    @Tool(description = "Looks the caller up in the CRM by their phone number: their name, their lead and their "
            + "active contracts. Call it when you need to know who the client is or whether they are already a "
            + "customer. Say ONLY what is returned from here.")
    public String getClientProfile() {
        String phone = findPhone();
        if (phone == null) {
            return "ERROR: the client's phone number is not known on this call.";
        }
        try {
            CrmClientProfile profile = catalog.findClientProfile(companyId, phone);
            CrmClientSnapshot lead = profile.lead();
            if ((lead == null || lead.leadId() == null) && profile.contracts().isEmpty()) {
                return "NOT A KNOWN CLIENT: this number has no lead and no active contract in the CRM.";
            }
            StringBuilder answer = new StringBuilder("Client in the CRM:");
            if (lead != null && lead.name() != null) {
                answer.append(" name ").append(safe(lead.name())).append(';');
            }
            if (lead != null && lead.leadId() != null) {
                answer.append(" has a lead;");
            }
            answer.append(" active contracts: ").append(profile.contracts().size()).append('.');
            profile.contracts().stream().limit(MAX_CONTRACTS).forEach(c -> {
                answer.append("\n- contract ").append(safe(c.number()));
                appendFigure(answer, ", remaining", c.residue(), c.currency());
                answer.append(c.delayDays() > 0 ? ", overdue by " + c.delayDays() + " days" : ", on schedule");
            });
            String result = answer.toString();
            session.recordToolFigures(FactGuard.figuresIn(result));
            return result;
        } catch (Exception e) {
            log.warn("[{}] client profile read failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Opens a NEW lead in the CRM for this caller. Call it only when the client is not in the CRM "
            + "yet (getClientProfile said so) and has shown interest in buying. Never open one for a known client.")
    public String createLead(
            @ToolParam(description = "the client's name as they gave it", required = false) String name,
            @ToolParam(description = "what they are interested in, e.g. '2 xonali, 700 mln gacha'", required = false)
            String interest) {
        String phone = findPhone();
        if (phone == null) {
            return "ERROR: the client's phone number is not known on this call.";
        }
        try {
            if (findLeadId(phone).isPresent()) {
                return "This number ALREADY HAS A LEAD in the CRM — do not open another. Continue the conversation.";
            }
            boolean accepted = crm.createLead(companyId, name, phone, SOURCE);
            if (!accepted) {
                return NOT_AVAILABLE;
            }
            session.recordOutcome("leadCreated", true);
            if (interest != null && !interest.isBlank()) {
                session.recordOutcome("leadInterest", safe(interest));
            }
            return "A new lead was opened in the CRM. Tell the client a manager will contact them.";
        } catch (Exception e) {
            log.warn("[{}] lead creation failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Writes a note on the caller's lead in the CRM. Call it for something a manager must know "
            + "that is not captured elsewhere: a preference, a family situation, a promise, an objection.")
    public String addLeadNote(@ToolParam(description = "the note, one or two sentences") String note) {
        if (note == null || note.isBlank()) {
            return "ERROR: there is no note text.";
        }
        String phone = findPhone();
        if (phone == null) {
            return "ERROR: the client's phone number is not known on this call.";
        }
        try {
            Optional<Long> leadId = findLeadId(phone);
            if (leadId.isEmpty()) {
                return NO_LEAD;
            }
            String taken = crm.postNote(companyId, leadId.get(), "[AI agent] " + safe(note));
            return taken != null ? "The note was saved on the client's lead." : NOT_AVAILABLE;
        } catch (Exception e) {
            log.warn("[{}] lead note failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Puts a task for a manager on the caller's lead in the CRM: a call back or a meeting at an "
            + "exact time. Call it when the client agreed a concrete day and hour. Do not guess the time.")
    public String createFollowUpTask(
            @ToolParam(description = "what the manager must do, e.g. 'Mijozga qayta qo'ng'iroq qilish'") String title,
            @ToolParam(description = "when it is due, local time, format yyyy-MM-dd HH:mm") String dueTime,
            @ToolParam(description = "details for the manager", required = false) String details) {
        LocalDateTime due = parseDueTime(dueTime);
        if (due == null) {
            return "ERROR: give the exact date and hour as yyyy-MM-dd HH:mm. Ask the client for a concrete time.";
        }
        if (title == null || title.isBlank()) {
            return "ERROR: say what the manager must do.";
        }
        String phone = findPhone();
        if (phone == null) {
            return "ERROR: the client's phone number is not known on this call.";
        }
        try {
            Optional<Long> leadId = findLeadId(phone);
            if (leadId.isEmpty()) {
                return NO_LEAD;
            }
            String taken = crm.createTask(companyId, new CreateCrmTaskRequest(leadId.get(), safe(title),
                    details != null ? safe(details) : null, due.atZone(findZone()).toInstant(), null));
            if (taken == null) {
                return NOT_AVAILABLE;
            }
            session.recordOutcome("followUpTaskTime", SPACED.format(due));
            return "The task was set for " + SPACED.format(due) + ". Tell the client a manager will do it then.";
        } catch (Exception e) {
            log.warn("[{}] follow-up task failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Moves the caller's lead to another stage of the sales funnel. Call it only when the call "
            + "clearly changed where the client is in the funnel (e.g. they refused, or agreed to a visit). "
            + "Name the stage exactly as the funnel calls it.")
    public String moveLeadStage(
            @ToolParam(description = "the funnel stage's name, e.g. 'Uchrashuv belgilandi'") String stageName,
            @ToolParam(description = "why the lead moves", required = false) String reason) {
        if (stageName == null || stageName.isBlank()) {
            return "ERROR: name the stage.";
        }
        String phone = findPhone();
        if (phone == null) {
            return "ERROR: the client's phone number is not known on this call.";
        }
        try {
            Optional<Long> leadId = findLeadId(phone);
            if (leadId.isEmpty()) {
                return NO_LEAD;
            }
            List<CrmPipeStatus> all = new ArrayList<>();
            catalog.findPipes(companyId).forEach(pipe -> all.addAll(pipe.statuses()));
            String wanted = stageName.trim().toLowerCase(Locale.ROOT);
            List<CrmPipeStatus> exact = all.stream()
                    .filter(s -> s.name() != null && s.name().trim().toLowerCase(Locale.ROOT).equals(wanted)).toList();
            List<CrmPipeStatus> matches = !exact.isEmpty() ? exact : all.stream()
                    .filter(s -> s.name() != null && s.name().toLowerCase(Locale.ROOT).contains(wanted)).toList();
            if (matches.size() != 1) {
                return matches.isEmpty()
                        ? "NO STAGE named \"" + safe(stageName) + "\". Stages: " + stageNames(all) + "."
                        : "SEVERAL STAGES match \"" + safe(stageName) + "\": " + stageNames(matches) + ". Pick one.";
            }
            boolean accepted = crm.updateLeadStage(companyId,
                    new UpdateLeadStageRequest(leadId.get(), matches.get(0).id(), reason != null ? safe(reason) : null));
            return accepted ? "The lead was moved to \"" + safe(matches.get(0).name()) + "\"." : NOT_AVAILABLE;
        } catch (Exception e) {
            log.warn("[{}] lead stage move failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Reads the client's flat bookings from the CRM: the flat, its status and until when it is "
            + "held. Call it when the client asks about their booking or whether a flat is still held for them. "
            + "Say ONLY what is returned from here.")
    public String getBookingStatus() {
        String phone = findPhone();
        if (phone == null) {
            return "ERROR: the client's phone number is not known on this call.";
        }
        try {
            List<CrmBooking> bookings = catalog.findBookingsByPhone(companyId, phone);
            if (bookings.isEmpty()) {
                return "NO BOOKINGS found for this client. Do not invent one.";
            }
            StringBuilder answer = new StringBuilder("Client's bookings:\n");
            for (CrmBooking booking : bookings) {
                answer.append("- ").append(safe(booking.houseName()));
                if (booking.flatNumber() != null) {
                    answer.append(", flat ").append(safe(booking.flatNumber()));
                }
                if (booking.status() != null) {
                    answer.append(", status ").append(safe(booking.status()));
                }
                if (booking.bookedUntil() != null) {
                    answer.append(", held until ").append(booking.bookedUntil());
                }
                appendFigure(answer, ", booking fee", booking.amount(), booking.currency());
                answer.append('\n');
            }
            String result = answer.toString();
            session.recordToolFigures(FactGuard.figuresIn(result));
            return result;
        } catch (Exception e) {
            log.warn("[{}] booking read failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    private Optional<Long> findLeadId(String phone) {
        CrmClientSnapshot snapshot = crm.findByPhone(companyId, phone);
        return snapshot != null && snapshot.leadId() != null ? Optional.of(snapshot.leadId()) : Optional.empty();
    }

    private String findPhone() {
        String phone = session.context() != null ? session.context().clientPhone() : null;
        return phone != null && !phone.isBlank() ? phone : null;
    }

    private ZoneId findZone() {
        String timezone = session.agent() != null ? session.agent().timezone() : null;
        try {
            return timezone != null ? ZoneId.of(timezone) : DEFAULT_ZONE;
        } catch (RuntimeException e) {
            return DEFAULT_ZONE;
        }
    }

    private static LocalDateTime parseDueTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        try {
            return LocalDateTime.parse(trimmed.replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(trimmed, SPACED);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static String stageNames(List<CrmPipeStatus> stages) {
        return String.join(", ", stages.stream().map(s -> safe(s.name())).toList());
    }

    private static void appendFigure(StringBuilder text, String label, BigDecimal value, String currency) {
        if (value == null) {
            return;
        }
        text.append(label).append(' ').append(value.stripTrailingZeros().toPlainString());
        if (currency != null) {
            text.append(' ').append(safe(currency));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : PromptSafeText.sanitize(value, MAX_TEXT);
    }
}
