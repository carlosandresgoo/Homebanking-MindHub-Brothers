import { Validators } from '@angular/forms';

/**
 * Same rule as the backend (`CreateClientRequest.PERSON_NAME`): letters of any language (José, Núñez,
 * Zoë) with single spaces, apostrophes or hyphens between parts.
 */
export const PERSON_NAME = /^\p{L}+(?:[ '-]\p{L}+)*$/u;

export const PERSON_NAME_ERROR = 'Solo letras (con tildes), espacios, apóstrofos o guiones.';

/** Validators for a first or last name field. */
export const personNameValidators = [
  Validators.required,
  Validators.maxLength(50),
  Validators.pattern(PERSON_NAME),
];
