package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.customers.domain.Ciudad;
import com.ep18.couriersync.backend.customers.domain.Departamento;
import com.ep18.couriersync.backend.customers.dto.CiudadDTOs.CreateCiudadInput;
import com.ep18.couriersync.backend.customers.dto.CiudadDTOs.UpdateCiudadInput;
import com.ep18.couriersync.backend.customers.repository.CiudadRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CiudadServiceTest {

    @Mock
    private CiudadRepository ciudadRepo;

    @Mock
    private DepartamentoRepository departamentoRepo;

    @InjectMocks
    private CiudadService service;

    @Test
    void createValidatesDepartmentAndDuplicateName() {
        Departamento antioquia = departamento(5, "Antioquia");
        when(departamentoRepo.findById(5)).thenReturn(Optional.of(antioquia));
        when(ciudadRepo.existsByNombreCiudadIgnoreCaseAndDepartamento_IdDepartamento("Medellin", 5))
                .thenReturn(false);
        when(ciudadRepo.save(any(Ciudad.class))).thenAnswer(invocation -> {
            Ciudad ciudad = invocation.getArgument(0);
            ciudad.setIdCiudad(9);
            return ciudad;
        });

        var view = service.create(new CreateCiudadInput("Medellin", 5));

        assertThat(view.idCiudad()).isEqualTo(9);
        assertThat(view.nombreCiudad()).isEqualTo("Medellin");
        assertThat(view.nombreDepartamento()).isEqualTo("Antioquia");
    }

    @Test
    void createFailsWhenDepartmentIsMissingOrNameExists() {
        when(departamentoRepo.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateCiudadInput("Medellin", 404)))
                .isInstanceOf(NotFoundException.class);

        when(departamentoRepo.findById(5)).thenReturn(Optional.of(departamento(5, "Antioquia")));
        when(ciudadRepo.existsByNombreCiudadIgnoreCaseAndDepartamento_IdDepartamento("Medellin", 5))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateCiudadInput("Medellin", 5)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateCanRenameAndMoveDepartment() {
        Ciudad ciudad = ciudad(3, "Bello", departamento(5, "Antioquia"));
        Departamento caldas = departamento(6, "Caldas");
        when(ciudadRepo.findById(3)).thenReturn(Optional.of(ciudad));
        when(ciudadRepo.existsByNombreCiudadIgnoreCaseAndDepartamento_IdDepartamento("Manizales", 5))
                .thenReturn(false);
        when(departamentoRepo.findById(6)).thenReturn(Optional.of(caldas));
        when(ciudadRepo.existsByNombreCiudadIgnoreCaseAndDepartamento_IdDepartamento("Manizales", 6))
                .thenReturn(false);
        when(ciudadRepo.save(ciudad)).thenReturn(ciudad);

        var view = service.update(new UpdateCiudadInput(3, "Manizales", 6));

        assertThat(view.nombreCiudad()).isEqualTo("Manizales");
        assertThat(view.idDepartamento()).isEqualTo(6);
    }

    @Test
    void updateRejectsDuplicatedNameInNewDepartment() {
        Ciudad ciudad = ciudad(3, "Bello", departamento(5, "Antioquia"));
        Departamento caldas = departamento(6, "Caldas");
        when(ciudadRepo.findById(3)).thenReturn(Optional.of(ciudad));
        when(departamentoRepo.findById(6)).thenReturn(Optional.of(caldas));
        when(ciudadRepo.existsByNombreCiudadIgnoreCaseAndDepartamento_IdDepartamento("Bello", 6))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(new UpdateCiudadInput(3, null, 6)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateKeepsCityWhenNameAndDepartmentDoNotChange() {
        Ciudad ciudad = ciudad(3, "Bello", departamento(5, "Antioquia"));
        when(ciudadRepo.findById(3)).thenReturn(Optional.of(ciudad));
        when(ciudadRepo.save(ciudad)).thenReturn(ciudad);

        var view = service.update(new UpdateCiudadInput(3, "BELLO", 5));

        assertThat(view.idCiudad()).isEqualTo(3);
        assertThat(view.nombreCiudad()).isEqualTo("Bello");
        assertThat(view.idDepartamento()).isEqualTo(5);
    }

    @Test
    void updateRejectsMissingCityOrDepartment() {
        when(ciudadRepo.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdateCiudadInput(404, "Bello", null)))
                .isInstanceOf(NotFoundException.class);

        Ciudad ciudad = ciudad(3, "Bello", departamento(5, "Antioquia"));
        when(ciudadRepo.findById(3)).thenReturn(Optional.of(ciudad));
        when(departamentoRepo.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdateCiudadInput(3, null, 404)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void searchUsesEmptyQueryAndDeleteWrapsIntegrityViolations() {
        when(ciudadRepo.findByNombreCiudadContainingIgnoreCase(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(ciudad(3, "Bello", departamento(5, "Antioquia")))));
        assertThat(service.search(null, 0, 10).content()).hasSize(1);
        verify(ciudadRepo).findByNombreCiudadContainingIgnoreCase(eq(""), any());

        when(ciudadRepo.existsById(3)).thenReturn(true);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk")).when(ciudadRepo).deleteById(3);
        assertThatThrownBy(() -> service.delete(3)).isInstanceOf(ConflictException.class);
    }

    @Test
    void findListAndDeleteHandleRepositoryResults() {
        Ciudad ciudad = ciudad(3, "Bello", departamento(5, "Antioquia"));
        when(ciudadRepo.findById(3)).thenReturn(Optional.of(ciudad));
        when(ciudadRepo.findById(404)).thenReturn(Optional.empty());

        assertThat(service.findById(3).nombreCiudad()).isEqualTo("Bello");
        assertThatThrownBy(() -> service.findById(404)).isInstanceOf(NotFoundException.class);

        when(ciudadRepo.findAllByDepartamento_IdDepartamento(eq(5), any()))
                .thenReturn(new PageImpl<>(List.of(ciudad)));
        assertThat(service.listByDepartamento(5, 0, 10).content()).hasSize(1);

        when(ciudadRepo.existsById(99)).thenReturn(false);
        assertThat(service.delete(99)).isFalse();
    }

    private static Ciudad ciudad(Integer id, String nombre, Departamento departamento) {
        Ciudad ciudad = new Ciudad();
        ciudad.setIdCiudad(id);
        ciudad.setNombreCiudad(nombre);
        ciudad.setDepartamento(departamento);
        return ciudad;
    }

    private static Departamento departamento(Integer id, String nombre) {
        Departamento departamento = new Departamento();
        departamento.setIdDepartamento(id);
        departamento.setNombreDepartamento(nombre);
        return departamento;
    }
}
