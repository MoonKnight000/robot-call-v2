package uz.murodjon.robotcallv2.siptrunk.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.siptrunk.application.dto.AddCallerIdNumberRequest;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CallerIdPoolDetail;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CreateCallerIdPoolRequest;
import uz.murodjon.robotcallv2.siptrunk.application.mapper.CallerIdPoolMapper;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.CallerIdPoolRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPool;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallerIdPoolServiceTest {

    @Mock
    private CallerIdPoolRepository repository;

    private CallerIdPoolMapper mapper;
    private CallerIdPoolService service;

    @BeforeEach
    void setUp() {
        mapper = new CallerIdPoolMapper();
        service = new CallerIdPoolService(repository, mapper);
    }

    @Test
    void createPoolSavesAndReturnsDetail() {
        long companyId = 1L;
        CreateCallerIdPoolRequest request = new CreateCallerIdPoolRequest(
                "Tashkent Pool",
                CallerIdStrategy.ROUND_ROBIN,
                "Central region",
                List.of("+998712001122")
        );

        CallerIdPool saved = new CallerIdPool(10L, companyId, "Tashkent Pool", CallerIdStrategy.ROUND_ROBIN, "Central region", List.of(), Instant.now(), Instant.now());
        when(repository.save(eq(companyId), any(CallerIdPool.class))).thenReturn(saved);
        when(repository.findById(companyId, 10L)).thenReturn(Optional.of(saved));
        when(repository.addNumber(companyId, 10L, "+998712001122", null)).thenReturn(number(100L, 10L, 3L));

        CallerIdPoolDetail detail = service.createPool(companyId, request);

        assertThat(detail.id()).isEqualTo(10L);
        assertThat(detail.name()).isEqualTo("Tashkent Pool");
        verify(repository).addNumber(companyId, 10L, "+998712001122", null);
    }

    /** A pool member must be one of the company's registered numbers — free text has no trunk. */
    @Test
    void addNumberRefusesANumberTheCompanyHasNotRegistered() {
        CallerIdPool pool = new CallerIdPool(10L, 1L, "Main Pool", CallerIdStrategy.ROUND_ROBIN, null, List.of(), Instant.now(), Instant.now());
        when(repository.findById(1L, 10L)).thenReturn(Optional.of(pool));
        when(repository.addNumber(1L, 10L, "+998712001122", "Line 1")).thenReturn(null);

        assertThatThrownBy(() -> service.addNumber(1L, 10L, new AddCallerIdNumberRequest("998 71 200 11 22", "Line 1")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acquireCallerIdOnTrunksReturnsTheNumberAndRecordsUsage() {
        long companyId = 1L;
        long poolId = 10L;
        CallerIdPoolNumber num = number(100L, poolId, 3L);
        CallerIdPool pool = new CallerIdPool(poolId, companyId, "Main Pool", CallerIdStrategy.ROUND_ROBIN, null, List.of(num), Instant.now(), Instant.now());

        when(repository.findById(companyId, poolId)).thenReturn(Optional.of(pool));

        CallerIdPoolNumber acquired = service.acquireCallerIdOnTrunks(companyId, poolId, null, Set.of(3L));

        assertThat(acquired.phone()).isEqualTo("+998712001122");
        assertThat(acquired.trunkId()).isEqualTo(3L);
        verify(repository).recordNumberUsage(eq(poolId), eq(100L), any(Instant.class));
    }

    /** A number whose trunk cannot carry the call now is no choice: null, and no usage is recorded. */
    @Test
    void acquireCallerIdOnTrunksReturnsNullWhenNoNumberIsOnAnOfferedTrunk() {
        long companyId = 1L;
        long poolId = 10L;
        CallerIdPool pool = new CallerIdPool(poolId, companyId, "Main Pool", CallerIdStrategy.ROUND_ROBIN, null,
                List.of(number(100L, poolId, 3L), number(101L, poolId, null)), Instant.now(), Instant.now());

        when(repository.findById(companyId, poolId)).thenReturn(Optional.of(pool));

        assertThat(service.acquireCallerIdOnTrunks(companyId, poolId, null, Set.of(4L))).isNull();
        verify(repository, never()).recordNumberUsage(anyLong(), anyLong(), any());
    }

    private static CallerIdPoolNumber number(long id, long poolId, Long trunkId) {
        return new CallerIdPoolNumber(id, poolId, "+998712001122", "Line 1", 5, 2, CallerIdNumberStatus.ACTIVE,
                null, null, Instant.now(), trunkId);
    }

    @Test
    void getPoolThrowsNotFoundWhenMissing() {
        when(repository.findById(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPool(1L, 999L))
                .isInstanceOf(NotFoundException.class);
    }
}
