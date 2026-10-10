package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.report.application.dto.*;

import java.util.List;

/**
 * UseCase interface for managing custom analytics boards and running ad-hoc analytics queries.
 */
public interface AnalyticsBoardUseCase {

    BoardDetail createBoard(long companyId, CreateBoardRequest request);

    BoardDetail updateBoard(long companyId, long boardId, UpdateBoardRequest request);

    BoardDetail getBoard(long companyId, long boardId);

    List<BoardRow> listBoards(long companyId);

    void deleteBoard(long companyId, long boardId);

    BoardWidgetRow addWidget(long companyId, long boardId, AddBoardWidgetRequest request);

    void deleteWidget(long companyId, long boardId, long widgetId);

    AnalyticsQueryResult queryAnalytics(long companyId, AnalyticsQueryRequest request);
}
