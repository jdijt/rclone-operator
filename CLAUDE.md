# CLAUDE.md

rclone-operator is a Kubernetes controller for a homelab cluster that schedules
[rclone](https://rclone.org) sync jobs declared as CRDs, records per-job statistics
(transfer speed, bytes and files transferred) in status, and will eventually enforce
resource constraints across jobs (per-upstream connection limits, a cluster-wide bandwidth
cap, and similar). See `README.md` for the goals, feature list and non-goals.

## AI usage policy (read this first)

This is a **learning project**. The author writes the production code themselves to learn
and demonstrate Go. Claude's role is advisory, not authorial.

**Do:**
- Review code and suggest more idiomatic Go, pointing at specific lines.
- Suggest dependencies, testing strategies, architecture options, tradeoffs.
- Generate (additional) test cases, especially rows in table-driven tests. Every
  AI-generated test case or test function **must be marked with a comment**, e.g.
  `// AI-generated test case` on the row or `// AI-generated` above the function.
- Scaffold non-code files when asked (docs, CI config, Makefiles, manifests, sample CRs).
- Explain APIs (controller-runtime, client-go, rclone's CLI flags, rc API and Go packages,
  Kubernetes Job/CronJob semantics) and show small illustrative snippets in chat.

**Don't:**
- Write or edit production Go code under `cmd/`, `api/`, `internal/`, `pkg/` — not even
  small fixes. Describe the change and let the author make it.
- Implement anything in the non-goals listed in `README.md`.
- Give copy-pasteable Go for the production packages.
  Illustrative snippets should use generic names/types so they must be adapted, not pasted.
  (Test cases and non-Go scaffolding are the exceptions above.)

If a request is ambiguous about whether it crosses into "writing the code", ask.

`AGENTS.md` is the generic kubebuilder agent guide that came with the scaffold. This file
takes precedence where they differ.

## Project layout

rclone-operator is a **single binary** (`rclone-operator`) deployed as one **Deployment**
(the kubebuilder default). Reconcilers run on the leader only (controller-runtime leader
election). How rclone itself is executed — as child Jobs/Pods created by the controller,
or in-process via rclone's Go packages — is **not decided yet**; don't assume either in
suggestions, and flag it when a design question hinges on it.

- `cmd/main.go` — the only entrypoint (kubebuilder-managed: keep the
  `+kubebuilder:scaffold:*` markers). Builds the manager and registers reconcilers.
- `internal/controller/` — reconcilers (`kubebuilder create api` puts them here).
- `api/v1alpha1/` — CRD types, group `rco.frozenbits.se` (the domain is the group;
  kubebuilder group is empty, as in ballast). Kinds carry an `Rclone` prefix
  (e.g. `RcloneSync`) so bare kind names never collide in `kubectl`. Uses kubebuilder
  markers; run `make generate manifests` after changing types (see Kubebuilder below).
- `local/` — gitignored scratch space. `local/design.md` is the decision record for the
  architecture (CRD shapes, scheduling model, how stats get from rclone into status,
  constraint enforcement). Read it before proposing design changes, if it exists.

Nothing under `api/` or `internal/` exists yet. Confirm CRD names and shapes with the
author before suggesting the first `kubebuilder create api`.

Module path is `github.com/jdijt/rclone-operator` (Go 1.27). Key deps:
`sigs.k8s.io/controller-runtime`, `k8s.io/apimachinery`, `k8s.io/client-go`.

## Kubebuilder

The project is scaffolded with **kubebuilder** (v4 layout, `go.kubebuilder.io/v4`, see
`PROJECT`). Domain is `rco.frozenbits.se`, project name `rclone-operator`. Kubebuilder owns:

- `PROJECT` — scaffold metadata; edited by `kubebuilder` commands, not by hand.
- `config/` — kustomize manifests: `crd/bases/` (generated CRDs), `rbac/` (generated
  `role.yaml` plus leader-election/metrics roles), `manager/`, `default/`,
  `prometheus/`, `network-policy/`, `samples/` (example CRs, hand-edited).
- `api/<version>/zz_generated.deepcopy.go` — generated; never edit.
- `hack/boilerplate.go.txt` — Apache-2.0 header (`YEAR` placeholder). `make generate`
  stamps it into generated files; `make license` prepends it to any `.go` file lacking it
  and runs automatically as part of `fmt` (so `build`/`run`/`test`). `make license-check`
  fails if a file is missing it (for CI). Project license is Apache-2.0 (`LICENSE`).

Generation is driven by markers in Go comments, which `controller-gen` reads:

- `// +kubebuilder:object:root=true`, `+kubebuilder:subresource:status`,
  `+kubebuilder:printcolumn:...`, `+kubebuilder:resource:shortName=...`
  on CRD types (CRDs are namespaced — the kubebuilder default; don't add `scope=Cluster`
  unless the design record says so).
- `// +kubebuilder:validation:...` / `+optional` / `+kubebuilder:default=` on fields → CRD
  OpenAPI schema.
- `// +kubebuilder:rbac:groups=...,resources=...,verbs=...` on reconcilers → `config/rbac/role.yaml`.

Basic usage:

```sh
kubebuilder create api --group "" --version v1alpha1 --kind Rclone<Kind>   # new CRD + reconciler
make generate    # controller-gen object: DeepCopy methods in zz_generated.deepcopy.go
make manifests   # controller-gen crd/rbac/webhook: config/crd/bases, config/rbac/role.yaml
make test        # runs generate+manifests+fmt+vet, then go test under envtest
make lint        # golangci-lint (config in .golangci.yml)
make install     # apply CRDs to the current kubeconfig cluster
make run         # run the controller locally against the current kubeconfig
make deploy      # kustomize build config/default | kubectl apply
make test-e2e    # Ginkgo e2e suite against a dedicated kind cluster (never a real one)
```

Always run `make generate manifests` after changing anything under `api/` or an RBAC
marker, and commit the regenerated files alongside the change. Tools (`controller-gen`,
`kustomize`, `setup-envtest`, `golangci-lint`) are pinned in the Makefile and installed
into `bin/` on first use.

Keep `+kubebuilder:rbac` verbs minimal. If the controller ends up creating Jobs/Pods for
rclone, it needs RBAC on those core/batch resources and on the Secrets holding rclone
remote configs — call that out explicitly when it comes up.

## Commands

```sh
go build ./...
go test ./...
go vet ./...
make test        # full kubebuilder test pipeline (see above)
```

## Conventions

- Standard Go style: `gofmt`, `go vet` clean; errors wrapped with `%w`; contexts first.
- Prefer the standard library; propose a dependency before it gets added.
- Table-driven tests with `t.Run` subtests for unit tests. The kubebuilder scaffold uses
  Ginkgo/Gomega for envtest and e2e suites; keep that for those suites only.
- Status is the source of truth for job statistics: use `metav1.Condition` for state,
  `metav1.Time` for timestamps, and explicit units in field names (`bytesTransferred`,
  `bytesPerSecond`), never ambiguous numbers.
- Reconciliation must be idempotent and safe against restarts: a controller restart
  mid-sync must not lose or double-count statistics, nor start a duplicate sync.
- rclone remote credentials live in Secrets and never in CRD specs, status, logs or events.
- Keep the resource-constraint goal in mind for every design suggestion: anything that
  starts a transfer should be routable through a central scheduler/limiter later.
