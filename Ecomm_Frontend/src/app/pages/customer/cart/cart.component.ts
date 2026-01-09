import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpClientModule, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { CartDTO, CartItemDTO } from '../../../models/cart-models';
import { OrderDTO } from '../../../models/order-models';
import { DiscountDTO } from '../../../models/discount-models';

import { CartService } from '../../../services/CartService';
import { AuthService } from '../../../services/auth.service';
import { CartUpdateService } from '../../../services/cart-update.service';
import { OrderService } from '../../../services/order.service';
import Swal from 'sweetalert2';
import { getFriendlyError } from '../../../utils/error-utils';

@Component({
  selector: 'app-customer-cart',
  standalone: true,
  imports: [CommonModule, RouterLink, HttpClientModule, FormsModule, DecimalPipe],
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.css'],
})
export class CartComponent implements OnInit, OnDestroy {
  cart: CartDTO | null = null;
  loadingCart = true;
  submitting = false;
  processingCheckout = false;

  couponCode: string = '';
  availableCoupons: DiscountDTO[] = [];
  loadingCoupons = true;

  private cartUpdateSubscription: Subscription | undefined;
  private cartService = inject(CartService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private cartUpdateService = inject(CartUpdateService);
  private orderService = inject(OrderService);

  private swalWithBootstrapButtons = Swal.mixin({
    customClass: {
      confirmButton: 'btn btn-success ms-2',
      cancelButton: 'btn btn-danger',
    },
    buttonsStyling: false,
  });

  ngOnInit(): void {
    this.loadInitialData();
    this.cartUpdateSubscription = this.cartUpdateService.cartChanged$.subscribe(() => {
      this.loadCart();
      this.loadAvailableCoupons();
    });
  }

  ngOnDestroy(): void {
    this.cartUpdateSubscription?.unsubscribe();
  }

  private loadInitialData(): void {
    this.loadCart();
    this.loadAvailableCoupons();
  }

  private showError(msg: string): void {
    Swal.fire({
      icon: 'error',
      title: 'Action Failed',
      text: "Cannot Exceed the Stock Limit..",
      confirmButtonColor: '#d33',
    });
  }

  loadCart(): void {
    const customerId = this.authService.getCurrentUserId();
    if (!customerId) {
      this.router.navigate(['/login']);
      return;
    }

    this.loadingCart = true;
    this.cartService.getCartByCustomerId(customerId).subscribe({
      next: (data) => {
        this.cart = data;
        this.couponCode = data.couponCode || '';
        this.loadingCart = false;
      },
      error: (err) => {
        this.loadingCart = false;
        if (err.status !== 404) {
          this.showError(getFriendlyError(err, 'Failed to load your cart.'));
        }
      }
    });
  }

  loadAvailableCoupons(): void {
    const customerId = this.authService.getCurrentUserId();
    if (!customerId) return;

    this.loadingCoupons = true;
    this.cartService.getAvailableCoupons(customerId).subscribe({
      next: (coupons) => {
        this.availableCoupons = coupons;
        this.loadingCoupons = false;
      },
      error: (err) => {
        this.loadingCoupons = false;
        console.error('Coupons error:', err);
      }
    });
  }

  onQuantityChange(item: CartItemDTO, event: Event): void {
    const input = event.target as HTMLInputElement;
    const newQty = parseInt(input.value, 10);
    const oldQty = item.quantity;
    const productId = item.productDetails.id;

    if (!productId) return;

    if (isNaN(newQty) || newQty < 1) {
      input.value = '1';
      this.updateItemQuantity(productId, 1, oldQty);
    } else {
      this.updateItemQuantity(productId, newQty, oldQty);
    }
  }

  updateItemQuantity(productId: number, newQty: number, oldQty: number): void {
    const customerId = this.authService.getCurrentUserId();
    if (!customerId) return;

    this.submitting = true;
    this.cartService.updateProductQuantityInCart(customerId, productId, newQty).subscribe({
      next: (updatedCart) => {
        this.cart = updatedCart;
        this.submitting = false;
        this.cartUpdateService.notifyCartChanged();
      },
      error: (err) => {
        this.submitting = false;
        this.showError(getFriendlyError(err, 'Stock limit reached or update failed.'));
        this.loadCart(); // Revert UI
      }
    });
  }

  removeItem(productId: number): void {
    const customerId = this.authService.getCurrentUserId();
    if (!customerId) return;

    this.swalWithBootstrapButtons.fire({
      title: 'Remove item?',
      text: "This item will be removed from your cart.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Yes, remove it!',
      reverseButtons: true
    }).then((result) => {
      if (result.isConfirmed) {
        this.cartService.removeProductFromCart(customerId, productId).subscribe({
          next: (data) => {
            this.cart = data;
            this.cartUpdateService.notifyCartChanged();
            Swal.fire('Removed!', 'Item removed successfully.', 'success');
          },
          error: (err) => this.showError(getFriendlyError(err, 'Could not remove item.'))
        });
      }
    });
  }
  isCouponValid(coupon: DiscountDTO): boolean {
  if (!coupon.minOrderAmount) return true;
  return this.getCartSubtotal() >= coupon.minOrderAmount;
}

applyCoupon(code?: string): void {
  const customerId = this.authService.getCurrentUserId();
  const couponToApply = (code || this.couponCode).trim();
  
  if (!customerId || !couponToApply) return;

  // 1. Find the coupon details from availableCoupons list
  const selectedCoupon = this.availableCoupons.find(c => c.code === couponToApply);

  // 2. Perform Client-side Validation for Minimum Order Amount
  if (selectedCoupon && !this.isCouponValid(selectedCoupon)) {
    const missingAmount = selectedCoupon.minOrderAmount! - this.getCartSubtotal();
    this.showError(
      `Minimum order not met. Add Rs.${missingAmount.toFixed(2)} more to use code: ${couponToApply}`
    );
    return;
  }

  this.submitting = true;
  this.cartService.applyCouponToCart(customerId, couponToApply).subscribe({
    next: (data) => {
      this.cart = data;
      this.submitting = false;
      this.couponCode = data.couponCode || ''; // Sync the input field with applied code
      this.cartUpdateService.notifyCartChanged();
      Swal.fire({
        icon: 'success',
        title: 'Coupon Applied!',
        text: `You saved Rs.${data.discountAmount?.toFixed(2) || 0}`,
        timer: 2000,
        showConfirmButton: false
      });
    },
    error: (err) => {
      this.submitting = false;
      this.showError(err.error?.message || 'Invalid or expired coupon code.');
    }
  });
}
  removeCoupon(): void {
    const customerId = this.authService.getCurrentUserId();
    if (!customerId) return;

    this.submitting = true;
    this.cartService.removeCouponFromCart(customerId).subscribe({
      next: (data) => {
        this.cart = data;
        this.couponCode = '';
        this.submitting = false;
        this.cartUpdateService.notifyCartChanged();
        Swal.fire('Removed', 'Coupon removed.', 'info');
      },
      error: (err) => {
        this.submitting = false;
        this.showError('Failed to remove coupon.');
      }
    });
  }

  clearCart(): void {
    const customerId = this.authService.getCurrentUserId();
    if (!customerId) return;

    this.swalWithBootstrapButtons.fire({
      title: 'Clear entire cart?',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Yes, clear it!'
    }).then((result) => {
      if (result.isConfirmed) {
        this.submitting = true;
        this.cartService.clearCart(customerId).subscribe({
          next: () => {
            this.loadCart();
            this.submitting = false;
            this.cartUpdateService.notifyCartChanged();
            Swal.fire('Cleared!', 'Your cart is now empty.', 'success');
          },
          error: (err) => {
            this.submitting = false;
            this.showError('Failed to clear cart.');
          }
        });
      }
    });
  }

  checkout(): void {
    const customerId = this.authService.getCurrentUserId();
    if (!customerId || !this.cart?.cartItems.length) return;

    this.processingCheckout = true;
    this.orderService.createOrderFromCart(customerId).subscribe({
      next: (order) => {
        this.processingCheckout = false;
        this.cartUpdateService.notifyCartChanged();
        this.router.navigate(['/home/checkout', order.id]);
      },
      error: (err) => {
        this.processingCheckout = false;
        this.showError(err.error?.message || 'Checkout failed.');
      }
    });
  }

  getCartSubtotal(): number {
    return this.cart?.cartItems?.reduce((acc, item) => acc + (item.price * item.quantity), 0) || 0;
  }
}