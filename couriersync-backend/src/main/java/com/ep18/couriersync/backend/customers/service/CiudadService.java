package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.dto.PagingDTOs.PageResponse;
import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.pagination.PageMapper;
import com.ep18.couriersync.backend.common.pagination.PageRequestUtil;
import com.ep18.couriersync.backend.customers.domain.Ciudad;
import com.ep18.couriersync.backend.customers.domain.Departamento;
import com.ep18.couriersync.backend.customers.dto.CiudadDTOs.CreateCiudadInput;
import com.ep18.couriersync.backend.customers.dto.CiudadDTOs.CiudadView;
import com.ep18.couriersync.backend.customers.dto.CiudadDTOs.UpdateCiudadInput;
import com.ep18.couriersync.backend.customers.repository.CiudadRepository;
import com.ep18.couriersync.backend.customers.repository.DepartamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.ep18.couriersync.backend.common.service.ServiceOperations.deleteIfPresent;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.findOrThrow;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.rejectWhen;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.valueOrDefault;

@Service
@RequiredArgsConstructor
public class CiudadService {

    private static final String SORT_BY_NOMBRE = "nombreCiudad";

    private final CiudadRepository ciudadRepo;
    private final DepartamentoRepository departamentoRepo;

    @Transactional
    public CiudadView create(CreateCiudadInput in) {
        Departamento departamento = findDepartamentoOrThrow(in.idDepartamento());
        rejectDuplicateName(
                in.nombreCiudad(),
                in.idDepartamento(),
                "Ya existe una ciudad con ese nombre en el departamento");

        Ciudad ciudad = new Ciudad();
        ciudad.setNombreCiudad(in.nombreCiudad());
        ciudad.setDepartamento(departamento);

        return toView(ciudadRepo.save(ciudad));
    }

    @Transactional
    public CiudadView update(UpdateCiudadInput in) {
        Ciudad ciudad = findCiudadOrThrow(in.idCiudad());

        Optional.ofNullable(in.nombreCiudad())
                .filter(nombre -> !nombre.equalsIgnoreCase(ciudad.getNombreCiudad()))
                .ifPresent(nombre -> renameCiudad(ciudad, nombre));
        Optional.ofNullable(in.idDepartamento())
                .filter(idDepartamento -> !idDepartamento.equals(ciudad.getDepartamento().getIdDepartamento()))
                .map(this::findDepartamentoOrThrow)
                .ifPresent(departamento -> moveCiudad(ciudad, departamento));

        return toView(ciudadRepo.save(ciudad));
    }

    @Transactional(readOnly = true)
    public CiudadView findById(Integer id) {
        return toView(findCiudadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CiudadView> listByDepartamento(Integer idDepartamento, Integer page, Integer size) {
        Page<Ciudad> ciudades = ciudadRepo.findAllByDepartamento_IdDepartamento(
                idDepartamento, PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(ciudades, this::toView);
    }

    @Transactional(readOnly = true)
    public PageResponse<CiudadView> search(String q, Integer page, Integer size) {
        Page<Ciudad> ciudades = ciudadRepo.findByNombreCiudadContainingIgnoreCase(
                valueOrDefault(q, ""), PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(ciudades, this::toView);
    }

    @Transactional
    public boolean delete(Integer id) {
        return deleteIfPresent(
                ciudadRepo,
                id,
                () -> new ConflictException("No se puede eliminar: existen registros relacionados"));
    }

    private void renameCiudad(Ciudad ciudad, String nombreCiudad) {
        rejectDuplicateName(
                nombreCiudad,
                ciudad.getDepartamento().getIdDepartamento(),
                "Ya existe ciudad con ese nombre en el departamento");
        ciudad.setNombreCiudad(nombreCiudad);
    }

    private void moveCiudad(Ciudad ciudad, Departamento departamento) {
        rejectDuplicateName(
                ciudad.getNombreCiudad(),
                departamento.getIdDepartamento(),
                "Ya existe ciudad con ese nombre en el nuevo departamento");
        ciudad.setDepartamento(departamento);
    }

    private void rejectDuplicateName(String nombreCiudad, Integer idDepartamento, String message) {
        rejectWhen(
                ciudadRepo.existsByNombreCiudadIgnoreCaseAndDepartamento_IdDepartamento(nombreCiudad, idDepartamento),
                () -> new ConflictException(message));
    }

    private Ciudad findCiudadOrThrow(Integer id) {
        return findOrThrow(ciudadRepo, id, () -> new NotFoundException("Ciudad no encontrada"));
    }

    private Departamento findDepartamentoOrThrow(Integer id) {
        return findOrThrow(departamentoRepo, id, () -> new NotFoundException("Departamento no encontrado"));
    }

    private CiudadView toView(Ciudad ciudad) {
        return new CiudadView(
                ciudad.getIdCiudad(),
                ciudad.getNombreCiudad(),
                ciudad.getDepartamento().getIdDepartamento(),
                ciudad.getDepartamento().getNombreDepartamento()
        );
    }
}
