package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.report.application.dto.*;
import uz.murodjon.robotcallv2.report.application.mapper.BoardMapper;
import uz.murodjon.robotcallv2.report.application.port.input.AnalyticsBoardUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.AnalyticsBoardRepository;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoard;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoardWidget;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsDataPoint;
import uz.murodjon.robotcallv2.report.domain.service.BoardValidator;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

@Service
public class AnalyticsBoardService implements AnalyticsBoardUseCase {

    private final AnalyticsBoardRepository boardRepository;
    private final BoardMapper boardMapper;

    public AnalyticsBoardService(AnalyticsBoardRepository boardRepository, BoardMapper boardMapper) {
        this.boardRepository = boardRepository;
        this.boardMapper = boardMapper;
    }

    @Override
    @Transactional
    public BoardDetail createBoard(long companyId, CreateBoardRequest request) {
        BoardValidator.validateBoardName(request.name());
        AnalyticsBoard board = new AnalyticsBoard(
                0L,
                companyId,
                request.name().trim(),
                request.description(),
                request.isDefault(),
                List.of(),
                Instant.now(),
                Instant.now()
        );
        AnalyticsBoard saved = boardRepository.save(companyId, board);
        return getBoard(companyId, saved.id());
    }

    @Override
    @Transactional
    public BoardDetail updateBoard(long companyId, long boardId, UpdateBoardRequest request) {
        AnalyticsBoard existing = boardRepository.findById(companyId, boardId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BOARD_NOT_FOUND, boardId));

        BoardValidator.validateBoardName(request.name());
        AnalyticsBoard updated = new AnalyticsBoard(
                existing.id(),
                companyId,
                request.name().trim(),
                request.description(),
                request.isDefault(),
                existing.widgets(),
                existing.createdAt(),
                Instant.now()
        );
        AnalyticsBoard saved = boardRepository.save(companyId, updated);
        return getBoard(companyId, saved.id());
    }

    @Override
    @Transactional(readOnly = true)
    public BoardDetail getBoard(long companyId, long boardId) {
        AnalyticsBoard board = boardRepository.findById(companyId, boardId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BOARD_NOT_FOUND, boardId));
        return boardMapper.toDetail(board);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoardRow> listBoards(long companyId) {
        return boardRepository.findAllByCompanyId(companyId).stream()
                .map(boardMapper::toRow)
                .toList();
    }

    @Override
    @Transactional
    public void deleteBoard(long companyId, long boardId) {
        boardRepository.findById(companyId, boardId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BOARD_NOT_FOUND, boardId));
        boardRepository.delete(companyId, boardId);
    }

    @Override
    @Transactional
    public BoardWidgetRow addWidget(long companyId, long boardId, AddBoardWidgetRequest request) {
        boardRepository.findById(companyId, boardId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BOARD_NOT_FOUND, boardId));

        AnalyticsBoardWidget widget = new AnalyticsBoardWidget(
                0L,
                boardId,
                request.title().trim(),
                request.widgetType(),
                request.metric(),
                request.groupBy(),
                request.timeRange(),
                request.gridRow(),
                request.gridCol(),
                request.gridWidth(),
                request.gridHeight(),
                Instant.now()
        );
        AnalyticsBoardWidget saved = boardRepository.saveWidget(companyId, boardId, widget);
        return boardMapper.toWidgetRow(saved);
    }

    @Override
    @Transactional
    public void deleteWidget(long companyId, long boardId, long widgetId) {
        boardRepository.findById(companyId, boardId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BOARD_NOT_FOUND, boardId));
        boardRepository.deleteWidget(companyId, boardId, widgetId);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsQueryResult queryAnalytics(long companyId, AnalyticsQueryRequest request) {
        List<AnalyticsDataPoint> points = boardRepository.queryAnalytics(
                companyId,
                request.metric(),
                request.groupBy(),
                request.from(),
                request.to(),
                request.campaignId(),
                request.agentId()
        );
        return new AnalyticsQueryResult(
                request.metric().name(),
                request.groupBy() != null ? request.groupBy().name() : "NONE",
                points
        );
    }
}
