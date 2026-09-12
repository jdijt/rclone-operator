# rclone-operator

rclone-operator is a project I took on for the following reasons:

- I want to write non-trivial Kubernetes controller in Go to improve & to demonstrate my skill with the language
- I have a bunch of [rclone](https://rclone.org) syncs in my homelab (backups, mirrors, offloading to cloud storage)
  that are currently ad-hoc CronJobs and scripts. I want them to be:
  - declared as Kubernetes resources with a schema, to make them easier to manage.
  - observable: how fast did the last run go, how much data & how many files were transferred, when did it last succeed.
  - well-behaved as a group: not all hammering the same upstream at once (One of the upstreams is a hetzner storage box, connection limit is tight).

## Feature / todo list

- [ ] CRDs to declare an rclone sync (source, destination, schedule, rclone flags, credentials via Secret).
- [ ] Controller runs the sync on schedule and reports run state via status conditions.
- [ ] Statistics per run in status: duration, transfer speed, bytes and files transferred, errors.
- [ ] Prometheus metrics for the same statistics.
- [ ] Resource constraints across jobs:
  - [ ] per-upstream (remote) connection / concurrency limits
  - [ ] cluster-wide max total bandwidth
  - [ ] ... etc.
- [ ] Sample CRs and an install bundle / Helm chart.

## Architecture

?? Lets see ;) ??

## Cluster/Controller Dependencies

- *Cert manager* The default install assumes cert-manager is installed in the cluster for webhook certificates.

## LLM Usage

As this is a learning project code will be mostly hand-written.

However Claude will be used in this project to:
- Review code and give suggestions on making it more idiomatic Go. Reviews start with hints (where and what kind of issue) and only get to the actual fix when I ask for it.
- Perform mechanistic actions, e.g.: reading CLI documentation and turn it into struct fields for `RCloneRemoteSpec`. The rule here is I define structure, these types/structs exist and this is how they go together, Claude fills in details/struct fields.
- Generate (additional) test cases, especially in table-based tests. These will be marked as such via comment. The first test of each kind in a package is written by me.
- Point me at idiomatic upstream code (stdlib, controller-runtime, rclone) to read before I write something similar.
- In general to generate suggestions/input for:
  - Useful dependencies
  - Testing strategies
  - ... etc..

Recurring mistakes I make, and what idiomatic Go does instead, are tracked in [docs/go-lessons.md](docs/go-lessons.md).
Claude's edit permissions on the Go code are restricted in [.claude/settings.json](.claude/settings.json).

These rules hold until I have a working POC that syncs actual data from an S3 (Ceph) bucket to my storage box. After that I'll revisit and relax them.

See also [CLAUDE.md](CLAUDE.md)

## Non goals

- Replacing rclone's own configuration format: the operator schedules and observes rclone, it does not reinvent it.
- Being a general-purpose job scheduler; this is rclone-specific on purpose.
- TODO: expand as the design settles.

## Getting started

This is a standard [kubebuilder](https://book.kubebuilder.io) project.

```sh
make test                                   # unit tests under envtest
make install                                # install CRDs into the current kubeconfig cluster
make run                                    # run the controller locally
make docker-build docker-push IMG=<registry>/rclone-operator:<tag>
make deploy IMG=<registry>/rclone-operator:<tag>
kubectl apply -k config/samples/
```

`make help` lists all targets. The e2e tests (`make test-e2e`) expect a dedicated
[kind](https://kind.sigs.k8s.io/) cluster, not a real one.

## License

Apache-2.0, see [LICENSE](LICENSE).
