package uz.murodjon.robotcallv2.chat.application.port.input;

import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.ChatRow;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/** The panel's view of the company's text conversations — read-only (UNIVERSAL-OPERATOR-PLAN E.4). */
public interface ChatHistoryUseCase {

    PageableData<ChatRow> findChats(long companyId, ChatFilter filter);

    ChatDetail requireChat(long companyId, long id);
}
