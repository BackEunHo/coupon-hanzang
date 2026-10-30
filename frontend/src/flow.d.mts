export const plans: Record<string, { name: string; price: string; description: string }>
export function selectedPlan(search: string): string | null
export function authDestination(search: string): string
export function validateSignup(name: string, phone: string, password: string, confirmation: string): string
