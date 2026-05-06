package com.ep18.couriersync.backend.customers.service;

import com.ep18.couriersync.backend.common.dto.PagingDTOs.PageResponse;
import com.ep18.couriersync.backend.common.exception.ConflictException;
import com.ep18.couriersync.backend.common.exception.NotFoundException;
import com.ep18.couriersync.backend.common.pagination.PageMapper;
import com.ep18.couriersync.backend.common.pagination.PageRequestUtil;
import com.ep18.couriersync.backend.customers.domain.Producto;
import com.ep18.couriersync.backend.customers.dto.ProductoDTOs.CreateProductoInput;
import com.ep18.couriersync.backend.customers.dto.ProductoDTOs.ProductoView;
import com.ep18.couriersync.backend.customers.dto.ProductoDTOs.UpdateProductoInput;
import com.ep18.couriersync.backend.customers.repository.ProductoRepository;
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
import static com.ep18.couriersync.backend.common.service.ServiceOperations.valueOrDefault;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepo;
    private static final String STRING_PRODUCTO_NO_ENCONTRADO = "Producto no encontrado";

    @Transactional
    public ProductoView create(CreateProductoInput in) {
        rejectWhen(
                productoRepo.existsByNombreProductoIgnoreCase(in.nombreProducto()),
                () -> new ConflictException("Ya existe un producto con ese nombre"));

        Producto producto = new Producto();
        producto.setNombreProducto(in.nombreProducto());
        producto.setPrecioUnitario(in.precioUnitario());
        producto.setIvaProducto(in.ivaProducto());
        producto.setMarca(in.marca());

        Producto saved = productoRepo.save(producto);
        return new ProductoView(
                saved.getIdProducto(),
                saved.getNombreProducto(),
                saved.getPrecioUnitario(),
                saved.getIvaProducto(),
                saved.getMarca()
        );
    }

    @Transactional
    public ProductoView update(UpdateProductoInput in) {
        Producto producto = findOrThrow(productoRepo, in.idProducto(), () -> new NotFoundException(STRING_PRODUCTO_NO_ENCONTRADO));

        rejectDuplicatedChange(
                in.nombreProducto(),
                producto.getNombreProducto(),
                String::equalsIgnoreCase,
                productoRepo::existsByNombreProductoIgnoreCase,
                () -> new ConflictException("Ya existe un producto con ese nombre"));

        setIfPresent(in.nombreProducto(), producto::setNombreProducto);
        setIfPresent(in.precioUnitario(), producto::setPrecioUnitario);
        setIfPresent(in.ivaProducto(), producto::setIvaProducto);
        setIfPresent(in.marca(), producto::setMarca);

        Producto saved = productoRepo.save(producto);
        return new ProductoView(
                saved.getIdProducto(),
                saved.getNombreProducto(),
                saved.getPrecioUnitario(),
                saved.getIvaProducto(),
                saved.getMarca()
        );
    }

    @Transactional(readOnly = true)
    public ProductoView findById(Integer id) {
        Producto producto = findOrThrow(productoRepo, id, () -> new NotFoundException(STRING_PRODUCTO_NO_ENCONTRADO));
        return new ProductoView(
                producto.getIdProducto(),
                producto.getNombreProducto(),
                producto.getPrecioUnitario(),
                producto.getIvaProducto(),
                producto.getMarca()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductoView> search(String q, Integer page, Integer size) {
        Page<Producto> productos = productoRepo.findByNombreProductoContainingIgnoreCase(
                valueOrDefault(q, ""),
                PageRequestUtil.of(page, size, Sort.by("nombreProducto").ascending())
        );
        return PageMapper.map(productos, producto -> new ProductoView(
                producto.getIdProducto(),
                producto.getNombreProducto(),
                producto.getPrecioUnitario(),
                producto.getIvaProducto(),
                producto.getMarca()
        ));
    }

    @Transactional
    public boolean delete(Integer id) {
        return deleteIfPresent(
                productoRepo,
                id,
                () -> new ConflictException("No se puede eliminar: el producto tiene movimientos asociados"));
    }

}
