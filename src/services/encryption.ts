import CryptoJS from 'crypto-js';

// Simple AES encryption using a shared passphrase for E2EE.
// For a production app, ECDH key exchange should be implemented.
export const encryptMessage = (message: string, secretKey: string): string => {
  return CryptoJS.AES.encrypt(message, secretKey).toString();
};

export const decryptMessage = (ciphertext: string, secretKey: string): string => {
  try {
    const bytes = CryptoJS.AES.decrypt(ciphertext, secretKey);
    return bytes.toString(CryptoJS.enc.Utf8);
  } catch (e) {
    return '🔒 Decryption Failed';
  }
};
