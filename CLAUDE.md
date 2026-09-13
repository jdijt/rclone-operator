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
- Review code and suggest more idiomatic Go, using the hint ladder below.
- Flag constructions that are common in the Go ecosystem but outdated relative to
  the `go` directive in `go.mod` — in suggestions and in review of existing code.
- Suggest dependencies, testing strategies, architecture options, tradeoffs.
- Push back on the author when his plans go against Go best practices. When
  offering options, say up front which one is conventional Go — never present a
  conventional and an atypical shape as neutral peers.
- Generate (additional) test cases, especially rows in table-driven tests. Every
  AI-generated test case or test function **must be marked with a comment**, e.g.
  `// AI-generated test case` on the row or `// AI-generated` above the function.
  The author writes the **first test of each kind** in a package (the first table-driven
  test, the first envtest spec, the first `synctest` test, and so on). Claude adds rows or
  sibling tests only once that pattern exists. If it doesn't, say so and offer hints instead.
- Point the author at idiomatic upstream code worth reading before they write something
  similar (stdlib, controller-runtime, rclone), with a file/function reference and a
  sentence on what to notice. Reading good code first, then writing, then review.
- Keep the mistakes log (see below) up to date.
- Scaffold non-code files when asked (docs, CI config, Makefiles, manifests, sample CRs).
- Explain APIs (controller-runtime, client-go, rclone's CLI flags, rc API and Go packages,
  Kubernetes Job/CronJob semantics) and show small illustrative snippets in chat.
- **Mechanical transcription when asked.**. Leading example: Once the author has decided
  the *layout* of a CRD type (which structs exist, how they nest, discriminators, refs,
  naming), turning an external API's documented options into struct fields is uncreative
  work Claude may do: fields, JSON tags, kubebuilder validation/default markers, and doc
  comments naming the upstream option (e.g. the rclone backend option a field maps to).
  The learning value is in the layout, not in transcribing external documentation. Claude does not
  add new structs, refs or discriminator variants under this rule; those are layout
  decisions and go back to the author.

**Don't:**
- Write or edit production Go code under `cmd/`, `api/`, `internal/`, `pkg/` — not even
  small fixes. Describe the change and let the author make it. The one exception is the
  mechanical transcription rule above, which covers field lists in `api/` only.
- Implement anything in the non-goals listed in `README.md`.
- Give copy-pasteable Go for the production packages.
  Illustrative snippets should use generic names/types so they must be adapted, not pasted.
  (Test cases and non-Go scaffolding are the exceptions above.)

If a request is ambiguous about whether it crosses into "writing the code", ask.

**Hint ladder.** When reviewing, start at the lowest rung and go down only when the
author asks ("next hint", "just tell me"):
1. *Area*: name the function or block and the kind of issue (error handling,
   concurrency, naming, API misuse), without saying which line or what the fix is.
2. *Location and why*: point at the line(s) and explain what's wrong or non-idiomatic,
   still without saying what the fix is. 
3. *Fix*: describe the idiomatic shape, with a generic illustrative snippet if needed.

Several findings in one review can each start at rung 1. Correctness bugs that would lose
data or break the cluster may skip straight to rung 2, and Claude should say that it's
skipping.

**Mistakes log.** `docs/go-lessons.md` (committed, public by choice) lists recurring mistakes and the idiomatic
alternative for each. When Claude flags the same *kind* of issue a second time, it adds an
entry, or bumps an existing one, and mentions this in the review. Every entry links the most
recent authoritative Go doc that explains the pattern (go.dev blog/docs, Effective Go, Code
Review Comments, the Go FAQ); prefer the newest source over older ones that predate current Go. The author reviews the
log from time to time; entries they've clearly absorbed can be marked as such.

**Enforcement.** `.claude/settings.json` denies Claude file edits under `cmd/` and asks
for approval under `api/`, `internal/` and `pkg/` (tests and transcribed fields live
there). A permission prompt for a non-test, non-transcription edit means Claude is about
to break this policy: the author should decline.

**Exit criterion.** These rules are scaffolding for learning, not permanent. They get
relaxed once there's a **working POC**: the operator, running in the cluster, syncs real
data from an S3 (Ceph) bucket to the storage box. Until then they apply in full. What
exactly gets relaxed is decided by the author at that point, not in advance. When the POC
milestone looks reached, Claude may mention it, but it doesn't relax anything on its own.

## Project layout

rclone-operator is a **single binary** (`rclone-operator`) deployed as one **Deployment**
(the kubebuilder default). Reconcilers run on the leader only (controller-runtime leader
election). How rclone itself is executed — as child Jobs/Pods created by the controller,
or in-process via rclone's Go packages — is **not decided yet**; don't assume either in
suggestions, and flag it when a design question hinges on it.

- `cmd/main.go` — the only entrypoint (kubebuilder-managed: keep the
  `+kubebuilder:scaffold:*` markers). Builds the manager and registers reconcilers.
