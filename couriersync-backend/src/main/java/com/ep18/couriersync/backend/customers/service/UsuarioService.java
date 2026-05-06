package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.dto.PagingDTOs.PageResponse;
import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.pagination.PageMapper;
import com.ep18.couriersync.backend.common.pagination.PageRequestUtil;
import com.ep18.couriersync.backend.customers.domain.Ciudad;
import com.ep18.couriersync.backend.customers.domain.Departamento;
import com.ep18.couriersync.backend.customers.domain.Rol;
import com.ep18.couriersync.backend.customers.domain.Usuario;
import com.ep18.couriersync.backend.customers.dto.UsuarioDTOs.CreateUsuarioInput;
import com.ep18.couriersync.backend.customers.dto.UsuarioDTOs.UpdateUsuarioInput;
import com.ep18.couriersync.backend.customers.dto.UsuarioDTOs.UsuarioView;
import com.ep18.couriersync.backend.customers.repository.CiudadRepository;
import com.ep18.couriersync.backend.customers.repository.DepartamentoRepository;
import com.ep18.couriersync.backend.customers.repository.RolRepository;
import com.ep18.couriersync.backend.customers.repository.UsuarioRepository;
import com.ep18.couriersync.backend.customers.validator.UsuarioValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final String SORT_BY_NOMBRE = "nombre";

    private final UsuarioRepository usuarioRepo;
    private final CiudadRepository ciudadRepo;
    private final DepartamentoRepository departamentoRepo;
    private final RolRepository rolRepo;

    @Transactional
    public UsuarioView create(CreateUsuarioInput in) {
        assertCorreoDisponible(in.correo());

        UsuarioReferences refs = findCreateReferences(in);
        Usuario usuario = new Usuario();
        applyCreateFields(usuario, in, refs);

        return toView(usuarioRepo.save(usuario));
    }

    @Transactional
    public UsuarioView update(UpdateUsuarioInput in) {
        Usuario usuario = findUsuarioOrThrow(in.idUsuario());

        assertCorreoDisponibleForUpdate(in.correo(), usuario);
        applyBasicChanges(usuario, in);
        applyLocationChanges(usuario, in);
        applyRolChange(usuario, in.idRol());

        return toView(usuarioRepo.save(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioView findById(Integer id) {
        return toView(findUsuarioOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioView> search(String q, Integer page, Integer size) {
        Page<Usuario> p = usuarioRepo.findByNombreContainingIgnoreCase(
                valueOrDefault(q, ""), PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(p, this::toView);
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioView> listByCiudad(Integer idCiudad, Integer page, Integer size) {
        Page<Usuario> p = usuarioRepo.findAllByCiudad_IdCiudad(
                idCiudad, PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(p, this::toView);
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioView> listByDepartamento(Integer idDepto, Integer page, Integer size) {
        Page<Usuario> p = usuarioRepo.findAllByDepartamento_IdDepartamento(
                idDepto, PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(p, this::toView);
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioView> listByRol(Integer idRol, Integer page, Integer size) {
        Page<Usuario> p = usuarioRepo.findAllByRol_IdRol(
                idRol, PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(p, this::toView);
    }

    @Transactional
    public boolean delete(Integer id) {
        if (!usuarioRepo.existsById(id)) {
            return false;
        }

        deleteExistingUsuario(id);
        return true;
    }

    private UsuarioReferences findCreateReferences(CreateUsuarioInput in) {
        Ciudad ciudad = findCiudadOrThrow(in.idCiudad());
        Departamento departamento = findDepartamentoOrThrow(in.idDepartamento());
        Rol rol = findRolOrThrow(in.idRol());

        UsuarioValidator.assertCiudadPerteneceADepartamento(ciudad, departamento.getIdDepartamento());
        return new UsuarioReferences(ciudad, departamento, rol);
    }

    private void applyCreateFields(Usuario usuario, CreateUsuarioInput in, UsuarioReferences refs) {
        usuario.setNombre(in.nombre());
        usuario.setCorreo(in.correo());
        usuario.setTelefono(in.telefono());
        usuario.setFechaRegistro(valueOrDefault(in.fechaRegistro(), LocalDate.now()));
        usuario.setDetalleDireccion(in.detalleDireccion());
        usuario.setCiudad(refs.ciudad());
        usuario.setDepartamento(refs.departamento());
        usuario.setRol(refs.rol());
    }

    private void applyBasicChanges(Usuario usuario, UpdateUsuarioInput in) {
        setIfPresent(in.nombre(), usuario::setNombre);
        setIfPresent(in.correo(), usuario::setCorreo);
        setIfPresent(in.telefono(), usuario::setTelefono);
        setIfPresent(in.fechaRegistro(), usuario::setFechaRegistro);
        setIfPresent(in.detalleDireccion(), usuario::setDetalleDireccion);
    }

    private void applyLocationChanges(Usuario usuario, UpdateUsuarioInput in) {
        if (!hasLocationChanges(in)) {
            return;
        }

        Ciudad ciudad = valueOrFind(in.idCiudad(), usuario.getCiudad(), this::findCiudadOrThrow);
        Departamento departamento = valueOrFind(
                in.idDepartamento(), usuario.getDepartamento(), this::findDepartamentoOrThrow);

        UsuarioValidator.assertCiudadPerteneceADepartamento(ciudad, departamento.getIdDepartamento());
        usuario.setCiudad(ciudad);
        usuario.setDepartamento(departamento);
    }

    private boolean hasLocationChanges(UpdateUsuarioInput in) {
        return in.idCiudad() != null || in.idDepartamento() != null;
    }

    private void applyRolChange(Usuario usuario, Integer idRol) {
        setIfPresent(idRol, id -> usuario.setRol(findRolOrThrow(id)));
    }

    private void assertCorreoDisponible(String correo) {
        if (usuarioRepo.existsByCorreoIgnoreCase(correo)) {
            throw new ConflictException("El correo ya esta registrado");
        }
    }

    private void assertCorreoDisponibleForUpdate(String correo, Usuario usuario) {
        if (correo == null || correo.equalsIgnoreCase(usuario.getCorreo())) {
            return;
        }

        assertCorreoDisponible(correo);
    }

    private Usuario findUsuarioOrThrow(Integer id) {
        return usuarioRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private Ciudad findCiudadOrThrow(Integer id) {
        return ciudadRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Ciudad no encontrada"));
    }

    private Departamento findDepartamentoOrThrow(Integer id) {
        return departamentoRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Departamento no encontrado"));
    }

    private Rol findRolOrThrow(Integer id) {
        return rolRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Rol no encontrado"));
    }

    private void deleteExistingUsuario(Integer id) {
        try {
            usuarioRepo.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("No se puede eliminar: existen registros relacionados");
        }
    }

    private <T> T valueOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private <T> T valueOrFind(Integer id, T currentValue, Finder<T> finder) {
        return id == null ? currentValue : finder.find(id);
    }

    private <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private UsuarioView toView(Usuario u) {
        return new UsuarioView(
                u.getIdUsuario(),
                u.getNombre(),
                u.getCorreo(),
                u.getTelefono(),
                u.getFechaRegistro(),
                u.getDetalleDireccion(),
                u.getCiudad().getIdCiudad(),
                u.getCiudad().getNombreCiudad(),
                u.getDepartamento().getIdDepartamento(),
                u.getDepartamento().getNombreDepartamento(),
                u.getRol().getIdRol(),
                u.getRol().getNombreRol()
        );
    }

    private record UsuarioReferences(Ciudad ciudad, Departamento departamento, Rol rol) {
    }

    @FunctionalInterface
    private interface Finder<T> {
        T find(Integer id);
    }
}
