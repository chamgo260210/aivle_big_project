export const meta = {
  name: 'business-validation',
  description: '사업 검증 계획의 한 판을 조사 → 분배 → 구현 → 계약 감사 → 검증으로 수행한다',
  whenToUse: '계획서(~/.claude/plans/wild-purring-aho.md)의 한 판(0판·1-1·2-2 …)을 실행할 때. args 로 어느 판인지 넘긴다.',
  phases: [
    { title: '조사', detail: '읽기 전용. 고칠 자리와 깨질 짝을 근거와 함께 뽑는다' },
    { title: '분배', detail: '어느 층을 어떤 순서로 고칠지 정한다' },
    { title: '구현', detail: '층마다 순차로. 계약 짝은 쪼개지 않는다' },
    { title: '계약 감사', detail: '한쪽만 고쳐 조용히 깨진 자리를 찾는다' },
    { title: '검증', detail: '실제로 돌리고 기존 실패와 가려낸다' },
    { title: '수선', detail: '감사·검증이 걸린 것만 고친다 (최대 2회)' },
  ],
}

const PLAN = 'C:/Users/User/.claude/plans/wild-purring-aho.md'
const SLICE = typeof args === 'string' ? args : (args && args.slice) || '계획서의 다음 미완료 판'

const 공통 = `
계획서: ${PLAN} (먼저 읽어라)
이번에 할 것: ${SLICE}

이 저장소의 규율:
- 두 곳을 동시에 고쳐야 하는 짝이 여럿이다. 한쪽만 고치면 컴파일·테스트는 통과한 뒤
  런타임에만 깨지거나 결과가 조용히 버려진다.
- 골든 픽스처 ai/tests/fixtures/market_research/*.json 은 AI·Java·프론트 3층 공용이다.
- app/research/bm/ 은 정본이 담당자 노트북이라 고치지 않는다. 우리 층에서 덮는다.
- app/research/research2/ 는 동결이다.
- 유료 실행(실제 LLM 호출)은 누구도 스스로 돌리지 않는다. 필요하면 보고하고 멈춘다.
- 경계 표시("법률 자문 아님", "가설이며 실제 고객 응답 아님", "사용자가 입력한 실행 계획이다"
  등)를 절대 지우지 않는다.
`

const 분배_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['layers', 'contractPairs', 'rationale'],
  properties: {
    layers: {
      type: 'array',
      description: '고쳐야 하는 층을 고치는 순서대로. 필요 없는 층은 빼라.',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['layer', 'instruction'],
        properties: {
          layer: { type: 'string', enum: ['ai', 'backend', 'frontend'] },
          instruction: { type: 'string', description: '그 층에서 무엇을 어떤 파일에 할지 구체적으로' },
        },
      },
    },
    contractPairs: {
      type: 'array',
      description: '이번 변경이 건드리는 「두 곳 동시 수정」 짝. 없으면 빈 배열.',
      items: { type: 'string' },
    },
    rationale: { type: 'string' },
  },
}

const 판정_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['ok', 'findings'],
  properties: {
    ok: { type: 'boolean' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['what', 'where', 'fix'],
        properties: {
          what: { type: 'string' },
          where: { type: 'string', description: '파일:줄' },
          fix: { type: 'string', description: '무엇을 어떻게 고쳐야 하는가' },
        },
      },
    },
  },
}

const AGENT_OF = { ai: 'bv-ai', backend: 'bv-backend', frontend: 'bv-frontend' }

// ── 1. 조사 — 읽기 전용이라 병렬이 안전하다 ────────────────────────────
phase('조사')
const [고칠자리, 깨질짝] = await parallel([
  () => agent(`${공통}

**고칠 자리**를 전수로 뽑아라. 파일:줄 근거 필수. 추측 금지.
이미 있는 함수·상수를 최대한 재사용하도록, 새로 만들지 않아도 되는 것을 먼저 찾아라.
만들어 놓고 안 쓰는 죽은 코드가 실제로 있으니 호출부를 끝까지 따라가라.`,
    { agentType: 'bv-explorer', label: '조사:고칠자리', phase: '조사' }),
  () => agent(`${공통}

**이번 변경으로 깨질 수 있는 짝과 회귀 위험**을 뽑아라. 파일:줄 근거 필수.
봉투(exact-match) · TaskType(네 곳) · 여정 칸 이름 매핑 · 골든 픽스처 3층 공용 ·
프롬프트↔validator · Flyway 번호. 해당 없으면 해당 없다고 명시하라.`,
    { agentType: 'bv-explorer', label: '조사:깨질짝', phase: '조사' }),
])

