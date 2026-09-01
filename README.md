# rclone-operator

rclone-operator is a project I took on for the following reasons:

- I want to write non-trivial Kubernetes controller in Go to improve & to demonstrate my skill with the language
- I have a bunch of [rclone](https://rclone.org) syncs in my homelab (backups, mirrors, offloading to cloud storage)
  that are currently ad-hoc CronJobs and scripts. I want them to be:
  - declared as Kubernetes resources, so they are versioned and reviewed like everything else in the cluster.
  - observable: how fast did the last run go, how much data & how many files were transferred, when did it last succeed.
  - well-behaved as a group: not all hammering the same upstream at once, not saturating my uplink.
  - Easy to configure declaratively, re-use existing file-systems for multiple jobs, set up encryption, etc.

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


## LLM Usage

As this is a learning project code will be mostly hand-written.

However Claude will be used in this project to:
- Review code and give suggestions on making it more idiomatic Go.
- Generate (additional) test cases, especially in table-based tests. These will be marked as such via comment.
- In general to generate suggestions/input for:
  - Useful dependencies
  - Testing strategies
  - ... etc..

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
