package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmCatalogUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.*;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A developer's Uysot data, reachable during a call or chat: flats on sale, residential
 * complexes, the caller's contract balance and payment schedule, and a callback from their
 * own manager.
 *
 * <p>Offered only to a company connected to Uysot, on both pipelines and in chat. Every
 * figure an answer carries — a price, an area, a balance — is one the CRM stated, and is
 * handed to {@link FactGuard} as a figure the agent may say; nothing here computes a price
 * or a debt of its own. A failed read answers "not available" rather than an error the
 * model would relay.
 */
public class RealEstateTools {

    private static final Logger log = LoggerFactory.getLogger(RealEstateTools.class);

    static final String CONTRACT_NUMBER_FACT = "contractNumber";
    static final String RESPONSIBLE_EMPLOYEE_OUTCOME = "responsibleEmployeeId";
    /** Memory facts: what the client last looked for, what they were offered, and what they liked. */
    static final String FLAT_SEARCH_FACT = "flatSearch";
    static final String OFFERED_FLATS_FACT = "offeredFlats";
    static final String INTERESTED_FLAT_FACT = "interestedFlat";

    private static final String NOT_AVAILABLE = "THE DATA IS NOT AVAILABLE RIGHT NOW — do not invent a price, "
            + "flat or amount. Tell the client a specialist will clarify it and offer to book a visit or a callback.";
    private static final String NO_FLATS = "NO FLATS ON SALE MATCH — do not invent one. Ask whether another room "
            + "count, budget or floor would suit, or offer a callback from a sales manager.";
    private static final String NO_CONTRACT = "NO CONTRACT FOUND for this client — do not invent an amount. Ask for "
            + "the contract number, or offer a callback from their manager.";

    private static final int MAX_FLATS = 3;
    private static final int MAX_DUES = 3;
    private static final int MAX_TEXT = 120;

    private final DialogOutcomeSink session;
    private final long companyId;
    private final CrmCatalogUseCase catalog;
    /** Where the flats a client asked about or liked are kept for the next conversation; null = not kept. */
    private final ClientMemoryUseCase memory;

    public RealEstateTools(DialogOutcomeSink session, long companyId, CrmCatalogUseCase catalog,
                           ClientMemoryUseCase memory) {
        this.session = session;
        this.companyId = companyId;
        this.catalog = catalog;
        this.memory = memory;
    }

    public RealEstateTools(DialogOutcomeSink session, long companyId, CrmCatalogUseCase catalog) {
        this(session, companyId, catalog, null);
    }

