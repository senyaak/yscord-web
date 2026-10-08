# Roadmap

A learning project: Kubernetes, GitOps, observability and Kafka, built step by
step around the yscord player. Each finished topic gets a cheat sheet in
[lessons/](../lessons/); [architecture.md](architecture.md) shows the current state.

## Done

| Stage | What | Lesson |
|---|---|---|
| 0–1 | minikube, kubectl; Postgres StatefulSet with a PVC, Secret, headless Service | |
| 2 | App Deployment, probes, resources; Kustomize base + local/release overlays; rollouts and rollbacks | |
| 3 | Gateway API (Envoy Gateway), in-cluster Cloudflare tunnel; yt-dlp YouTube-only allowlist, no raw errors to clients | |
| 3c | Blue/green cluster migration to Calico; NetworkPolicies (default-deny, DNS, per-app flows) | [001](../lessons/001.cluster-migration.md), [002](../lessons/002.network-policies-and-dns.md) |
| 3d | yt-dlp kept fresh: init container self-update + Renovate-pinned version | |
| 3b | Argo CD, auto-sync/self-heal/prune; release by git tag | [003](../lessons/003.argocd-releases-migrations.md) |
| 3e | Migrations: SQL files generated from the model, checksums, advisory lock, guard tests (schema in sync, seeded data); migration Job gating the rollout | [003](../lessons/003.argocd-releases-migrations.md) |

## In progress: 3f — declarative bootstrap

1. ✅ Deploy repo [yscord-deploy](https://github.com/senyaak/yscord-deploy): the release job publishes manifests + image version in one commit; main never touches the cluster.
2. ✅ App of apps with sync waves; Argo CD manages itself; Envoy Gateway and the platform move to the deploy repo.
3. Secrets from an external store via External Secrets Operator (lab: local OpenBao on the host). One manual secret left: the store credential ("secret zero").
4. Idempotent bootstrap script with preflight checks; prove it on a fresh cluster (blue/green migration #3), then delete the old one. Lesson 004.

## Next

| Stage | What |
|---|---|
| 4 | Prometheus + Grafana. Business metrics (listeners, commands, yt-dlp latency, cache hits); cloudflared metrics; alerts on "site down" and restarts. Side task: audio cache eviction (LRU by last play, size and count caps, never the current or next track). |
| 5 | Kafka via Strimzi; the player publishes domain events. |
| 6 | stats-service: its own Gradle module, image and Deployment; consumes events, aggregates into Postgres, REST API. |
| 7 | Stats tab in the UI, Grafana panels on Postgres, track autocomplete from the catalog built from events. |
| 8 | Several player replicas without a leader: a replicated state machine over a single-partition event topic. |

## Later

1. Staging environment: main → staging, a tag promotes the image staging verified (brings back "build once, promote"); e2e tests.
2. Postgres replication.
3. Cache eviction demo with a tiny `emptyDir` size limit (pod gets evicted).
4. Local dev loop that coexists with Argo CD self-heal.
5. Argo CD admin access: change the admin password and delete
   `argocd-initial-admin-secret`, or log in through SSO (Dex with GitHub).
6. Rotate the External Secrets AppRole `secret_id` instead of one that never expires.
7. REST handler: replace echoing `IllegalArgumentException` messages with an own exception type.
