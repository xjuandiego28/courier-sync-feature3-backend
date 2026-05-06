package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.customers.domain.Rol;
import com.ep18.couriersync.backend.customers.dto.RolDTOs.CreateRolInput;
import com.ep18.couriersync.backend.customers.dto.RolDTOs.UpdateRolInput;
import com.ep18.couriersync.backend.customers.repository.RolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolServiceTest {

    @Mock
    private RolRepository rolRepo;

    @InjectMocks
    private RolService service;

    @Test
    void createStoresRoleWhenNameIsAvailable() {
        when(rolRepo.existsByNombreRolIgnoreCase("CLIENTE")).thenReturn(false);
        when(rolRepo.save(any(Rol.class))).thenAnswer(invocation -> {
            Rol rol = invocation.getArgument(0);
            rol.setIdRol(3);
            return rol;
        });

        var view = service.create(new CreateRolInput("CLIENTE"));

        assertThat(view.idRol()).isEqualTo(3);
        assertThat(view.nombreRol()).isEqualTo("CLIENTE");
    }

    @Test
    void createRejectsDuplicatedName() {
        when(rolRepo.existsByNombreRolIgnoreCase("CLIENTE")).thenReturn(true);

        CreateRolInput input = new CreateRolInput("CLIENTE");
        assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void updateRenamesRoleWhenNameIsAvailable() {
        Rol existing = rol(3, "CLIENTE");
        when(rolRepo.findById(3)).thenReturn(Optional.of(existing));
        when(rolRepo.existsByNombreRolIgnoreCase("ADMIN")).thenReturn(false);
        when(rolRepo.save(existing)).thenReturn(existing);

        var view = service.update(new UpdateRolInput(3, "ADMIN"));

        assertThat(view.idRol()).isEqualTo(3);
        assertThat(view.nombreRol()).isEqualTo("ADMIN");
    }

    @Test
    void updateRejectsMissingAndDuplicatedRoles() {
        when(rolRepo.findById(404)).thenReturn(Optional.empty());

        UpdateRolInput missingRoleInput = new UpdateRolInput(404, "ADMIN");
        assertThatThrownBy(() -> service.update(missingRoleInput))
                .isInstanceOf(NotFoundException.class);

        Rol existing = rol(3, "CLIENTE");
        when(rolRepo.findById(3)).thenReturn(Optional.of(existing));
        when(rolRepo.existsByNombreRolIgnoreCase("ADMIN")).thenReturn(true);

        UpdateRolInput duplicatedNameInput = new UpdateRolInput(3, "ADMIN");
        assertThatThrownBy(() -> service.update(duplicatedNameInput))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void findListAndDeleteHandleRepositoryResults() {
        Rol rol = rol(3, "CLIENTE");
        when(rolRepo.findById(3)).thenReturn(Optional.of(rol));
        assertThat(service.findById(3).nombreRol()).isEqualTo("CLIENTE");

        when(rolRepo.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rol)));
        assertThat(service.list(0, 10).content()).hasSize(1);

        when(rolRepo.existsById(3)).thenReturn(true);
        assertThat(service.delete(3)).isTrue();
        verify(rolRepo).deleteById(3);
    }

    @Test
    void deleteReturnsFalseWhenMissingAndWrapsIntegrityViolations() {
        when(rolRepo.existsById(1)).thenReturn(false);
        assertThat(service.delete(1)).isFalse();

        when(rolRepo.existsById(2)).thenReturn(true);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk"))
                .when(rolRepo).deleteById(2);

        assertThatThrownBy(() -> service.delete(2)).isInstanceOf(ConflictException.class);
    }

    private static Rol rol(Integer id, String nombre) {
        Rol rol = new Rol();
        rol.setIdRol(id);
        rol.setNombreRol(nombre);
        return rol;
    }
}
