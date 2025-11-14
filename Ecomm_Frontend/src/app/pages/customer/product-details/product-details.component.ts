import { Component, inject, OnInit } from '@angular/core';
import { ProductDTO } from '../../../models/product.model';
import { ActivatedRoute, Router } from '@angular/router';
import { ProductService } from '../../../services/product.service';
import { CartService } from '../../../services/CartService';
import { AuthService } from '../../../services/auth.service';
import { AddItemToCartRequestDTO } from '../../../models/add-item-to-cart-models';
import { CartDTO } from '../../../models/cart-models';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductReviewComponent } from '../product-review/product-review.component';
import Swal from 'sweetalert2';
import { WishlistService } from '../../../services/WishlistService';

@Component({
  selector: 'app-product-details',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductReviewComponent],
  templateUrl: './product-details.component.html',
  styleUrls: ['./product-details.component.css']
})
export class ProductDetailsComponent implements OnInit {
  product: ProductDTO | null = null;
  isLoading: boolean = false;
  quantity: number = 1;
  message: string | null = null;
  messageType: 'success' | 'error' | null = null;
  currentImageIndex: number = 0;

  private route = inject(ActivatedRoute);
  private productService = inject(ProductService);
  private cartService = inject(CartService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private wishlistService = inject(WishlistService); // Added WishlistService injection

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const productId = Number(params.get('id'));
      if (productId) {
        this.isLoading = true;
        this.message = null;
        this.messageType = null;
        this.productService.getProductById(productId).subscribe({
          next: (product) => {
            this.product = product;
            this.currentImageIndex = 0;
            this.isLoading = false;
          },
          error: (err) => {
            console.error('Failed to fetch product details', err);
            this.isLoading = false;
            this.message = 'Product not found.';
            this.messageType = 'error';
            setTimeout(() => this.router.navigate(['/home']), 3000);
          }
        });
      }
    });
  }

  // Safely select an image
  selectImage(index: number): void {
    if (!this.product?.images || index < 0 || index >= this.product.images.length) return;
    this.currentImageIndex = index;
  }

  // Safely navigate to the previous image
  prevImage(): void {
    if (!this.product?.images || this.currentImageIndex <= 0) return;
    this.currentImageIndex--;
  }

  // Safely navigate to the next image
  nextImage(): void {
    if (!this.product?.images || this.currentImageIndex >= this.product.images.length - 1) return;
    this.currentImageIndex++;
  }

  /**
   * Adds the product to the user's wishlist.
   * Handles authentication check and API communication.
   * @param productId The ID of the product to add (can be undefined).
   */
  addToWishlist(productId: number | undefined): void { // Changed parameter type to accept undefined
    if (!productId) {
      Swal.fire({
        icon: 'error',
        title: 'Error',
        text: 'Cannot add to wishlist: Product ID is missing.',
        confirmButtonText: 'OK'
      });
      return;
    }

    const customerId = this.authService.getCurrentUserId();
    if (!customerId) {
      Swal.fire({
        icon: 'error',
        title: 'Not Logged In',
        text: 'You must be logged in to add products to the wishlist.',
        footer: '<a href="/login">Login now</a>',
        confirmButtonText: 'OK'
      });
      return;
    }

    this.wishlistService.addToWishlist(productId).subscribe({
      next: (response) => {
        Swal.fire({
          icon: 'success',
          title: 'Wishlist Updated!',
          text: `${response.productName} has been added to your wishlist.`,
          showConfirmButton: false,
          timer: 2000
        });
        // Logic to update the heart icon to 'bi-heart-fill' would go here if you tracked wishlist status
      },
      error: (error) => {
        let message = 'Failed to add item to wishlist. Please try again.';
        
        if (error.status === 409) { // Assuming 409 Conflict for duplicate
          message = error.error?.message || 'This product is already in your wishlist.';
        } else if (error.status === 401 || error.status === 403) {
          message = 'Please log in to add items to your wishlist.';
        } else {
          message = error.error?.message || message;
        }

        Swal.fire({
          icon: 'warning',
          title: 'Action Failed',
          text: message
        });
      }
    });
  }


  addToCart(): void {
    if (!this.product) return;

    const customerId = this.authService.getCurrentUserId();
    if (!customerId) {
      Swal.fire({
        icon: 'error',
        title: 'Not Logged In',
        text: 'You must be logged in to add products to the cart.',
        footer: '<a href="/login">Login now</a>',
        confirmButtonText: 'OK'
      });
      return;
    }

    if (!this.product.id) {
      Swal.fire({
        icon: 'error',
        title: 'Error',
        text: 'Product ID is missing. Cannot add to cart.',
        confirmButtonText: 'OK'
      });
      return;
    }

    const addItemToCartDTO: AddItemToCartRequestDTO = {
      productId: this.product.id,
      quantity: this.quantity
    };

    this.cartService.addProductToCart(customerId, addItemToCartDTO).subscribe({
      next: (cartDto: CartDTO) => {
        Swal.fire({
          icon: 'success',
          title: 'Added to Cart!',
          text: 'The product has been added to your cart.',
          confirmButtonText: 'Continue Shopping'
        });
      },
      error: (err) => {
        console.error('Failed to add item to cart', err);
        Swal.fire({
          icon: 'error',
          title: 'Error',
          text: 'Failed to add product to cart. Please try again.',
          confirmButtonText: 'OK'
        });
      }
    });
  }

  onQuantityChange(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    let value = parseInt(inputElement.value, 10);
    if (isNaN(value) || value < 1) {
      value = 1;
      inputElement.value = '1';
    } else if (this.product && value > this.product.stockQuantity) {
      value = this.product.stockQuantity;
      inputElement.value = value.toString();
    }
    this.quantity = value;
  }
}