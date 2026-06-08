# Tasks: fix/error-handling-consistency

> Change: `fix/error-handling-consistency`

## Phase 1: Core handlers (PR1)

- [x] 1. ProductionBatchExceptionHandler → ErrorResponse
- [x] 2. BulkProductExceptionHandler → ErrorResponse
- [x] 3. ReceiptExceptionHandler → ErrorResponse
- [x] 4. SaleController 404 con ErrorResponse body

## Phase 2: Rate limit + Global handlers (PR2)

- [ ] 5. RateLimitFilter 429 con timestamp y path
- [ ] 6. 4 nuevos handlers HTTP en GlobalExceptionHandler