// ── 2. 분배 ────────────────────────────────────────────────────────────
phase('분배')
const 분배 = await agent(`${공통}

조사 결과:
### 고칠 자리
${고칠자리 || '(조사 실패)'}

### 깨질 짝
${깨질짝 || '(조사 실패)'}

어느 층을 **어떤 순서로** 고칠지 정하라.

⚠ **계약 짝은 쪼개지 마라.** 봉투처럼 AI 쪽과 Java 쪽을 같이 고쳐야 하는 것은
**한 층의 instruction 안에 둘 다** 적어라(bv-ai 는 Java 계약 파일을 고칠 수 있다).
층을 나누는 기준은 언어가 아니라 **계약의 경계**다.

필요 없는 층은 빼라. 프론트 변경이 없으면 frontend 를 넣지 마라.`,
  { label: '분배', phase: '분배', schema: 분배_SCHEMA })

log(`층 ${분배.layers.length}개 · 계약 짝 ${분배.contractPairs.length}개`)
if (분배.contractPairs.length) log(`짝: ${분배.contractPairs.join(' / ')}`)

// ── 3. 구현 — **순차**. 병렬로 두면 계약 짝이 쪼개진다 ──────────────────
phase('구현')
const 구현결과 = []
for (const [i, 층] of 분배.layers.entries()) {
  const 앞선작업 = 구현결과.length
    ? `\n\n### 앞 층이 이미 한 일 (중복하지 마라)\n${구현결과.join('\n\n')}`
    : ''
  const out = await agent(`${공통}

### 이 층에서 할 일
${층.instruction}

### 이번 변경이 건드리는 계약 짝
${분배.contractPairs.length ? 분배.contractPairs.join('\n') : '없음'}

### 조사 결과 (고칠 자리)
${고칠자리 || '(없음)'}${앞선작업}

고친 뒤 **자기 층 테스트를 좁혀서 돌리고 출력을 붙여라.** 안 돌렸으면 안 돌렸다고 써라.
보고에는 「고친 파일 목록 · 왜 · 돌린 테스트와 결과 · 못 한 것」을 담아라.`,
    { agentType: AGENT_OF[층.layer], label: `구현:${층.layer}`, phase: '구현' })
  구현결과.push(`[${층.layer}]\n${out || '(실패)'}`)
}

// ── 4~6. 감사 → 검증 → 수선 ────────────────────────────────────────────
let 감사, 검증
for (let round = 0; round < 3; round++) {
  phase('계약 감사')
  감사 = await agent(`${공통}

### 이번에 한 구현
${구현결과.join('\n\n')}

git status·git diff 로 바뀐 파일을 파악하고, 짝이 맞는지 검사 목록을 **전부** 돌아라.`,
    { agentType: 'bv-contract-auditor', label: `감사#${round + 1}`, phase: '계약 감사',
      schema: 판정_SCHEMA })

  phase('검증')
  검증 = await agent(`${공통}

### 이번에 한 구현
${구현결과.join('\n\n')}

실제로 돌리고, 새 실패와 기존 실패를 **가려내라**. 개수를 붙여라.
유료 실행은 하지 마라 — 필요하면 보고만 하라.`,
    { agentType: 'bv-verifier', label: `검증#${round + 1}`, phase: '검증',
      schema: 판정_SCHEMA })

  const 문제 = [...(감사?.findings || []), ...(검증?.findings || [])]
  if ((감사?.ok !== false) && (검증?.ok !== false) && !문제.length) break
  if (round === 2) { log(`수선 한도 도달 — 남은 문제 ${문제.length}건`); break }

  phase('수선')
  log(`문제 ${문제.length}건 — 수선 ${round + 1}회차`)
  const 수선 = await agent(`${공통}

### 감사·검증이 찾은 문제 (이것만 고쳐라. 다른 것을 손대지 마라)
${문제.map((f, n) => `${n + 1}. ${f.what}\n   위치: ${f.where}\n   고칠 것: ${f.fix}`).join('\n')}

### 이번에 한 구현
${구현결과.join('\n\n')}

고친 뒤 관련 테스트를 돌리고 출력을 붙여라.`,
    { agentType: AGENT_OF[분배.layers[0]?.layer] || 'bv-ai',
      label: `수선#${round + 1}`, phase: '수선' })
  구현결과.push(`[수선#${round + 1}]\n${수선 || '(실패)'}`)
}

return {
  slice: SLICE,
  layers: 분배.layers.map((l) => l.layer),
  contractPairs: 분배.contractPairs,
  구현: 구현결과,
  감사,
  검증,
  남은문제: [...(감사?.findings || []), ...(검증?.findings || [])],
}
