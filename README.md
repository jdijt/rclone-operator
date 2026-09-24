# rclone-operator

A Kubernetes operator for my homelab that runs [rclone](https://rclone.org) syncs declared as custom resources.

I have a bunch of rclone syncs (backups, mirrors, offloading to cloud storage) that are currently ad-hoc CronJobs and
scripts. I want them to be:

- declared as Kubernetes resources with a schema, to make them easier to manage.
- observable: how fast did the last run go, how much data & how many files were transferred, when did it last succeed.
- well-behaved as a group: not all hammering the same upstream at once (one of the upstreams is a Hetzner storage box,
  and its connection limit is tight).

It is built with [Quarkus](https://quarkus.io) and the [Java Operator SDK](https://javaoperatorsdk.io).

## Feature / todo list

- [x] `RCloneRemote` / `RCloneClusterRemote`: rclone remotes (sftp, s3, crypt, free-form template) with credentials
  taken from Secrets, validated by CEL rules and an admission webhook.
- [ ] CRDs to declare an rclone sync (source, destination, schedule, rclone flags).
- [ ] Controller runs the sync on schedule and reports run state via status conditions.
- [ ] Statistics per run in status: duration, transfer speed, bytes and files transferred, errors.
- [ ] Prometheus metrics for the same statistics.
- [ ] Resource constraints across jobs:
  - [ ] per-upstream (remote) connection / concurrency limits
  - [ ] cluster-wide max total bandwidth
  - [ ] ... etc.
- [ ] Sample CRs and an install bundle / Helm chart.
- [ ] Narrow the generated RBAC. quarkus-operator-sdk gives the primary resources all common verbs (incl. create,
  delete, finalizers), while the operator only needs get/list/watch plus get/update/patch on status. `@RBACRule` and
  overrides in `src/main/kubernetes/kubernetes.yml` only add rules. Check newer extension versions or raise it
  upstream.

## Cluster dependencies

- *cert-manager*: the generated manifests use it to issue the webhook serving certificate and inject its CA into the
  `ValidatingWebhookConfiguration`.

## Non goals

- Replacing rclone's own configuration format: the operator schedules and observes rclone, it does not reinvent it.
- Being a general-purpose job scheduler; this is rclone-specific on purpose.
- TODO: expand as the design settles.

## Getting started

```sh
./mvnw verify                 # unit tests, plus API-server tests against a real kube-apiserver (kube-api-test)
./mvnw quarkus:dev            # run the operator against the current kubeconfig; applies the CRDs
./mvnw package                # CRDs, RBAC, Deployment etc. are generated into target/kubernetes/
kubectl apply -f samples/
```

The API-server tests download `kube-apiserver` and `etcd` to `~/.kubeapitest` on first use. Code is formatted with
palantir-java-format via Spotless: `./mvnw spotless:apply`.

## LLM usage

Claude is used as a coding assistant on this project.

## License

Apache-2.0, see [LICENSE](LICENSE).
