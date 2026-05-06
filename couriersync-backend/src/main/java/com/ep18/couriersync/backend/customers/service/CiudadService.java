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
    private static final String STRING_CIUDAD_NO_ENCONTRADA = "Ciudad no encontrada";

    private final CiudadRepository ciudadRepo;
    private final DepartamentoRepository departamentoRepo;

    @Transactional
    public CiudadView create(CreateCiudadInput in) {
        Departamento departamento = findOrThrow(
                departamentoRepo,
                in.idDepartamento(),
                () -> new NotFoundException("Departamento no encontrado"));
        rejectDuplicateName(
                in.nombreCiudad(),
                in.idDepartamento(),
                "Ya existe una ciudad con ese nombre en el departamento");

        Ciudad ciudad = new Ciudad();
        ciudad.setNombreCiudad(in.nombreCiudad());
        ciudad.setDepartamento(departamento);

        Ciudad saved = ciudadRepo.save(ciudad);
        return new CiudadView(
                saved.getIdCiudad(),
                saved.getNombreCiudad(),
                saved.getDepartamento().getIdDepartamento(),
                saved.getDepartamento().getNombreDepartamento()
        );
    }

    @Transactional
    public CiudadView update(UpdateCiudadInput in) {
        Ciudad ciudad = findOrThrow(ciudadRepo, in.idCiudad(), () -> new NotFoundException(STRING_CIUDAD_NO_ENCONTRADA));

        Optional.ofNullable(in.nombreCiudad())
                .filter(nombre -> !nombre.equalsIgnoreCase(ciudad.getNombreCiudad()))
                .ifPresent(nombre -> {
                    rejectDuplicateName(
                            nombre,
                            ciudad.getDepartamento().getIdDepartamento(),
                            "Ya existe ciudad con ese nombre en el departamento");
                    ciudad.setNombreCiudad(nombre);
                });
        Optional.ofNullable(in.idDepartamento())
                .filter(idDepartamento -> !idDepartamento.equals(ciudad.getDepartamento().getIdDepartamento()))
                .map(idDepartamento -> findOrThrow(
                        departamentoRepo,
                        idDepartamento,
                        () -> new NotFoundException("Departamento no encontrado")))
                .ifPresent(departamento -> {
                    rejectDuplicateName(
                            ciudad.getNombreCiudad(),
                            departamento.getIdDepartamento(),
                            "Ya existe ciudad con ese nombre en el nuevo departamento");
                    ciudad.setDepartamento(departamento);
                });

        Ciudad saved = ciudadRepo.save(ciudad);
        return new CiudadView(
                saved.getIdCiudad(),
                saved.getNombreCiudad(),
                saved.getDepartamento().getIdDepartamento(),
                saved.getDepartamento().getNombreDepartamento()
        );
    }

    @Transactional(readOnly = true)
    public CiudadView findById(Integer id) {
        Ciudad ciudad = findOrThrow(ciudadRepo, id, () -> new NotFoundException(STRING_CIUDAD_NO_ENCONTRADA));
        return new CiudadView(
                ciudad.getIdCiudad(),
                ciudad.getNombreCiudad(),
                ciudad.getDepartamento().getIdDepartamento(),
                ciudad.getDepartamento().getNombreDepartamento()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<CiudadView> listByDepartamento(Integer idDepartamento, Integer page, Integer size) {
        Page<Ciudad> ciudades = ciudadRepo.findAllByDepartamento_IdDepartamento(
                idDepartamento, PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(ciudades, ciudad -> new CiudadView(
                ciudad.getIdCiudad(),
                ciudad.getNombreCiudad(),
                ciudad.getDepartamento().getIdDepartamento(),
                ciudad.getDepartamento().getNombreDepartamento()
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<CiudadView> search(String q, Integer page, Integer size) {
        Page<Ciudad> ciudades = ciudadRepo.findByNombreCiudadContainingIgnoreCase(
                valueOrDefault(q, ""), PageRequestUtil.of(page, size, Sort.by(SORT_BY_NOMBRE).ascending()));
        return PageMapper.map(ciudades, ciudad -> new CiudadView(
                ciudad.getIdCiudad(),
                ciudad.getNombreCiudad(),
                ciudad.getDepartamento().getIdDepartamento(),
                ciudad.getDepartamento().getNombreDepartamento()
        ));
    }

    @Transactional
    public boolean delete(Integer id) {
        return deleteIfPresent(
                ciudadRepo,
                id,
                () -> new ConflictException("No se puede eliminar: existen registros relacionados"));
    }

    private void rejectDuplicateName(String nombreCiudad, Integer idDepartamento, String message) {
        rejectWhen(
                ciudadRepo.existsByNombreCiudadIgnoreCaseAndDepartamento_IdDepartamento(nombreCiudad, idDepartamento),
                () -> new ConflictException(message));
    }
}
