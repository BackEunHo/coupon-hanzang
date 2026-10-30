export const plans = {
  free: { name: '무료 요금제', price: '월 0원', description: '월 문자 발송 한도 안에서 쿠폰을 발급하는 방식입니다.' },
  monthly: { name: '월 요금제', price: '월 19,000원', description: '매월 이용료를 내고 쿠폰을 발급하는 방식입니다.' },
  usage: { name: '월 종량제', price: '건당 50원', description: '무료 한도를 넘긴 사용량에 따라 요금을 내는 방식입니다.' },
}
export function selectedPlan(search) {
  const plan = new URLSearchParams(search).get('plan')
  return plan && Object.hasOwn(plans, plan) ? plan : null
}
export function authDestination(search) {
  const plan = selectedPlan(search)
  return plan ? `/pricing/confirm/?plan=${plan}` : '/welcome/'
}
export function validateSignup(name, phone, password, confirmation) {
  if (!name.trim() || name.trim().length > 100) return '가게 이름을 1~100자로 입력해 주세요.'
  if (!/^(010\d{8}|010-\d{4}-\d{4})$/.test(phone)) return '올바른 010 휴대전화 번호를 입력해 주세요.'
  if (password.length < 8 || password.length > 128) return '비밀번호를 8~128자로 입력해 주세요.'
  if (password !== confirmation) return '비밀번호가 일치하지 않습니다.'
  return ''
}
