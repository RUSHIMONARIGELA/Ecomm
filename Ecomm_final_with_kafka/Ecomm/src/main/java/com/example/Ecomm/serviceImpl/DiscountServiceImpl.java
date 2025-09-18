package com.example.Ecomm.serviceImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.Ecomm.dto.DiscountDTO;
import com.example.Ecomm.entitiy.Discount;
import com.example.Ecomm.entitiy.Discount.DiscountType;
import com.example.Ecomm.exception.ResourceNotFoundException;
import com.example.Ecomm.repository.DiscountRepository;
import com.example.Ecomm.service.DiscountService;

import jakarta.transaction.Transactional;

@Service
public class DiscountServiceImpl implements DiscountService {

	@Autowired
	private DiscountRepository discountRepository;

	
	@Override
	@Transactional
	public DiscountDTO createDiscount(DiscountDTO discountDTO) {
		if (discountRepository.findByCode(discountDTO.getCode()).isPresent()) {
			throw new IllegalArgumentException("Discount code '" + discountDTO.getCode() + "' already exists.");
		}
		Discount discount = mapDTOToDiscount(discountDTO);
		discount.setUsedCount(0);
		Discount savedDiscount = discountRepository.save(discount);
		return mapDiscountToDTO(savedDiscount);
	}

	@Override
	public DiscountDTO getDiscountById(Long id) {
		Discount discount = discountRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Discount", "id", id));
		return mapDiscountToDTO(discount);
	}

	@Override
	public DiscountDTO getDiscountByCode(String code) {
		Discount discount = discountRepository.findByCode(code)
				.orElseThrow(() -> new ResourceNotFoundException("Discount", "code", code));
		return mapDiscountToDTO(discount);
	}

	@Override
	public List<DiscountDTO> getAllDiscounts() {
		return discountRepository.findAll().stream().map(this::mapDiscountToDTO).collect(Collectors.toList());
	}

	@Override
	@Transactional
	public DiscountDTO updateDiscount(Long id, DiscountDTO discountDTO) {
		Discount existingDiscount = discountRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Discount", "id", id));

		if (!existingDiscount.getCode().equals(discountDTO.getCode())
				&& discountRepository.findByCode(discountDTO.getCode()).isPresent()) {
			throw new IllegalArgumentException("Discount code '" + discountDTO.getCode() + "' already exists.");
		}

		existingDiscount.setCode(discountDTO.getCode());
		existingDiscount.setType(DiscountType.valueOf(discountDTO.getType()));
		existingDiscount.setValue(discountDTO.getValue());
		existingDiscount.setMinOrderAmount(discountDTO.getMinOrderAmount());
		
		existingDiscount.setStartDate(discountDTO.getStartDate());
		existingDiscount.setEndDate(discountDTO.getEndDate());

		existingDiscount.setUsageLimit(discountDTO.getUsageLimit());
		existingDiscount.setActive(discountDTO.isActive());

		Discount updatedDiscount = discountRepository.save(existingDiscount);
		return mapDiscountToDTO(updatedDiscount);
	}

	@Override
	@Transactional
	public void deleteDiscount(Long id) {
		Discount discount = discountRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Discount", "id", id));
		discountRepository.delete(discount);
	}

	@Override
	public boolean isValidDiscount(String code, BigDecimal currentAmount) {
		return discountRepository.findByCode(code).map(discount -> {
			LocalDateTime now = LocalDateTime.now();
			if (!discount.isActive()) {
				System.out.println("DEBUG: Discount " + code + " is not active.");
				return false;
			}
			if (now.isBefore(discount.getStartDate()) || now.isAfter(discount.getEndDate())) {
				System.out.println("DEBUG: Discount " + code + " is outside its valid date range.");
				return false;
			}
			if (discount.getUsageLimit() != null && discount.getUsedCount() >= discount.getUsageLimit()) {
				System.out.println("DEBUG: Discount " + code + " has reached its usage limit.");
				return false;
			}
			if (discount.getMinOrderAmount() != null && currentAmount.compareTo(discount.getMinOrderAmount()) < 0) {
				System.out.println("DEBUG: Discount " + code + " requires a minimum order amount of "
						+ discount.getMinOrderAmount() + ".");
				return false;
			}
			return true;
		}).orElse(false);
	}

	private Discount mapDTOToDiscount(DiscountDTO discountDTO) {
		Discount discount = new Discount();
		if (discountDTO.getId() != null) {
			discount.setId(discountDTO.getId());
		}
		discount.setCode(discountDTO.getCode());
		discount.setType(DiscountType.valueOf(discountDTO.getType()));
		discount.setValue(discountDTO.getValue());
		discount.setMinOrderAmount(discountDTO.getMinOrderAmount());
		
		discount.setStartDate(discountDTO.getStartDate());
		discount.setEndDate(discountDTO.getEndDate());

		discount.setUsageLimit(discountDTO.getUsageLimit());
		discount.setActive(discountDTO.isActive());
		discount.setUsedCount(discountDTO.getUsedCount() != null ? discountDTO.getUsedCount() : 0);
		return discount;
	}

	private DiscountDTO mapDiscountToDTO(Discount discount) {
		DiscountDTO discountDTO = new DiscountDTO();
		discountDTO.setId(discount.getId());
		discountDTO.setCode(discount.getCode());
		discountDTO.setType(discount.getType().name());
		discountDTO.setValue(discount.getValue());
		discountDTO.setMinOrderAmount(discount.getMinOrderAmount());
		
		discountDTO.setStartDate(discount.getStartDate());
		discountDTO.setEndDate(discount.getEndDate());

		discountDTO.setUsageLimit(discount.getUsageLimit());
		discountDTO.setUsedCount(discount.getUsedCount());
		discountDTO.setActive(discount.isActive());
		return discountDTO;
	}

   
    @Override
    public List<DiscountDTO> getAvailableCouponsForCustomer(Long customerId) {
        
        List<Discount> allActiveDiscounts = discountRepository.findByActiveTrue(); 
        LocalDateTime now = LocalDateTime.now(); 

        List<Discount> availableDiscounts = allActiveDiscounts.stream()
                .filter(discount -> {
                    
                    return discount.getEndDate().isAfter(now) || discount.getEndDate().isEqual(now);
                })
             
                .collect(Collectors.toList());

        return availableDiscounts.stream()
                .map(this::mapDiscountToDTO) 
                .collect(Collectors.toList());
    }
}
