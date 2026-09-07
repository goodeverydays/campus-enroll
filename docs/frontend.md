# Phase 8 frontend and Gateway boundary

## User flow

The Vue application is a student-facing client for the existing APIs. It never
collects a legacy-system password and never accepts a student ID from the browser.
The supported flow is:

1. the legacy system issues a one-time SSO ticket;
2. it redirects to `/sso?ticket=...`;
3. the frontend exchanges that ticket through the Gateway;
4. the short-lived JWT is stored in browser local storage;
5. the Gateway validates the JWT, removes any browser-supplied `X-Student-Id`, and
   injects the trusted `student_id` claim for internal services.

The client supports public course and teaching-section browsing, SSO exchange,
student profile display, asynchronous enrollment with final-state polling, active
enrollment listing, and course withdrawal.

## Local development

Start the backend Compose stack, then run the Vite development server:

```powershell
cd frontend
npm ci
npm run dev
```

Vite listens on `http://127.0.0.1:5173` and proxies `/api` to the Gateway on
`http://localhost:18000`. The normal Compose stack builds the frontend into Nginx
and exposes the complete application at `http://localhost:15173`.

## Gateway controls

The frontend Nginx container proxies only `/api` to `gateway-service`; internal
service ports are not used by browser code. The Gateway adds or validates
`X-Request-Id` and returns it as a response header while downstream services place
the same value in the standard response envelope.

Enrollment and withdrawal writes use Spring Cloud Gateway's Redis token-bucket
limiter. The key is the validated JWT `student_id`, never a client-supplied header.
Defaults allow five tokens per second with a burst of ten and may be adjusted with:

```text
GATEWAY_ENROLLMENT_REPLENISH_RATE
GATEWAY_ENROLLMENT_BURST_CAPACITY
```

Read-only enrollment queries and request-status polling use a separate route and
are not charged against the write limiter.

## Verification

```powershell
cd frontend
npm test
npm run build
cd ..
.\scripts\verify-phase8.ps1
```

The runtime verifier checks Nginx health, production assets, SPA deep links, API
proxying, request-ID propagation, SSO exchange, concurrent Redis rate limiting,
and cleanup of temporary authentication data.
