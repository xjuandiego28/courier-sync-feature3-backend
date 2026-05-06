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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DomicilioService {

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
                idUsuario, PageRequestUtil.of(page, size, Sort.by("fechaPedido").descending()));
        return PageMapper.map(p, this::toViewLight);
    }

    @Transactional(readOnly = true)
    public PageResponse<DomicilioView> listByEstado(String estado, Integer page, Integer size) {
        Page<Domicilio> p = domicilioRepo.findAllByEstadoIgnoreCase(
                estado, PageRequestUtil.of(page, size, Sort.by("fechaPedido").descending()));
        return PageMapper.map(p, this::toViewLight);
    }

    @Transactional(readOnly = true)
    public PageResponse<DomicilioView> listByFecha(LocalDate start, LocalDate end, Integer page, Integer size) {
        Page<Domicilio> p = domicilioRepo.findAllByFechaPedidoBetween(
                start, end, PageRequestUtil.of(page, size, Sort.by("fechaPedido").descending()));
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
        for (DetalleLineaInput detalle : emptyIfNull(detalles)) {
            dom.addDetalle(createDetalle(detalle));
        }
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
        for (DetalleLineaUpdateInput detalle : emptyIfNull(detalles)) {
            updateDetalle(detallesById, detalle);
        }
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
        List<Integer> ids = emptyIfNull(deleteDetalleIds);
        if (ids.isEmpty()) {
            return;
        }

        dom.getDetalles().removeIf(detalle -> ids.contains(detalle.getIdDetalle()));
        detalleRepo.deleteAllByDomicilio_IdDomicilioAndIdDetalleIn(dom.getIdDomicilio(), ids);
    }

    private DomicilioView saveAndReturnView(Domicilio dom) {
        Domicilio saved = domicilioRepo.save(dom);
        return toView(fetchAgregado(saved.getIdDomicilio()));
    }

    private Usuario findUsuarioOrThrow(Integer id) {
        return usuarioRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private Producto findProductoOrThrow(Integer id) {
        return productoRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + id));
    }

    private Domicilio findDomicilioOrThrow(Integer id) {
        return domicilioRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Domicilio no encontrado"));
    }

    private DetalleDomicilio findDetalleOrThrow(Map<Integer, DetalleDomicilio> detallesById, Integer id) {
        DetalleDomicilio detalle = detallesById.get(id);
        if (detalle == null) {
            throw new NotFoundException("Detalle no encontrado: " + id);
        }
        return detalle;
    }

    private void assertEditable(Domicilio dom) {
        if (isEstadoCerrado(dom.getEstado())) {
            throw new ConflictException("El domicilio no es editable en estado: " + dom.getEstado());
        }
    }

    private void assertDeletable(Domicilio dom) {
        if (isEstadoCerrado(dom.getEstado())) {
            throw new ConflictException("No se puede eliminar un domicilio en estado: " + dom.getEstado());
        }
    }

    private void recalcTotales(Domicilio dom) {
        double valorPedido = dom.getDetalles() == null ? 0.0 :
                dom.getDetalles().stream()
                        .mapToDouble(d -> nvl(d.getPrecioNeto()) * nvl(d.getCantidad()))
                        .sum();
        dom.setValorPedido(valorPedido);
        dom.setValorTotal(valorPedido + nvl(dom.getValorDomicilio()));
    }

    private boolean isEstadoCerrado(String estado) {
        return estado != null && ESTADOS_CERRADOS.contains(estado.trim().toUpperCase(Locale.ROOT));
    }

    private double nvl(Double d) {
        return d == null ? 0.0 : d;
    }

    private int nvl(Integer i) {
        return i == null ? 0 : i;
    }

    private <T> T valueOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
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
