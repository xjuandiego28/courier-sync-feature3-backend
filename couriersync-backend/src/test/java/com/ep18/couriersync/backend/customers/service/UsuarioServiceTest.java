package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.exception.ValidationException;
import com.ep18.couriersync.backend.customers.domain.Ciudad;
import com.ep18.couriersync.backend.customers.domain.Departamento;
import com.ep18.couriersync.backend.customers.domain.Rol;
import com.ep18.couriersync.backend.customers.domain.Usuario;
import com.ep18.couriersync.backend.customers.dto.UsuarioDTOs.CreateUsuarioInput;
import com.ep18.couriersync.backend.customers.dto.UsuarioDTOs.UpdateUsuarioInput;
import com.ep18.couriersync.backend.customers.repository.CiudadRepository;
import com.ep18.couriersync.backend.customers.repository.DepartamentoRepository;
import com.ep18.couriersync.backend.customers.repository.RolRepository;
import com.ep18.couriersync.backend.customers.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
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
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepo;

    @Mock
    private CiudadRepository ciudadRepo;

    @Mock
    private DepartamentoRepository departamentoRepo;

    @Mock
    private RolRepository rolRepo;

    @InjectMocks
    private UsuarioService service;

    @Test
    void createSavesUserWhenReferencesAreValid() {
        Departamento departamento = departamento(1, "Antioquia");
        Ciudad ciudad = ciudad(2, "Medellin", departamento);
        Rol rol = rol(3, "CLIENTE");
        when(usuarioRepo.existsByCorreoIgnoreCase("ana@mail.com")).thenReturn(false);
        when(ciudadRepo.findById(2)).thenReturn(Optional.of(ciudad));
        when(departamentoRepo.findById(1)).thenReturn(Optional.of(departamento));
        when(rolRepo.findById(3)).thenReturn(Optional.of(rol));
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setIdUsuario(9);
            return usuario;
        });

        var view = service.create(new CreateUsuarioInput(
                "Ana", "ana@mail.com", "3001234567", LocalDate.of(2026, 5, 1), "Calle 1", 2, 1, 3));

        assertThat(view.idUsuario()).isEqualTo(9);
        assertThat(view.nombreCiudad()).isEqualTo("Medellin");
        assertThat(view.nombreDepartamento()).isEqualTo("Antioquia");
        assertThat(view.nombreRol()).isEqualTo("CLIENTE");
    }

    @Test
    void createRejectsDuplicatedEmailAndInvalidLocation() {
        when(usuarioRepo.existsByCorreoIgnoreCase("ana@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUsuarioInput(
                "Ana", "ana@mail.com", "3001234567", null, "Calle 1", 2, 1, 3)))
                .isInstanceOf(ConflictException.class);

        Departamento departamento = departamento(1, "Antioquia");
        Ciudad ciudad = ciudad(2, "Cali", departamento(4, "Valle"));
        when(usuarioRepo.existsByCorreoIgnoreCase("bea@mail.com")).thenReturn(false);
        when(ciudadRepo.findById(2)).thenReturn(Optional.of(ciudad));
        when(departamentoRepo.findById(1)).thenReturn(Optional.of(departamento));
        when(rolRepo.findById(3)).thenReturn(Optional.of(rol(3, "CLIENTE")));

        assertThatThrownBy(() -> service.create(new CreateUsuarioInput(
                "Bea", "bea@mail.com", "3001234567", null, "Calle 1", 2, 1, 3)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateAppliesProvidedFieldsAndReferenceChanges() {
        Departamento antioquia = departamento(1, "Antioquia");
        Departamento caldas = departamento(4, "Caldas");
        Usuario usuario = usuario(9, "Ana", "ana@mail.com", ciudad(2, "Medellin", antioquia), antioquia, rol(3, "CLIENTE"));
        when(usuarioRepo.findById(9)).thenReturn(Optional.of(usuario));
        when(usuarioRepo.existsByCorreoIgnoreCase("ana.nueva@mail.com")).thenReturn(false);
        when(ciudadRepo.findById(5)).thenReturn(Optional.of(ciudad(5, "Manizales", caldas)));
        when(departamentoRepo.findById(4)).thenReturn(Optional.of(caldas));
        when(rolRepo.findById(6)).thenReturn(Optional.of(rol(6, "ADMIN")));
        when(usuarioRepo.save(usuario)).thenReturn(usuario);

        var view = service.update(new UpdateUsuarioInput(
                9, "Ana Maria", "ana.nueva@mail.com", null, null, null, 5, 4, 6));

        assertThat(view.nombre()).isEqualTo("Ana Maria");
        assertThat(view.correo()).isEqualTo("ana.nueva@mail.com");
        assertThat(view.nombreCiudad()).isEqualTo("Manizales");
        assertThat(view.nombreRol()).isEqualTo("ADMIN");
    }

    @Test
    void updateAllowsSameEmailIgnoringCaseAndRejectsMissingUser() {
        Departamento departamento = departamento(1, "Antioquia");
        Usuario usuario = usuario(9, "Ana", "ana@mail.com", ciudad(2, "Medellin", departamento), departamento, rol(3, "CLIENTE"));
        when(usuarioRepo.findById(9)).thenReturn(Optional.of(usuario));
        when(usuarioRepo.save(usuario)).thenReturn(usuario);

        var view = service.update(new UpdateUsuarioInput(9, null, "ANA@mail.com", null, null, null, null, null, null));

        assertThat(view.correo()).isEqualTo("ANA@mail.com");

        when(usuarioRepo.findById(404)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(new UpdateUsuarioInput(404, null, null, null, null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void searchAndDeleteHandleRepositoryResults() {
        Departamento departamento = departamento(1, "Antioquia");
        Usuario usuario = usuario(9, "Ana", "ana@mail.com", ciudad(2, "Medellin", departamento), departamento, rol(3, "CLIENTE"));
        when(usuarioRepo.findByNombreContainingIgnoreCase(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(usuario)));

        assertThat(service.search(null, 0, 10).content()).hasSize(1);
        verify(usuarioRepo).findByNombreContainingIgnoreCase(eq(""), any());

        when(usuarioRepo.existsById(9)).thenReturn(true);
        assertThat(service.delete(9)).isTrue();
        verify(usuarioRepo).deleteById(9);

        when(usuarioRepo.existsById(10)).thenReturn(true);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk")).when(usuarioRepo).deleteById(10);
        assertThatThrownBy(() -> service.delete(10)).isInstanceOf(ConflictException.class);
    }

    private static Usuario usuario(Integer id, String nombre, String correo, Ciudad ciudad, Departamento departamento, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setNombre(nombre);
        usuario.setCorreo(correo);
        usuario.setTelefono("3001234567");
        usuario.setFechaRegistro(LocalDate.of(2026, 5, 1));
        usuario.setDetalleDireccion("Calle 1");
        usuario.setCiudad(ciudad);
        usuario.setDepartamento(departamento);
        usuario.setRol(rol);
        return usuario;
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

    private static Rol rol(Integer id, String nombre) {
        Rol rol = new Rol();
        rol.setIdRol(id);
        rol.setNombreRol(nombre);
        return rol;
    }
}
