package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.dto.PagingDTOs.PageResponse;
import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.pagination.PageMapper;
import com.ep18.couriersync.backend.common.pagination.PageRequestUtil;
import com.ep18.couriersync.backend.customers.domain.Rol;
import com.ep18.couriersync.backend.customers.dto.RolDTOs.CreateRolInput;
import com.ep18.couriersync.backend.customers.dto.RolDTOs.RolView;
import com.ep18.couriersync.backend.customers.dto.RolDTOs.UpdateRolInput;
import com.ep18.couriersync.backend.customers.repository.RolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ep18.couriersync.backend.common.service.ServiceOperations.deleteIfPresent;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.findOrThrow;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.rejectDuplicatedChange;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.rejectWhen;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.setIfPresent;

@Service
@RequiredArgsConstructor
public class RolService {

    private final RolRepository rolRepo;

    @Transactional
    public RolView create(CreateRolInput in) {
        rejectWhen(
                rolRepo.existsByNombreRolIgnoreCase(in.nombreRol()),
                () -> new ConflictException("Ya existe un rol con ese nombre"));

        Rol rol = new Rol();
        rol.setNombreRol(in.nombreRol());
        return toView(rolRepo.save(rol));
    }

    @Transactional
    public RolView update(UpdateRolInput in) {
        Rol rol = findRolOrThrow(in.idRol());

        rejectDuplicatedChange(
                in.nombreRol(),
                rol.getNombreRol(),
                String::equalsIgnoreCase,
                rolRepo::existsByNombreRolIgnoreCase,
                () -> new ConflictException("Ya existe un rol con ese nombre"));
        setIfPresent(in.nombreRol(), rol::setNombreRol);

        return toView(rolRepo.save(rol));
    }

    @Transactional(readOnly = true)
    public RolView findById(Integer id) {
        return toView(findRolOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<RolView> list(Integer page, Integer size) {
        Page<Rol> roles = rolRepo.findAll(PageRequestUtil.of(page, size, Sort.by("nombreRol").ascending()));
        return PageMapper.map(roles, this::toView);
    }

    @Transactional
    public boolean delete(Integer id) {
        return deleteIfPresent(
                rolRepo,
                id,
                () -> new ConflictException("No se puede eliminar: existen usuarios asociados a este rol"));
    }

    private Rol findRolOrThrow(Integer id) {
        return findOrThrow(rolRepo, id, () -> new NotFoundException("Rol no encontrado"));
    }

    private RolView toView(Rol rol) {
        return new RolView(rol.getIdRol(), rol.getNombreRol());
    }
}
