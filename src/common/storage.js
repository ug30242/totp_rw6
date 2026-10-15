import storage from '@system.storage';
import { createAccountStore } from './account-store';

const store = createAccountStore(storage);
export const loadAccounts = store.loadAccounts;
export const appendAccounts = store.appendAccounts;
export const syncPhoneAccounts = store.syncPhoneAccounts;
export const moveAccountOrder = store.moveAccountOrder;
export const removeAccountAt = store.removeAccountAt;
