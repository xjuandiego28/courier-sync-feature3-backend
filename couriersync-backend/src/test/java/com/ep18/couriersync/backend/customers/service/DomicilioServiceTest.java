package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.customers.domain.DetalleDomicilio;
import com.ep18.couriersync.backend.customers.domain.Domicilio;
import com.ep18.couriersync.backend.customers.domain.Producto;
import com.ep18.couriersync.backend.customers.domain.Usuario;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.CreateDomicilioInput;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.DetalleLineaInput;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.DetalleLineaUpdateInput;
import com.ep18.couriersync.backend.customers.dto.DomicilioDTOs.UpdateDomicilioInput;
import com.ep18.couriersync.backend.customers.repository.DetalleDomicilioRepository;
import com.ep18.couriersync.backend.customers.repository.DomicilioRepository;
import com.ep18.couriersync.backend.customers.repository.ProductoRepository;
import com.ep18.couriersync.backend.customers.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomicilioServiceTest {

    @Mock
    private DomicilioRepository domicilioRepo;

    @Mock
    private DetalleDomicilioRepository detalleRepo;

    @Mock
    private UsuarioRepository usuarioRepo;

    @Mock
    private ProductoRepository productoRepo;

    @InjectMocks
    private DomicilioService service;

    @Test
    void createAddsDetailsAndRecalculatesTotals() {
        AtomicReference<Domicilio> savedRef = saveReturnsEntityWithId(99);
        when(usuarioRepo.findById(1)).thenReturn(Optional.of(usuario(1, "Ana")));
        when(productoRepo.findById(10)).thenReturn(Optional.of(producto(10, "Pan")));
        when(productoRepo.findById(11)).thenReturn(Optional.of(producto(11, "Cafe")));
        when(domicilioRepo.findById(99)).thenAnswer(invocation -> Optional.of(savedRef.get()));

        var view = service.create(new CreateDomicilioInput(
                1,
                "100",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 2),
                null,
                4_000.0,
                null,
                null,
                List.of(new DetalleLineaInput(10, 2, 3_000.0), new DetalleLineaInput(11, 1, 5_000.0))
        ));

        assertThat(view.idDomicilio()).isEqualTo(99);
        assertThat(view.estado()).isEqualTo("CREADO");
        assertThat(view.valorPedido()).isEqualTo(11_000.0);
        assertThat(view.valorTotal()).isEqualTo(15_000.0);
        assertThat(view.detalles()).hasSize(2);
        assertThat(savedRef.get().getDetalles()).allSatisfy(detalle ->
                assertThat(detalle.getDomicilio()).isSameAs(savedRef.get()));
    }

    @Test
    void updateAddsUpdatesDeletesDetailsAndRecalculatesTotals() {
        Domicilio existing = domicilio(7, "CREADO");
        existing.addDetalle(detalle(20, producto(10, "Pan"), 1, 3_000.0));
        AtomicReference<Domicilio> savedRef = saveReturnsEntityWithId(7);
        when(domicilioRepo.findById(7)).thenReturn(Optional.of(existing));
        when(productoRepo.findById(11)).thenReturn(Optional.of(producto(11, "Cafe")));
        when(domicilioRepo.findById(7)).thenAnswer(invocation -> Optional.of(savedRef.get() == null ? existing : savedRef.get()));

        var view = service.update(new UpdateDomicilioInput(
                7,
                null,
                "200",
                null,
                null,
                null,
                2_000.0,
                null,
                "EN_RUTA",
                List.of(new DetalleLineaInput(11, 2, 4_000.0)),
                List.of(new DetalleLineaUpdateInput(20, null, 3, 2_500.0)),
                List.of()
        ));

        assertThat(view.cedulaRecibe()).isEqualTo("200");
        assertThat(view.estado()).isEqualTo("EN_RUTA");
        assertThat(view.valorPedido()).isEqualTo(15_500.0);
        assertThat(view.valorTotal()).isEqualTo(17_500.0);
        assertThat(view.detalles()).hasSize(2);
    }

    @Test
    void updateDeletesRequestedDetails() {
        Domicilio existing = domicilio(7, "CREADO");
        existing.addDetalle(detalle(20, producto(10, "Pan"), 1, 3_000.0));
        when(domicilioRepo.findById(7)).thenReturn(Optional.of(existing));
        when(domicilioRepo.save(existing)).thenReturn(existing);

        var view = service.update(new UpdateDomicilioInput(
                7, null, null, null, null, null, null, null, null, null, null, List.of(20)));

        assertThat(view.detalles()).isEmpty();
        assertThat(view.valorPedido()).isZero();
        verify(detalleRepo).deleteAllByDomicilio_IdDomicilioAndIdDetalleIn(7, List.of(20));
    }

    @Test
    void updateRejectsClosedOrdersAndUnknownDetails() {
        when(domicilioRepo.findById(8)).thenReturn(Optional.of(domicilio(8, "entregado")));

        UpdateDomicilioInput closedOrderInput = new UpdateDomicilioInput(
                8, null, "x", null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(closedOrderInput))
                .isInstanceOf(ConflictException.class);
        verify(domicilioRepo, never()).save(any());

        when(domicilioRepo.findById(9)).thenReturn(Optional.of(domicilio(9, "CREADO")));

        UpdateDomicilioInput unknownDetailInput = new UpdateDomicilioInput(
                9,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new DetalleLineaUpdateInput(404, null, 1, null)),
                null
        );
        assertThatThrownBy(() -> service.update(unknownDetailInput)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findListAndDeleteDelegateToRepository() {
        Domicilio domicilio = domicilio(7, "CREADO");
        domicilio.addDetalle(detalle(20, producto(10, "Pan"), 1, 3_000.0));
        when(domicilioRepo.findById(7)).thenReturn(Optional.of(domicilio));
        assertThat(service.findById(7).detalles()).hasSize(1);

        when(domicilioRepo.findAllByEstadoIgnoreCase(any(), any())).thenReturn(new PageImpl<>(List.of(domicilio)));
        assertThat(service.listByEstado("creado", 0, 5).content()).hasSize(1);

        when(domicilioRepo.findAllByUsuario_IdUsuario(any(), any())).thenReturn(new PageImpl<>(List.of(domicilio)));
        assertThat(service.listByUsuario(1, 0, 5).content()).hasSize(1);

        when(domicilioRepo.findAllByFechaPedidoBetween(any(), any(), any())).thenReturn(new PageImpl<>(List.of(domicilio)));
        assertThat(service.listByFecha(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), 0, 5).content())
                .hasSize(1);

        assertThat(service.delete(7)).isTrue();
        verify(domicilioRepo).delete(domicilio);
    }

    @Test
    void createRejectsMissingUserOrProduct() {
        when(usuarioRepo.findById(404)).thenReturn(Optional.empty());

        CreateDomicilioInput missingUserInput = new CreateDomicilioInput(
                404, "100", null, LocalDate.of(2026, 5, 2), null, null, null, null, List.of());
        assertThatThrownBy(() -> service.create(missingUserInput))
                .isInstanceOf(NotFoundException.class);

        when(usuarioRepo.findById(1)).thenReturn(Optional.of(usuario(1, "Ana")));
        when(productoRepo.findById(404)).thenReturn(Optional.empty());

        CreateDomicilioInput missingProductInput = new CreateDomicilioInput(
                1,
                "100",
                null,
                LocalDate.of(2026, 5, 2),
                null,
                null,
                null,
                null,
                List.of(new DetalleLineaInput(404, 1, 3_000.0)));
        assertThatThrownBy(() -> service.create(missingProductInput))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRejectsClosedOrders() {
        when(domicilioRepo.findById(7)).thenReturn(Optional.of(domicilio(7, "CANCELADO")));

        assertThatThrownBy(() -> service.delete(7)).isInstanceOf(ConflictException.class);
        verify(domicilioRepo, never()).delete(any());
    }

    private AtomicReference<Domicilio> saveReturnsEntityWithId(Integer id) {
        AtomicReference<Domicilio> savedRef = new AtomicReference<>();
        when(domicilioRepo.save(any(Domicilio.class))).thenAnswer(invocation -> {
            Domicilio domicilio = invocation.getArgument(0);
            domicilio.setIdDomicilio(id);
            savedRef.set(domicilio);
            return domicilio;
        });
        return savedRef;
    }

    private static Domicilio domicilio(Integer id, String estado) {
        Domicilio domicilio = new Domicilio();
        domicilio.setIdDomicilio(id);
        domicilio.setUsuario(usuario(1, "Ana"));
        domicilio.setCedulaRecibe("100");
        domicilio.setFechaPedido(LocalDate.of(2026, 5, 1));
        domicilio.setFechaEntrega(LocalDate.of(2026, 5, 2));
        domicilio.setValorPedido(3_000.0);
        domicilio.setValorDomicilio(1_000.0);
        domicilio.setValorTotal(4_000.0);
        domicilio.setEstado(estado);
        return domicilio;
    }

    private static DetalleDomicilio detalle(Integer id, Producto producto, Integer cantidad, Double precio) {
        DetalleDomicilio detalle = new DetalleDomicilio();
        detalle.setIdDetalle(id);
        detalle.setProducto(producto);
        detalle.setCantidad(cantidad);
        detalle.setPrecioNeto(precio);
        return detalle;
    }

    private static Producto producto(Integer id, String nombre) {
        Producto producto = new Producto();
        producto.setIdProducto(id);
        producto.setNombreProducto(nombre);
        return producto;
    }

    private static Usuario usuario(Integer id, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setNombre(nombre);
        return usuario;
    }
}
