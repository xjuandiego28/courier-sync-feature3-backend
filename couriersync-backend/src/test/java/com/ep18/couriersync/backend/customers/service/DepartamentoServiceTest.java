package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.customers.domain.Departamento;
import com.ep18.couriersync.backend.customers.dto.DepartamentoDTOs.CreateDepartamentoInput;
import com.ep18.couriersync.backend.customers.dto.DepartamentoDTOs.UpdateDepartamentoInput;
import com.ep18.couriersync.backend.customers.repository.DepartamentoRepository;
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
class DepartamentoServiceTest {

    @Mock
    private DepartamentoRepository departamentoRepo;

    @InjectMocks
    private DepartamentoService service;

    @Test
    void createStoresDepartmentWhenNameIsAvailable() {
        when(departamentoRepo.existsByNombreDepartamentoIgnoreCase("Antioquia")).thenReturn(false);
        when(departamentoRepo.save(any(Departamento.class))).thenAnswer(invocation -> {
            Departamento departamento = invocation.getArgument(0);
            departamento.setIdDepartamento(5);
            return departamento;
        });

        var view = service.create(new CreateDepartamentoInput("Antioquia"));

        assertThat(view.idDepartamento()).isEqualTo(5);
        assertThat(view.nombreDepartamento()).isEqualTo("Antioquia");
    }

    @Test
    void createRejectsDuplicatedName() {
        when(departamentoRepo.existsByNombreDepartamentoIgnoreCase("Antioquia")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateDepartamentoInput("Antioquia")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void updateRenamesDepartmentWhenNameIsAvailable() {
        Departamento existing = departamento(5, "Antioquia");
        when(departamentoRepo.findById(5)).thenReturn(Optional.of(existing));
        when(departamentoRepo.existsByNombreDepartamentoIgnoreCase("Caldas")).thenReturn(false);
        when(departamentoRepo.save(existing)).thenReturn(existing);

        var view = service.update(new UpdateDepartamentoInput(5, "Caldas"));

        assertThat(view.idDepartamento()).isEqualTo(5);
        assertThat(view.nombreDepartamento()).isEqualTo("Caldas");
    }

    @Test
    void updateRejectsMissingAndDuplicatedDepartments() {
        when(departamentoRepo.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdateDepartamentoInput(404, "Caldas")))
                .isInstanceOf(NotFoundException.class);

        Departamento existing = departamento(5, "Antioquia");
        when(departamentoRepo.findById(5)).thenReturn(Optional.of(existing));
        when(departamentoRepo.existsByNombreDepartamentoIgnoreCase("Caldas")).thenReturn(true);

        assertThatThrownBy(() -> service.update(new UpdateDepartamentoInput(5, "Caldas")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void findListAndDeleteHandleRepositoryResults() {
        Departamento departamento = departamento(5, "Antioquia");
        when(departamentoRepo.findById(5)).thenReturn(Optional.of(departamento));
        assertThat(service.findById(5).nombreDepartamento()).isEqualTo("Antioquia");

        when(departamentoRepo.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(departamento)));
        assertThat(service.list(0, 10).content()).hasSize(1);

        when(departamentoRepo.existsById(5)).thenReturn(true);
        assertThat(service.delete(5)).isTrue();
        verify(departamentoRepo).deleteById(5);
    }

    @Test
    void deleteReturnsFalseWhenMissingAndWrapsIntegrityViolations() {
        when(departamentoRepo.existsById(1)).thenReturn(false);
        assertThat(service.delete(1)).isFalse();

        when(departamentoRepo.existsById(2)).thenReturn(true);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk"))
                .when(departamentoRepo).deleteById(2);

        assertThatThrownBy(() -> service.delete(2)).isInstanceOf(ConflictException.class);
    }

    private static Departamento departamento(Integer id, String nombre) {
        Departamento departamento = new Departamento();
        departamento.setIdDepartamento(id);
        departamento.setNombreDepartamento(nombre);
        return departamento;
    }
}
