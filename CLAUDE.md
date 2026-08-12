# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

# Project Context Rules

Before performing project tasks:

1. Read PROJECT_CONTEXT.md first.
2. For cross-project tasks, read `/data/projects/SYSTEM_CONTEXT.md`.
3. Do not scan the whole repository by default.
4. Use PROJECT_CONTEXT.md to locate relevant files first (this repo is packaged by `auth`/`role`/`permission`/`application`/`common` — see PROJECT_CONTEXT.md §4).
5. Read source code when implementation details are required.
6. Code is the final source of truth.
7. After modifying code, determine whether PROJECT_CONTEXT.md needs to be updated — especially the RBAC tables (`auth_user`/`auth_role`/`auth_permission`/`auth_user_role`/`auth_role_permission`) and the ER diagram in §7.
8. Update PROJECT_CONTEXT.md only when project navigation or important architectural/business facts changed.
9. For cross-service architectural changes, also check:
   - `/data/projects/gateway-service/PROJECT_CONTEXT.md` (depends on `/internal/token/validate`'s response shape)
   - `/data/projects/wms/wms-system/PROJECT_CONTEXT.md` (depends on `/auth/context`'s response shape)
   - `/data/projects/SYSTEM_CONTEXT.md`
10. If documentation conflicts with code: CODE IS SOURCE OF TRUTH. If the conflict involves explicit system architecture conventions (e.g. Auth is no longer the unified identity center, or its port isn't 8081 anymore), report the conflict before changing architecture.

## Known Gap (confirmed when this doc was written — don't re-investigate from scratch)

`/internal/token/validate` has no access control of its own (no header check, no IP allowlist in code) — its safety depends entirely on network-level isolation of port 8081. Confirm whether that isolation actually exists before assuming this endpoint is protected. See PROJECT_CONTEXT.md §13.
