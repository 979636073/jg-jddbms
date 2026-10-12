const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')

const tests = fs.readdirSync(__dirname)
  .filter(name => name.endsWith('.test.js') && !name.startsWith('dmp-'))
  .sort()

for (const name of tests) {
  const result = spawnSync(process.execPath, [path.join(__dirname, name)], {
    stdio: 'inherit',
  })
  if (result.status !== 0) process.exit(result.status || 1)
}

console.log(`核心界面回归测试通过：${tests.length} 项`)
