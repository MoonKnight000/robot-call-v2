package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.report.application.dto.*;
import uz.murodjon.robotcallv2.report.application.port.input.AnalyticsBoardUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class AnalyticsBoardControllerImpl implements AnalyticsBoardController {

    private final AnalyticsBoardUseCase analyticsBoardUseCase;

    public AnalyticsBoardControllerImpl(AnalyticsBoardUseCase analyticsBoardUseCase) {
        this.analyticsBoardUseCase = analyticsBoardUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<BoardDetail>> create(long companyId, CreateBoardRequest request) {
        return ResponseEntity.ok(ResponseData.ok(analyticsBoardUseCase.createBoard(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<BoardRow>>> list(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(analyticsBoardUseCase.listBoards(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<BoardDetail>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(analyticsBoardUseCase.getBoard(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<BoardDetail>> update(long companyId, long id, UpdateBoardRequest request) {
        return ResponseEntity.ok(ResponseData.ok(analyticsBoardUseCase.updateBoard(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        analyticsBoardUseCase.deleteBoard(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<BoardWidgetRow>> addWidget(long companyId, long id, AddBoardWidgetRequest request) {
        return ResponseEntity.ok(ResponseData.ok(analyticsBoardUseCase.addWidget(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteWidget(long companyId, long id, long widgetId) {
        analyticsBoardUseCase.deleteWidget(companyId, id, widgetId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<AnalyticsQueryResult>> query(long companyId, AnalyticsQueryRequest request) {
        return ResponseEntity.ok(ResponseData.ok(analyticsBoardUseCase.queryAnalytics(companyId, request)));
    }
}
