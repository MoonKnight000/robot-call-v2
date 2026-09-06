package uz.murodjon.robotcallv2.report.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.report.domain.entity.CallDetail;
import uz.murodjon.robotcallv2.report.domain.entity.CallRow;
import uz.murodjon.robotcallv2.report.domain.entity.TranscriptLine;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptRendererTest {

    private final TranscriptRenderer renderer = new TranscriptRenderer();

    @Test
    void formatsTranscriptWithTimestampsAndSpeakers() {
        CallRow callRow = new CallRow(
                100L, 10L, "+998901234567", "uz-UZ",
                Instant.now(), Instant.now().plusSeconds(65), 65,
                Disposition.PROMISE_TO_PAY, "NORMAL_CLEARING", true, "To'lovga va'da berdi",
                null, null, null, "Ali Valiyev", "Qarz Undirish", "Robot");

        List<TranscriptLine> lines = List.of(
                new TranscriptLine(1, "AGENT", "Assalomu alaykum, Ali aka.", "GREETING", 500, null),
                new TranscriptLine(2, "CLIENT", "Assalomu alaykum, eshitaman.", null, 2500, 0.95f),
                new TranscriptLine(3, "AGENT", "Qarzdorlik bo'yicha bezovta qilmoqdamiz.", "DEBT_NOTICE", 65000, null)
        );

        CallDetail detail = new CallDetail(callRow, lines, null, null, false, null, false, null, null);

        String content = new String(renderer.renderBytes(detail), StandardCharsets.UTF_8);

        assertThat(renderer.contentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(content)
                .contains("[00:00:00] AGENT: Assalomu alaykum, Ali aka.")
                .contains("[00:00:02] CLIENT: Assalomu alaykum, eshitaman.")
                .contains("[00:01:05] AGENT: Qarzdorlik bo'yicha bezovta qilmoqdamiz.");
    }
}
