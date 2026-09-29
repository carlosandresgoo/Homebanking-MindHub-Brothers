import { PERSON_NAME } from './person-name';

describe('PERSON_NAME', () => {
  it.each(['José', 'Núñez', 'María José', "O'Brien", 'Pérez-Gil', 'Zoë', 'Melba'])(
    'accepts %s',
    (name) => expect(PERSON_NAME.test(name)).toBe(true),
  );

  it.each(['', ' José', 'José ', 'Ana  María', 'Chl0e', 'A--B', '<script>', 'Ana_'])(
    'rejects "%s"',
    (name) => expect(PERSON_NAME.test(name)).toBe(false),
  );
});
