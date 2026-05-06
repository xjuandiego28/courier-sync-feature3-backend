package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.dto.PagingDTOs.PageResponse;
import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.pagination.PageMapper;
import com.ep18.couriersync.backend.common.pagination.PageRequestUtil;
import com.ep18.couriersync.backend.customers.domain.Departamento;
import com.ep18.couriersync.backend.customers.dto.DepartamentoDTOs.CreateDepartamentoInput;
import com.ep18.couriersync.backend.customers.dto.DepartamentoDTOs.DepartamentoView;
import com.ep18.couriersync.backend.customers.dto.DepartamentoDTOs.UpdateDepartamentoInput;
import com.ep18.couriersync.backend.customers.repository.DepartamentoRepository;
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
public class DepartamentoService {

    private final DepartamentoRepository departamentoRepo;

    @Transactional
    public DepartamentoView create(CreateDepartamentoInput in) {
        rejectWhen(
                departamentoRepo.existsByNombreDepartamentoIgnoreCase(in.nombreDepartamento()),
                () -> new ConflictException("Ya existe un departamento con ese nombre"));

        Departamento departamento = new Departamento();
        departamento.setNombreDepartamento(in.nombreDepartamento());
        Departamento saved = departamentoRepo.save(departamento);
        return new DepartamentoView(saved.getIdDepartamento(), saved.getNombreDepartamento());
    }

    @Transactional
    public DepartamentoView update(UpdateDepartamentoInput in) {
        Departamento departamento = findOrThrow(
                departamentoRepo,
                in.idDepartamento(),
                () -> new NotFoundException("Departamento no encontrado"));

        rejectDuplicatedChange(
                in.nombreDepartamento(),
                departamento.getNombreDepartamento(),
                String::equalsIgnoreCase,
                departamentoRepo::existsByNombreDepartamentoIgnoreCase,
                () -> new ConflictException("Ya existe un departamento con ese nombre"));
        setIfPresent(in.nombreDepartamento(), departamento::setNombreDepartamento);

        Departamento saved = departamentoRepo.save(departamento);
        return new DepartamentoView(saved.getIdDepartamento(), saved.getNombreDepartamento());
    }

    @Transactional(readOnly = true)
    public DepartamentoView findById(Integer id) {
        Departamento departamento = findOrThrow(
                departamentoRepo,
                id,
                () -> new NotFoundException("Departamento no encontrado"));
        return new DepartamentoView(departamento.getIdDepartamento(), departamento.getNombreDepartamento());
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartamentoView> list(Integer page, Integer size) {
        Page<Departamento> departamentos = departamentoRepo.findAll(
                PageRequestUtil.of(page, size, Sort.by("nombreDepartamento").ascending()));
        return PageMapper.map(
                departamentos,
                departamento -> new DepartamentoView(
                        departamento.getIdDepartamento(),
                        departamento.getNombreDepartamento()));
    }

    @Transactional
    public boolean delete(Integer id) {
        return deleteIfPresent(
                departamentoRepo,
                id,
                () -> new ConflictException("No se puede eliminar: existen registros relacionados"));
    }

}
