# Skill Registry

**Delegator use only.** Any agent that launches sub-agents reads this registry to resolve compact rules, then injects them directly into sub-agent prompts. Sub-agents do NOT read this registry or individual SKILL.md files.

See `_shared/skill-resolver.md` for the full resolution protocol.

## User Skills

| Trigger | Skill | Path |
|---------|-------|------|
| When creating a pull request, opening a PR, or preparing changes for review | branch-pr | C:/Users/zottt/.config/opencode/skills/branch-pr/SKILL.md |
| When writing Go tests, using teatest, or adding test coverage | go-testing | C:/Users/zottt/.config/opencode/skills/go-testing/SKILL.md |
| When creating a GitHub issue, reporting a bug, or requesting a feature | issue-creation | C:/Users/zottt/.config/opencode/skills/issue-creation/SKILL.md |
| When user says "judgment day", "judgment-day", "review adversarial", "dual review", "doble review", "juzgar", "que lo juzguen" | judgment-day | C:/Users/zottt/.config/opencode/skills/judgment-day/SKILL.md |
| When user asks to create a new skill, add agent instructions, or document patterns for AI | skill-creator | C:/Users/zottt/.config/opencode/skills/skill-creator/SKILL.md |

## Compact Rules

Pre-digested rules per skill. Delegators copy matching blocks into sub-agent prompts as `## Project Standards (auto-resolved)`.

### branch-pr
- Every PR MUST link an approved issue (status:approved)
- Branch naming: `^(feat|fix|chore|docs|style|refactor|perf|test|build|ci|revert)\/[a-z0-9._-]+$`
- Exactly one `type:*` label per PR
- Run shellcheck on modified scripts before opening PR
- Automated checks must pass before merge is possible

### go-testing
- Use table-driven tests for multiple test cases (struct with name, input, expected, wantErr)
- Bubbletea TUI: use `teatest` for programmatic testing with `teatest.Check...` assertions
- Golden file testing for complex outputs (compare against .golden files)
- Run tests with `go test ./...` or `go test -cover` for coverage

### issue-creation
- Blank issues disabled - MUST use template (bug_report or feature_request)
- Every issue gets `status:needs-review` automatically on creation
- Maintainer MUST add `status:approved` before any PR can be opened
- Questions go to Discussions, not issues
- Search for duplicates before creating new issue

### judgment-day
- Launch TWO independent blind sub-agents in parallel via `delegate` (never sequential)
- Each judge works independently without knowing about the other (no cross-contamination)
- Synthesis combines findings from both judges, applies fixes, re-judges (max 2 iterations)
- Before launching: resolve skills via Skill Resolver Protocol, inject Compact Rules into BOTH judge prompts
- Escalate to human after 2 failed iterations

### skill-creator
- Create in `skills/{skill-name}/SKILL.md` with YAML frontmatter (name, description with Trigger:, license)
- Compact Rules section: 5-15 lines of actionable rules ("do X", "never Y")
- Don't create for trivial/one-off tasks or when documentation already exists
- Use `allowed-tools` field to restrict tool access (e.g., `Read, Edit, Write, Glob`)
- Include examples only for critical patterns, not full tutorials

## Project Conventions

| File | Path | Notes |
|------|------|-------|
| AGENTS.md | C:/Users/zottt/.config/opencode/AGENTS.md | Global agent instructions (persona, engram protocol, skills) |
| PROJECT_HISTORY.md | C:/Users/zottt/Desktop/mundo-limpio-backend/PROJECT_HISTORY.md | Project evolution, architecture, patterns, next steps |
| README.md | C:/Users/zottt/Desktop/mundo-limpio-backend/README.md | Tech stack, endpoints, execution, testing |

Read the convention files listed above for project-specific patterns and rules. All referenced paths have been extracted — no need to read index files to discover more.
