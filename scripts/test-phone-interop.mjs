import fs from 'node:fs';
import assert from 'node:assert/strict';
import {createPhoneImporter} from '../src/common/phone-import.js';
import {createAccountStore} from '../src/common/account-store.js';
import {generateTOTP} from '../src/common/totp.js';

// Input is emitted by the compiled Android implementation, not a JS reimplementation.
const raw=fs.readFileSync('android/app/build/interop/phone-packet.json','utf8');
const packet=JSON.parse(raw);
let value='[]';const replies=[];
const store=createAccountStore({get({success}){success(value);},set({value:v,success}){value=v;success();}});
const receiver=createPhoneImporter({save:store.syncPhoneAccounts,send:s=>replies.push(JSON.parse(s)),update(){}});
await receiver.receive(raw);
assert.equal(replies.at(-1).status,'saved');assert.equal(replies.at(-1).id,packet.id);
let accounts=await store.loadAccounts();assert.equal(accounts.length,1);
assert.equal(generateTOTP(accounts[0],59000).otp,'287082');
packet.id='b'.repeat(32);packet.account.name='Updated from Android';
await receiver.receive(JSON.stringify(packet));accounts=await store.loadAccounts();
assert.equal(accounts.length,1);assert.equal(accounts[0].name,'Updated from Android');
receiver.close();
console.log('Android packet -> watch validation -> persistent save -> acknowledgement -> RFC OTP -> update passed');
