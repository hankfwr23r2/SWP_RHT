package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.SystemLog;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.SystemLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminSystemLogServiceTest {
    private final SystemLogRepository repository = mock(SystemLogRepository.class);
    private final AdminSystemLogService service = new AdminSystemLogService(repository);

    @Test
    void excludesOrderLogsFromDetailAndRejectsOrderFilter() {
        when(repository.findById(5)).thenReturn(Optional.of(SystemLog.builder().id(5).targetTable("BOOKING").build()));
        assertThatThrownBy(() -> service.detail(5)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.search("BOOKING", null, null, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyRecordsRemainReadableWithoutInventingAnActor() {
        when(repository.findById(1)).thenReturn(Optional.of(SystemLog.builder().id(1).targetTable("USER")
                .targetRecordID(42).newData("Khóa tài khoản cũ").build()));
        var response = service.detail(1);
        assertThat(response.legacy()).isTrue();
        assertThat(response.actorName()).isNull();
        assertThat(response.targetKey()).isEqualTo("42");
        assertThat(response.newData()).isEqualTo("Khóa tài khoản cũ");
    }

    @Test
    void newRecordKeepsSnapshotWhenAccountNameChanges() {
        User currentUser = new User(); currentUser.setId(7); currentUser.setUsername("Changed name");
        when(repository.findById(2)).thenReturn(Optional.of(SystemLog.builder().id(2).targetTable("USER")
                .dataVersion(1).actorName("Original name").actionbyUserid(currentUser).build()));
        assertThat(service.detail(2).actorName()).isEqualTo("Original name");
    }

    @Test
    void validatesPaginationAndTimeRangeBeforeQuerying() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        Instant now = Instant.now();
        assertThatThrownBy(() -> service.search(null, null, null, null, now, now, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesServerPaginationAndStableTimeThenIdOrder() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<SystemLog>(List.of()));
        var page = service.search("USER", "UPDATE_ACCOUNT", null, null, null, null, 2, 50);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().stream().map(order -> order.getProperty()).toList())
                .containsExactly("createdAt", "id");
        assertThat(page.content()).isEmpty();
    }
}
