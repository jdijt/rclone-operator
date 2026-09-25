# CLAUDE.md

rclone-operator is a Kubernetes operator for a homelab cluster that runs [rclone](https://rclone.org) syncs declared
as CRDs, records per-run statistics (transfer speed, bytes and files transferred) in status, and will eventually
enforce resource constraints across syncs (per-upstream connection limits, a cluster-wide bandwidth cap, and similar).
See `README.md` for the goals, feature list and non-goals.

Built with Quarkus and the Java Operator SDK (JOSDK) via the `quarkus-operator-sdk` extension. Java 21, Maven.

## Design decisions

- **Execution model:** rclone runs as `rclone rcd` instances that the operator drives over the rc API (`sync/sync`
  with `_async` + `_group`, `job/status`, `core/stats`, `core/bwlimit`). No Jobs/Pods per sync.
- **Transfer services:** users declare rcd instances as a CRD (name undecided), in cluster-scoped and namespaced
  variants. The operator provisions Deployment + Service + mTLS. A sync references a source remote, a destination
  remote and a transfer service. Services can mount PVCs for `local` endpoints.
- **Remotes:** `RCloneRemote` (namespaced) and `RCloneClusterRemote` (cluster-scoped) share `RCloneRemoteSpec`.
  Cluster remotes resolve Secrets in the operator namespace.
- **Unions:** a `type` discriminator plus one optional field per variant, enforced with CEL rules
  (`@ValidationRule`), following KEP-1027. Planned for the sync trigger too.
- **Template remotes** use `${name}` placeholders filled from Secret keys declared in `inputs`.
- **Constraint scope:** anything that starts a transfer must be routable through a central scheduler/limiter.

## Layout

Package root `eu.derfniw.rco`:

- `api.v1alpha1` — CRD model classes, group `rco.frozenbits.se`. Kinds carry an `RClone` prefix. CRDs are generated
  at build time by the fabric8 CRD generator from the annotations (`@ValidationRule`, `@Required`, `@Size`,
  `@Min`/`@Max`, `@Default`, `@AdditionalPrinterColumn`) into `target/kubernetes/`.
- `validation` — shared by all resource kinds. `ResourceValidator<S>` validates a custom resource and returns
  sorted `FieldError`s (Kubernetes field errors): Hibernate Validator on the spec, then an overridable
  `customValidation` for checks that don't fit a constraint. Every constraint carries one `Reason` payload (the error
  type); offending values go in the dynamic payload. No dependency on the Kubernetes client beyond `CustomResource`.
- `remote` — Jakarta constraints for remote checks CRD markers can't express (`@SelectedBackendPresent`,
  `@DeclaredPlaceholders`, plus `@NotNull`/`@NotBlank` on the model), and `RemoteValidator` (injected into the
  reconcilers and the webhook).
- `controller` — reconcilers. `AbstractRemoteReconciler` holds the logic shared by both remote kinds.
- `webhook` — validating admission webhooks, a plain JAX-RS resource on fabric8's `AdmissionReview` model, served
  under `/webhooks/validate/<plural>`.
- `src/main/kubernetes/kubernetes.yml` — hand-written manifests merged into the generated ones (webhook
  configuration, cert-manager Issuer/Certificate).
- `samples/` — example CRs.

## Commands

```sh
./mvnw verify              # all tests
./mvnw spotless:apply      # format (palantir-java-format) and add license headers
./mvnw quarkus:dev         # run against the current kubeconfig
./mvnw package             # also generates target/kubernetes/*.yml
```

## Testing

- `@QuarkusTest`s with injected beans, also for unit-level logic like the validator and the reconcile logic
  (`@ParameterizedTest` + `argumentSet` for tables). Beans use package-private `@Inject` fields.
- The app starts the operator, so every `@QuarkusTest` needs a real kube-apiserver + etcd from `KubeApiServerResource`
  (fabric8 kube-api-test). It registers the webhooks against the Quarkus test HTTPS port; pass the init arg
  `webhooks=false` to run without them. Quarkus restarts the app for each distinct resource setup, so reuse plain
  `@WithTestResource(KubeApiServerResource.class)` unless a test needs otherwise.
- The running operator writes status concurrently with tests: update with `unlock().edit(...)` or re-fetch first.
- e2e tests against kind (image build, cert-manager, metrics) are not ported yet.

## Conventions

- rclone remote credentials live in Secrets and never in CRD specs, status, logs or events.
- Never put user input into a constraint message template (Hibernate Validator interpolates `{…}` and `${…}`): use
  message parameters or the dynamic payload.
- Status is the source of truth for sync statistics: conditions for state, explicit units in field names
  (`bytesTransferred`, `bytesPerSecond`).
- Reconciliation must be idempotent and safe against restarts: a restart mid-sync must not lose or double-count
  statistics, nor start a duplicate sync. Only patch status when it changed.
- Keep the scheduler/limiter, the rclone rc client and stats accounting free of Kubernetes client/JOSDK types;
  reconcilers adapt between them and Kubernetes.
- RBAC is generated by quarkus-operator-sdk. Rules on the primary resources always get all common verbs, and
  `@RBACRule` / overrides in `src/main/kubernetes/kubernetes.yml` only add to them. Put extra rules on each
  concrete `@ControllerConfiguration` class.
