package uz.murodjon.robotcallv2.chat.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A line a person from the company writes into a conversation they took over. */
public record OperatorChatMessageRequest(@NotBlank @Size(max = 4000) String text) {
}
