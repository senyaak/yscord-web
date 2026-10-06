# Architecture

Living diagrams of what runs where and how a change reaches the cluster.
Updated as each stage lands. Dashed boxes are planned, not built yet.

## Runtime: how a request reaches the app

```mermaid
flowchart LR
    browser([Browser]) --> cf["Cloudflare edge<br/>yscord-web.senyaak.work"]

    subgraph cluster["minikube yscord-dev (CNI: Calico)"]
        subgraph ns_edge["ns edge"]
            cfd["cloudflared x2<br/>tunnel k8s"]
            gw["Gateway main"]
        end

        subgraph ns_egs["ns envoy-gateway-system"]
            egc["Envoy Gateway<br/>controller"]
            envoy["Envoy proxy<br/>svc envoy-main"]
        end

        subgraph ns_yscord["ns yscord (NetworkPolicy: default-deny)"]
            route["HTTPRoute yscord"]
            app["Deployment yscord<br/>init: update-yt-dlp<br/>DB_MIGRATE=off"]
            mig["Job yscord-migrate<br/>(per sync, then exits)"]
            pg[("StatefulSet postgres<br/>PVC 1Gi")]
        end

        subgraph ns_kube["ns kube-system"]
            dns["CoreDNS"]
        end
    end

    cf -- "tunnel (dialled out by cloudflared)" --> cfd
    cfd -- ":80" --> envoy
    envoy -- ":8080" --> app
    app -- ":5432" --> pg
    mig -- ":5432" --> pg
    app -- ":53" --> dns
    app -- ":443/:80, no private ranges" --> yt([YouTube, GitHub])

    egc -. "programs from" .-> gw
    egc -. "programs from" .-> route
    egc -. configures .-> envoy
```

## Delivery: how a change reaches the cluster

```mermaid
flowchart LR
    push(["git push main"]) --> build["CI: build + test<br/>image sha-xxxxxxx"]
    build --> ghcr[("GHCR<br/>ghcr.io/senyaak/yscord-web")]

    tag(["git push tag vX.Y.Z"]) --> release["CI release job<br/>tag image X.Y.Z<br/>kustomize edit set image"]
    release -- "add tag, no rebuild" --> ghcr
    release -- "commit: Release vX.Y.Z [skip ci]" --> repo[("GitHub repo, main<br/>k8s/overlays/release")]

    subgraph cluster["minikube yscord-dev"]
        argo["Argo CD<br/>Application yscord<br/>auto-sync, self-heal, prune"]
        subgraph waves["ns yscord, applied in sync waves"]
            w0["wave 0: Postgres, Services,<br/>NetworkPolicies, HTTPRoute"]
            w1["wave 1: Job yscord-migrate<br/>(Sync hook)"]
            w2["wave 2: Deployment yscord"]
            w0 --> w1 -- "only if the Job succeeded" --> w2
        end
        kubelet["kubelet"]
    end

    argo -- "polls ~3 min" --> repo
    argo -- "apply" --> w0
    kubelet -- "pull image" --> ghcr

    hand["By hand (kubectl):<br/>Argo CD install, Application,<br/>Envoy Gateway, k8s/platform,<br/>Secrets"] -.-> cluster

    deployrepo[("separate deploy repo")]:::planned
    appofapps["app-of-apps + sync-waves"]:::planned
    vault[("Vault on the host")]:::planned
    eso["External Secrets Operator"]:::planned

    release -.-> deployrepo
    argo -.-> appofapps
    eso -.-> vault

    classDef planned stroke-dasharray: 5 5,opacity:0.6
```
