package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The memory keeps a longer history for the operator; the prompt gets only the newest few conversations. */
class CallContextMemoryTest {

    @Test
    void onlyTheNewestRememberedConversationsReachThePrompt() {
        List<RememberedCall> calls = IntStream.range(0, 10)
                .mapToObj(i -> new RememberedCall(null, Instant.parse("2026-08-01T09:00:00Z").minusSeconds(i * 86400L),
                        "debt", Disposition.HUNG_UP, "call " + i, (long) i, null))
                .toList();
        ClientMemory memory = ClientMemory.builder().id(1).companyId(1).phone("+998901234567")
                .recentCalls(calls).build();

        CallContext context = new CallContext(Map.of(), null, memory);

        assertThat(context.memory().recentCalls()).extracting(RememberedCall::summary)
                .containsExactly("call 0", "call 1", "call 2");
        assertThat(context.memory().recentCalls()).extracting(RememberedCall::id)
                .containsExactlyElementsOf(calls.subList(0, CallContext.MAX_PROMPT_CALLS).stream()
                        .map(RememberedCall::id).toList());
    }
}
