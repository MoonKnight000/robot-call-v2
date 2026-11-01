package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsContentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsRecipient;

/**
 * Settings of a SEND_SMS function.
 *
 * @param contentMode how {@code content} is used; null reads as {@link AgentSmsContentMode#STATIC}
 * @param content     the SMS text ({@code STATIC}, {@code {{variables}}} filled in) or the instruction
 *                    the model writes the text from ({@code PROMPT})
 * @param sendTo      who is texted; null reads as {@link AgentSmsRecipient#CALLER}
 * @param number      the number or {@code {{variable}}} of {@link AgentSmsRecipient#NUMBER}
 * @param speakAfter  whether the agent says the SMS was sent; false keeps it silent about it
 */
public record AgentFunctionSms(
        AgentSmsContentMode contentMode,
        String content,
        AgentSmsRecipient sendTo,
        String number,
        boolean speakAfter
) {

    public AgentFunctionSms {
        contentMode = contentMode == null ? AgentSmsContentMode.STATIC : contentMode;
        content = content == null || content.isBlank() ? null : content.trim();
        sendTo = sendTo == null ? AgentSmsRecipient.CALLER : sendTo;
        number = number == null || number.isBlank() ? null : number.trim();
    }
}
