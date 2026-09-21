---
name: containerization-practices
description: Best practices for writing Dockerfiles, docker-compose files, and Kubernetes manifests — multi-stage builds, minimal base images, layer-cache ordering, non-root users, secret handling, and health checks. Use this whenever creating or editing a Dockerfile, docker-compose.yml, or Kubernetes/Helm manifest, whenever a project already contains one of these files and you're changing how the app runs or builds, or when the user asks about containerizing an application, image size, or Docker/Kubernetes best practices. Do not force this skill onto projects with no containerization present unless the user explicitly asks to add it — this is not a universal skill like the others.
metadata:
  category: infrastructure
---

# Containerization Practices

A container image is both a build artifact and a security boundary: its size affects every deploy and every cold start, and its contents define exactly what an attacker gets if the container is ever compromised. Unlike most of the other skills in this set, this one only applies when a project actually uses containers — check for a `Dockerfile`, `docker-compose.yml`, or Kubernetes/Helm manifests before assuming this is relevant.

## Multi-stage builds

Separate the environment that *builds* the application (compilers, dev dependencies, build tools) from the environment that *runs* it. A `node_modules` full of devDependencies, a full Go toolchain, or a compiler doesn't need to ship in the final image — it was only ever needed to produce the artifact.

```dockerfile
FROM node:20 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-slim
WORKDIR /app
COPY --from=build /app/dist ./dist
COPY --from=build /app/node_modules ./node_modules
CMD ["node", "dist/index.js"]
```

The final image only contains what's needed to run — smaller, faster to pull, and with a much smaller attack surface than one that also carries the entire build toolchain.

## Choose a minimal, pinned base image

Prefer slim/minimal or distroless base images over full OS images — fewer packages means fewer potential vulnerabilities and a smaller image to pull on every deploy. Pin to a specific version tag (or better, a digest) rather than `latest`: `latest` means the exact same Dockerfile can produce a different image tomorrow, silently, which breaks reproducibility and makes "it worked yesterday" bugs much harder to track down.

## Order instructions for cache efficiency

Docker caches each layer and invalidates everything after the first changed layer. Put things that change rarely (installing dependencies) before things that change on every commit (copying application source):

```dockerfile
COPY package*.json ./
RUN npm ci
COPY . .
```

If `COPY . .` came before `RUN npm ci`, every source change would invalidate the dependency-install layer too, turning a five-second cached build into a multi-minute one on every single change.

## Use a `.dockerignore`

Exclude `.git`, `node_modules` (if installed fresh in the build stage), local env files, build artifacts, and anything not actually needed inside the image. Without it, the entire build context — including secrets in a local `.env` someone forgot about — gets sent to the Docker daemon and can end up baked into a layer.

## Never bake secrets into image layers

Don't `COPY` a credentials file or `ARG`/`ENV` a secret value directly into the image — even if a later layer deletes it, it persists in the layer history and can be extracted from the image. Use build-time secret mounts (`--mount=type=secret` in modern Docker), or inject secrets at runtime via environment variables from a secret manager / orchestrator secret store, never at build time.

## Run as a non-root user

By default, a process inside a container that isn't given an explicit user runs as root — if the application is compromised, the attacker has root inside the container, which is a much larger foothold than a normal user account. Create and switch to an unprivileged user near the end of the Dockerfile:

```dockerfile
RUN useradd --create-home appuser
USER appuser
```

## Define health checks

Add a `HEALTHCHECK` (Docker) or readiness/liveness probes (Kubernetes) so the orchestrator can detect a container that's running but not actually serving traffic correctly, and restart or stop routing to it accordingly. Without one, a hung process looks "up" indefinitely from the orchestrator's point of view — the container is running, it's just not doing anything useful.

## One primary process per container

Keep each container scoped to a single responsibility (one process, one service) rather than running multiple unrelated services inside it via a process supervisor. This mirrors single-responsibility at the code level: independent scaling, independent restarts, and independent logs/health checks all depend on the container boundary actually corresponding to one thing that can fail or scale on its own.

## Keep local and production configuration honest

`docker-compose.yml` for local development and the actual deployment manifests (Kubernetes, ECS, etc.) inevitably diverge somewhat, but keep the divergence deliberate and documented — same base image, same key environment variable names, same health-check behavior — so "works in compose" is actually predictive of "works in production," rather than the two environments quietly drifting into being different applications that happen to share a Dockerfile.
