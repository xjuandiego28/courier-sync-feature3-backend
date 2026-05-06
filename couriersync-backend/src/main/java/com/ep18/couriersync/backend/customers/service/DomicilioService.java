package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.dto.PagingDTOs.PageResponse;
import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.pagination.PageMapper;
import com.ep18.couriersync.backend.common.pagination.PageRequestUtil;
import com.ep18.couriersync.backend.customers.domain.DetalleDomicilio;
import com.ep18.couriersync.backend.customers.domain.Domicilio;
import com.ep18.couriersync.backend.customers.domain.Producto;
import com.ep18.couriersync.backend.customers.domain.Usuario;
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
import java.util.stream.Stream;

import static com.ep18.couriersync.backend.common.service.ServiceOperations.findOrThrow;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.nvl;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.rejectWhen;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.setIfPresent;
import static com.ep18.couriersync.backend.common.service.ServiceOperations.valueOrDefault;

@Service
@RequiredArgsConstructor
public class DomicilioService {

    private static final String FECHA_PEDIDO = "fechaPedido";
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
        Domicilio dom = findDomicilioOrThrow(in.idDomicilio());
        assertEditable(dom);
        applyBasicChanges(dom, in);
        addDetalles(dom, in.addDetalles());
        updateDetalles(dom, in.updateDetalles());
        deleteDetalles(dom, in.deleteDetalleIds());
        recalcTotales(dom);
        return saveAndReturnView(dom);
    }

    @Transactional(readOnly = true)
    public DomicilioView findById(Integer id) {
        return toView(fetchAgregado(id));
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
        Domicilio dom = findDomicilioOrThrow(id);
        assertDeletable(dom);
        domicilioRepo.delete(dom);
        return true;
    }

    private Domicilio fetchAgregado(Integer id) {
        return findDomicilioOrThrow(id);
    }

    private void applyCreateFields(Domicilio dom, CreateDomicilioInput in) {
        dom.setUsuario(findUsuarioOrThrow(in.idUsuario()));
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
        safeStream(detalles)
                .map(this::createDetalle)
                .forEach(dom::addDetalle);
    }

    private DetalleDomicilio createDetalle(DetalleLineaInput in) {
        DetalleDomicilio detalle = new DetalleDomicilio();
        detalle.setProducto(findProductoOrThrow(in.idProducto()));
        detalle.setCantidad(in.cantidad());
        detalle.setPrecioNeto(in.precioNeto());
        return detalle;
    }

    private void updateDetalles(Domicilio dom, List<DetalleLineaUpdateInput> detalles) {
        Map<Integer, DetalleDomicilio> detallesById = indexDetallesById(dom);
        safeStream(detalles)
                .forEach(detalle -> updateDetalle(detallesById, detalle));
    }

    private Map<Integer, DetalleDomicilio> indexDetallesById(Domicilio dom) {
        return dom.getDetalles().stream()
                .filter(detalle -> detalle.getIdDetalle() != null)
                .collect(Collectors.toMap(DetalleDomicilio::getIdDetalle, Function.identity()));
    }

    private void updateDetalle(Map<Integer, DetalleDomicilio> detallesById, DetalleLineaUpdateInput in) {
        DetalleDomicilio detalle = findDetalleOrThrow(detallesById, in.idDetalle());
        setIfPresent(in.idProducto(), idProducto -> detalle.setProducto(findProductoOrThrow(idProducto)));
        setIfPresent(in.cantidad(), detalle::setCantidad);
        setIfPresent(in.precioNeto(), detalle::setPrecioNeto);
    }

    private void deleteDetalles(Domicilio dom, List<Integer> deleteDetalleIds) {
        List<Integer> ids = safeStream(deleteDetalleIds).toList();
        java.util.Optional.of(ids)
                .filter(presentIds -> !presentIds.isEmpty())
                .ifPresent(presentIds -> {
                    dom.getDetalles().removeIf(detalle -> presentIds.contains(detalle.getIdDetalle()));
                    detalleRepo.deleteAllByDomicilio_IdDomicilioAndIdDetalleIn(dom.getIdDomicilio(), presentIds);
                });
    }

    private DomicilioView saveAndReturnView(Domicilio dom) {
        Domicilio saved = domicilioRepo.save(dom);
        return toView(fetchAgregado(saved.getIdDomicilio()));
    }

    private Usuario findUsuarioOrThrow(Integer id) {
        return findOrThrow(usuarioRepo, id, () -> new NotFoundException("Usuario no encontrado"));
    }

    private Producto findProductoOrThrow(Integer id) {
        return findOrThrow(productoRepo, id, () -> new NotFoundException("Producto no encontrado: " + id));
    }

    private Domicilio findDomicilioOrThrow(Integer id) {
        return findOrThrow(domicilioRepo, id, () -> new NotFoundException("Domicilio no encontrado"));
    }

    private DetalleDomicilio findDetalleOrThrow(Map<Integer, DetalleDomicilio> detallesById, Integer id) {
        return java.util.Optional.ofNullable(detallesById.get(id))
                .orElseThrow(() -> new NotFoundException("Detalle no encontrado: " + id));
    }

    private void assertEditable(Domicilio dom) {
        rejectWhen(
                isEstadoCerrado(dom.getEstado()),
                () -> new ConflictException("El domicilio no es editable en estado: " + dom.getEstado()));
    }

    private void assertDeletable(Domicilio dom) {
        rejectWhen(
                isEstadoCerrado(dom.getEstado()),
                () -> new ConflictException("No se puede eliminar un domicilio en estado: " + dom.getEstado()));
    }

    private void recalcTotales(Domicilio dom) {
        double valorPedido = safeStream(dom.getDetalles())
                .mapToDouble(d -> nvl(d.getPrecioNeto()) * nvl(d.getCantidad()))
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

    private <T> Stream<T> safeStream(List<T> values) {
        return valueOrDefault(values, List.<T>of()).stream();
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
