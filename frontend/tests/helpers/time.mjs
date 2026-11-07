export function fakeTime() {
  let now = 0
  let id = 0
  const tasks = new Map()
  return {
    now: () => now,
    set: (fn, delay) => { const key = ++id; tasks.set(key, { fn, at: now + delay }); return key },
    clear: key => tasks.delete(key),
    async advance(ms) {
      now += ms
      for (const [key, task] of [...tasks]) if (task.at <= now) { tasks.delete(key); task.fn() }
      await new Promise(resolve => setImmediate(resolve))
    },
    count: () => tasks.size,
  }
}
