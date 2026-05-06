package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.dto.PagingDTOs.PageResponse;
import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.pagination.PageMapper;
import com.ep18.couriersync.backend.common.pagination.PageRequestUtil;
import com.ep18.couriersync.backend.customers.domain.DetalleDomicilio;
import com.ep18.couriersync.backend.customers.domain.Domicilio;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.CreateDomicilioInput;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.DetalleLineaInput;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.DetalleLineaUpdateInput;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.DetalleLineaView;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.DomicilioView;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.UpdateDomicilioInput;
import com.ep18.couriersync.backend.customers.repository.DetalleDomicilioRepository;
import com.ep18.couriersync.backend.customers.repository.DomicilioRepository;
import com.ep18.couriersync.backend.customers.repository.ProductoRepository;
import com.ep18.couriersync.backend.customers.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ep18.couriersync.backend.common.service.ServiceOperations.findOrThrow;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.nvl;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.rejectWhen;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.setIfPresent;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.valueOrDefault;

@Service
@RequiredArgsConstructor
public class DomicilioService {

    private static final String FECHA_PEDIDO = "fechaPedido";
    private static final String STRING_DOMICILIO_NO_ENCONTRADO = "Domicilio no encontrado";
    private static final String ESTADO_CREADO = "CREADO";
    private static final Set<String> ESTADOS_CERRADOS = Set.of("ENTREGADO", "CANCELADO");

    private final DomicilioRepository domicilioRepo;
    private final DetalleDomicilioRepository detalleRepo;
    private final UsuarioRepository usuarioRepo;
    private final ProductoRepository productoRepo;

    @Transactional
    public DomicilioView create(CreateDomicilioInput in) {
        Domicilio dom = new Domicilio();
        applyCreateFields(dom, in);
        addDetalles(dom, in.detalles());
        recalcTotales(dom);
        return saveAndReturnView(dom);
    }

    @Transactional
    public DomicilioView update(UpdateDomicilioInput in) {
        Domicilio dom = findOrThrow(domicilioRepo, in.idDomicilio(), () -> new NotFoundException(STRING_DOMICILIO_NO_ENCONTRADO));
        rejectWhen(
                isEstadoCerrado(dom.getEstado()),
                () -> new ConflictException("El domicilio no es editable en estado: " + dom.getEstado()));
        applyBasicChanges(dom, in);
        addDetalles(dom, in.addDetalles());
        updateDetalles(dom, in.updateDetalles());
        deleteDetalles(dom, in.deleteDetalleIds());
        recalcTotales(dom);
        return saveAndReturnView(dom);
    }

    @Transactional(readOnly = true)
    public DomicilioView findById(Integer id) {
        return toView(findOrThrow(domicilioRepo, id, () -> new NotFoundException(STRING_DOMICILIO_NO_ENCONTRADO)));
    }

    @Transactional(readOnly = true)
    public PageResponse<DomicilioView> listByUsuario(Integer idUsuario, Integer page, Integer size) {
        Page<Domicilio> p = domicilioRepo.findAllByUsuario_IdUsuario(
                idUsuario, PageRequestUtil.of(page, size, Sort.by(FECHA_PEDIDO).descending()));
        return PageMapper.map(p, this::toViewLight);
    }

    @Transactional(readOnly = true)
    public PageResponse<DomicilioView> listByEstado(String estado, Integer page, Integer size) {
        Page<Domicilio> p = domicilioRepo.findAllByEstadoIgnoreCase(
                estado, PageRequestUtil.of(page, size, Sort.by(FECHA_PEDIDO).descending()));
        return PageMapper.map(p, this::toViewLight);
    }

    @Transactional(readOnly = true)
    public PageResponse<DomicilioView> listByFecha(LocalDate start, LocalDate end, Integer page, Integer size) {
        Page<Domicilio> p = domicilioRepo.findAllByFechaPedidoBetween(
                start, end, PageRequestUtil.of(page, size, Sort.by(FECHA_PEDIDO).descending()));
        return PageMapper.map(p, this::toViewLight);
    }

    @Transactional
    public boolean delete(Integer id) {
        Domicilio dom = findOrThrow(domicilioRepo, id, () -> new NotFoundException(STRING_DOMICILIO_NO_ENCONTRADO));
        rejectWhen(
                isEstadoCerrado(dom.getEstado()),
                () -> new ConflictException("No se puede eliminar un domicilio en estado: " + dom.getEstado()));
        domicilioRepo.delete(dom);
        return true;
    }

    private void applyCreateFields(Domicilio dom, CreateDomicilioInput in) {
        dom.setUsuario(findOrThrow(usuarioRepo, in.idUsuario(), () -> new NotFoundException("Usuario no encontrado")));
        dom.setCedulaRecibe(in.cedulaRecibe());
        dom.setFechaPedido(valueOrDefault(in.fechaPedido(), LocalDate.now()));
        dom.setFechaEntrega(in.fechaEntrega());
        dom.setEstado(valueOrDefault(in.estado(), ESTADO_CREADO));
        dom.setValorDomicilio(nvl(in.valorDomicilio()));
    }

