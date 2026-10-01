/**
 * Explains what is wrong with an email as the user types, or returns null when it is valid.
 * An empty value is not reported: the form only complains once something has been typed.
 * Mirrors the backend rule closely enough to catch typos; the backend still validates.
 */
export function emailProblem(value: string | null | undefined): string | null {
  const email = (value ?? '').trim();
  if (!email) {
    return null;
  }
  if (/\s/.test(email)) {
    return 'Email cannot contain spaces';
  }
  const at = email.indexOf('@');
  if (at === -1) {
    return 'Email must include @';
  }
  if (email.indexOf('@', at + 1) !== -1) {
    return 'Email can only include one @';
  }
  if (at === 0) {
    return 'Add the name before the @';
  }
  const domain = email.slice(at + 1);
  if (!/^[^.]+(\.[^.]+)+$/.test(domain)) {
    return 'Add a domain after the @, e.g. name@example.com';
  }
  return null;
}

export function isValidEmail(value: string | null | undefined): boolean {
  return !!(value ?? '').trim() && emailProblem(value) === null;
}
