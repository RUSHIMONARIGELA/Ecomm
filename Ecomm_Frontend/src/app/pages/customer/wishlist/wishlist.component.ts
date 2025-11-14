import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { CartService } from '../../../services/CartService';
import { AuthService } from '../../../services/auth.service';
import { ProductService } from '../../../services/product.service';
import { forkJoin, map, Observable } from 'rxjs';
import Swal from 'sweetalert2';
import { WishlistItemDTO, WishlistService } from '../../../services/WishlistService';
import { ProductDTO } from '../../../models/product.model';

// Extend the DTO to include a properly converted Date and Product Image URL
interface WishlistItemView extends Omit<WishlistItemDTO, 'addedAt'> {
  addedAt: Date;
  productImageUrl: string; 
}

@Component({
  selector: 'app-wishlist',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './wishlist.component.html',
  styleUrls: ['./wishlist.component.css']
})
export class WishlistComponent implements OnInit {
  wishlist: WishlistItemView[] = [];
  isLoading: boolean = true;
  errorMessage: string | null = null;

  private wishlistService = inject(WishlistService);
  private cartService = inject(CartService);
  private productService = inject(ProductService);
  private authService = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    this.loadWishlist();
  }

  /**
   * Converts the LocalDateTime array [year, month, day, hour, minute, second, nanosecond]
   * from the Spring Boot backend into a JavaScript Date object.
   * @param dateArray The date array from the DTO.
   * @returns A JavaScript Date object.
   */
  private convertJavaDateArrayToJSDate(dateArray: number[]): Date {
    // Note: Month in Java (1-12) is different from JS Date (0-11), so we subtract 1.
    return new Date(
      dateArray[0], // Year
      dateArray[1] - 1, // Month (0-indexed)
      dateArray[2], // Day
      dateArray[3], // Hour
      dateArray[4], // Minute
      dateArray[5], // Second
      Math.floor(dateArray[6] / 1000000) // Milliseconds (convert nano to milli)
    );
  }

  /**
   * Fetches the user's wishlist and then retrieves full product details (including images)
   * for each item.
   */
  loadWishlist(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.wishlistService.getWishlist().subscribe({
      next: (dtoList: WishlistItemDTO[]) => {
        if (dtoList.length === 0) {
          this.wishlist = [];
          this.isLoading = false;
          return;
        }

        // 1. Convert DTOs to WishlistItemView and prepare product detail Observables
        const productRequests: Observable<ProductDTO>[] = [];
        const baseWishlistView: Partial<WishlistItemView>[] = [];

        for (const dto of dtoList) {
          baseWishlistView.push({
            ...dto,
            // The result of this conversion is a Date object, hence the interface must use Date.
            addedAt: this.convertJavaDateArrayToJSDate(dto.addedAt as unknown as number[]),
            productImageUrl: '' // Placeholder, will be filled by the product service call
          });
          // Push the Observable for fetching product details
          productRequests.push(this.productService.getProductById(dto.productId));
        }

        // 2. Fetch all product details concurrently using forkJoin
        forkJoin(productRequests).subscribe({
          next: (productDetails: ProductDTO[]) => {
            this.wishlist = productDetails.map((product, index) => {
              const baseItem = baseWishlistView[index];
              return {
                ...baseItem,
                productImageUrl: product.images && product.images.length > 0 ? product.images[0] : 'https://placehold.co/50x50/CCCCCC/333333?text=No+Img'
              } as WishlistItemView;
            });
            this.isLoading = false;
          },
          error: (err) => {
            console.error('Error fetching product details for wishlist:', err);
            this.errorMessage = 'Failed to load product details for wishlist items.';
            this.isLoading = false;
            this.wishlist = [];
          }
        });
      },
      error: (err) => {
        console.error('Error fetching wishlist:', err);
        this.errorMessage = 'Failed to retrieve your wishlist. Please try again later.';
        this.isLoading = false;
        this.wishlist = [];
      }
    });
  }

  /**
   * Removes an item from the wishlist and reloads the list upon success.
   * @param productId The ID of the product to remove.
   */
  removeItem(productId: number): void {
    Swal.fire({
      title: 'Are you sure?',
      text: "You won't be able to revert this!",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#d33',
      cancelButtonColor: '#3085d6',
      confirmButtonText: 'Yes, remove it!'
    }).then((result) => {
      if (result.isConfirmed) {
        this.wishlistService.removeFromWishlist(productId).subscribe({
          next: () => {
            Swal.fire(
              'Removed!',
              'The product has been removed from your wishlist.',
              'success'
            );
            this.loadWishlist(); // Reload data
          },
          error: (err) => {
            console.error('Failed to remove item', err);
            Swal.fire(
              'Error!',
              'Failed to remove product. Please try again.',
              'error'
            );
          }
        });
      }
    });
  }

  /**
   * Moves a product from the wishlist to the cart (removes from wishlist and adds to cart).
   * @param item The wishlist item to move.
   */
  moveToCart(item: WishlistItemView): void {
    const customerId = this.authService.getCurrentUserId();
    if (!customerId) {
      Swal.fire({
        icon: 'error',
        title: 'Not Logged In',
        text: 'You must be logged in to move products to the cart.',
        footer: '<a href="/login">Login now</a>',
      });
      return;
    }

    // 1. Add to Cart
    this.cartService.addProductToCart(customerId, { productId: item.productId, quantity: 1 }).subscribe({
      next: () => {
        // 2. Remove from Wishlist (only if added to cart successfully)
        this.wishlistService.removeFromWishlist(item.productId).subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: 'Moved to Cart!',
              text: `${item.productName} is now in your cart.`,
              showConfirmButton: false,
              timer: 2000
            });
            this.loadWishlist(); // Reload list
          },
          error: (err) => {
            console.error('Failed to remove from wishlist after cart move', err);
            // Even if removal fails, inform user that cart update was successful
            Swal.fire('Partial Success', 'Product added to cart, but failed to remove from wishlist. Please refresh.', 'warning');
          }
        });
      },
      error: (err) => {
        let message = err.error?.message || 'Failed to add product to cart. Please try again.';

        if (err.status === 400 && message.includes('out of stock')) {
          message = 'This product is currently out of stock.';
        }

        console.error('Failed to add to cart', err);
        Swal.fire({
          icon: 'error',
          title: 'Cart Error',
          text: message,
          confirmButtonText: 'OK'
        });
      }
    });
  }
}