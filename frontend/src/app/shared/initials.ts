/** "Melba", "Morel" -> "MM". Used for avatar placeholders. */
export function initials(name: string, lastName: string): string {
  return `${name.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
}
