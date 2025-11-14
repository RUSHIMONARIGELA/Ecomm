import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, AfterViewInit } from '@angular/core'; 
import { FormsModule } from '@angular/forms';
import { UserDetailsDTO } from '../../../models/customer-models'; 
import { UserService } from '../../../services/user.service'; 
import { AuthService } from '../../../services/auth.service'; 

declare const lucide: any; 

@Component({
  selector: 'app-user-management',
  standalone: true, 
  imports: [CommonModule, FormsModule],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.css'
})
export class UserManagementComponent { 
users: UserDetailsDTO[] = [];
  loading: boolean = true;
  error: string | null = null;

  selectedUser: UserDetailsDTO | null = null;

  availableRoles: string[] = ['CUSTOMER', 'ADMIN', 'SUPER_ADMIN'];
  selectedRoles: string[] = []; 

  showRoleModal: boolean = false;
  currentUserId: number | null = null;

  private userService = inject(UserService);
  private authService = inject(AuthService);

  ngOnInit(): void {
    this.currentUserId = this.authService.getCurrentUserId();
    console.log('UserManagementComponent: Authenticated User ID (currentUserId):', this.currentUserId);
    this.loadUsers();
  }

  ngAfterViewInit(): void {
    if (typeof lucide !== 'undefined') {
      lucide.createIcons();
    } else {
      console.warn('Lucide icons script not loaded. Icons may not render.');
    }
  }

  loadUsers(): void {
    this.loading = true;
    this.error = null;
    this.userService.getAllUsers().subscribe({
      next: (users) => {
        this.users = users;
        this.loading = false;
        console.log('UserManagementComponent: Users loaded:', this.users);
      },
      error: (err) => {
        console.error('UserManagementComponent: Error loading users:', err);
        this.error = 'Failed to load users. ' + (err.error?.message || err.message);
        this.loading = false;
      }
    });
  }

  openRoleModal(user: UserDetailsDTO): void {
    console.log('UserManagementComponent: openRoleModal called for user:', user.username, ' (ID:', user.id, ')');
    console.log('UserManagementComponent: Current Authenticated User ID:', this.currentUserId);
    if (user.id === this.currentUserId) {
      alert('You cannot change your own roles.');
      console.warn('UserManagementComponent: Attempted to edit own roles. Blocking.');
      return;
    }

    // Only allow editing for admin / super-admin accounts. Customers (and other non-admin accounts) cannot be changed.
    const roles = user.roles || [];
    const isAdminAccount = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_SUPER_ADMIN');
    if (!isAdminAccount) {
      alert('Only admin accounts can be edited. Customer accounts cannot be changed.');
      console.warn('UserManagementComponent: Attempted to edit non-admin account. Blocking. User roles:', roles);
      return;
    }
    this.selectedUser = { ...user };

    // For radio-based single selection, keep at most one role (prefer the first existing role)
    this.selectedRoles = (this.selectedUser.roles && this.selectedUser.roles.length > 0) ? [this.selectedUser.roles[0]] : [];
    this.showRoleModal = true;
    console.log('UserManagementComponent: Modal opened for user:', this.selectedUser.username);
    console.log('UserManagementComponent: Initial selectedRoles in modal:', this.selectedRoles);
  }

  closeRoleModal(): void {
    this.selectedUser = null;
    this.selectedRoles = [];
    this.showRoleModal = false;
    this.error = null;
    console.log('UserManagementComponent: Modal closed.');
  }

  onRoleChange(roleWithoutPrefix: string, event: Event): void { 
    const isChecked = (event.target as HTMLInputElement).checked;
    const fullRoleName = `ROLE_${roleWithoutPrefix}`; 

    if (isChecked) {
      if (!this.selectedRoles.includes(fullRoleName)) {
        this.selectedRoles.push(fullRoleName);
      }
    } else {
      this.selectedRoles = this.selectedRoles.filter(r => r !== fullRoleName);
    }
    console.log('UserManagementComponent: Selected roles updated:', this.selectedRoles);
  }

  onRoleSelect(roleWithoutPrefix: string, event: Event): void {
    const fullRoleName = `ROLE_${roleWithoutPrefix}`;
    this.selectedRoles = [fullRoleName];
    console.log('UserManagementComponent: Role selected (single):', this.selectedRoles);
  }

  updateUserRoles(): void {
    if (!this.selectedUser || !this.selectedUser.id) {
      this.error = 'No user selected for role update.';
      console.error('UserManagementComponent: No user selected for role update.');
      return;
    }

    this.error = null;
    const rolesToSend = this.selectedRoles; 

    console.log('UserManagementComponent: Attempting to update roles for user ID:', this.selectedUser.id, 'with roles:', rolesToSend);

    // Safety: prevent downgrading an admin account to CUSTOMER via the UI.
    const originallyAdmin = (this.selectedUser.roles || []).includes('ROLE_ADMIN') || (this.selectedUser.roles || []).includes('ROLE_SUPER_ADMIN');
    const tryingToSetCustomer = rolesToSend.includes('ROLE_CUSTOMER');
    if (originallyAdmin && tryingToSetCustomer) {
      this.error = 'Cannot change an admin account to a customer account.';
      console.warn('UserManagementComponent: Blocked attempt to set admin to customer for user ID:', this.selectedUser.id);
      return;
    }

    this.userService.updateUserRoles(this.selectedUser.id, rolesToSend).subscribe({
      next: (updatedUser) => {
        alert('User roles updated successfully!');
        this.closeRoleModal();
        this.loadUsers();
        console.log('UserManagementComponent: Roles updated successfully for user:', updatedUser.username);
      },
      error: (err) => {
        console.error('UserManagementComponent: Error updating user roles:', err);
        this.error = 'Failed to update roles: ' + (err.error?.message || err.message);
      }
    });
  }

  /**
   * Return whether a role option should be enabled for selection.
   * Business rule: if the target user is an admin (ROLE_ADMIN or ROLE_SUPER_ADMIN),
   * they must not be changed to CUSTOMER. In that case only SUPER_ADMIN may be selected.
   */
  isRoleOptionAllowed(roleWithoutPrefix: string): boolean {
    if (!this.selectedUser) return true;
    const roles = this.selectedUser.roles || [];
    const isAdminAccount = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_SUPER_ADMIN');
    if (isAdminAccount) {
      // Allow switching between ADMIN and SUPER_ADMIN for admin accounts (i.e. allow demotion/promote),
      // but do not allow setting CUSTOMER for admin accounts.
      return roleWithoutPrefix === 'SUPER_ADMIN' || roleWithoutPrefix === 'ADMIN';
    }
    return true;
  }
}
