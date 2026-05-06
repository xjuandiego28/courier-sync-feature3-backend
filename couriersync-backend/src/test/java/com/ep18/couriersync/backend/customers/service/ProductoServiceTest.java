package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.customers.domain.Producto;
import com.ep18.couriersync.backend.customers.dto.ProductoDTOs.CreateProductoInput;
import com.ep18.couriersync.backend.customers.dto.ProductoDTOs.UpdateProductoInput;
import com.ep18.couriersync.backend.customers.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepo;

    @InjectMocks
    private ProductoService service;

    @Test
    void createStoresProductWhenNameIsAvailable() {
        when(productoRepo.existsByNombreProductoIgnoreCase("Cafe")).thenReturn(false);
        when(productoRepo.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto producto = invocation.getArgument(0);
            producto.setIdProducto(7);
            return producto;
        });

        var view = service.create(new CreateProductoInput("Cafe", 12_000.0, 0.19, "Local"));

        assertThat(view.idProducto()).isEqualTo(7);
        assertThat(view.nombreProducto()).isEqualTo("Cafe");
        assertThat(view.precioUnitario()).isEqualTo(12_000.0);
        verify(productoRepo).save(any(Producto.class));
    }

    @Test
    void createRejectsDuplicatedName() {
        when(productoRepo.existsByNombreProductoIgnoreCase("Cafe")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateProductoInput("Cafe", 12_000.0, 0.19, "Local")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void updateChangesOnlyProvidedFieldsAndChecksNameCollision() {
        Producto existing = producto(4, "Cafe", 10_000.0, 0.19, "A");
        when(productoRepo.findById(4)).thenReturn(Optional.of(existing));
        when(productoRepo.existsByNombreProductoIgnoreCase("Te")).thenReturn(false);
        when(productoRepo.save(existing)).thenReturn(existing);

        var view = service.update(new UpdateProductoInput(4, "Te", null, 0.05, null));

        assertThat(view.nombreProducto()).isEqualTo("Te");
        assertThat(view.precioUnitario()).isEqualTo(10_000.0);
        assertThat(view.ivaProducto()).isEqualTo(0.05);
        assertThat(view.marca()).isEqualTo("A");
    }

    @Test
    void updateRejectsMissingAndDuplicatedProducts() {
        when(productoRepo.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdateProductoInput(404, "Te", null, null, null)))
                .isInstanceOf(NotFoundException.class);

        Producto existing = producto(4, "Cafe", 10_000.0, 0.19, "A");
        when(productoRepo.findById(4)).thenReturn(Optional.of(existing));
        when(productoRepo.existsByNombreProductoIgnoreCase("Te")).thenReturn(true);

        assertThatThrownBy(() -> service.update(new UpdateProductoInput(4, "Te", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void searchUsesEmptyQueryWhenNull() {
        when(productoRepo.findByNombreProductoContainingIgnoreCase(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(producto(1, "Cafe", 10.0, 0.19, "A"))));

        var page = service.search(null, 0, 10);

        assertThat(page.content()).hasSize(1);
        verify(productoRepo).findByNombreProductoContainingIgnoreCase(eq(""), any());
    }

    @Test
    void findByIdReturnsProductViewAndFailsWhenMissing() {
        when(productoRepo.findById(1)).thenReturn(Optional.of(producto(1, "Cafe", 10.0, 0.19, "A")));
        when(productoRepo.findById(404)).thenReturn(Optional.empty());

        assertThat(service.findById(1).nombreProducto()).isEqualTo("Cafe");
        assertThatThrownBy(() -> service.findById(404)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteReturnsFalseWhenMissingAndWrapsIntegrityViolations() {
        when(productoRepo.existsById(1)).thenReturn(false);
        assertThat(service.delete(1)).isFalse();

        when(productoRepo.existsById(2)).thenReturn(true);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk"))
                .when(productoRepo).deleteById(2);

        assertThatThrownBy(() -> service.delete(2)).isInstanceOf(ConflictException.class);
        verifyNoMoreInteractions(productoRepo);
    }

    private static Producto producto(Integer id, String nombre, Double precio, Double iva, String marca) {
        Producto producto = new Producto();
        producto.setIdProducto(id);
        producto.setNombreProducto(nombre);
        producto.setPrecioUnitario(precio);
        producto.setIvaProducto(iva);
        producto.setMarca(marca);
        return producto;
    }
}
