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
        rejectWhen(
                usuarioRepo.existsByCorreoIgnoreCase(in.correo()),
                () -> new ConflictException("El correo ya esta registrado"));

        Ciudad ciudad = findOrThrow(ciudadRepo, in.idCiudad(), () -> new NotFoundException("Ciudad no encontrada"));
        Departamento departamento = findOrThrow(
                departamentoRepo,
                in.idDepartamento(),
                () -> new NotFoundException("Departamento no encontrado"));
        Rol rol = findOrThrow(rolRepo, in.idRol(), () -> new NotFoundException("Rol no encontrado"));
        UsuarioValidator.assertCiudadPerteneceADepartamento(ciudad, departamento.getIdDepartamento());

        Usuario usuario = new Usuario();
        usuario.setNombre(in.nombre());
        usuario.setCorreo(in.correo());
        usuario.setTelefono(in.telefono());
        usuario.setFechaRegistro(valueOrDefault(in.fechaRegistro(), LocalDate.now()));
        usuario.setDetalleDireccion(in.detalleDireccion());
        usuario.setCiudad(ciudad);
        usuario.setDepartamento(departamento);
        usuario.setRol(rol);

        Usuario saved = usuarioRepo.save(usuario);
        return new UsuarioView(
                saved.getIdUsuario(),
                saved.getNombre(),
                saved.getCorreo(),
                saved.getTelefono(),
                saved.getFechaRegistro(),
                saved.getDetalleDireccion(),
                saved.getCiudad().getIdCiudad(),
                saved.getCiudad().getNombreCiudad(),
                saved.getDepartamento().getIdDepartamento(),
                saved.getDepartamento().getNombreDepartamento(),
                saved.getRol().getIdRol(),
                saved.getRol().getNombreRol()
        );
    }

    @Transactional
    public UsuarioView update(UpdateUsuarioInput in) {
        Usuario usuario = findOrThrow(usuarioRepo, in.idUsuario(), () -> new NotFoundException("Usuario no encontrado"));

        rejectDuplicatedChange(
                in.correo(),
                usuario.getCorreo(),
                String::equalsIgnoreCase,
                usuarioRepo::existsByCorreoIgnoreCase,
                () -> new ConflictException("El correo ya esta registrado"));
        setIfPresent(in.nombre(), usuario::setNombre);
        setIfPresent(in.correo(), usuario::setCorreo);
        setIfPresent(in.telefono(), usuario::setTelefono);
        setIfPresent(in.fechaRegistro(), usuario::setFechaRegistro);
        setIfPresent(in.detalleDireccion(), usuario::setDetalleDireccion);

        if (Stream.of(in.idCiudad(), in.idDepartamento()).anyMatch(Objects::nonNull)) {
            Ciudad ciudad = Optional.ofNullable(in.idCiudad())
                    .map(id -> findOrThrow(ciudadRepo, id, () -> new NotFoundException("Ciudad no encontrada")))
                    .orElse(usuario.getCiudad());
            Departamento departamento = Optional.ofNullable(in.idDepartamento())
                    .map(id -> findOrThrow(
                            departamentoRepo,
                            id,
                            () -> new NotFoundException("Departamento no encontrado")))
                    .orElse(usuario.getDepartamento());

            UsuarioValidator.assertCiudadPerteneceADepartamento(ciudad, departamento.getIdDepartamento());
            usuario.setCiudad(ciudad);
            usuario.setDepartamento(departamento);
        }

        setIfPresent(
                in.idRol(),
                id -> usuario.setRol(findOrThrow(rolRepo, id, () -> new NotFoundException("Rol no encontrado"))));

        Usuario saved = usuarioRepo.save(usuario);
        return new UsuarioView(
                saved.getIdUsuario(),
                saved.getNombre(),
                saved.getCorreo(),
                saved.getTelefono(),
                saved.getFechaRegistro(),
                saved.getDetalleDireccion(),
                saved.getCiudad().getIdCiudad(),
                saved.getCiudad().getNombreCiudad(),
                saved.getDepartamento().getIdDepartamento(),
                saved.getDepartamento().getNombreDepartamento(),
                saved.getRol().getIdRol(),
                saved.getRol().getNombreRol()
        );
    }

    @Transactional(readOnly = true)
    public UsuarioView findById(Integer id) {
        Usuario usuario = findOrThrow(usuarioRepo, id, () -> new NotFoundException("Usuario no encontrado"));
        return new UsuarioView(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getTelefono(),
                usuario.getFechaRegistro(),
                usuario.getDetalleDireccion(),
                usuario.getCiudad().getIdCiudad(),
                usuario.getCiudad().getNombreCiudad(),
                usuario.getDepartamento().getIdDepartamento(),
                usuario.getDepartamento().getNombreDepartamento(),
                usuario.getRol().getIdRol(),
                usuario.getRol().getNombreRol()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioView> search(String q, Integer page, Integer size) {
        Page<Usuario> p = usuarioRepo.findByNombreContainingIgnoreCase(
                valueOrDefault(q, ""), PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(p, usuario -> new UsuarioView(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getTelefono(),
                usuario.getFechaRegistro(),
                usuario.getDetalleDireccion(),
                usuario.getCiudad().getIdCiudad(),
                usuario.getCiudad().getNombreCiudad(),
                usuario.getDepartamento().getIdDepartamento(),
                usuario.getDepartamento().getNombreDepartamento(),
                usuario.getRol().getIdRol(),
                usuario.getRol().getNombreRol()
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioView> listByCiudad(Integer idCiudad, Integer page, Integer size) {
        Page<Usuario> p = usuarioRepo.findAllByCiudad_IdCiudad(
                idCiudad, PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(p, usuario -> new UsuarioView(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getTelefono(),
                usuario.getFechaRegistro(),
                usuario.getDetalleDireccion(),
                usuario.getCiudad().getIdCiudad(),
                usuario.getCiudad().getNombreCiudad(),
                usuario.getDepartamento().getIdDepartamento(),
                usuario.getDepartamento().getNombreDepartamento(),
                usuario.getRol().getIdRol(),
                usuario.getRol().getNombreRol()
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioView> listByDepartamento(Integer idDepto, Integer page, Integer size) {
        Page<Usuario> p = usuarioRepo.findAllByDepartamento_IdDepartamento(
                idDepto, PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(p, usuario -> new UsuarioView(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getTelefono(),
                usuario.getFechaRegistro(),
                usuario.getDetalleDireccion(),
                usuario.getCiudad().getIdCiudad(),
                usuario.getCiudad().getNombreCiudad(),
                usuario.getDepartamento().getIdDepartamento(),
                usuario.getDepartamento().getNombreDepartamento(),
                usuario.getRol().getIdRol(),
                usuario.getRol().getNombreRol()
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<UsuarioView> listByRol(Integer idRol, Integer page, Integer size) {
        Page<Usuario> p = usuarioRepo.findAllByRol_IdRol(
                idRol, PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(p, usuario -> new UsuarioView(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getTelefono(),
                usuario.getFechaRegistro(),
                usuario.getDetalleDireccion(),
                usuario.getCiudad().getIdCiudad(),
                usuario.getCiudad().getNombreCiudad(),
                usuario.getDepartamento().getIdDepartamento(),
                usuario.getDepartamento().getNombreDepartamento(),
                usuario.getRol().getIdRol(),
                usuario.getRol().getNombreRol()
        ));
    }

    @Transactional
    public boolean delete(Integer id) {
        return deleteIfPresent(
                usuarioRepo,
                id,
                () -> new ConflictException("No se puede eliminar: existen registros relacionados"));
    }

}