    @Tool(description = "Searches flats ON SALE in the company's residential complexes. Call it when the client "
            + "asks what is available, about prices, room counts, floors or a budget. Say ONLY flats and prices "
            + "returned from here — never invent a flat, price or area.")
    public String searchFlats(
            @ToolParam(description = "number of rooms the client wants, e.g. 2", required = false) Integer rooms,
            @ToolParam(description = "the client's maximum budget as a plain number, in the complex's currency",
                    required = false) BigDecimal maxPrice,
            @ToolParam(description = "minimum price as a plain number", required = false) BigDecimal minPrice,
            @ToolParam(description = "residential complex name the client named", required = false) String complexName,
            @ToolParam(description = "lowest acceptable floor", required = false) Integer floorFrom,
            @ToolParam(description = "highest acceptable floor", required = false) Integer floorTo) {
        try {
            Long houseId = findHouseId(complexName);
            if (complexName != null && !complexName.isBlank() && houseId == null) {
                return "No residential complex named \"" + PromptSafeText.sanitize(complexName, MAX_TEXT)
                        + "\". Our complexes: " + listComplexNames() + ". Ask which one the client means.";
            }
            List<CrmFlat> flats = catalog.searchFlats(companyId,
                    new FlatSearchFilter(rooms, minPrice, maxPrice, houseId, floorFrom, floorTo, MAX_FLATS));
            if (flats.isEmpty()) {
                return NO_FLATS;
            }
            StringBuilder answer = new StringBuilder("Flats on sale (cheapest first):\n");
            for (CrmFlat flat : flats) {
                answer.append("- ").append(describe(flat)).append('\n');
            }
            String result = answer.toString();
            session.recordToolFigures(FactGuard.figuresIn(result));
            session.rememberCatalogResult(result);
            if (rooms != null) {
                session.recordOutcome("roomsNeeded", rooms + " xonali");
            }
            if (maxPrice != null) {
                session.recordOutcome("budgetRange", plain(maxPrice));
            }
            remember(Map.of(
                    FLAT_SEARCH_FACT, describeSearch(rooms, minPrice, maxPrice, complexName),
                    OFFERED_FLATS_FACT, String.join("; ", flats.stream().map(RealEstateTools::shortLabel).toList())));
            return result;
        } catch (Exception e) {
            log.warn("[{}] flat search failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Remembers the flat the client liked or chose, so the next call or chat continues from it. "
            + "Call it when the client says a specific flat suits them, asks to hold it, or wants to see it.")
    public String rememberFlatInterest(
            @ToolParam(description = "the flat as it was offered: complex, block, number, rooms, area, price")
            String flat,
            @ToolParam(description = "what the client said about it (e.g. 'ko'rgani kelmoqchi', 'oilasi bilan maslahatlashadi')",
                    required = false) String note) {
        if (flat == null || flat.isBlank()) {
            return "ERROR: say which flat the client liked.";
        }
        String text = PromptSafeText.sanitize(flat.trim(), MAX_TEXT * 2)
                + (note != null && !note.isBlank() ? " — " + PromptSafeText.sanitize(note.trim(), MAX_TEXT) : "");
        session.recordOutcome(INTERESTED_FLAT_FACT, text);
        remember(Map.of(INTERESTED_FLAT_FACT, text));
        return "Noted. The client's choice is remembered for the next conversation; continue with them.";
    }

    @Tool(description = "Lists the company's residential complexes on sale with their address, floors, blocks and "
            + "planned hand-over date. Call it when the client asks which projects there are, where they are or "
            + "when they will be finished.")
    public String listResidentialComplexes() {
        try {
            List<CrmHouse> houses = catalog.findHouses(companyId).stream().filter(CrmHouse::onSale).toList();
            if (houses.isEmpty()) {
                return NOT_AVAILABLE;
            }
            StringBuilder answer = new StringBuilder("Residential complexes on sale:\n");
            for (CrmHouse house : houses) {
                answer.append("- ").append(safe(house.name()));
                if (house.address() != null) {
                    answer.append(", address: ").append(safe(house.address()));
                }
                if (house.floors() != null) {
                    answer.append(", ").append(house.floors()).append(" floors");
                }
                if (house.handOverDate() != null) {
                    answer.append(", planned hand-over ").append(safe(house.handOverDate()));
                }
                if (!house.buildings().isEmpty()) {
                    answer.append(", blocks: ").append(safe(String.join(", ", house.buildings())));
                }
                answer.append('\n');
            }
            String result = answer.toString();
            session.recordToolFigures(FactGuard.figuresIn(result));
            return result;
        } catch (Exception e) {
            log.warn("[{}] complex list failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Reads the client's contract from the CRM: total, paid, remaining balance, days overdue "
            + "and the next payment. Call it when the client asks how much is left, how much they owe or when "
            + "the next payment is. State ONLY the figures returned from here.")
    public String getContractBalance(
            @ToolParam(description = "contract number if the client named one", required = false) String contractNumber) {
        try {
            Optional<CrmContract> contract = findContract(contractNumber);
            if (contract.isEmpty()) {
                return NO_CONTRACT;
            }
            CrmContract c = contract.get();
            rememberResponsible(c);
            StringBuilder answer = new StringBuilder("Contract ").append(safe(c.number())).append(":");
            appendFigure(answer, " total", c.amount(), c.currency());
            appendFigure(answer, ", paid", c.paidAmount(), c.currency());
            appendFigure(answer, ", remaining", c.residue(), c.currency());
            answer.append(c.delayDays() > 0 ? ", overdue by " + c.delayDays() + " days" : ", payments on schedule");
            catalog.findPaymentSchedule(companyId, c.id()).stream()
                    .filter(due -> !due.paid())
                    .findFirst()
                    .ifPresent(next -> {
                        answer.append(". Next payment");
                        if (next.date() != null) {
                            answer.append(" due ").append(next.date());
                        }
                        appendFigure(answer, ":", next.remaining() != null ? next.remaining() : next.amount(),
                                c.currency());
                    });
            answer.append('.');
            String result = answer.toString();
            session.recordToolFigures(FactGuard.figuresIn(result));
            session.recordOutcome(CONTRACT_NUMBER_FACT, c.number());
            return result;
        } catch (Exception e) {
            log.warn("[{}] contract balance read failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Reads the client's upcoming and overdue monthly payments from the CRM. Call it when the "
            + "client asks for their payment schedule or the dates and amounts of coming payments.")
    public String getPaymentSchedule(
            @ToolParam(description = "contract number if the client named one", required = false) String contractNumber) {
        try {
            Optional<CrmContract> contract = findContract(contractNumber);
            if (contract.isEmpty()) {
                return NO_CONTRACT;
            }
            CrmContract c = contract.get();
            rememberResponsible(c);
            LocalDate today = LocalDate.now();
            List<CrmPaymentDue> unpaid = catalog.findPaymentSchedule(companyId, c.id()).stream()
                    .filter(due -> !due.paid())
                    .toList();
            if (unpaid.isEmpty()) {
                return "Contract " + safe(c.number()) + " has no unpaid scheduled payments.";
            }
            StringBuilder answer = new StringBuilder("Unpaid payments of contract ").append(safe(c.number())).append(":\n");
            unpaid.stream().limit(MAX_DUES).forEach(due -> {
                answer.append("- ").append(due.date() != null ? due.date().toString() : "no date");
                appendFigure(answer, ":", due.remaining() != null ? due.remaining() : due.amount(), c.currency());
                if (due.date() != null && due.date().isBefore(today)) {
                    answer.append(" (overdue)");
                }
                answer.append('\n');
            });
            String result = answer.toString();
            session.recordToolFigures(FactGuard.figuresIn(result));
            return result;
        } catch (Exception e) {
            log.warn("[{}] payment schedule read failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Reads ALL of the client's active contracts and what is owed on each: the remaining balance, "
            + "how many days it is overdue and which monthly payments are past due. Call it when the client asks "
            + "how much they owe in total, or when you must know every contract before talking about debt. "
            + "State ONLY the figures returned from here — never add them up yourself.")
    public String getDebtSummary() {
        try {
            String phone = session.context() != null ? session.context().phone() : null;
            List<CrmContract> contracts = phone != null ? catalog.findActiveContractsByPhone(companyId, phone) : List.of();
            if (contracts.isEmpty()) {
                return NO_CONTRACT;
            }
            LocalDate today = LocalDate.now();
            StringBuilder answer = new StringBuilder("Client's active contracts (most overdue first):\n");
            contracts.stream()
                    .sorted((a, b) -> Integer.compare(b.delayDays(), a.delayDays()))
                    .limit(MAX_DUES)
                    .forEach(c -> {
                        answer.append("- contract ").append(safe(c.number()));
                        appendFigure(answer, ", remaining", c.residue(), c.currency());
                        answer.append(c.delayDays() > 0 ? ", overdue by " + c.delayDays() + " days" : ", on schedule");
                        List<CrmPaymentDue> pastDue = catalog.findPaymentSchedule(companyId, c.id()).stream()
                                .filter(due -> !due.paid() && due.date() != null && due.date().isBefore(today))
                                .toList();
                        if (!pastDue.isEmpty()) {
                            answer.append(", past-due payments:");
                            pastDue.stream().limit(MAX_DUES).forEach(due -> {
                                answer.append(' ').append(due.date());
                                appendFigure(answer, ":", due.remaining() != null ? due.remaining() : due.amount(),
                                        c.currency());
                                answer.append(';');
                            });
                        }
                        answer.append('\n');
                    });
            contracts.stream().filter(c -> c.responsibleEmployeeId() != null).findFirst().ifPresent(this::rememberResponsible);
            String result = answer.toString();
            session.recordToolFigures(FactGuard.figuresIn(result));
            return result;
        } catch (Exception e) {
            log.warn("[{}] debt summary read failed: {}", session.channelId(), e.getMessage());
            return NOT_AVAILABLE;
        }
    }

    @Tool(description = "Asks the client's own sales or contract manager to call them back. Call it when the client "
            + "wants a person to call, has a question you cannot answer, or wants to discuss terms or a discount.")
    public String requestManagerCallback(
            @ToolParam(description = "what the client wants to discuss") String reason,
            @ToolParam(description = "when the client prefers to be called, if they said", required = false)
            String preferredTime) {
        session.recordOutcome("managerCallbackRequested", true);
        if (reason != null && !reason.isBlank()) {
            session.recordOutcome("managerCallbackReason", reason.trim());
        }
        if (preferredTime != null && !preferredTime.isBlank()) {
            session.recordOutcome("managerCallbackTime", preferredTime.trim());
        }
        String manager = findResponsibleName();
        log.info("[{}] manager callback requested", session.channelId());
        return manager != null
                ? "Callback requested from the client's manager " + manager + ". Tell the client " + manager
                        + " will call them back."
                : "Callback requested from a sales manager. Tell the client a manager will call them back.";
    }

    /** Keeps {@code facts} in the client's memory — never fails the turn over it. */
    private void remember(Map<String, Object> facts) {
        String phone = session.context() != null ? session.context().phone() : null;
        if (memory == null || phone == null) {
            return;
        }
        try {
            memory.rememberFacts(companyId, phone, facts);
        } catch (Exception e) {
            log.warn("[{}] could not remember flat interest: {}", session.channelId(), e.getMessage());
        }
    }

    private static String describeSearch(Integer rooms, BigDecimal minPrice, BigDecimal maxPrice, String complexName) {
        List<String> parts = new java.util.ArrayList<>();
        if (rooms != null) {
            parts.add(rooms + " xonali");
        }
        if (minPrice != null) {
            parts.add(plain(minPrice) + " dan");
        }
        if (maxPrice != null) {
            parts.add(plain(maxPrice) + " gacha");
        }
        if (complexName != null && !complexName.isBlank()) {
            parts.add(PromptSafeText.sanitize(complexName.trim(), MAX_TEXT));
        }
        return parts.isEmpty() ? "har qanday xonadon" : String.join(", ", parts);
    }

    private static String shortLabel(CrmFlat flat) {
        StringBuilder label = new StringBuilder(safe(flat.houseName()));
        if (flat.buildingName() != null) {
            label.append(' ').append(safe(flat.buildingName()));
        }
        if (flat.number() != null) {
            label.append(" №").append(safe(flat.number()));
        }
        if (flat.rooms() != null) {
            label.append(", ").append(flat.rooms()).append(" xona");
        }
        if (flat.price() != null) {
            label.append(", ").append(plain(flat.price()));
            if (flat.currency() != null) {
                label.append(' ').append(safe(flat.currency()));
            }
        }
        return label.toString();
    }

    /** The contract the caller asked about: by the number they named, the call's own fact, or their phone. */
    private Optional<CrmContract> findContract(String contractNumber) {
        String number = contractNumber != null && !contractNumber.isBlank() ? contractNumber.trim()
                : session.context() != null && session.context().fact(CONTRACT_NUMBER_FACT) instanceof String s
                        && !s.isBlank() ? s.trim() : null;
        if (number != null) {
            Optional<CrmContract> byNumber = catalog.findContractByNumber(companyId, number);
            if (byNumber.isPresent()) {
                return byNumber;
            }
        }
        String phone = session.context() != null ? session.context().phone() : null;
        List<CrmContract> byPhone = phone != null ? catalog.findActiveContractsByPhone(companyId, phone) : List.of();
        // More than one: the one most behind is the one worth talking about.
        return byPhone.stream().max((a, b) -> Integer.compare(a.delayDays(), b.delayDays()));
    }

    private void rememberResponsible(CrmContract contract) {
        if (contract.responsibleEmployeeId() != null) {
            session.recordOutcome(RESPONSIBLE_EMPLOYEE_OUTCOME, contract.responsibleEmployeeId());
            if (contract.responsibleEmployeeName() != null) {
                session.recordOutcome("responsibleEmployeeName", contract.responsibleEmployeeName());
            }
        }
    }

    private String findResponsibleName() {
        Object name = session.outcome().get("responsibleEmployeeName");
        if (name instanceof String s && !s.isBlank()) {
            return PromptSafeText.sanitize(s, MAX_TEXT);
        }
        Object fact = session.context() != null ? session.context().fact("responsibleName") : null;
        return fact instanceof String s && !s.isBlank() ? PromptSafeText.sanitize(s, MAX_TEXT) : null;
    }

    private Long findHouseId(String complexName) {
        if (complexName == null || complexName.isBlank()) {
            return null;
        }
        String wanted = complexName.trim().toLowerCase(Locale.ROOT);
        return catalog.findHouses(companyId).stream()
                .filter(house -> house.name() != null && house.name().toLowerCase(Locale.ROOT).contains(wanted)
                        || house.name() != null && wanted.contains(house.name().toLowerCase(Locale.ROOT)))
                .map(CrmHouse::id)
                .findFirst()
                .orElse(null);
    }

    private String listComplexNames() {
        return String.join(", ", catalog.findHouses(companyId).stream()
                .filter(CrmHouse::onSale)
                .map(house -> PromptSafeText.sanitize(house.name(), MAX_TEXT))
                .toList());
    }

    private static String describe(CrmFlat flat) {
        StringBuilder text = new StringBuilder();
        text.append(safe(flat.houseName()));
        if (flat.buildingName() != null) {
            text.append(", ").append(safe(flat.buildingName()));
        }
        if (flat.rooms() != null) {
            text.append(", ").append(flat.rooms()).append(" rooms");
        }
        if (flat.area() != null) {
            text.append(", ").append(plain(flat.area())).append(" m2");
        }
        if (flat.floor() != null) {
            text.append(", floor ").append(flat.floor());
        }
        appendFigure(text, ", price", flat.price(), flat.currency());
        appendFigure(text, " (with repair", flat.priceRepaired(), flat.currency());
        if (flat.priceRepaired() != null) {
            text.append(')');
        }
        return text.toString();
    }

    private static void appendFigure(StringBuilder text, String label, BigDecimal value, String currency) {
        if (value == null) {
            return;
        }
        text.append(label).append(' ').append(plain(value));
        if (currency != null) {
            text.append(' ').append(safe(currency));
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String safe(String value) {
        return value == null ? "" : PromptSafeText.sanitize(value, MAX_TEXT);
    }
}
