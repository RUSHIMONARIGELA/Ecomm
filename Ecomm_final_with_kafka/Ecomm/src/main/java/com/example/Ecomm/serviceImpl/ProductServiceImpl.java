package com.example.Ecomm.serviceImpl;

import com.example.Ecomm.dto.BulkUploadResultDTO;
import com.example.Ecomm.dto.ProductDTO;
import com.example.Ecomm.entitiy.CartItem;
import com.example.Ecomm.entitiy.Category;
import com.example.Ecomm.entitiy.Product;
import com.example.Ecomm.exception.ResourceNotFoundException;
import com.example.Ecomm.repository.CartItemRepository;
import com.example.Ecomm.repository.CategoryRepository;
import com.example.Ecomm.repository.ProductRepository;
import com.example.Ecomm.service.ProductService;
import com.example.Ecomm.util.CsvHelper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CategoryRepository categoryRepository;
	
	@Autowired
	private CartItemRepository cartItemRepository;
	

	@Override
	@Transactional(readOnly = true)
	public List<ProductDTO> getAllProducts() {
		return productRepository.findAll().stream().map(this::mapProductToDTO).collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public ProductDTO getProductById(Long id) {
		Product product = productRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Product", "Id", id));
		return mapProductToDTO(product);
	}

	@Override
	@Transactional
	public ProductDTO createProduct(ProductDTO productDTO) {
		Product product = mapDTOToProduct(productDTO);
		Product savedProduct = productRepository.save(product);
		return mapProductToDTO(savedProduct);
	}

	@Override
	@Transactional
	public ProductDTO updateProduct(Long id, ProductDTO productDTO) {
		Product existingProduct = productRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Product", "Id", id));

		existingProduct.setName(productDTO.getName());
		existingProduct.setDescription(productDTO.getDescription());
		existingProduct.setImages(productDTO.getImages());
		existingProduct.setPrice(productDTO.getPrice());
		existingProduct.setStockQuantity(productDTO.getStockQuantity());

		if (productDTO.getCategoryId() != null) {
			Category category = categoryRepository.findById(productDTO.getCategoryId())
					.orElseThrow(() -> new ResourceNotFoundException("Category", "Id", productDTO.getCategoryId()));
			existingProduct.setCategory(category);
		} else {
			throw new IllegalArgumentException("Category ID is required for product update.");
		}

		Product updatedProduct = productRepository.save(existingProduct);
		return mapProductToDTO(updatedProduct);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProductDTO> getProductsByCategoryId(Long categoryId) {
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new ResourceNotFoundException("Category", "Id", categoryId));

		return productRepository.findByCategory(category).stream().map(this::mapProductToDTO)
				.collect(Collectors.toList());
	}

	
	@Override
	@Transactional 
	public void deleteProduct(Long id) {
		Product product = productRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Product", "Id", id));

		List<CartItem> associatedCartItems = cartItemRepository.findByProduct(product);
		if (!associatedCartItems.isEmpty()) {
			cartItemRepository.deleteAll(associatedCartItems);
		}

		productRepository.delete(product);
	}
	
    @Override
    @Transactional
    public BulkUploadResultDTO uploadProductsFromCsv(MultipartFile file) {
        int totalProcessed = 0;
        int addedCount = 0;
        int skippedCount = 0;
        StringBuilder messageBuilder = new StringBuilder();

        try {
            List<ProductDTO> productDTOs = CsvHelper.csvToProductDTOs(file.getInputStream());
            totalProcessed = productDTOs.size();

            for (ProductDTO productDTO : productDTOs) {
                Category category = null;
                if (productDTO.getCategoryId() != null) {
                    category = categoryRepository.findById(productDTO.getCategoryId()).orElse(null);
                }

                if (category == null) {
                    skippedCount++;
                    messageBuilder.append("Skipped '").append(productDTO.getName())
                                  .append("' (Category ID ")
                                  .append(productDTO.getCategoryId() != null ? productDTO.getCategoryId() : "null")
                                  .append(" not found). ");
                    continue;
                }

                boolean exists = productRepository.findByNameIgnoreCaseAndCategoryId(productDTO.getName(), productDTO.getCategoryId()).isPresent();

                if (exists) {
                    skippedCount++;
                    messageBuilder.append("Skipped '").append(productDTO.getName())
                                  .append("' (Duplicate found in category '").append(category.getName()).append("'). ");
                } else {
                    Product product = mapDTOToProduct(productDTO);
                    product.setCategory(category);
                    productRepository.save(product);
                    addedCount++;
                }
            }
            String finalMessage = String.format("CSV upload complete. %d products processed: %d added, %d skipped.", totalProcessed, addedCount, skippedCount);
            if (messageBuilder.length() > 0) {
                finalMessage += " Details: " + messageBuilder.toString();
            }
            return new BulkUploadResultDTO(totalProcessed, addedCount, skippedCount, finalMessage);

        } catch (IOException e) {
            return new BulkUploadResultDTO(totalProcessed, addedCount, skippedCount, "Failed to read CSV file: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace(); 
            return new BulkUploadResultDTO(totalProcessed, addedCount, skippedCount, "Failed to process CSV file: " + e.getMessage());
        }
    }


	@Override
	@Transactional
	public List<ProductDTO> createMultipleProducts(List<ProductDTO> productDTOs) {
		List<Product> products = productDTOs.stream().map(this::mapDTOToProduct).collect(Collectors.toList());
		List<Product> savedProducts = productRepository.saveAll(products);

		return savedProducts.stream().map(this::mapProductToDTO).collect(Collectors.toList());
	}
	
	
		@Override
		@Transactional(readOnly = true)
		public Optional<ProductDTO> getProductByName(String name) {
			return productRepository.findByName(name).map(this::mapProductToDTO);
		}
	

	private ProductDTO mapProductToDTO(Product product) {
		ProductDTO productDTO = new ProductDTO();
		productDTO.setId(product.getId());
		productDTO.setName(product.getName());
		productDTO.setDescription(product.getDescription());
		productDTO.setImages(product.getImages());
		productDTO.setPrice(product.getPrice());
		productDTO.setStockQuantity(product.getStockQuantity());

		if (product.getCategory() != null) {
			productDTO.setCategoryId(product.getCategory().getId());
			productDTO.setCategoryName(product.getCategory().getName());
		}
		return productDTO;
	}

	private Product mapDTOToProduct(ProductDTO productDTO) {
		Product product = new Product();
		

		product.setName(productDTO.getName());
		product.setDescription(productDTO.getDescription());
		product.setImages(productDTO.getImages());
		product.setPrice(productDTO.getPrice());
		product.setStockQuantity(productDTO.getStockQuantity());

		if (productDTO.getCategoryId() != null) {
			Category category = categoryRepository.findById(productDTO.getCategoryId())
					.orElseThrow(() -> new ResourceNotFoundException("Category", "Id", productDTO.getCategoryId()));
			product.setCategory(category);
		} else {
			throw new IllegalArgumentException("Category ID is required for product creation/update.");
		}
		return product;
	}

}
