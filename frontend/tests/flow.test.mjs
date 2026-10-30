import { test } from 'node:test'
import assert from 'node:assert/strict'
import { selectedPlan, authDestination, validateSignup } from '../src/flow.mjs'

test('only known plans can be resumed after authentication', () => {
  assert.equal(selectedPlan('?plan=monthly'), 'monthly')
  assert.equal(selectedPlan('?plan=unknown'), null)
  assert.equal(authDestination('?plan=monthly'), '/pricing/confirm/?plan=monthly')
  assert.equal(authDestination('?plan=https://bad.example'), '/welcome/')
})

test('signup validates required fields and matching passwords', () => {
  assert.ok(validateSignup('', '01012345678', 'password1', 'password1'))
  assert.ok(validateSignup('가게', '01012345678', 'password1', 'different'))
  assert.equal(validateSignup('가게', '01012345678', 'password1', 'password1'), '')
})