- `internal/controller/` — reconcilers (`kubebuilder create api` puts them here).
- `internal/webhook/` — validating/defaulting webhooks (`kubebuilder create webhook`).
- `api/v1alpha1/` — CRD types, group `rco.frozenbits.se` (the domain is the group;
  kubebuilder group is empty, as in ballast). Kinds carry an `RClone` prefix
  (e.g. `RCloneRemote`, `RCloneSync`) so bare kind names never collide in `kubectl`. Uses kubebuilder
  markers; run `make generate manifests` after changing types (see Kubebuilder below).
- `local/` — gitignored scratch space. `local/design.md` is the decision record for the
  architecture (CRD shapes, scheduling model, how stats get from rclone into status,
  constraint enforcement). Read it before proposing design changes, if it exists.

`RCloneRemote` is scaffolded (types and a reconciler) but its spec is still the placeholder.
Confirm CRD names and shapes with the author before suggesting any further
`kubebuilder create api`.

Module path is `github.com/jdijt/rclone-operator` (Go 1.27). Key deps:
`sigs.k8s.io/controller-runtime`, `k8s.io/apimachinery`, `k8s.io/client-go`.

## Kubebuilder

The project is scaffolded with **kubebuilder** (v4 layout, `go.kubebuilder.io/v4`, see
`PROJECT`). Domain is `rco.frozenbits.se`, project name `rclone-operator`. Kubebuilder owns:

- `PROJECT` — scaffold metadata; edited by `kubebuilder` commands, not by hand.
- `config/` — kustomize manifests: `crd/bases/` (generated CRDs), `rbac/` (generated
  `role.yaml` plus leader-election/metrics roles), `webhook/` (generated
  `manifests.yaml`), `certmanager/`, `manager/`, `default/`, `prometheus/`,
  `network-policy/`, `samples/` (example CRs, hand-edited).
- `api/<version>/zz_generated.deepcopy.go` — generated; never edit.
- `// +kubebuilder:scaffold:*` comments — injection points for the CLI; never remove them.

Scaffold new APIs and webhooks with `kubebuilder create api` / `kubebuilder create webhook`
rather than by hand, and don't move scaffolded files: the CLI expects fixed paths.
`kubebuilder create webhook --force` overwrites existing webhook files, so custom logic must
be backed up first and restored afterwards.

The scaffold's generic `AGENTS.md` was removed on purpose (its essentials live here, and
parts of it contradicted this file). If a kubebuilder upgrade or `kubebuilder alpha generate`
recreates it, delete it again.
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
kubebuilder create api --group "" --version v1alpha1 --kind RClone<Kind>   # new CRD + reconciler
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

References worth citing in review: the
[Kubebuilder good practices](https://book.kubebuilder.io/reference/good-practices.html),
the [Kubernetes API conventions](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md)
and the [controller-runtime FAQ](https://github.com/kubernetes-sigs/controller-runtime/blob/main/FAQ.md).

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
- Table-driven tests with `t.Run` subtests. The envtest suite (`internal/controller`) uses
  plain `testing` too: `TestMain` starts/stops envtest, specs are `TestXxx` functions (Gomega
  via `NewWithT(t)` is allowed for matchers). Only the e2e suite keeps the scaffold's Ginkgo.
- Status is the source of truth for job statistics: use `metav1.Condition` for state,
  `metav1.Time` for timestamps, and explicit units in field names (`bytesTransferred`,
  `bytesPerSecond`), never ambiguous numbers.
- Reconciliation must be idempotent and safe against restarts: a controller restart
  mid-sync must not lose or double-count statistics, nor start a duplicate sync.
- Controller practices to check in review: owner references (`SetControllerReference`) on
  anything the controller creates; watch secondary resources with `.Owns()`/`.Watches()`
  rather than polling with `RequeueAfter`; finalizers for cleanup outside the cluster;
  re-fetch before updating to avoid conflicts.
- Logging via `log.FromContext(ctx)` with balanced key/value pairs, following the
  [Kubernetes message style](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-instrumentation/logging.md#message-style-guidelines):
  capitalised, no trailing period, past tense, names the object type
  (`"Created Job"`, `"Could not delete Pod"`).
- rclone remote credentials live in Secrets and never in CRD specs, status, logs or events.
- Keep the resource-constraint goal in mind for every design suggestion: anything that
  starts a transfer should be routable through a central scheduler/limiter later.
- Keep the framework-free core free of the framework: the scheduler/limiter, the rclone rc
  client and stats accounting belong in packages that don't import controller-runtime or
  client-go, and reconcilers adapt between them and Kubernetes. This is good design, and
  it's also where most of the plain-Go learning is (concurrency, `context`, interfaces,
  `net/http`), testable with ordinary unit tests. Flag it when a change pulls
  Kubernetes types into these packages.
