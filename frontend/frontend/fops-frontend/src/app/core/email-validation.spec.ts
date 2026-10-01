import { emailProblem, isValidEmail } from './email-validation';

describe('emailProblem', () => {
  it.each([
    ['', null],
    ['   ', null],
    ['ana@fops.local', null],
    ['  ana.maria+test@sub.example.co  ', null],
    ['ana', 'Email must include @'],
    ['ana @fops.local', 'Email cannot contain spaces'],
    ['ana@@fops.local', 'Email can only include one @'],
    ['@fops.local', 'Add the name before the @'],
    ['ana@', 'Add a domain after the @, e.g. name@example.com'],
    ['ana@fops', 'Add a domain after the @, e.g. name@example.com'],
    ['ana@fops.', 'Add a domain after the @, e.g. name@example.com'],
    ['ana@.local', 'Add a domain after the @, e.g. name@example.com']
  ])('"%s" -> %s', (value, expected) => {
    expect(emailProblem(value)).toBe(expected);
  });

  it('treats an empty value as not yet valid, without reporting a problem', () => {
    expect(emailProblem('')).toBeNull();
    expect(isValidEmail('')).toBe(false);
    expect(isValidEmail('ana@fops.local')).toBe(true);
  });
});
