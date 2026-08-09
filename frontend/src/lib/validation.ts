const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^$|^[+0-9 ()-]{7,20}$/;
const URL_PATTERN = /^https?:\/\/.+/i;

export function isBlank(value: string) {
  return value.trim().length === 0;
}

export function isValidEmail(value: string) {
  return EMAIL_PATTERN.test(value);
}

export function isValidPhone(value: string) {
  return PHONE_PATTERN.test(value);
}

export function isValidUrl(value: string) {
  return URL_PATTERN.test(value);
}
