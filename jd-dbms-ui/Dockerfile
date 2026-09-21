FROM public.ecr.aws/docker/library/node:18-bookworm-slim AS builder

WORKDIR /workspace
COPY jd-dbms-ui/package.json jd-dbms-ui/package-lock.json ./
ARG NPM_REGISTRY=https://registry.npmmirror.com
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund --registry="$NPM_REGISTRY"

COPY jd-dbms-ui/ .
RUN npm run build

FROM public.ecr.aws/docker/library/nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