    private void applyBasicChanges(Domicilio dom, UpdateDomicilioInput in) {
        setIfPresent(in.cedulaRecibe(), dom::setCedulaRecibe);
        setIfPresent(in.fechaPedido(), dom::setFechaPedido);
        setIfPresent(in.fechaEntrega(), dom::setFechaEntrega);
        setIfPresent(in.estado(), dom::setEstado);
        setIfPresent(in.valorDomicilio(), valor -> dom.setValorDomicilio(nvl(valor)));
    }

    private void addDetalles(Domicilio dom, List<DetalleLineaInput> detalles) {
        valueOrDefault(detalles, List.<DetalleLineaInput>of()).stream()
                .map(this::createDetalle)
                .forEach(dom::addDetalle);
    }

    private DetalleDomicilio createDetalle(DetalleLineaInput in) {
        DetalleDomicilio detalle = new DetalleDomicilio();
        detalle.setProducto(findOrThrow(
                productoRepo,
                in.idProducto(),
                () -> new NotFoundException("Producto no encontrado: " + in.idProducto())));
        detalle.setCantidad(in.cantidad());
        detalle.setPrecioNeto(in.precioNeto());
        return detalle;
    }

    private void updateDetalles(Domicilio dom, List<DetalleLineaUpdateInput> detalles) {
        Map<Integer, DetalleDomicilio> detallesById = indexDetallesById(dom);
        valueOrDefault(detalles, List.<DetalleLineaUpdateInput>of()).stream()
                .forEach(detalle -> updateDetalle(detallesById, detalle));
    }

    private Map<Integer, DetalleDomicilio> indexDetallesById(Domicilio dom) {
        return dom.getDetalles().stream()
                .filter(detalle -> detalle.getIdDetalle() != null)
                .collect(Collectors.toMap(DetalleDomicilio::getIdDetalle, Function.identity()));
    }

    private void updateDetalle(Map<Integer, DetalleDomicilio> detallesById, DetalleLineaUpdateInput in) {
        DetalleDomicilio detalle = java.util.Optional.ofNullable(detallesById.get(in.idDetalle()))
                .orElseThrow(() -> new NotFoundException("Detalle no encontrado: " + in.idDetalle()));
        setIfPresent(in.idProducto(), idProducto -> detalle.setProducto(findOrThrow(
                productoRepo,
                idProducto,
                () -> new NotFoundException("Producto no encontrado: " + idProducto))));
        setIfPresent(in.cantidad(), detalle::setCantidad);
        setIfPresent(in.precioNeto(), detalle::setPrecioNeto);
    }

    private void deleteDetalles(Domicilio dom, List<Integer> deleteDetalleIds) {
        List<Integer> ids = valueOrDefault(deleteDetalleIds, List.<Integer>of()).stream().toList();
        java.util.Optional.of(ids)
                .filter(presentIds -> !presentIds.isEmpty())
                .ifPresent(presentIds -> {
                    dom.getDetalles().removeIf(detalle -> presentIds.contains(detalle.getIdDetalle()));
                    detalleRepo.deleteAllByDomicilio_IdDomicilioAndIdDetalleIn(dom.getIdDomicilio(), presentIds);
                });
    }

    private DomicilioView saveAndReturnView(Domicilio dom) {
        Domicilio saved = domicilioRepo.save(dom);
        return toView(findOrThrow(
                domicilioRepo,
                saved.getIdDomicilio(),
                () -> new NotFoundException(STRING_DOMICILIO_NO_ENCONTRADO)));
    }

    private void recalcTotales(Domicilio dom) {
        double valorPedido = dom.getDetalles().stream()
                .mapToDouble(d -> nvl(d.getPrecioNeto()) * java.util.Objects.requireNonNullElse(d.getCantidad(), 0))
                .sum();
        dom.setValorPedido(valorPedido);
        dom.setValorTotal(valorPedido + nvl(dom.getValorDomicilio()));
    }

    private boolean isEstadoCerrado(String estado) {
        return java.util.Optional.ofNullable(estado)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(ESTADOS_CERRADOS::contains)
                .isPresent();
    }

    private DomicilioView toView(Domicilio d) {
        List<DetalleLineaView> dets = d.getDetalles().stream()
                .map(this::toLineaView)
                .toList();
        return new DomicilioView(
                d.getIdDomicilio(),
                d.getUsuario().getIdUsuario(),
                d.getUsuario().getNombre(),
                d.getCedulaRecibe(),
                d.getFechaPedido(),
                d.getFechaEntrega(),
                d.getValorPedido(),
                d.getValorDomicilio(),
                d.getValorTotal(),
                d.getEstado(),
                dets
        );
    }

    private DomicilioView toViewLight(Domicilio d) {
        return new DomicilioView(
                d.getIdDomicilio(),
                d.getUsuario().getIdUsuario(),
                d.getUsuario().getNombre(),
                d.getCedulaRecibe(),
                d.getFechaPedido(),
                d.getFechaEntrega(),
                d.getValorPedido(),
                d.getValorDomicilio(),
                d.getValorTotal(),
                d.getEstado(),
                List.of()
        );
    }

    private DetalleLineaView toLineaView(DetalleDomicilio x) {
        return new DetalleLineaView(
                x.getIdDetalle(),
                x.getProducto().getIdProducto(),
                x.getProducto().getNombreProducto(),
                x.getCantidad(),
                x.getPrecioNeto()
        );
    }
}
