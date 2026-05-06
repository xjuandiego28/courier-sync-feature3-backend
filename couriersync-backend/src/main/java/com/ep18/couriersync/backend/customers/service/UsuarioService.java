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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.ep18.couriersync.backend.common.service.ServiceOperations.deleteIfPresent;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.findOrThrow;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.rejectDuplicatedChange;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.rejectWhen;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.setIfPresent;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.valueOrDefault;

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
        return deleteIfPresent(
                usuarioRepo,
                id,
                () -> new ConflictException("No se puede eliminar: existen registros relacionados"));
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
        Optional.of(in)
                .filter(this::hasLocationChanges)
                .ifPresent(input -> updateLocation(usuario, input));
    }

    private boolean hasLocationChanges(UpdateUsuarioInput in) {
        return Stream.of(in.idCiudad(), in.idDepartamento()).anyMatch(Objects::nonNull);
    }

    private void updateLocation(Usuario usuario, UpdateUsuarioInput in) {
        Ciudad ciudad = valueOrFind(in.idCiudad(), usuario.getCiudad(), this::findCiudadOrThrow);
        Departamento departamento = valueOrFind(
                in.idDepartamento(), usuario.getDepartamento(), this::findDepartamentoOrThrow);

        UsuarioValidator.assertCiudadPerteneceADepartamento(ciudad, departamento.getIdDepartamento());
        usuario.setCiudad(ciudad);
        usuario.setDepartamento(departamento);
    }

    private void applyRolChange(Usuario usuario, Integer idRol) {
        setIfPresent(idRol, id -> usuario.setRol(findRolOrThrow(id)));
    }

    private void assertCorreoDisponible(String correo) {
        rejectWhen(
                usuarioRepo.existsByCorreoIgnoreCase(correo),
                () -> new ConflictException("El correo ya esta registrado"));
    }

    private void assertCorreoDisponibleForUpdate(String correo, Usuario usuario) {
        rejectDuplicatedChange(
                correo,
                usuario.getCorreo(),
                String::equalsIgnoreCase,
                usuarioRepo::existsByCorreoIgnoreCase,
                () -> new ConflictException("El correo ya esta registrado"));
    }

    private Usuario findUsuarioOrThrow(Integer id) {
        return findOrThrow(usuarioRepo, id, () -> new NotFoundException("Usuario no encontrado"));
    }

    private Ciudad findCiudadOrThrow(Integer id) {
        return findOrThrow(ciudadRepo, id, () -> new NotFoundException("Ciudad no encontrada"));
    }

    private Departamento findDepartamentoOrThrow(Integer id) {
        return findOrThrow(departamentoRepo, id, () -> new NotFoundException("Departamento no encontrado"));
    }

    private Rol findRolOrThrow(Integer id) {
        return findOrThrow(rolRepo, id, () -> new NotFoundException("Rol no encontrado"));
    }

    private <T> T valueOrFind(Integer id, T currentValue, Finder<T> finder) {
        return Optional.ofNullable(id).map(finder::find).orElse(currentValue);
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
