package uz.murodjon.robotcallv2.memory.domain.entity;

import uz.murodjon.robotcallv2.memory.domain.enums.MemorySourceKind;

import java.time.Instant;

/**
 * Where one remembered fact came from, so an operator can tell a value the client said on a call
 * from one a tool looked up or a colleague typed — and open the call that said it.
 *
 * @param callAttemptId the call that wrote it; {@code null} when it was not a call
 * @param chatSessionId the chat that wrote it; {@code null} when it was not a chat
 * @param tool          the tool that wrote it ({@code searchCatalog}, a function name); {@code null} otherwise
 */
public record MemorySource(
        MemorySourceKind kind,
        Long callAttemptId,
        Long chatSessionId,
        String tool,
        Instant at
) {

    public static MemorySource operator(Instant at) {
        return new MemorySource(MemorySourceKind.OPERATOR, null, null, null, at);
    }

    /** A tool on a call ({@code callAttemptId}) or a chat ({@code chatSessionId}); 0 means not that one. */
    public static MemorySource tool(String tool, long callAttemptId, long chatSessionId, Instant at) {
        return new MemorySource(MemorySourceKind.TOOL, callAttemptId != 0 ? callAttemptId : null,
                chatSessionId != 0 ? chatSessionId : null, tool, at);
    }

    /** The conversation a remembered call stands for: a chat when it carries a chat id, otherwise a call. */
    public static MemorySource conversation(RememberedCall call, Instant at) {
        boolean chat = call.chatSessionId() != null;
        return new MemorySource(chat ? MemorySourceKind.CHAT : MemorySourceKind.CALL, call.callAttemptId(),
                call.chatSessionId(), null, at);
    }
}
